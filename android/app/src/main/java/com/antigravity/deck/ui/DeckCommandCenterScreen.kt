package com.antigravity.deck.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import com.antigravity.deck.hardware.SpeechUiState
import com.antigravity.deck.model.AgentSession
import com.antigravity.deck.model.AgentStatus
import com.antigravity.deck.model.NotificationPreferences
import com.antigravity.deck.model.ReasoningEffort
import com.antigravity.deck.network.ConnectionStatus
import com.antigravity.deck.ui.theme.DeckColors

@Composable
fun DeckCommandCenterScreen(
    uiState: DeckUiState,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onSendPrompt: (String) -> Unit,
    onSetReasoningEffort: (ReasoningEffort) -> Unit,
    onSetModel: (String) -> Unit,
    onConnectUsbPreset: () -> Unit,
    onOpenPairingSheet: () -> Unit,
    onDismissPairingSheet: () -> Unit,
    onSubmitPairingInput: (String) -> Boolean,
    onOpenNotificationSettings: () -> Unit,
    onDismissNotificationSettings: () -> Unit,
    onUpdateNotificationPrefs: (NotificationPreferences) -> Unit,
    onTriggerSimulation: () -> Unit,
    onStartPushToTalk: () -> Unit,
    onStopPushToTalk: () -> Unit
) {
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

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var showTextComposer by remember { mutableStateOf(true) }
    var textPromptDraft by remember { mutableStateOf("") }

    // 思考中カード向けのパルスアニメーションを共有する
    val infiniteTransition = rememberInfiniteTransition(label = "deck_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DeckColors.Background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 接続状態とワンタップ接続バー
            TopConnectionBar(
                connectionStatus = uiState.connectionStatus,
                onConnectUsbPreset = onConnectUsbPreset,
                onOpenPairingSheet = onOpenPairingSheet,
                onOpenNotificationSettings = onOpenNotificationSettings,
                onTriggerSimulation = onTriggerSimulation
            )

            // 通知未許可時のコンパクトな案内バー
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                    color = Color(0xFF332709),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, DeckColors.StatusApproval)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsOff,
                            contentDescription = "通知許可",
                            tint = DeckColors.StatusApproval,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "通知を受け取るにはタップして通知権限を許可してください",
                            color = DeckColors.StatusApproval,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 上部: 単一エージェント状態カード
            AgentStatusHeroCard(
                session = uiState.session,
                thinkingPulseAlpha = pulseAlpha
            )

            // 中部: Approval Preview と状態ログ領域
            ApprovalPreviewPanel(
                session = uiState.session,
                speechState = uiState.speechState,
                feedbackBanner = uiState.feedbackBanner,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            // 下部: Command Dock
            CommandDock(
                session = uiState.session,
                availableModels = uiState.availableModels,
                speechState = uiState.speechState,
                hasAudioPermission = hasAudioPermission,
                showTextComposer = showTextComposer,
                textPromptDraft = textPromptDraft,
                onTextDraftChange = { textPromptDraft = it },
                onToggleTextComposer = { showTextComposer = !showTextComposer },
                onSendTextPrompt = {
                    val prompt = textPromptDraft.trim()
                    if (prompt.isNotEmpty()) {
                        onSendPrompt(prompt)
                        textPromptDraft = ""
                    }
                },
                onApprove = onApprove,
                onReject = onReject,
                onSetReasoningEffort = onSetReasoningEffort,
                onSetModel = onSetModel,
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
        NotificationSettingsDialog(
            prefs = uiState.notificationPrefs,
            onUpdate = onUpdateNotificationPrefs,
            onDismiss = onDismissNotificationSettings
        )
    }
}

@Composable
private fun TopConnectionBar(
    connectionStatus: ConnectionStatus,
    onConnectUsbPreset: () -> Unit,
    onOpenPairingSheet: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onTriggerSimulation: () -> Unit
) {
    val (dotColor, statusText) = when (connectionStatus) {
        is ConnectionStatus.Connected -> DeckColors.StatusDone to connectionStatus.endpoint.label
        ConnectionStatus.Connecting -> DeckColors.StatusThinking to "接続中..."
        is ConnectionStatus.Reconnecting -> DeckColors.StatusApproval to "再接続 (${connectionStatus.attempt})"
        ConnectionStatus.Disconnected -> DeckColors.StatusError to "未接続"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DeckColors.Surface)
            .border(1.dp, DeckColors.BorderMuted, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Antigravity Deck",
                style = MaterialTheme.typography.labelMedium,
                color = DeckColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                color = DeckColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        CompactActionChip(
            label = "USB",
            icon = Icons.Rounded.Usb,
            tint = DeckColors.StatusThinking,
            onClick = onConnectUsbPreset
        )
        Spacer(modifier = Modifier.width(6.dp))
        CompactActionChip(
            label = "QR",
            icon = Icons.Rounded.QrCodeScanner,
            tint = DeckColors.TextPrimary,
            onClick = onOpenPairingSheet
        )
        Spacer(modifier = Modifier.width(6.dp))
        CompactActionChip(
            label = "Sim",
            icon = Icons.Rounded.PlayArrow,
            tint = DeckColors.StatusApproval,
            onClick = onTriggerSimulation
        )
        Spacer(modifier = Modifier.width(6.dp))
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onOpenNotificationSettings),
            color = DeckColors.SurfaceElevated,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, DeckColors.BorderMuted)
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Notifications,
                    contentDescription = "通知設定",
                    tint = DeckColors.TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun CompactActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = DeckColors.SurfaceElevated,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, DeckColors.BorderMuted)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                color = tint,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun AgentStatusHeroCard(
    session: AgentSession,
    thinkingPulseAlpha: Float
) {
    val baseAccent = when (session.status) {
        AgentStatus.IDLE -> DeckColors.StatusIdle
        AgentStatus.THINKING -> DeckColors.StatusThinking
        AgentStatus.WAITING_APPROVAL -> DeckColors.StatusApproval
        AgentStatus.DONE -> DeckColors.StatusDone
        AgentStatus.ERROR -> DeckColors.StatusError
    }

    val targetBackground = when (session.status) {
        AgentStatus.IDLE -> DeckColors.Surface
        AgentStatus.THINKING -> Color(0xFF0E2942)
        AgentStatus.WAITING_APPROVAL -> Color(0xFF332709)
        AgentStatus.DONE -> Color(0xFF0B2E22)
        AgentStatus.ERROR -> Color(0xFF381216)
    }

    val targetBorder = when (session.status) {
        AgentStatus.THINKING -> baseAccent.copy(alpha = thinkingPulseAlpha)
        AgentStatus.IDLE -> DeckColors.BorderMuted
        else -> baseAccent
    }

    val animatedBg by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = tween(durationMillis = 280),
        label = "hero_card_bg"
    )
    val animatedBorder by animateColorAsState(
        targetValue = targetBorder,
        animationSpec = tween(durationMillis = 220),
        label = "hero_card_border"
    )

    val borderWidth = if (session.status == AgentStatus.WAITING_APPROVAL || session.status == AgentStatus.THINKING) {
        2.dp
    } else {
        1.dp
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = animatedBg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(borderWidth, animatedBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val dotAlpha = if (session.status == AgentStatus.THINKING) thinkingPulseAlpha else 1f
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(baseAccent.copy(alpha = dotAlpha))
                    )
                    Text(
                        text = session.status.label,
                        color = baseAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = DeckColors.Background.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, DeckColors.BorderMuted)
                ) {
                    Text(
                        text = "${session.modelName} / ${session.reasoningEffort.label}",
                        color = DeckColors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = session.title,
                color = DeckColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 21.sp
            )
        }
    }
}

