package com.antigravity.deck

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.deck.hardware.DeckNotificationManager
import com.antigravity.deck.hardware.HapticsController
import com.antigravity.deck.hardware.NotificationActionBus
import com.antigravity.deck.hardware.PushToTalkManager
import com.antigravity.deck.hardware.SpeechUiState
import com.antigravity.deck.model.AgentSession
import com.antigravity.deck.model.AgentStatus
import com.antigravity.deck.model.DeckEndpointConfig
import com.antigravity.deck.model.ProtocolCodec
import com.antigravity.deck.model.ReasoningEffort
import com.antigravity.deck.model.ServerEvent
import com.antigravity.deck.network.ConnectionStatus
import com.antigravity.deck.network.DeckWebSocketClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DeckUiState(
    val session: AgentSession = AgentSession.initial(),
    val availableModels: List<String> = AgentSession.DEFAULT_AVAILABLE_MODELS,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val endpointConfig: DeckEndpointConfig = DeckEndpointConfig.usbLocalhost(),
    val speechState: SpeechUiState = SpeechUiState(),
    val feedbackBanner: String? = null,
    val showPairingSheet: Boolean = false
)

class DeckViewModel(application: Application) : AndroidViewModel(application) {

    private val haptics = HapticsController(application)
    private val notifications = DeckNotificationManager(application)
    private val wsClient = DeckWebSocketClient()
    private val pushToTalk = PushToTalkManager(application) { transcript ->
        sendPrompt(transcript)
    }

    private val _uiState = MutableStateFlow(DeckUiState())
    val uiState: StateFlow<DeckUiState> = _uiState.asStateFlow()

    private var hasReceivedSync = false

    init {
        viewModelScope.launch {
            wsClient.connectionStatus.collect { status ->
                _uiState.update { it.copy(connectionStatus = status) }
            }
        }

        viewModelScope.launch {
            wsClient.endpointConfig.collect { config ->
                _uiState.update { it.copy(endpointConfig = config) }
            }
        }

        viewModelScope.launch {
            pushToTalk.state.collect { speech ->
                _uiState.update { it.copy(speechState = speech) }
            }
        }

        viewModelScope.launch {
            wsClient.events.collect { event ->
                handleServerEvent(event)
            }
        }

        viewModelScope.launch {
            NotificationActionBus.actions.collect { action ->
                handleNotificationAction(action.approved)
            }
        }

        // 起動直後はUSBリバース接続プリセットへの接続を自動試行する
        wsClient.connectUsbPreset()
    }

    private fun handleNotificationAction(approved: Boolean) {
        haptics.triggerSelectionClick()
        val payload = if (approved) {
            ProtocolCodec.commandApprove()
        } else {
            ProtocolCodec.commandReject()
        }
        val sent = wsClient.send(payload)
        val label = if (approved) "承認" else "拒否"
        _uiState.update {
            it.copy(
                feedbackBanner = if (sent) {
                    "通知から操作を${label}しました"
                } else {
                    "未接続のため送信できませんでした"
                }
            )
        }
    }

    private fun handleServerEvent(event: ServerEvent) {
        when (event) {
            is ServerEvent.StateSync -> {
                val prevStatus = _uiState.value.session.status
                val updated = event.session

                if (hasReceivedSync && prevStatus != updated.status) {
                    triggerStatusHaptics(updated.status)
                    notifications.notifySessionTransition(updated)
                }
                hasReceivedSync = true

                _uiState.update {
                    it.copy(
                        session = updated,
                        availableModels = event.availableModels ?: it.availableModels
                    )
                }
            }

            is ServerEvent.AuthAck -> {
                if (!event.authenticated) {
                    _uiState.update {
                        it.copy(feedbackBanner = event.message ?: "認証トークンが一致しません")
                    }
                }
            }
        }
    }

    private fun triggerStatusHaptics(status: AgentStatus) {
        when (status) {
            AgentStatus.WAITING_APPROVAL -> haptics.triggerWaitingApproval()
            AgentStatus.DONE -> haptics.triggerDone()
            AgentStatus.ERROR -> haptics.triggerError()
            else -> Unit
        }
    }

    fun approve() {
        haptics.triggerSelectionClick()
        notifications.cancel()
        val sent = wsClient.send(ProtocolCodec.commandApprove())
        _uiState.update {
            it.copy(
                feedbackBanner = if (sent) "承認コマンドを送信しました" else "未接続のため送信できませんでした"
            )
        }
    }

    fun reject() {
        haptics.triggerSelectionClick()
        notifications.cancel()
        val sent = wsClient.send(ProtocolCodec.commandReject())
        _uiState.update {
            it.copy(
                feedbackBanner = if (sent) "拒否コマンドを送信しました" else "未接続のため送信できませんでした"
            )
        }
    }

    fun sendPrompt(promptText: String) {
        val trimmed = promptText.trim()
        if (trimmed.isEmpty()) return
        val sent = wsClient.send(ProtocolCodec.commandSendPrompt(trimmed))
        _uiState.update {
            it.copy(
                feedbackBanner = if (sent) {
                    "プロンプト送信: $trimmed"
                } else {
                    "未接続のため送信できませんでした"
                }
            )
        }
    }

    fun setReasoningEffort(effort: ReasoningEffort) {
        haptics.triggerSelectionClick()
        // 画面応答性を優先してローカル状態を先行更新する
        _uiState.update { state ->
            state.copy(
                session = state.session.copy(reasoningEffort = effort)
            )
        }
        wsClient.send(ProtocolCodec.commandSetReasoningEffort(effort))
    }

    fun setModel(modelName: String) {
        val trimmed = modelName.trim()
        if (trimmed.isEmpty()) return
        haptics.triggerSelectionClick()
        _uiState.update { state ->
            state.copy(
                session = state.session.copy(modelName = trimmed),
                feedbackBanner = "モデルを $trimmed に切り替えました"
            )
        }
        wsClient.send(ProtocolCodec.commandSetModel(trimmed))
    }

    fun connectUsbPreset() {
        haptics.triggerSelectionClick()
        hasReceivedSync = false
        wsClient.connectUsbPreset()
        _uiState.update { it.copy(feedbackBanner = "USB localhost:8765 へ接続を試行しています") }
    }

    fun applyPairingRawInput(raw: String): Boolean {
        val config = ProtocolCodec.parsePairingInput(raw) ?: return false
        haptics.triggerSelectionClick()
        hasReceivedSync = false
        wsClient.connect(config)
        _uiState.update {
            it.copy(
                showPairingSheet = false,
                feedbackBanner = "${config.label} へ接続しています"
            )
        }
        return true
    }

    fun triggerSimulation() {
        haptics.triggerSelectionClick()
        val sent = wsClient.send(ProtocolCodec.commandSimulateLifecycle())
        _uiState.update {
            it.copy(
                feedbackBanner = if (sent) {
                    "テストライフサイクルを開始しました"
                } else {
                    "未接続のためシミュレーションを開始できませんでした"
                }
            )
        }
    }

    fun openPairingSheet() {
        _uiState.update { it.copy(showPairingSheet = true) }
    }

    fun closePairingSheet() {
        _uiState.update { it.copy(showPairingSheet = false) }
    }

    fun startPushToTalk() {
        haptics.triggerSelectionClick()
        pushToTalk.onPressStart()
    }

    fun stopPushToTalk() {
        pushToTalk.onPressRelease()
    }

    fun clearBanner() {
        _uiState.update { it.copy(feedbackBanner = null) }
    }

    override fun onCleared() {
        super.onCleared()
        pushToTalk.release()
        wsClient.release()
    }
}
