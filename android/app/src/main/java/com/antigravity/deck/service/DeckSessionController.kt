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
import com.antigravity.deck.i18n.AppLanguage
import com.antigravity.deck.i18n.DeckI18n
import com.antigravity.deck.i18n.DeckStrings
import com.antigravity.deck.model.AgentSession
import com.antigravity.deck.model.AgentStatus
import com.antigravity.deck.model.DeckEndpointConfig
import com.antigravity.deck.model.NotificationPreferences
import com.antigravity.deck.model.ProtocolCodec
import com.antigravity.deck.model.ReasoningEffort
import com.antigravity.deck.model.ServerEvent
import com.antigravity.deck.network.ConnectionStatus
import com.antigravity.deck.network.DeckWebSocketClient
import com.antigravity.deck.ui.theme.AppThemeMode
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
    val notificationPrefs: NotificationPreferences = NotificationPreferences(),
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val backgroundEnabled: Boolean = true,
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
    private val wsClient = DeckWebSocketClient { strings() }

    private val initialEndpoint = loadSavedEndpoint()
    private val initialNotificationPrefs = loadNotificationPrefs()
    private val initialAppLanguage = loadAppLanguage()
    private val initialThemeMode = loadThemeMode()
    private val initialBackgroundEnabled = prefs.getBoolean(KEY_BACKGROUND_ENABLED, true)
    private val _state = MutableStateFlow(
        DeckSessionSnapshot(
            endpointConfig = initialEndpoint,
            notificationPrefs = initialNotificationPrefs,
            appLanguage = initialAppLanguage,
            themeMode = initialThemeMode,
            backgroundEnabled = initialBackgroundEnabled
        )
    )
    val state: StateFlow<DeckSessionSnapshot> = _state.asStateFlow()

    private fun strings(): DeckStrings = DeckI18n.strings(_state.value.appLanguage)

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

        notifications.refreshChannels(strings())
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

    private fun loadNotificationPrefs(): NotificationPreferences {
        return NotificationPreferences(
            notifyOnApproval = prefs.getBoolean(KEY_NOTIFY_APPROVAL, true),
            showApprovalPreviewInNotification = prefs.getBoolean(KEY_NOTIFY_APPROVAL_PREVIEW, true),
            notifyOnDone = prefs.getBoolean(KEY_NOTIFY_DONE, true),
            notifyOnError = prefs.getBoolean(KEY_NOTIFY_ERROR, true),
            hapticsEnabled = prefs.getBoolean(KEY_HAPTICS_ENABLED, true)
        )
    }

    private fun loadAppLanguage(): AppLanguage {
        return AppLanguage.parse(prefs.getString(KEY_APP_LANGUAGE, null))
    }

    private fun loadThemeMode(): AppThemeMode {
        return AppThemeMode.parse(prefs.getString(KEY_THEME_MODE, null))
    }

    fun isBackgroundServiceEnabled(): Boolean = _state.value.backgroundEnabled

    fun setThemeMode(mode: AppThemeMode) {
        haptics.triggerSelectionClick()
        prefs.edit().putString(KEY_THEME_MODE, mode.wireValue).apply()
        val s = strings()
        _state.update {
            it.copy(
                themeMode = mode,
                feedbackBanner = s.bannerThemeChanged(s.themeLabel(mode))
            )
        }
    }

    fun setBackgroundServiceEnabled(enabled: Boolean) {
        haptics.triggerSelectionClick()
        prefs.edit().putBoolean(KEY_BACKGROUND_ENABLED, enabled).apply()
        if (enabled) {
            DeckForegroundService.start(appContext)
        } else {
            DeckForegroundService.stop(appContext)
        }
        val s = strings()
        _state.update {
            it.copy(
                backgroundEnabled = enabled,
                feedbackBanner = if (enabled) s.bannerBackgroundStarted else s.bannerBackgroundStopped
            )
        }
    }

    fun setAppLanguage(language: AppLanguage) {
        haptics.triggerSelectionClick()
        prefs.edit().putString(KEY_APP_LANGUAGE, language.wireValue).apply()
        val updatedStrings = DeckI18n.strings(language)
        notifications.refreshChannels(updatedStrings)
        _state.update {
            it.copy(
                appLanguage = language,
                feedbackBanner = updatedStrings.bannerLanguageChanged(language.optionLabel)
            )
        }
        val current = _state.value
        notifications.updateForegroundNotification(current.session, current.connectionStatus, updatedStrings)
    }

    fun updateNotificationPrefs(updated: NotificationPreferences) {
        haptics.triggerSelectionClick()
        prefs.edit()
            .putBoolean(KEY_NOTIFY_APPROVAL, updated.notifyOnApproval)
            .putBoolean(KEY_NOTIFY_APPROVAL_PREVIEW, updated.showApprovalPreviewInNotification)
            .putBoolean(KEY_NOTIFY_DONE, updated.notifyOnDone)
            .putBoolean(KEY_NOTIFY_ERROR, updated.notifyOnError)
            .putBoolean(KEY_HAPTICS_ENABLED, updated.hapticsEnabled)
            .apply()
        _state.update { it.copy(notificationPrefs = updated) }
        if (!updated.shouldNotify(_state.value.session.status)) {
            notifications.cancel()
        }
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
        val s = strings()
        val payload = if (approved) {
            ProtocolCodec.commandApprove()
        } else {
            ProtocolCodec.commandReject(s.rejectReasonFromMobile)
        }
        val sent = wsClient.send(payload)
        _state.update {
            it.copy(
                feedbackBanner = if (sent) {
                    if (approved) s.bannerApprovedFromNotification else s.bannerRejectedFromNotification
                } else {
                    s.bannerNotConnected
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
                    val currentPrefs = _state.value.notificationPrefs
                    if (currentPrefs.shouldVibrate(updated.status)) {
                        triggerStatusHaptics(updated.status)
                    }
                    notifications.notifySessionTransition(updated, currentPrefs, strings())
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
                        it.copy(feedbackBanner = event.message ?: strings().bannerAuthMismatch)
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
        return notifications.buildForegroundNotification(
            current.session,
            current.connectionStatus,
            DeckI18n.strings(current.appLanguage)
        )
    }

    fun updateForegroundNotification(
        session: AgentSession,
        connectionStatus: ConnectionStatus
    ) {
        notifications.updateForegroundNotification(session, connectionStatus, strings())
    }

    fun approve() {
        haptics.triggerSelectionClick()
        notifications.cancel()
        val s = strings()
        val sent = wsClient.send(ProtocolCodec.commandApprove())
        _state.update {
            it.copy(
                feedbackBanner = if (sent) s.bannerApproveSent else s.bannerNotConnected
            )
        }
    }

    fun reject() {
        haptics.triggerSelectionClick()
        notifications.cancel()
        val s = strings()
        val sent = wsClient.send(ProtocolCodec.commandReject(s.rejectReasonFromMobile))
        _state.update {
            it.copy(
                feedbackBanner = if (sent) s.bannerRejectSent else s.bannerNotConnected
            )
        }
    }

    fun sendPrompt(promptText: String) {
        val trimmed = promptText.trim()
        if (trimmed.isEmpty()) return
        val s = strings()
        val sent = wsClient.send(ProtocolCodec.commandSendPrompt(trimmed))
        _state.update {
            it.copy(
                feedbackBanner = if (sent) {
                    s.bannerPromptSent(trimmed)
                } else {
                    s.bannerNotConnected
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
        val s = strings()
        _state.update { snapshot ->
            snapshot.copy(
                session = snapshot.session.copy(modelName = trimmed),
                feedbackBanner = s.bannerModelSwitched(trimmed)
            )
        }
        wsClient.send(ProtocolCodec.commandSetModel(trimmed))
    }

    fun selectConversation(conversationId: String, title: String? = null) {
        val trimmedId = conversationId.trim()
        if (trimmedId.isEmpty()) return
        haptics.triggerSelectionClick()
        val s = strings()
        val sent = wsClient.send(ProtocolCodec.commandSelectConversation(trimmedId))
        val label = title?.trim()?.takeIf { it.isNotEmpty() } ?: trimmedId.take(8)
        _state.update {
            it.copy(
                feedbackBanner = if (sent) s.bannerConversationSwitched(label) else s.bannerNotConnected
            )
        }
    }

    fun connectUsbPreset() {
        haptics.triggerSelectionClick()
        hasReceivedSync = false
        val preset = DeckEndpointConfig.usbLocalhost()
        saveEndpoint(preset)
        wsClient.connect(preset)
        _state.update { it.copy(feedbackBanner = strings().bannerConnectingUsb) }
    }

    fun applyPairingRawInput(raw: String): Boolean {
        val config = ProtocolCodec.parsePairingInput(raw) ?: return false
        haptics.triggerSelectionClick()
        hasReceivedSync = false
        saveEndpoint(config)
        wsClient.connect(config)
        _state.update {
            it.copy(feedbackBanner = strings().bannerConnectingEndpoint(config.label))
        }
        return true
    }

    fun triggerSimulation() {
        haptics.triggerSelectionClick()
        val s = strings()
        val sent = wsClient.send(ProtocolCodec.commandSimulateLifecycle())
        _state.update {
            it.copy(
                feedbackBanner = if (sent) {
                    s.bannerSimulationStarted
                } else {
                    s.bannerSimulationNotConnected
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
        private const val KEY_NOTIFY_APPROVAL = "notify_approval"
        private const val KEY_NOTIFY_APPROVAL_PREVIEW = "notify_approval_preview"
        private const val KEY_NOTIFY_DONE = "notify_done"
        private const val KEY_NOTIFY_ERROR = "notify_error"
        private const val KEY_HAPTICS_ENABLED = "haptics_enabled"
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_BACKGROUND_ENABLED = "background_enabled"

        @Volatile
        private var instance: DeckSessionController? = null

        fun getInstance(context: Context): DeckSessionController {
            return instance ?: synchronized(this) {
                instance ?: DeckSessionController(context.applicationContext).also { instance = it }
            }
        }
    }
}