@Composable
private fun ApprovalPreviewPanel(
    session: AgentSession,
    speechState: SpeechUiState,
    feedbackBanner: String?,
    modifier: Modifier = Modifier
) {
    val isWaiting = session.status == AgentStatus.WAITING_APPROVAL
    val isWaitingApprovalWithPreview = isWaiting && !session.approvalPreview.isNullOrBlank()
    val hasResponse = !isWaitingApprovalWithPreview &&
        session.approvalPreview.isNullOrBlank() &&
        !session.lastResponse.isNullOrBlank()
    val borderColor by animateColorAsState(
        targetValue = if (isWaiting) DeckColors.StatusApproval else DeckColors.BorderMuted,
        animationSpec = tween(durationMillis = 250),
        label = "preview_border"
    )

    Surface(
        modifier = modifier,
        color = DeckColors.Surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (isWaiting) 1.5.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = when {
                    isWaitingApprovalWithPreview || isWaiting -> "承認待ちプレビュー"
                    hasResponse -> "Antigravity からの返答"
                    else -> "エージェント出力 / コンテキスト"
                },
                color = when {
                    isWaiting -> DeckColors.StatusApproval
                    hasResponse -> DeckColors.StatusDone
                    else -> DeckColors.TextSecondary
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // 音声認識中や操作ログがある場合のインラインステータス
            val statusMessage = when {
                speechState.isListening -> "音声入力中: ${speechState.partialText.ifEmpty { "..." }}"
                speechState.isFinalizing -> speechState.statusHint ?: "確定中..."
                !feedbackBanner.isNullOrEmpty() -> feedbackBanner
                !speechState.statusHint.isNullOrEmpty() -> speechState.statusHint
                else -> null
            }
            if (statusMessage != null) {
                Text(
                    text = statusMessage,
                    color = if (speechState.isListening) DeckColors.StatusThinking else DeckColors.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 返答テキストまたは承認待ちプレビューのスクロール表示領域
            val scrollState = rememberScrollState()
            LaunchedEffect(session.lastResponse, session.approvalPreview) {
                scrollState.scrollTo(0)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF060A12))
                    .padding(10.dp)
                    .verticalScroll(scrollState)
            ) {
                val previewContent = when {
                    !session.approvalPreview.isNullOrBlank() -> session.approvalPreview
                    !session.lastResponse.isNullOrBlank() -> session.lastResponse
                    session.status == AgentStatus.THINKING -> "エージェントが思考およびツール実行を進めています..."
                    session.status == AgentStatus.DONE -> "タスクは正常に完了しました。"
                    session.status == AgentStatus.ERROR -> "エラー状態になりました。承認または新規プロンプトを送信してください。"
                    else -> "承認待ちのコマンドや差分はありません。下部の入力欄またはマイクボタン長押しからプロンプトを送信できます。"
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
                        color = if (isWaiting || !session.lastResponse.isNullOrBlank()) {
                            DeckColors.TextPrimary
                        } else {
                            DeckColors.TextSecondary
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CommandDock(
    session: AgentSession,
    availableModels: List<String>,
    speechState: SpeechUiState,
    hasAudioPermission: Boolean,
    showTextComposer: Boolean,
    textPromptDraft: String,
    onTextDraftChange: (String) -> Unit,
    onToggleTextComposer: () -> Unit,
    onSendTextPrompt: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onSetReasoningEffort: (ReasoningEffort) -> Unit,
    onSetModel: (String) -> Unit,
    onRequestAudioPermission: () -> Unit,
    onStartPushToTalk: () -> Unit,
    onStopPushToTalk: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DeckColors.Surface)
            .border(1.dp, DeckColors.BorderMuted, RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // チャットプロンプト入力行
        if (showTextComposer) {
            val hasDraft = textPromptDraft.isNotBlank()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = textPromptDraft,
                    onValueChange = onTextDraftChange,
                    placeholder = {
                        Text(
                            text = "Antigravity へプロンプトを送信",
                            color = DeckColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSendTextPrompt() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DeckColors.TextPrimary,
                        unfocusedTextColor = DeckColors.TextPrimary,
                        focusedContainerColor = DeckColors.Background,
                        unfocusedContainerColor = DeckColors.Background,
                        focusedBorderColor = DeckColors.StatusThinking,
                        unfocusedBorderColor = DeckColors.BorderMuted,
                        cursorColor = DeckColors.StatusThinking
                    ),
                    modifier = Modifier.weight(1f)
                )
                FilledIconButton(
                    onClick = onSendTextPrompt,
                    enabled = hasDraft,
                    shape = RoundedCornerShape(10.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = DeckColors.AccentBlue,
                        contentColor = Color.White,
                        disabledContainerColor = DeckColors.SurfaceElevated,
                        disabledContentColor = DeckColors.TextSecondary
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "送信"
                    )
                }
            }
        }

        // Approve / Reject 大型アクションボタン
        val isWaitingApproval = session.status == AgentStatus.WAITING_APPROVAL
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onReject,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isWaitingApproval) DeckColors.RejectRed else Color(0xFF4C1D24),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Reject", modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Reject", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Button(
                onClick = onApprove,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isWaitingApproval) DeckColors.ApproveGreen else Color(0xFF144235),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Icon(Icons.Rounded.Check, contentDescription = "Approve", modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Approve", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }

        // Push-to-Talk マイクボタンとキーボード切り替え
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .height(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onToggleTextComposer),
                color = DeckColors.SurfaceElevated,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, DeckColors.BorderMuted)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Keyboard,
                        contentDescription = "テキスト入力切り替え",
                        tint = if (showTextComposer) DeckColors.StatusThinking else DeckColors.TextSecondary
                    )
                }
            }

            PushToTalkButton(
                isListening = speechState.isListening,
                hasAudioPermission = hasAudioPermission,
                onRequestPermission = onRequestAudioPermission,
                onPressStart = onStartPushToTalk,
                onPressRelease = onStopPushToTalk,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            )
        }

        // AIモデル切り替えセレクター
        ModelSelectorRow(
            models = availableModels,
            selectedModel = session.modelName,
            onSelectModel = onSetModel
        )

        // Reasoning Effort セレクター (low / medium / high / xhigh)
        ReasoningEffortSelector(
            selected = session.reasoningEffort,
            onSelect = onSetReasoningEffort
        )
    }
}

