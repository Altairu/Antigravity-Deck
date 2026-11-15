package com.antigravity.deck.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.antigravity.deck.DeckUiState
import com.antigravity.deck.UpdateUiState
import com.antigravity.deck.hardware.SpeechUiState
import com.antigravity.deck.i18n.AppLanguage
import com.antigravity.deck.i18n.LocalDeckStrings
import com.antigravity.deck.model.AgentSession
import com.antigravity.deck.model.AgentStatus
import com.antigravity.deck.model.ArtifactDocument
import com.antigravity.deck.model.ConversationMessage
import com.antigravity.deck.model.ConversationSummary
import com.antigravity.deck.model.MessageRole
import com.antigravity.deck.model.NotificationPreferences
import com.antigravity.deck.model.ReasoningEffort
import com.antigravity.deck.network.ConnectionStatus
import com.antigravity.deck.ui.theme.AppThemeMode
import com.antigravity.deck.ui.theme.DeckColors

@Composable
fun DeckCommandCenterScreen(
    uiState: DeckUiState,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onSendPrompt: (String) -> Unit,
    onSetReasoningEffort: (ReasoningEffort) -> Unit,
    onSetModel: (String) -> Unit,
    onSelectConversation: (String, String?) -> Unit,
    onConnectUsbPreset: () -> Unit,
    onOpenPairingSheet: () -> Unit,
    onDismissPairingSheet: () -> Unit,
    onSubmitPairingInput: (String) -> Boolean,
    onOpenNotificationSettings: () -> Unit,
    onDismissNotificationSettings: () -> Unit,
    onUpdateNotificationPrefs: (NotificationPreferences) -> Unit,
    onSetAppLanguage: (AppLanguage) -> Unit,
    onSetThemeMode: (AppThemeMode) -> Unit,
    onSetBackgroundServiceEnabled: (Boolean) -> Unit,
    onSetAutoApproveEnabled: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    onInstallAvailableUpdate: () -> Unit,
    onTriggerSimulation: () -> Unit,
    onStartPushToTalk: () -> Unit,
    onStopPushToTalk: () -> Unit
) {
    val s = LocalDeckStrings.current
    val context = LocalContext.current

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    var textPromptDraft by remember { mutableStateOf("") }
    var showConversationPicker by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DeckColors.Background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TopHeaderBar(
                connectionStatus = uiState.connectionStatus,
                hasAvailableUpdate = uiState.updateState.availableUpdate != null,
                onConnectUsbPreset = onConnectUsbPreset,
                onOpenPairingSheet = onOpenPairingSheet,
                onOpenSettings = onOpenNotificationSettings
            )

            if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                    color = DeckColors.ApprovalBannerBackground,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, DeckColors.StatusApproval.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsOff,
                            contentDescription = s.notificationPermissionDesc,
                            tint = DeckColors.StatusApproval,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = s.notificationPermissionPrompt,
                            color = DeckColors.StatusApproval,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            AgentStatusCard(
                session = uiState.session,
                availableModels = uiState.availableModels,
                autoApproveEnabled = uiState.autoApproveEnabled,
                onSetModel = onSetModel,
                onSetReasoningEffort = onSetReasoningEffort,
                onSetAutoApproveEnabled = onSetAutoApproveEnabled,
                onOpenConversationPicker = { showConversationPicker = true }
            )

            ResponsePanel(
                session = uiState.session,
                speechState = uiState.speechState,
                feedbackBanner = uiState.feedbackBanner,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            if (
                uiState.session.status == AgentStatus.WAITING_APPROVAL &&
                !uiState.autoApproveEnabled &&
                !uiState.session.antigravityTurbo
            ) {
                ApprovalActionRow(
                    onApprove = onApprove,
                    onReject = onReject
                )
            }

            PromptInputBar(
                draft = textPromptDraft,
                onDraftChange = { textPromptDraft = it },
                onSendPrompt = {
                    val trimmed = textPromptDraft.trim()
                    if (trimmed.isNotEmpty()) {
                        onSendPrompt(trimmed)
                        textPromptDraft = ""
                    }
                },
                speechState = uiState.speechState,
                hasAudioPermission = hasAudioPermission,
                onRequestAudioPermission = {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                onStartPushToTalk = onStartPushToTalk,
                onStopPushToTalk = onStopPushToTalk
            )
        }
    }

    if (uiState.showPairingSheet) {
        QrPairingDialog(
            currentConfig = uiState.endpointConfig,
            onDismiss = onDismissPairingSheet,
            onSubmitRawPairing = onSubmitPairingInput,
            onConnectUsbPreset = onConnectUsbPreset
        )
    }

    if (uiState.showNotificationSettings) {
        SettingsDialog(
            prefs = uiState.notificationPrefs,
            appLanguage = uiState.appLanguage,
            themeMode = uiState.themeMode,
            backgroundEnabled = uiState.backgroundEnabled,
            autoApproveEnabled = uiState.autoApproveEnabled,
            updateState = uiState.updateState,
            onUpdate = onUpdateNotificationPrefs,
            onSetAppLanguage = onSetAppLanguage,
            onSetThemeMode = onSetThemeMode,
            onSetBackgroundServiceEnabled = onSetBackgroundServiceEnabled,
            onSetAutoApproveEnabled = onSetAutoApproveEnabled,
            onCheckForUpdates = onCheckForUpdates,
            onInstallAvailableUpdate = onInstallAvailableUpdate,
            onTriggerSimulation = onTriggerSimulation,
            onDismiss = onDismissNotificationSettings
        )
    }

    if (showConversationPicker) {
        ConversationPickerDialog(
            conversations = uiState.session.conversations,
            currentConversationId = uiState.session.conversationId,
            onSelect = { item ->
                showConversationPicker = false
                onSelectConversation(item.id, item.title)
            },
            onDismiss = { showConversationPicker = false }
        )
    }
}

