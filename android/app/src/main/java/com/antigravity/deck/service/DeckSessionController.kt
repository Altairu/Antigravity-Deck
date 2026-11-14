package com.antigravity.deck.service

import android.app.Notification
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.antigravity.deck.hardware.DeckNotificationManager
import com.antigravity.deck.hardware.HapticsController
import com.antigravity.deck.hardware.NotificationActionBus
import com.antigravity.deck.model.AgentSession
import com.antigravity.deck.model.AgentStatus
import com.antigravity.deck.model.DeckEndpointConfig
import com.antigravity.deck.model.ProtocolCodec
import com.antigravity.deck.model.ReasoningEffort
import com.antigravity.deck.model.ServerEvent
import com.antigravity.deck.network.ConnectionStatus
import com.antigravity.deck.network.DeckWebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DeckSessionSnapshot(
    val session: AgentSession = AgentSession.initial(),
    val availableModels: List<String> = AgentSession.DEFAULT_AVAILABLE_MODELS,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val endpointConfig: DeckEndpointConfig = DeckEndpointConfig.usbLocalhost(),
    val feedbackBanner: String? = null
)

// アプリ全体で生存し、バックグラウンドでもWebSocket接続と通知や振動を維持するコントローラー
class DeckSessionController private constructor(
    private val appContext: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val haptics = HapticsController(appContext)
    private val notifications = DeckNotificationManager(appContext)
    private val wsClient = DeckWebSocketClient()

    private val initialEndpoint = loadSavedEndpoint()
    private val _state = MutableStateFlow(DeckSessionSnapshot(endpointConfig = initialEndpoint))
    val state: StateFlow<DeckSessionSnapshot> = _state.asStateFlow()

    private var hasReceivedSync = false

    init {
        scope.launch {
            wsClient.connectionStatus.collect { status ->
                _state.update { it.copy(connectionStatus = status) }
            }
        }

        scope.launch {
            wsClient.endpointConfig.collect { config ->
                _state.update { it.copy(endpointConfig = config) }
            }
        }

        scope.launch {
            wsClient.events.collect { event ->
                handleServerEvent(event)
            }
        }

        scope.launch {
            NotificationActionBus.actions.collect { action ->
                handleNotificationAction(action.approved)
            }
        }

        registerNetworkCallback()
        wsClient.connect(initialEndpoint)
    }

    private fun loadSavedEndpoint(): DeckEndpointConfig {
        val host = prefs.getString(KEY_HOST, null)?.trim().orEmpty()
        if (host.isEmpty()) {
            return DeckEndpointConfig.usbLocalhost()
        }
        val port = prefs.getInt(KEY_PORT, DeckEndpointConfig.DEFAULT_PORT)
        val token = prefs.getString(KEY_TOKEN, DeckEndpointConfig.DEFAULT_TOKEN)
            ?: DeckEndpointConfig.DEFAULT_TOKEN
        val label = prefs.getString(KEY_LABEL, null) ?: if (host == "127.0.0.1") {
            "USB localhost:$port"
        } else {
            "LAN ($host:$port)"
        }
        return DeckEndpointConfig(host = host, port = port, token = token, label = label)
    }

    private fun saveEndpoint(config: DeckEndpointConfig) {
        prefs.edit()
            .putString(KEY_HOST, config.host)
            .putInt(KEY_PORT, config.port)
            .putString(KEY_TOKEN, config.token)
            .putString(KEY_LABEL, config.label)
            .apply()
    }

    private fun registerNetworkCallback() {
        val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            connectivityManager.registerNetworkCallback(
                request,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        scope.launch {
                            val currentStatus = wsClient.connectionStatus.value
                            if (
                                currentStatus is ConnectionStatus.Disconnected ||
                                currentStatus is ConnectionStatus.Reconnecting
                            ) {
                                wsClient.connect(_state.value.endpointConfig)
                            }
                        }
                    }
                }
            )
        } catch (_: Exception) {
            // 端末制約でネットワーク監視を登録できない場合は自動再接続タイマーに委ねる
        }
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
        _state.update {
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
                val prevStatus = _state.value.session.status
                val updated = event.session

                if (hasReceivedSync && prevStatus != updated.status) {
                    triggerStatusHaptics(updated.status)
                    notifications.notifySessionTransition(updated)
                }
                hasReceivedSync = true

                _state.update {
                    it.copy(
                        session = updated,
                        availableModels = event.availableModels ?: it.availableModels
                    )
                }
            }

            is ServerEvent.AuthAck -> {
                if (!event.authenticated) {
                    _state.update {
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

    fun buildForegroundNotification(): Notification {
        val current = _state.value
        return notifications.buildForegroundNotification(current.session, current.connectionStatus)
    }

    fun updateForegroundNotification(
        session: AgentSession,
        connectionStatus: ConnectionStatus
    ) {
        notifications.updateForegroundNotification(session, connectionStatus)
    }

    fun approve() {
        haptics.triggerSelectionClick()
        notifications.cancel()
        val sent = wsClient.send(ProtocolCodec.commandApprove())
        _state.update {
            it.copy(
                feedbackBanner = if (sent) "承認コマンドを送信しました" else "未接続のため送信できませんでした"
            )
        }
    }

    fun reject() {
        haptics.triggerSelectionClick()
        notifications.cancel()
        val sent = wsClient.send(ProtocolCodec.commandReject())
        _state.update {
            it.copy(
                feedbackBanner = if (sent) "拒否コマンドを送信しました" else "未接続のため送信できませんでした"
            )
        }
    }

    fun sendPrompt(promptText: String) {
        val trimmed = promptText.trim()
        if (trimmed.isEmpty()) return
        val sent = wsClient.send(ProtocolCodec.commandSendPrompt(trimmed))
        _state.update {
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
        _state.update { snapshot ->
            snapshot.copy(
                session = snapshot.session.copy(reasoningEffort = effort)
            )
        }
        wsClient.send(ProtocolCodec.commandSetReasoningEffort(effort))
    }

    fun setModel(modelName: String) {
        val trimmed = modelName.trim()
        if (trimmed.isEmpty()) return
        haptics.triggerSelectionClick()
        _state.update { snapshot ->
            snapshot.copy(
                session = snapshot.session.copy(modelName = trimmed),
                feedbackBanner = "モデルを $trimmed に切り替えました"
            )
        }
        wsClient.send(ProtocolCodec.commandSetModel(trimmed))
    }

    fun connectUsbPreset() {
        haptics.triggerSelectionClick()
        hasReceivedSync = false
        val preset = DeckEndpointConfig.usbLocalhost()
        saveEndpoint(preset)
        wsClient.connect(preset)
        _state.update { it.copy(feedbackBanner = "USB localhost:8765 へ接続を試行しています") }
    }

    fun applyPairingRawInput(raw: String): Boolean {
        val config = ProtocolCodec.parsePairingInput(raw) ?: return false
        haptics.triggerSelectionClick()
        hasReceivedSync = false
        saveEndpoint(config)
        wsClient.connect(config)
        _state.update {
            it.copy(feedbackBanner = "${config.label} へ接続しています")
        }
        return true
    }

    fun triggerSimulation() {
        haptics.triggerSelectionClick()
        val sent = wsClient.send(ProtocolCodec.commandSimulateLifecycle())
        _state.update {
            it.copy(
                feedbackBanner = if (sent) {
                    "テストライフサイクルを開始しました"
                } else {
                    "未接続のためシミュレーションを開始できませんでした"
                }
            )
        }
    }

    fun triggerSelectionClick() {
        haptics.triggerSelectionClick()
    }

    fun clearBanner() {
        _state.update { it.copy(feedbackBanner = null) }
    }

    companion object {
        private const val PREFS_NAME = "deck_prefs"
        private const val KEY_HOST = "endpoint_host"
        private const val KEY_PORT = "endpoint_port"
        private const val KEY_TOKEN = "endpoint_token"
        private const val KEY_LABEL = "endpoint_label"

        @Volatile
        private var instance: DeckSessionController? = null

        fun getInstance(context: Context): DeckSessionController {
            return instance ?: synchronized(this) {
                instance ?: DeckSessionController(context.applicationContext).also { instance = it }
            }
        }
    }
}
