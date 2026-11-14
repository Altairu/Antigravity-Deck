package com.antigravity.deck

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antigravity.deck.ui.DeckCommandCenterScreen
import com.antigravity.deck.ui.theme.AntigravityDeckTheme

class MainActivity : ComponentActivity() {

    private val deckViewModel: DeckViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        setContent {
            val uiState by deckViewModel.uiState.collectAsStateWithLifecycle()

            AntigravityDeckTheme {
                DeckCommandCenterScreen(
                    uiState = uiState,
                    onApprove = deckViewModel::approve,
                    onReject = deckViewModel::reject,
                    onSendPrompt = deckViewModel::sendPrompt,
                    onSetReasoningEffort = deckViewModel::setReasoningEffort,
                    onSetModel = deckViewModel::setModel,
                    onConnectUsbPreset = deckViewModel::connectUsbPreset,
                    onOpenPairingSheet = deckViewModel::openPairingSheet,
                    onDismissPairingSheet = deckViewModel::closePairingSheet,
                    onSubmitPairingInput = deckViewModel::applyPairingRawInput,
                    onTriggerSimulation = deckViewModel::triggerSimulation,
                    onStartPushToTalk = deckViewModel::startPushToTalk,
                    onStopPushToTalk = deckViewModel::stopPushToTalk
                )
            }
        }
    }
}
