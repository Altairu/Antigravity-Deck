package com.antigravity.deck

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antigravity.deck.i18n.DeckI18n
import com.antigravity.deck.i18n.LocalDeckStrings
import com.antigravity.deck.service.DeckForegroundService
import com.antigravity.deck.ui.DeckCommandCenterScreen
import com.antigravity.deck.ui.theme.AntigravityDeckTheme

class MainActivity : ComponentActivity() {

    private val deckViewModel: DeckViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (deckViewModel.isBackgroundServiceEnabled()) {
            DeckForegroundService.start(this)
        }

        setContent {
            val uiState by deckViewModel.uiState.collectAsStateWithLifecycle()
            val strings = DeckI18n.strings(uiState.appLanguage)
            val useDark = uiState.themeMode.resolveDark(isSystemInDarkTheme())

            LaunchedEffect(useDark) {
                val style = if (useDark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            AntigravityDeckTheme(themeMode = uiState.themeMode) {
                CompositionLocalProvider(LocalDeckStrings provides strings) {
                    DeckCommandCenterScreen(
                        uiState = uiState,
                        onApprove = deckViewModel::approve,
                        onReject = deckViewModel::reject,
                        onSendPrompt = deckViewModel::sendPrompt,
                        onSetReasoningEffort = deckViewModel::setReasoningEffort,
                        onSetModel = deckViewModel::setModel,
                        onSelectConversation = deckViewModel::selectConversation,
                        onConnectUsbPreset = deckViewModel::connectUsbPreset,
                        onOpenPairingSheet = deckViewModel::openPairingSheet,
                        onDismissPairingSheet = deckViewModel::closePairingSheet,
                        onSubmitPairingInput = deckViewModel::applyPairingRawInput,
                        onOpenNotificationSettings = deckViewModel::openNotificationSettings,
                        onDismissNotificationSettings = deckViewModel::closeNotificationSettings,
                        onUpdateNotificationPrefs = deckViewModel::updateNotificationPrefs,
                        onSetAppLanguage = deckViewModel::setAppLanguage,
                        onSetThemeMode = deckViewModel::setThemeMode,
                        onSetBackgroundServiceEnabled = deckViewModel::setBackgroundServiceEnabled,
                        onSetAutoApproveEnabled = deckViewModel::setAutoApproveEnabled,
                        onCheckForUpdates = { deckViewModel.checkForUpdates(silent = false) },
                        onInstallAvailableUpdate = deckViewModel::installAvailableUpdate,
                        onTriggerSimulation = deckViewModel::triggerSimulation,
                        onStartPushToTalk = deckViewModel::startPushToTalk,
                        onStopPushToTalk = deckViewModel::stopPushToTalk
                    )
                }
            }
        }
    }
}
