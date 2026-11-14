package com.antigravity.deck

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.deck.hardware.PushToTalkManager
import com.antigravity.deck.hardware.SpeechUiState
import com.antigravity.deck.model.AgentSession
import com.antigravity.deck.model.DeckEndpointConfig
import com.antigravity.deck.model.ReasoningEffort
import com.antigravity.deck.network.ConnectionStatus
import com.antigravity.deck.service.DeckSessionController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DeckUiState(
    val session: AgentSession = AgentSession.initial(),
    val availableModels: List<String> = AgentSession.DEFAULT_AVAILABLE_MODELS,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val endpointConfig: DeckEndpointConfig = DeckEndpointConfig.usbLocalhost(),
    val speechState: SpeechUiState = SpeechUiState(),
    val feedbackBanner: String? = null,
    val showPairingSheet: Boolean = false
)

// 画面固有の状態と常駐セッションコントローラーの状態を統合するViewModel
class DeckViewModel(application: Application) : AndroidViewModel(application) {

    private val controller = DeckSessionController.getInstance(application)
    private val pushToTalk = PushToTalkManager(application) { transcript ->
        controller.sendPrompt(transcript)
    }
    private val showPairingSheet = MutableStateFlow(false)

    val uiState: StateFlow<DeckUiState> = combine(
        controller.state,
        pushToTalk.state,
        showPairingSheet
    ) { snapshot, speech, pairingVisible ->
        DeckUiState(
            session = snapshot.session,
            availableModels = snapshot.availableModels,
            connectionStatus = snapshot.connectionStatus,
            endpointConfig = snapshot.endpointConfig,
            speechState = speech,
            feedbackBanner = snapshot.feedbackBanner,
            showPairingSheet = pairingVisible
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = DeckUiState(
            session = controller.state.value.session,
            availableModels = controller.state.value.availableModels,
            connectionStatus = controller.state.value.connectionStatus,
            endpointConfig = controller.state.value.endpointConfig,
            feedbackBanner = controller.state.value.feedbackBanner
        )
    )

    fun approve() = controller.approve()

    fun reject() = controller.reject()

    fun sendPrompt(promptText: String) = controller.sendPrompt(promptText)

    fun setReasoningEffort(effort: ReasoningEffort) = controller.setReasoningEffort(effort)

    fun setModel(modelName: String) = controller.setModel(modelName)

    fun connectUsbPreset() = controller.connectUsbPreset()

    fun applyPairingRawInput(raw: String): Boolean {
        val applied = controller.applyPairingRawInput(raw)
        if (applied) {
            showPairingSheet.value = false
        }
        return applied
    }

    fun triggerSimulation() = controller.triggerSimulation()

    fun openPairingSheet() {
        showPairingSheet.value = true
    }

    fun closePairingSheet() {
        showPairingSheet.value = false
    }

    fun startPushToTalk() {
        controller.triggerSelectionClick()
        pushToTalk.onPressStart()
    }

    fun stopPushToTalk() = pushToTalk.onPressRelease()

    fun clearBanner() = controller.clearBanner()

    override fun onCleared() {
        super.onCleared()
        // バックグラウンドサービス側の接続は維持し、画面に紐づく音声認識リソースのみ解放する
        pushToTalk.release()
    }
}
