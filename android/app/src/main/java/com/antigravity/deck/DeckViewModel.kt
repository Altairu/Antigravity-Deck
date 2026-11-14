package com.antigravity.deck

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.deck.hardware.PushToTalkManager
import com.antigravity.deck.hardware.SpeechUiState
import com.antigravity.deck.i18n.AppLanguage
import com.antigravity.deck.i18n.DeckI18n
import com.antigravity.deck.i18n.DeckStrings
import com.antigravity.deck.model.AgentSession
import com.antigravity.deck.model.DeckEndpointConfig
import com.antigravity.deck.model.NotificationPreferences
import com.antigravity.deck.model.ReasoningEffort
import com.antigravity.deck.network.ConnectionStatus
import com.antigravity.deck.service.DeckSessionController
import com.antigravity.deck.ui.theme.AppThemeMode
import com.antigravity.deck.updater.ApkInstallResult
import com.antigravity.deck.updater.AppUpdateManager
import com.antigravity.deck.updater.ReleaseInfo
import com.antigravity.deck.updater.UpdateCheckResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UpdateUiState(
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val availableUpdate: ReleaseInfo? = null,
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val progressPercent: Int? = null,
    val statusMessage: String? = null
)

data class DeckUiState(
    val session: AgentSession = AgentSession.initial(),
    val availableModels: List<String> = AgentSession.DEFAULT_AVAILABLE_MODELS,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val endpointConfig: DeckEndpointConfig = DeckEndpointConfig.usbLocalhost(),
    val notificationPrefs: NotificationPreferences = NotificationPreferences(),
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val backgroundEnabled: Boolean = true,
    val speechState: SpeechUiState = SpeechUiState(),
    val feedbackBanner: String? = null,
    val showPairingSheet: Boolean = false,
    val showNotificationSettings: Boolean = false,
    val updateState: UpdateUiState = UpdateUiState()
)

// 画面固有の状態と常駐セッションコントローラーの状態を統合するViewModel
class DeckViewModel(application: Application) : AndroidViewModel(application) {

    private val controller = DeckSessionController.getInstance(application)
    private val pushToTalk = PushToTalkManager(
        context = application,
        languageProvider = { controller.state.value.appLanguage }
    ) { transcript ->
        controller.sendPrompt(transcript)
    }
    private val showPairingSheet = MutableStateFlow(false)
    private val showNotificationSettings = MutableStateFlow(false)
    private val updateState = MutableStateFlow(UpdateUiState())

    init {
        checkForUpdates(silent = true)
    }