@Composable
private fun PushToTalkButton(
    isListening: Boolean,
    hasAudioPermission: Boolean,
    onRequestPermission: () -> Unit,
    onPressStart: () -> Unit,
    onPressRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentHasPermission by rememberUpdatedState(hasAudioPermission)
    val currentRequestPermission by rememberUpdatedState(onRequestPermission)
    val currentOnStart by rememberUpdatedState(onPressStart)
    val currentOnRelease by rememberUpdatedState(onPressRelease)

    val bgColor by animateColorAsState(
        targetValue = if (isListening) DeckColors.AccentBlue else DeckColors.SurfaceElevated,
        animationSpec = tween(durationMillis = 150),
        label = "ptt_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isListening) DeckColors.StatusThinking else DeckColors.BorderMuted,
        animationSpec = tween(durationMillis = 150),
        label = "ptt_border"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
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
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Mic,
                contentDescription = "Push to Talk",
                tint = if (isListening) Color.White else DeckColors.StatusThinking
            )
            Text(
                text = when {
                    !hasAudioPermission -> "マイク権限を許可して音声入力"
                    isListening -> "録音中... 指を離すと送信"
                    else -> "長押しで音声プロンプト送信"
                },
                color = if (isListening) Color.White else DeckColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun ModelSelectorRow(
    models: List<String>,
    selectedModel: String,
    onSelectModel: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        models.forEach { model ->
            val isActive = model.equals(selectedModel, ignoreCase = true)
            val bg by animateColorAsState(
                targetValue = if (isActive) DeckColors.AccentBlue else DeckColors.Background,
                animationSpec = tween(durationMillis = 180),
                label = "model_bg_$model"
            )
            val borderColor by animateColorAsState(
                targetValue = if (isActive) DeckColors.StatusThinking else DeckColors.BorderMuted,
                animationSpec = tween(durationMillis = 180),
                label = "model_border_$model"
            )
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelectModel(model) },
                color = bg,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, borderColor)
            ) {
                Text(
                    text = model,
                    color = if (isActive) Color.White else DeckColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun ReasoningEffortSelector(
    selected: ReasoningEffort,
    onSelect: (ReasoningEffort) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DeckColors.Background)
            .border(1.dp, DeckColors.BorderMuted, RoundedCornerShape(10.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ReasoningEffort.entries.forEach { effort ->
            val isActive = effort == selected
            val bg by animateColorAsState(
                targetValue = if (isActive) DeckColors.AccentBlue else Color.Transparent,
                animationSpec = tween(durationMillis = 180),
                label = "effort_bg_${effort.wireValue}"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .clickable { onSelect(effort) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = effort.label,
                    color = if (isActive) Color.White else DeckColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun NotificationSettingsDialog(
    prefs: NotificationPreferences,
    onUpdate: (NotificationPreferences) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeckColors.Surface,
        titleContentColor = DeckColors.TextPrimary,
        textContentColor = DeckColors.TextPrimary,
        title = {
            Text(
                text = "通知の詳細設定",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NotificationSettingRow(
                    title = "承認待ちの通知",
                    description = "コマンド実行やファイル編集の確認待ちを通知",
                    checked = prefs.notifyOnApproval,
                    onCheckedChange = {
                        onUpdate(prefs.copy(notifyOnApproval = it))
                    }
                )
                NotificationSettingRow(
                    title = "承認通知にプレビュー表示",
                    description = "承認待ち通知の中に実行コマンドや差分詳細を含める",
                    checked = prefs.showApprovalPreviewInNotification,
                    enabled = prefs.notifyOnApproval,
                    onCheckedChange = {
                        onUpdate(prefs.copy(showApprovalPreviewInNotification = it))
                    }
                )
                NotificationSettingRow(
                    title = "会話終了の通知",
                    description = "エージェントの応答やタスク完了時に通知",
                    checked = prefs.notifyOnDone,
                    onCheckedChange = {
                        onUpdate(prefs.copy(notifyOnDone = it))
                    }
                )
                NotificationSettingRow(
                    title = "エラー発生の通知",
                    description = "ツールエラーや異常終了時に通知",
                    checked = prefs.notifyOnError,
                    onCheckedChange = {
                        onUpdate(prefs.copy(notifyOnError = it))
                    }
                )
                NotificationSettingRow(
                    title = "通知時のバイブレーション",
                    description = "オンにした通知のタイミングに合わせて振動",
                    checked = prefs.hapticsEnabled,
                    onCheckedChange = {
                        onUpdate(prefs.copy(hapticsEnabled = it))
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "閉じる",
                    color = DeckColors.StatusThinking,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

@Composable
private fun NotificationSettingRow(
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
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                color = DeckColors.TextSecondary,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = DeckColors.AccentBlue,
                uncheckedThumbColor = DeckColors.TextSecondary,
                uncheckedTrackColor = DeckColors.Background
            )
        )
    }
}