@Composable
private fun TopHeaderBar(
    connectionStatus: ConnectionStatus,
    hasAvailableUpdate: Boolean,
    onConnectUsbPreset: () -> Unit,
    onOpenPairingSheet: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val s = LocalDeckStrings.current
    val (dotColor, statusText) = when (connectionStatus) {
        is ConnectionStatus.Connected -> DeckColors.StatusDone to connectionStatus.endpoint.label
        ConnectionStatus.Connecting -> DeckColors.StatusThinking to s.connConnecting
        is ConnectionStatus.Reconnecting -> DeckColors.StatusApproval to "${s.connReconnectingPrefix} (${connectionStatus.attempt})"
        ConnectionStatus.Disconnected -> DeckColors.StatusError to s.connDisconnected
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Antigravity Deck",
                color = DeckColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = statusText,
                color = DeckColors.TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onConnectUsbPreset,
            modifier = Modifier.size(38.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Usb,
                contentDescription = "USB",
                tint = DeckColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        IconButton(
            onClick = onOpenPairingSheet,
            modifier = Modifier.size(38.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.QrCodeScanner,
                contentDescription = "QR",
                tint = DeckColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        Box {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = s.settingsButtonDesc,
                    tint = DeckColors.TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (hasAvailableUpdate) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 6.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(DeckColors.AccentBlue)
                )
            }
        }
    }
}

@Composable
private fun AgentStatusCard(
    session: AgentSession,
    availableModels: List<String>,
    autoApproveEnabled: Boolean,
    onSetModel: (String) -> Unit,
    onSetReasoningEffort: (ReasoningEffort) -> Unit,
    onSetAutoApproveEnabled: (Boolean) -> Unit,
    onOpenConversationPicker: () -> Unit
) {
    val s = LocalDeckStrings.current
    val statusColor = when (session.status) {
        AgentStatus.IDLE -> DeckColors.StatusIdle
        AgentStatus.THINKING -> DeckColors.StatusThinking
        AgentStatus.WAITING_APPROVAL -> DeckColors.StatusApproval
        AgentStatus.DONE -> DeckColors.StatusDone
        AgentStatus.ERROR -> DeckColors.StatusError
    }

    var modelMenuExpanded by remember { mutableStateOf(false) }
    val modelChoices = remember(availableModels, session.modelName) {
        if (availableModels.contains(session.modelName)) {
            availableModels
        } else {
            listOf(session.modelName) + availableModels
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DeckColors.Surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, DeckColors.BorderMuted)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = statusColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = s.statusLabel(session.status),
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = session.title,
                    color = DeckColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (session.antigravityTurbo) {
                    Surface(
                        color = DeckColors.StatusDone.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bolt,
                                contentDescription = s.turboModeBadge,
                                tint = DeckColors.StatusDone,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = s.turboModeBadge,
                                color = DeckColors.StatusDone,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSetAutoApproveEnabled(!autoApproveEnabled) },
                    color = if (autoApproveEnabled) {
                        DeckColors.AccentBlue.copy(alpha = 0.18f)
                    } else {
                        DeckColors.SurfaceElevated
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = if (autoApproveEnabled) {
                        BorderStroke(1.dp, DeckColors.AccentBlue.copy(alpha = 0.5f))
                    } else {
                        null
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = s.autoApproveChipLabel,
                            tint = if (autoApproveEnabled) DeckColors.AccentBlue else DeckColors.TextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = s.autoApproveChipLabel,
                            color = if (autoApproveEnabled) DeckColors.AccentBlue else DeckColors.TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenConversationPicker),
                    color = DeckColors.SurfaceElevated,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Forum,
                            contentDescription = s.conversationSwitchButton,
                            tint = DeckColors.TextSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (session.conversations.isNotEmpty()) {
                                "${s.conversationSwitchButton} (${session.conversations.size})"
                            } else {
                                s.conversationSwitchButton
                            },
                            color = DeckColors.TextSecondary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { modelMenuExpanded = true },
                        color = DeckColors.SurfaceElevated,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = session.modelName,
                                color = DeckColors.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                tint = DeckColors.TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = modelMenuExpanded,
                        onDismissRequest = { modelMenuExpanded = false }
                    ) {
                        modelChoices.forEach { model ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = model,
                                        fontWeight = if (model == session.modelName) {
                                            FontWeight.Bold
                                        } else {
                                            FontWeight.Normal
                                        }
                                    )
                                },
                                onClick = {
                                    modelMenuExpanded = false
                                    onSetModel(model)
                                }
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ReasoningEffort.entries.forEach { effort ->
                        val selected = effort == session.reasoningEffort
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onSetReasoningEffort(effort) },
                            color = if (selected) DeckColors.AccentBlue else DeckColors.SurfaceElevated,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = effort.label,
                                color = if (selected) Color.White else DeckColors.TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class ResponseTab {
    APPROVAL,
    HISTORY,
    ARTIFACTS,
    LATEST
}

private val ISO_TIME_REGEX = Regex("""T(\d{2}:\d{2})""")
private val ISO_DATE_TIME_REGEX = Regex("""^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2})""")

private fun extractShortTime(timestamp: String?): String? {
    if (timestamp.isNullOrBlank()) return null
    return ISO_TIME_REGEX.find(timestamp)?.groupValues?.getOrNull(1)
}

private fun extractShortDateTime(timestamp: String?): String? {
    if (timestamp.isNullOrBlank()) return null
    val match = ISO_DATE_TIME_REGEX.find(timestamp) ?: return null
    return "${match.groupValues[1]} ${match.groupValues[2]}"
}

@Composable
private fun PanelTabSelector(
    options: List<Pair<ResponseTab, String>>,
    selected: ResponseTab,
    onSelect: (ResponseTab) -> Unit
) {
    Surface(
        color = DeckColors.SurfaceElevated,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, DeckColors.BorderMuted)
    ) {
        Row(
            modifier = Modifier.padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEach { (tab, label) ->
                val isSelected = tab == selected
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSelect(tab) },
                    color = if (isSelected) DeckColors.AccentBlue else Color.Transparent,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else DeckColors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationMessageCard(
    message: ConversationMessage,
    roleLabel: String
) {
    val isUser = message.role == MessageRole.USER
    val cardColor = if (isUser) {
        DeckColors.AccentBlue.copy(alpha = 0.12f)
    } else {
        DeckColors.SurfaceElevated
    }
    val borderColor = if (isUser) {
        DeckColors.AccentBlue.copy(alpha = 0.35f)
    } else {
        DeckColors.BorderMuted
    }
    val roleColor = if (isUser) DeckColors.AccentBlue else DeckColors.StatusDone
    val shortTime = remember(message.timestamp) { extractShortTime(message.timestamp) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (isUser) 18.dp else 0.dp),
        color = cardColor,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 11.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = roleLabel,
                    color = roleColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (shortTime != null) {
                    Text(
                        text = shortTime,
                        color = DeckColors.TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }

            if (isUser) {
                Text(
                    text = message.content,
                    color = DeckColors.TextPrimary,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp
                )
            } else {
                MarkdownResponseView(
                    markdown = message.content,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ResponsePanel(
    session: AgentSession,
    speechState: SpeechUiState,
    feedbackBanner: String?,
    modifier: Modifier = Modifier
) {
    val s = LocalDeckStrings.current
    val isWaiting = session.status == AgentStatus.WAITING_APPROVAL
    val hasResponse = !session.lastResponse.isNullOrBlank() && session.approvalPreview.isNullOrBlank()
    var activeTab by remember {
        mutableStateOf(if (isWaiting) ResponseTab.APPROVAL else ResponseTab.HISTORY)
    }

    LaunchedEffect(isWaiting) {
        activeTab = when {
            isWaiting -> ResponseTab.APPROVAL
            activeTab == ResponseTab.APPROVAL -> ResponseTab.HISTORY
            else -> activeTab
        }
    }

    val artifactsLabel = if (session.artifacts.isNotEmpty()) {
        "${s.tabArtifacts} (${session.artifacts.size})"
    } else {
        s.tabArtifacts
    }
    val tabOptions = remember(isWaiting, s, artifactsLabel) {
        if (isWaiting) {
            listOf(
                ResponseTab.APPROVAL to s.tabApproval,
                ResponseTab.HISTORY to s.tabHistory,
                ResponseTab.ARTIFACTS to artifactsLabel
            )
        } else {
            listOf(
                ResponseTab.HISTORY to s.tabHistory,
                ResponseTab.ARTIFACTS to artifactsLabel,
                ResponseTab.LATEST to s.tabLatest
            )
        }
    }

    var selectedArtifactName by remember(session.conversationId) {
        mutableStateOf<String?>(null)
    }
    val selectedArtifact = remember(session.artifacts, selectedArtifactName) {
        session.artifacts.firstOrNull { it.name == selectedArtifactName }
            ?: session.artifacts.firstOrNull()
    }

    val historyItems = remember(session.messages, session.lastResponse) {
        when {
            session.messages.isNotEmpty() -> session.messages
            !session.lastResponse.isNullOrBlank() -> listOf(
                ConversationMessage(
                    role = MessageRole.ASSISTANT,
                    content = session.lastResponse,
                    timestamp = session.updatedAt
                )
            )
            else -> emptyList()
        }
    }

    val headerTitle = when (activeTab) {
        ResponseTab.APPROVAL -> s.sectionWaitingApproval
        ResponseTab.HISTORY -> s.sectionConversationHistory
        ResponseTab.ARTIFACTS -> s.sectionArtifacts
        ResponseTab.LATEST -> when {
            isWaiting -> s.sectionWaitingApproval
            hasResponse -> s.sectionAgentResponse
            else -> s.sectionAgentOutput
        }
    }

    Surface(
        modifier = modifier,
        color = DeckColors.Surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, DeckColors.BorderMuted)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = headerTitle,
                    color = if (activeTab == ResponseTab.APPROVAL && isWaiting) {
                        DeckColors.StatusApproval
                    } else {
                        DeckColors.TextSecondary
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )

                PanelTabSelector(
                    options = tabOptions,
                    selected = activeTab,
                    onSelect = { activeTab = it }
                )
            }

            val statusMessage = when {
                speechState.isListening -> s.speechOverlayListening(speechState.partialText.ifEmpty { "..." })
                speechState.isFinalizing -> speechState.statusHint ?: s.speechOverlayFinalizingFallback
                !feedbackBanner.isNullOrEmpty() -> feedbackBanner
                !speechState.statusHint.isNullOrEmpty() -> speechState.statusHint
                else -> null
            }
            if (statusMessage != null) {
                Text(
                    text = statusMessage,
                    color = if (speechState.isListening) DeckColors.AccentBlue else DeckColors.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (activeTab == ResponseTab.ARTIFACTS && session.artifacts.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    session.artifacts.forEach { artifact ->
                        val isSelected = selectedArtifact?.name == artifact.name
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedArtifactName = artifact.name },
                            color = if (isSelected) DeckColors.AccentBlue else DeckColors.SurfaceElevated,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) DeckColors.AccentBlue else DeckColors.BorderMuted
                            )
                        ) {
                            Text(
                                text = artifact.name,
                                color = if (isSelected) Color.White else DeckColors.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = DeckColors.BorderMuted)

            val scrollState = rememberScrollState()
            val lastHistoryContent = historyItems.lastOrNull()?.content
            LaunchedEffect(activeTab, historyItems.size, lastHistoryContent) {
                if (activeTab == ResponseTab.HISTORY && historyItems.isNotEmpty()) {
                    scrollState.scrollTo(scrollState.maxValue)
                }
            }
            LaunchedEffect(
                activeTab,
                session.lastResponse,
                session.approvalPreview,
                selectedArtifact?.name,
                selectedArtifact?.content
            ) {
                if (activeTab != ResponseTab.HISTORY) {
                    scrollState.scrollTo(0)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                when (activeTab) {
                    ResponseTab.HISTORY -> {
                        if (historyItems.isEmpty()) {
                            Text(
                                text = s.emptyHistory,
                                color = DeckColors.TextSecondary,
                                fontSize = 13.5.sp,
                                lineHeight = 20.sp
                            )
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                historyItems.forEach { msg ->
                                    ConversationMessageCard(
                                        message = msg,
                                        roleLabel = if (msg.role == MessageRole.USER) s.roleUser else s.roleAssistant
                                    )
                                }
                            }
                        }
                    }

                    ResponseTab.ARTIFACTS -> {
                        if (selectedArtifact == null) {
                            Text(
                                text = s.emptyArtifacts,
                                color = DeckColors.TextSecondary,
                                fontSize = 13.5.sp,
                                lineHeight = 20.sp
                            )
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (!selectedArtifact.summary.isNullOrBlank()) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = DeckColors.SurfaceElevated,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, DeckColors.BorderMuted)
                                    ) {
                                        Text(
                                            text = selectedArtifact.summary,
                                            color = DeckColors.TextSecondary,
                                            fontSize = 12.sp,
                                            lineHeight = 17.sp,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                                MarkdownResponseView(
                                    markdown = selectedArtifact.content,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    ResponseTab.APPROVAL,
                    ResponseTab.LATEST -> {
                        val previewContent = when {
                            !session.approvalPreview.isNullOrBlank() -> session.approvalPreview
                            !session.lastResponse.isNullOrBlank() -> session.lastResponse
                            session.status == AgentStatus.THINKING -> s.emptyThinking
                            session.status == AgentStatus.DONE -> s.emptyDone
                            session.status == AgentStatus.ERROR -> s.emptyError
                            else -> s.emptyIdle
                        }
                        val isMonospace = !session.approvalPreview.isNullOrBlank() || session.lastResponse.isNullOrBlank()
                        if (!isMonospace && !session.lastResponse.isNullOrBlank()) {
                            MarkdownResponseView(
                                markdown = session.lastResponse,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = previewContent,
                                color = if (session.approvalPreview.isNullOrBlank()) {
                                    DeckColors.TextSecondary
                                } else {
                                    DeckColors.TextPrimary
                                },
                                fontFamily = if (!session.approvalPreview.isNullOrBlank()) {
                                    FontFamily.Monospace
                                } else {
                                    FontFamily.Default
                                },
                                fontSize = if (!session.approvalPreview.isNullOrBlank()) 12.sp else 13.5.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApprovalActionRow(
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = onReject,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, DeckColors.RejectRed),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DeckColors.RejectRed),
            contentPadding = PaddingValues(vertical = 12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Reject",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
        }

        Button(
            onClick = onApprove,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DeckColors.ApproveGreen,
                contentColor = Color.White
            ),
            contentPadding = PaddingValues(vertical = 12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Approve",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun PromptInputBar(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSendPrompt: () -> Unit,
    speechState: SpeechUiState,
    hasAudioPermission: Boolean,
    onRequestAudioPermission: () -> Unit,
    onStartPushToTalk: () -> Unit,
    onStopPushToTalk: () -> Unit
) {
    val s = LocalDeckStrings.current
    val hasDraft = draft.isNotBlank()

    val currentHasPermission by rememberUpdatedState(hasAudioPermission)
    val currentRequestPermission by rememberUpdatedState(onRequestAudioPermission)
    val currentOnStart by rememberUpdatedState(onStartPushToTalk)
    val currentOnRelease by rememberUpdatedState(onStopPushToTalk)

    val micBg by animateColorAsState(
        targetValue = if (speechState.isListening) DeckColors.AccentBlue else DeckColors.Surface,
        animationSpec = tween(durationMillis = 150),
        label = "mic_bg"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            placeholder = {
                Text(
                    text = if (speechState.isListening) s.pttRecording else s.promptPlaceholder,
                    color = DeckColors.TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSendPrompt() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = DeckColors.TextPrimary,
                unfocusedTextColor = DeckColors.TextPrimary,
                focusedContainerColor = DeckColors.Surface,
                unfocusedContainerColor = DeckColors.Surface,
                focusedBorderColor = DeckColors.AccentBlue,
                unfocusedBorderColor = DeckColors.BorderMuted,
                cursorColor = DeckColors.AccentBlue
            ),
            modifier = Modifier.weight(1f)
        )

        if (hasDraft) {
            FilledIconButton(
                onClick = onSendPrompt,
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = DeckColors.AccentBlue,
                    contentColor = Color.White
                ),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = s.sendButtonDesc
                )
            }
        } else {
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                if (!currentHasPermission) {
                                    currentRequestPermission()
                                    return@detectTapGestures
                                }
                                currentOnStart()
                                try {
                                    tryAwaitRelease()
                                } finally {
                                    currentOnRelease()
                                }
                            }
                        )
                    },
                color = micBg,
                shape = CircleShape,
                border = BorderStroke(
                    1.dp,
                    if (speechState.isListening) DeckColors.AccentBlue else DeckColors.BorderMuted
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = s.pttHoldToSpeak,
                        tint = if (speechState.isListening) Color.White else DeckColors.TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    prefs: NotificationPreferences,
    appLanguage: AppLanguage,
    themeMode: AppThemeMode,
    backgroundEnabled: Boolean,
    autoApproveEnabled: Boolean,
    updateState: UpdateUiState,
    onUpdate: (NotificationPreferences) -> Unit,
    onSetAppLanguage: (AppLanguage) -> Unit,
    onSetThemeMode: (AppThemeMode) -> Unit,
    onSetBackgroundServiceEnabled: (Boolean) -> Unit,
    onSetAutoApproveEnabled: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    onInstallAvailableUpdate: () -> Unit,
    onTriggerSimulation: () -> Unit,
    onDismiss: () -> Unit
) {
    val s = LocalDeckStrings.current
    val resolvedLangLabel = appLanguage.resolve().optionLabel

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeckColors.Surface,
        titleContentColor = DeckColors.TextPrimary,
        textContentColor = DeckColors.TextPrimary,
        title = {
            Text(
                text = s.settingsDialogTitle,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingSwitchRow(
                    title = s.settingAutoApproveTitle,
                    description = s.settingAutoApproveDesc,
                    checked = autoApproveEnabled,
                    onCheckedChange = onSetAutoApproveEnabled
                )
                SettingSwitchRow(
                    title = s.settingBackgroundTitle,
                    description = s.settingBackgroundDesc,
                    checked = backgroundEnabled,
                    onCheckedChange = onSetBackgroundServiceEnabled
                )

                HorizontalDivider(color = DeckColors.BorderMuted)

                Text(
                    text = s.themeSectionTitle,
                    color = DeckColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                SegmentedOptionRow(
                    options = AppThemeMode.entries.map { it to s.themeLabel(it) },
                    selected = themeMode,
                    onSelect = onSetThemeMode
                )

                Text(
                    text = s.languageSectionTitle,
                    color = DeckColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                SegmentedOptionRow(
                    options = AppLanguage.entries.map { it to it.optionLabel },
                    selected = appLanguage,
                    onSelect = onSetAppLanguage
                )
                if (appLanguage == AppLanguage.SYSTEM) {
                    Text(
                        text = s.languageSystemResolvedNote(resolvedLangLabel),
                        color = DeckColors.TextSecondary,
                        fontSize = 11.sp
                    )
                }

                HorizontalDivider(color = DeckColors.BorderMuted)

                SettingSwitchRow(
                    title = s.settingNotifyApprovalTitle,
                    description = s.settingNotifyApprovalDesc,
                    checked = prefs.notifyOnApproval,
                    onCheckedChange = {
                        onUpdate(prefs.copy(notifyOnApproval = it))
                    }
                )
                SettingSwitchRow(
                    title = s.settingNotifyPreviewTitle,
                    description = s.settingNotifyPreviewDesc,
                    checked = prefs.showApprovalPreviewInNotification,
                    enabled = prefs.notifyOnApproval,
                    onCheckedChange = {
                        onUpdate(prefs.copy(showApprovalPreviewInNotification = it))
                    }
                )
                SettingSwitchRow(
                    title = s.settingNotifyDoneTitle,
                    description = s.settingNotifyDoneDesc,
                    checked = prefs.notifyOnDone,
                    onCheckedChange = {
                        onUpdate(prefs.copy(notifyOnDone = it))
                    }
                )
                SettingSwitchRow(
                    title = s.settingNotifyErrorTitle,
                    description = s.settingNotifyErrorDesc,
                    checked = prefs.notifyOnError,
                    onCheckedChange = {
                        onUpdate(prefs.copy(notifyOnError = it))
                    }
                )
                SettingSwitchRow(
                    title = s.settingHapticsTitle,
                    description = s.settingHapticsDesc,
                    checked = prefs.hapticsEnabled,
                    onCheckedChange = {
                        onUpdate(prefs.copy(hapticsEnabled = it))
                    }
                )

                HorizontalDivider(color = DeckColors.BorderMuted)

                Text(
                    text = s.updateSectionTitle(updateState.currentVersion),
                    color = DeckColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                if (!updateState.statusMessage.isNullOrBlank()) {
                    Text(
                        text = updateState.statusMessage,
                        color = if (updateState.availableUpdate != null) {
                            DeckColors.AccentBlue
                        } else {
                            DeckColors.TextSecondary
                        },
                        fontSize = 12.sp
                    )
                }

                if (updateState.isDownloading) {
                    val progress = (updateState.progressPercent ?: 0) / 100f
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = DeckColors.AccentBlue,
                        trackColor = DeckColors.Background
                    )
                }

                val available = updateState.availableUpdate
                if (available != null) {
                    Button(
                        onClick = onInstallAvailableUpdate,
                        enabled = !updateState.isDownloading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DeckColors.AccentBlue,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (updateState.isDownloading) {
                                s.updateDownloaderProgress(updateState.progressPercent ?: 0)
                            } else {
                                s.updateDownloadAction(available.tagName)
                            },
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onCheckForUpdates,
                        enabled = !updateState.isChecking && !updateState.isDownloading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (updateState.isChecking) {
                                s.updateCheckButtonChecking
                            } else {
                                s.updateCheckAction
                            },
                            fontSize = 13.sp
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        onTriggerSimulation()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = s.simulationActionLabel,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = s.closeButton,
                    color = DeckColors.AccentBlue,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

@Composable
private fun <T> SegmentedOptionRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(value) },
                color = if (isSelected) DeckColors.AccentBlue else DeckColors.Background,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(
                    1.dp,
                    if (isSelected) DeckColors.AccentBlue else DeckColors.BorderMuted
                )
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else DeckColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (enabled) DeckColors.TextPrimary else DeckColors.TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                color = DeckColors.TextSecondary,
                fontSize = 11.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = DeckColors.AccentBlue,
                uncheckedThumbColor = DeckColors.TextSecondary,
                uncheckedTrackColor = DeckColors.Background
            )
        )
    }
}

@Composable
private fun ConversationPickerDialog(
    conversations: List<ConversationSummary>,
    currentConversationId: String?,
    onSelect: (ConversationSummary) -> Unit,
    onDismiss: () -> Unit
) {
    val s = LocalDeckStrings.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeckColors.Surface,
        titleContentColor = DeckColors.TextPrimary,
        textContentColor = DeckColors.TextPrimary,
        title = {
            Text(
                text = s.conversationPickerTitle,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
        },
        text = {
            if (conversations.isEmpty()) {
                Text(
                    text = s.conversationEmpty,
                    color = DeckColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    conversations.forEach { item ->
                        val isCurrent = item.id == currentConversationId
                        val updatedLabel = remember(item.updatedAt) {
                            extractShortDateTime(item.updatedAt)
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelect(item) },
                            color = if (isCurrent) {
                                DeckColors.AccentBlue.copy(alpha = 0.12f)
                            } else {
                                DeckColors.SurfaceElevated
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isCurrent) DeckColors.AccentBlue else DeckColors.BorderMuted
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = item.title,
                                        color = DeckColors.TextPrimary,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isCurrent) {
                                        Surface(
                                            color = DeckColors.AccentBlue,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = s.conversationCurrentBadge,
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(
                                                    horizontal = 6.dp,
                                                    vertical = 2.dp
                                                )
                                            )
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = s.conversationMeta(
                                            item.messageCount,
                                            item.artifactCount
                                        ),
                                        color = DeckColors.TextSecondary,
                                        fontSize = 11.sp
                                    )
                                    if (updatedLabel != null) {
                                        Text(
                                            text = updatedLabel,
                                            color = DeckColors.TextSecondary,
                                            fontSize = 10.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = s.closeButton,
                    color = DeckColors.AccentBlue,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}