    val uiState: StateFlow<DeckUiState> = combine(
        controller.state,
        pushToTalk.state,
        showPairingSheet,
        showNotificationSettings,
        updateState
    ) { snapshot, speech, pairingVisible, notificationSettingsVisible, update ->
        DeckUiState(
            session = snapshot.session,
            availableModels = snapshot.availableModels,
            connectionStatus = snapshot.connectionStatus,
            endpointConfig = snapshot.endpointConfig,
            notificationPrefs = snapshot.notificationPrefs,
            appLanguage = snapshot.appLanguage,
            themeMode = snapshot.themeMode,
            backgroundEnabled = snapshot.backgroundEnabled,
            speechState = speech,
            feedbackBanner = snapshot.feedbackBanner,
            showPairingSheet = pairingVisible,
            showNotificationSettings = notificationSettingsVisible,
            updateState = update
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = DeckUiState(
            session = controller.state.value.session,
            availableModels = controller.state.value.availableModels,
            connectionStatus = controller.state.value.connectionStatus,
            endpointConfig = controller.state.value.endpointConfig,
            notificationPrefs = controller.state.value.notificationPrefs,
            appLanguage = controller.state.value.appLanguage,
            themeMode = controller.state.value.themeMode,
            backgroundEnabled = controller.state.value.backgroundEnabled,
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

    fun openNotificationSettings() {
        showNotificationSettings.value = true
    }

    fun closeNotificationSettings() {
        showNotificationSettings.value = false
    }

    fun updateNotificationPrefs(prefs: NotificationPreferences) {
        controller.updateNotificationPrefs(prefs)
    }

    fun setAppLanguage(language: AppLanguage) {
        controller.setAppLanguage(language)
    }

    fun setThemeMode(mode: AppThemeMode) {
        controller.setThemeMode(mode)
    }

    fun setBackgroundServiceEnabled(enabled: Boolean) {
        controller.setBackgroundServiceEnabled(enabled)
    }

    fun isBackgroundServiceEnabled(): Boolean = controller.isBackgroundServiceEnabled()

    private fun strings(): DeckStrings = DeckI18n.strings(controller.state.value.appLanguage)

    fun checkForUpdates(silent: Boolean = false) {
        if (updateState.value.isChecking || updateState.value.isDownloading) return
        val s = strings()
        viewModelScope.launch {
            updateState.update {
                it.copy(
                    isChecking = true,
                    statusMessage = if (silent) it.statusMessage else s.updateChecking
                )
            }
            when (val result = AppUpdateManager.checkForUpdate(BuildConfig.VERSION_NAME, strings = s)) {
                is UpdateCheckResult.UpdateAvailable -> {
                    updateState.update {
                        it.copy(
                            isChecking = false,
                            availableUpdate = result.release,
                            statusMessage = s.updateAvailable(result.release.tagName)
                        )
                    }
                }

                is UpdateCheckResult.UpToDate -> {
                    updateState.update {
                        it.copy(
                            isChecking = false,
                            availableUpdate = null,
                            statusMessage = if (silent) null else s.updateUpToDate(result.currentVersion)
                        )
                    }
                }

                UpdateCheckResult.NoReleasesYet -> {
                    updateState.update {
                        it.copy(
                            isChecking = false,
                            availableUpdate = null,
                            statusMessage = if (silent) null else s.updateNoReleases
                        )
                    }
                }

                is UpdateCheckResult.Error -> {
                    updateState.update {
                        it.copy(
                            isChecking = false,
                            statusMessage = if (silent) null else s.updateCheckFailed(result.message)
                        )
                    }
                }
            }
        }
    }

    fun installAvailableUpdate() {
        val release = updateState.value.availableUpdate ?: return
        if (updateState.value.isDownloading) return
        val s = strings()

        viewModelScope.launch {
            updateState.update {
                it.copy(
                    isDownloading = true,
                    progressPercent = 0,
                    statusMessage = s.updateDownloading(release.tagName)
                )
            }
            try {
                when (
                    val result = AppUpdateManager.downloadAndInstallApk(
                        context = getApplication(),
                        release = release,
                        strings = s,
                        onProgress = { pct ->
                            updateState.update { it.copy(progressPercent = pct) }
                        }
                    )
                ) {
                    ApkInstallResult.LaunchedInstaller -> {
                        updateState.update {
                            it.copy(
                                isDownloading = false,
                                progressPercent = 100,
                                statusMessage = s.updateInstallerLaunched
                            )
                        }
                    }

                    ApkInstallResult.NeedsInstallPermission -> {
                        AppUpdateManager.openInstallPermissionSettings(getApplication())
                        updateState.update {
                            it.copy(
                                isDownloading = false,
                                progressPercent = null,
                                statusMessage = s.updateInstallPermissionNeeded
                            )
                        }
                    }

                    is ApkInstallResult.Error -> {
                        updateState.update {
                            it.copy(
                                isDownloading = false,
                                progressPercent = null,
                                statusMessage = s.updateFailed(result.message)
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                updateState.update {
                    it.copy(
                        isDownloading = false,
                        progressPercent = null,
                        statusMessage = s.updateDownloadFailed(e.message ?: s.updateGenericError)
                    )
                }
            }
        }
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
