package com.antigravity.deck.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import com.antigravity.deck.model.AgentStatus
import com.antigravity.deck.ui.theme.AppThemeMode
import java.util.Locale

// アプリ内の表示言語設定
enum class AppLanguage(val wireValue: String, val optionLabel: String) {
    SYSTEM("system", "System"),
    JAPANESE("ja", "日本語"),
    ENGLISH("en", "English");

    fun resolve(locale: Locale = Locale.getDefault()): AppLanguage {
        return when (this) {
            SYSTEM -> if (locale.language.equals("ja", ignoreCase = true)) JAPANESE else ENGLISH
            JAPANESE, ENGLISH -> this
        }
    }

    fun speechLocaleTag(locale: Locale = Locale.getDefault()): String {
        return when (resolve(locale)) {
            AppLanguage.JAPANESE -> "ja-JP"
            AppLanguage.ENGLISH, AppLanguage.SYSTEM -> "en-US"
        }
    }

    companion object {
        fun parse(raw: String?): AppLanguage {
            return entries.firstOrNull { it.wireValue.equals(raw, ignoreCase = true) } ?: SYSTEM
        }
    }
}

data class DeckStrings(
    // ステータス表示
    val statusIdle: String,
    val statusThinking: String,
    val statusWaitingApproval: String,
    val statusDone: String,
    val statusError: String,

    // 接続状態
    val connConnecting: String,
    val connReconnectingPrefix: String,
    val connDisconnected: String,
    val wsReconnectingReason: String,
    val wsClosedReason: String,
    val wsNetworkError: String,

    // コントローラー操作バナー
    val bannerNotConnected: String,
    val bannerApprovedFromNotification: String,
    val bannerRejectedFromNotification: String,
    val bannerAuthMismatch: String,
    val bannerApproveSent: String,
    val bannerRejectSent: String,
    val bannerPromptSent: (String) -> String,
    val bannerModelSwitched: (String) -> String,
    val bannerConversationSwitched: (String) -> String,
    val bannerConnectingUsb: String,
    val bannerConnectingEndpoint: (String) -> String,
    val bannerSimulationStarted: String,
    val bannerSimulationNotConnected: String,
    val bannerLanguageChanged: (String) -> String,
    val rejectReasonFromMobile: String,

    // 通知チャンネルと通知本文
    val channelAlertsName: String,
    val channelAlertsDesc: String,
    val channelStatusName: String,
    val channelStatusDesc: String,
    val channelForegroundName: String,
    val channelForegroundDesc: String,
    val fgTitleConnected: (String) -> String,
    val fgTitleStandby: String,
    val notifyApprovalPreviewFallback: String,
    val notifyApprovalHiddenPreview: String,
    val notifyApprovalTitle: (String) -> String,
    val notifyDoneTitle: (String) -> String,
    val notifyErrorTitle: String,

    // 音声入力 (Push-to-Talk)
    val speechUnavailable: String,
    val speechListeningHint: String,
    val speechStartFailed: (String) -> String,
    val speechFinalizing: String,
    val speechNoAudio: String,
    val speechNotRecognized: String,
    val speechPermissionMissing: String,
    val speechBusy: String,
    val speechErrorCode: (Int) -> String,
    val speechSent: (String) -> String,

    // メイン画面 UI
    val notificationPermissionDesc: String,
    val notificationPermissionPrompt: String,
    val settingsButtonDesc: String,
    val sectionWaitingApproval: String,
    val sectionAgentResponse: String,
    val sectionAgentOutput: String,
    val tabHistory: String,
    val tabLatest: String,
    val tabApproval: String,
    val tabArtifacts: String,
    val sectionConversationHistory: String,
    val sectionArtifacts: String,
    val roleUser: String,
    val roleAssistant: String,
    val emptyHistory: String,
    val emptyArtifacts: String,
    val conversationPickerTitle: String,
    val conversationSwitchButton: String,
    val conversationEmpty: String,
    val conversationCurrentBadge: String,
    val conversationMeta: (Int, Int) -> String,
    val speechOverlayListening: (String) -> String,
    val speechOverlayFinalizingFallback: String,
    val emptyThinking: String,
    val emptyDone: String,
    val emptyError: String,
    val emptyIdle: String,
    val promptPlaceholder: String,
    val sendButtonDesc: String,
    val toggleKeyboardDesc: String,
    val pttNeedPermission: String,
    val pttRecording: String,
    val pttHoldToSpeak: String,

    // 設定ダイアログ (通知・言語・テーマ・バックグラウンド・アップデート)
    val settingsDialogTitle: String,
    val languageSectionTitle: String,
    val languageSystemResolvedNote: (String) -> String,
    val themeSectionTitle: String,
    val themeSystemLabel: String,
    val themeLightLabel: String,
    val themeDarkLabel: String,
    val settingBackgroundTitle: String,
    val settingBackgroundDesc: String,
    val bannerBackgroundStarted: String,
    val bannerBackgroundStopped: String,
    val bannerThemeChanged: (String) -> String,
    val simulationActionLabel: String,
    val settingNotifyApprovalTitle: String,
    val settingNotifyApprovalDesc: String,
    val settingNotifyPreviewTitle: String,
    val settingNotifyPreviewDesc: String,
    val settingNotifyDoneTitle: String,
    val settingNotifyDoneDesc: String,
    val settingNotifyErrorTitle: String,
    val settingNotifyErrorDesc: String,
    val settingHapticsTitle: String,
    val settingHapticsDesc: String,
    val updateSectionTitle: (String) -> String,
    val updateChecking: String,
    val updateAvailable: (String) -> String,
    val updateUpToDate: (String) -> String,
    val updateNoReleases: String,
    val updateCheckFailed: (String) -> String,
    val updateDownloading: (String) -> String,
    val updateDownloaderProgress: (Int) -> String,
    val updateDownloadAction: (String) -> String,
    val updateCheckAction: String,
    val updateCheckButtonChecking: String,
    val updateInstallerLaunched: String,
    val updateInstallPermissionNeeded: String,
    val updateFailed: (String) -> String,
    val updateDownloadFailed: (String) -> String,
    val updateGenericError: String,
    val updateGithubApiError: (Int) -> String,
    val updateNetworkError: String,
    val updateApkDownloadFailed: (Int) -> String,
    val updateApkEmpty: String,
    val updateInstallerLaunchError: String,
    val closeButton: String,

    // QR ペアリングダイアログ
    val pairingTitle: String,
    val pairingDesc: String,
    val pairingQrParseError: String,
    val pairingCameraPermissionNeeded: String,
    val pairingGrantCameraPermission: String,
    val pairingUrlFieldLabel: String,
    val pairingInvalidEndpoint: String,
    val pairingConnectButton: String
) {
    fun statusLabel(status: AgentStatus): String {
        return when (status) {
            AgentStatus.IDLE -> statusIdle
            AgentStatus.THINKING -> statusThinking
            AgentStatus.WAITING_APPROVAL -> statusWaitingApproval
            AgentStatus.DONE -> statusDone
            AgentStatus.ERROR -> statusError
        }
    }

    fun themeLabel(mode: AppThemeMode): String {
        return when (mode) {
            AppThemeMode.SYSTEM -> themeSystemLabel
            AppThemeMode.LIGHT -> themeLightLabel
            AppThemeMode.DARK -> themeDarkLabel
        }
    }
}

val JapaneseStrings = DeckStrings(
    statusIdle = "待機",
    statusThinking = "思考中",
    statusWaitingApproval = "承認待ち",
    statusDone = "完了",
    statusError = "エラー",

    connConnecting = "接続中...",
    connReconnectingPrefix = "再接続",
    connDisconnected = "未接続",
    wsReconnectingReason = "再接続試行中",
    wsClosedReason = "接続が閉じられました",
    wsNetworkError = "ネットワークエラー",

    bannerNotConnected = "未接続のため送信できませんでした",
    bannerApprovedFromNotification = "通知から操作を承認しました",
    bannerRejectedFromNotification = "通知から操作を拒否しました",
    bannerAuthMismatch = "認証トークンが一致しません",
    bannerApproveSent = "承認コマンドを送信しました",
    bannerRejectSent = "拒否コマンドを送信しました",
    bannerPromptSent = { text -> "プロンプト送信: $text" },
    bannerModelSwitched = { model -> "モデルを $model に切り替えました" },
    bannerConversationSwitched = { title -> "会話を切り替えました: $title" },
    bannerConnectingUsb = "USB localhost:8765 へ接続を試行しています",
    bannerConnectingEndpoint = { label -> "$label へ接続しています" },
    bannerSimulationStarted = "テストライフサイクルを開始しました",
    bannerSimulationNotConnected = "未接続のためシミュレーションを開始できませんでした",
    bannerLanguageChanged = { label -> "表示言語を $label に切り替えました" },
    rejectReasonFromMobile = "スマートフォンから拒否されました",

    channelAlertsName = "承認待ち / エラー通知",
    channelAlertsDesc = "承認待ちやエラー発生時の緊急通知を表示します",
    channelStatusName = "タスク完了通知",
    channelStatusDesc = "エージェントのタスク完了を通知します",
    channelForegroundName = "バックグラウンド監視常駐",
    channelForegroundDesc = "バックグラウンドでPC側とのWebSocket接続を維持するための常駐通知です",
    fgTitleConnected = { status -> "Antigravity Deck 監視中: $status" },
    fgTitleStandby = "Antigravity Deck バックグラウンド待機中",
    notifyApprovalPreviewFallback = "ツール実行の確認待ちです",
    notifyApprovalHiddenPreview = "PC側で承認待ちの操作があります",
    notifyApprovalTitle = { title -> "承認待ち: $title" },
    notifyDoneTitle = { title -> "タスク完了: $title" },
    notifyErrorTitle = "エラー発生",

    speechUnavailable = "この端末では音声認識サービスを利用できません",
    speechListeningHint = "聞き取り中... 離すと送信します",
    speechStartFailed = { msg -> "音声認識の開始に失敗しました: $msg" },
    speechFinalizing = "音声を確定しています...",
    speechNoAudio = "音声が検出されませんでした",
    speechNotRecognized = "音声を認識できませんでした",
    speechPermissionMissing = "マイク権限がありません",
    speechBusy = "音声認識がビジー状態です",
    speechErrorCode = { code -> "音声認識エラー (code=$code)" },
    speechSent = { text -> "送信: $text" },

    notificationPermissionDesc = "通知許可",
    notificationPermissionPrompt = "通知を受け取るにはタップして通知権限を許可してください",
    settingsButtonDesc = "設定と通知",
    sectionWaitingApproval = "承認待ちプレビュー",
    sectionAgentResponse = "Antigravity からの返答",
    sectionAgentOutput = "エージェント出力 / コンテキスト",
    tabHistory = "履歴",
    tabLatest = "最新",
    tabApproval = "承認",
    tabArtifacts = "成果物",
    sectionConversationHistory = "会話履歴",
    sectionArtifacts = "生成されたアーティファクト",
    roleUser = "あなた",
    roleAssistant = "Antigravity",
    emptyHistory = "まだ会話履歴はありません。プロンプトを送信するとここにやり取りが表示されます。",
    emptyArtifacts = "この会話で生成されたアーティファクトはまだありません。",
    conversationPickerTitle = "会話セッションを切り替え",
    conversationSwitchButton = "会話一覧",
    conversationEmpty = "保存された会話セッションが見つかりません。",
    conversationCurrentBadge = "表示中",
    conversationMeta = { msgCount, artifactCount -> "履歴 ${msgCount}件 / 成果物 ${artifactCount}件" },
    speechOverlayListening = { text -> "音声入力中: $text" },
    speechOverlayFinalizingFallback = "確定中...",
    emptyThinking = "エージェントが思考およびツール実行中です...",
    emptyDone = "タスクは正常に完了しました。",
    emptyError = "エラー状態になりました。承認または新しい指示を送信してください。",
    emptyIdle = "承認待ちのコマンドや差分はありません。下部の入力欄またはマイクボタン長押しからプロンプトを送信できます。",
    promptPlaceholder = "Antigravity へプロンプトを送信",
    sendButtonDesc = "送信",
    toggleKeyboardDesc = "テキスト入力切り替え",
    pttNeedPermission = "マイク権限を許可して音声入力",
    pttRecording = "録音中... 指を離すと送信",
    pttHoldToSpeak = "長押しで音声プロンプト送信",

    settingsDialogTitle = "設定",
    languageSectionTitle = "表示言語 / Language",
    languageSystemResolvedNote = { resolved -> "端末の設定に従う (現在: $resolved)" },
    themeSectionTitle = "テーマ / Theme",
    themeSystemLabel = "自動",
    themeLightLabel = "ライト",
    themeDarkLabel = "ダーク",
    settingBackgroundTitle = "バックグラウンド監視",
    settingBackgroundDesc = "画面を閉じても接続と通知を維持する",
    bannerBackgroundStarted = "バックグラウンド監視を開始しました",
    bannerBackgroundStopped = "バックグラウンド監視を停止しました",
    bannerThemeChanged = { label -> "テーマを $label に切り替えました" },
    simulationActionLabel = "動作テスト (Simulate)",
    settingNotifyApprovalTitle = "承認待ちの通知",
    settingNotifyApprovalDesc = "コマンド実行やファイル編集の確認待ちを通知",
    settingNotifyPreviewTitle = "承認通知にプレビュー表示",
    settingNotifyPreviewDesc = "承認待ち通知の中に実行コマンドや差分詳細を含める",
    settingNotifyDoneTitle = "会話終了の通知",
    settingNotifyDoneDesc = "エージェントの応答やタスク完了時に通知",
    settingNotifyErrorTitle = "エラー発生の通知",
    settingNotifyErrorDesc = "ツールエラーや異常終了時に通知",
    settingHapticsTitle = "通知時のバイブレーション",
    settingHapticsDesc = "オンにした通知のタイミングに合わせて振動",
    updateSectionTitle = { ver -> "アプリのアップデート (v$ver)" },
    updateChecking = "GitHub Releases を確認中...",
    updateAvailable = { tag -> "新しいバージョン $tag が利用可能です" },
    updateUpToDate = { ver -> "最新バージョンです (v$ver)" },
    updateNoReleases = "公開済みのリリースはまだありません",
    updateCheckFailed = { msg -> "確認失敗: $msg" },
    updateDownloading = { tag -> "$tag をダウンロード中..." },
    updateDownloaderProgress = { pct -> "ダウンロード中 ($pct%)" },
    updateDownloadAction = { tag -> "$tag をダウンロードして更新" },
    updateCheckAction = "最新バージョンを確認",
    updateCheckButtonChecking = "GitHub Releases を確認中...",
    updateInstallerLaunched = "インストーラを起動しました",
    updateInstallPermissionNeeded = "このアプリからのインストールを許可してから再実行してください",
    updateFailed = { msg -> "更新失敗: $msg" },
    updateDownloadFailed = { msg -> "ダウンロード失敗: $msg" },
    updateGenericError = "エラー",
    updateGithubApiError = { code -> "GitHub API エラー ($code)" },
    updateNetworkError = "ネットワークエラー",
    updateApkDownloadFailed = { code -> "APKダウンロード失敗 ($code)" },
    updateApkEmpty = "APKデータが空です",
    updateInstallerLaunchError = "インストーラ起動エラー",
    closeButton = "閉じる",

    pairingTitle = "PCペアリング接続",
    pairingDesc = "拡張機能の Show Pairing QR をスキャンするか、接続URLを入力してください",
    pairingQrParseError = "QRコードの接続形式を解析できませんでした",
    pairingCameraPermissionNeeded = "QR読み取りにはカメラ権限が必要です",
    pairingGrantCameraPermission = "カメラ権限を許可",
    pairingUrlFieldLabel = "WebSocket URL または IP:Port",
    pairingInvalidEndpoint = "接続先を認識できませんでした",
    pairingConnectButton = "接続する"
)

val EnglishStrings = DeckStrings(
    statusIdle = "Idle",
    statusThinking = "Thinking",
    statusWaitingApproval = "Approval",
    statusDone = "Done",
    statusError = "Error",

    connConnecting = "Connecting...",
    connReconnectingPrefix = "Retry",
    connDisconnected = "Offline",
    wsReconnectingReason = "Reconnecting",
    wsClosedReason = "Connection closed",
    wsNetworkError = "Network error",

    bannerNotConnected = "Not connected to PC",
    bannerApprovedFromNotification = "Approved from notification",
    bannerRejectedFromNotification = "Rejected from notification",
    bannerAuthMismatch = "Authentication token mismatch",
    bannerApproveSent = "Approval sent",
    bannerRejectSent = "Rejection sent",
    bannerPromptSent = { text -> "Sent: $text" },
    bannerModelSwitched = { model -> "Switched model to $model" },
    bannerConversationSwitched = { title -> "Switched conversation: $title" },
    bannerConnectingUsb = "Connecting to USB localhost:8765",
    bannerConnectingEndpoint = { label -> "Connecting to $label" },
    bannerSimulationStarted = "Started test lifecycle",
    bannerSimulationNotConnected = "Not connected; cannot start simulation",
    bannerLanguageChanged = { label -> "Language switched to $label" },
    rejectReasonFromMobile = "Rejected from mobile app",

    channelAlertsName = "Approval & Error Alerts",
    channelAlertsDesc = "Urgent alerts when agent approval is needed or an error occurs",
    channelStatusName = "Task Completion",
    channelStatusDesc = "Notifications when an agent task completes",
    channelForegroundName = "Background Sync Service",
    channelForegroundDesc = "Persistent notification to keep the WebSocket connection alive",
    fgTitleConnected = { status -> "Antigravity Deck Active: $status" },
    fgTitleStandby = "Antigravity Deck Standby",
    notifyApprovalPreviewFallback = "Tool execution awaiting approval",
    notifyApprovalHiddenPreview = "Action awaiting approval on PC",
    notifyApprovalTitle = { title -> "Approval needed: $title" },
    notifyDoneTitle = { title -> "Task completed: $title" },
    notifyErrorTitle = "Error occurred",

    speechUnavailable = "Speech recognition is unavailable on this device",
    speechListeningHint = "Listening... Release to send",
    speechStartFailed = { msg -> "Failed to start speech recognition: $msg" },
    speechFinalizing = "Finalizing speech...",
    speechNoAudio = "No speech detected",
    speechNotRecognized = "Could not recognize speech",
    speechPermissionMissing = "Microphone permission is required",
    speechBusy = "Speech recognizer is busy",
    speechErrorCode = { code -> "Speech recognition error (code=$code)" },
    speechSent = { text -> "Sent: $text" },

    notificationPermissionDesc = "Notification permission",
    notificationPermissionPrompt = "Tap to allow notification permission to receive alerts",
    settingsButtonDesc = "Settings & Notifications",
    sectionWaitingApproval = "Approval Preview",
    sectionAgentResponse = "Response from Antigravity",
    sectionAgentOutput = "Agent Output / Context",
    tabHistory = "History",
    tabLatest = "Latest",
    tabApproval = "Approval",
    tabArtifacts = "Artifacts",
    sectionConversationHistory = "Conversation History",
    sectionArtifacts = "Generated Artifacts",
    roleUser = "You",
    roleAssistant = "Antigravity",
    emptyHistory = "No conversation history yet. Send a prompt to see messages here.",
    emptyArtifacts = "No artifacts generated in this conversation yet.",
    conversationPickerTitle = "Switch Conversation",
    conversationSwitchButton = "Sessions",
    conversationEmpty = "No saved conversations found.",
    conversationCurrentBadge = "Active",
    conversationMeta = { msgCount, artifactCount -> "$msgCount msgs / $artifactCount artifacts" },
    speechOverlayListening = { text -> "Voice input: $text" },
    speechOverlayFinalizingFallback = "Finalizing...",
    emptyThinking = "Agent is thinking and executing tools...",
    emptyDone = "Task completed successfully.",
    emptyError = "An error occurred. Approve or send a new prompt.",
    emptyIdle = "No pending approvals or diffs. Type below or hold the mic button to send a prompt.",
    promptPlaceholder = "Send prompt to Antigravity",
    sendButtonDesc = "Send",
    toggleKeyboardDesc = "Toggle text input",
    pttNeedPermission = "Allow mic permission for voice input",
    pttRecording = "Recording... Release to send",
    pttHoldToSpeak = "Hold to send voice prompt",

    settingsDialogTitle = "Settings",
    languageSectionTitle = "Language / 表示言語",
    languageSystemResolvedNote = { resolved -> "Follow device setting (current: $resolved)" },
    themeSectionTitle = "Theme",
    themeSystemLabel = "System",
    themeLightLabel = "Light",
    themeDarkLabel = "Dark",
    settingBackgroundTitle = "Background monitoring",
    settingBackgroundDesc = "Keep WebSocket connection and alerts active in background",
    bannerBackgroundStarted = "Background monitoring started",
    bannerBackgroundStopped = "Background monitoring stopped",
    bannerThemeChanged = { label -> "Theme switched to $label" },
    simulationActionLabel = "Run Test Lifecycle",
    settingNotifyApprovalTitle = "Approval notifications",
    settingNotifyApprovalDesc = "Notify when command execution or file edits need approval",
    settingNotifyPreviewTitle = "Show preview in notification",
    settingNotifyPreviewDesc = "Include command or diff details in approval alerts",
    settingNotifyDoneTitle = "Task completion notifications",
    settingNotifyDoneDesc = "Notify when the agent finishes responding or completes a task",
    settingNotifyErrorTitle = "Error notifications",
    settingNotifyErrorDesc = "Notify on tool errors or abnormal termination",
    settingHapticsTitle = "Vibration on alerts",
    settingHapticsDesc = "Vibrate when enabled notifications are triggered",
    updateSectionTitle = { ver -> "App Updates (v$ver)" },
    updateChecking = "Checking GitHub Releases...",
    updateAvailable = { tag -> "New version $tag is available" },
    updateUpToDate = { ver -> "You are up to date (v$ver)" },
    updateNoReleases = "No published releases yet",
    updateCheckFailed = { msg -> "Check failed: $msg" },
    updateDownloading = { tag -> "Downloading $tag..." },
    updateDownloaderProgress = { pct -> "Downloading ($pct%)" },
    updateDownloadAction = { tag -> "Download & Install $tag" },
    updateCheckAction = "Check for Updates",
    updateCheckButtonChecking = "Checking GitHub Releases...",
    updateInstallerLaunched = "Installer launched",
    updateInstallPermissionNeeded = "Allow installing unknown apps from this app and try again",
    updateFailed = { msg -> "Update failed: $msg" },
    updateDownloadFailed = { msg -> "Download failed: $msg" },
    updateGenericError = "Error",
    updateGithubApiError = { code -> "GitHub API error ($code)" },
    updateNetworkError = "Network error",
    updateApkDownloadFailed = { code -> "APK download failed ($code)" },
    updateApkEmpty = "APK payload is empty",
    updateInstallerLaunchError = "Failed to launch installer",
    closeButton = "Close",

    pairingTitle = "Pair with PC",
    pairingDesc = "Scan the Show Pairing QR code from the extension or enter the connection URL",
    pairingQrParseError = "Could not parse QR pairing payload",
    pairingCameraPermissionNeeded = "Camera permission is required to scan QR codes",
    pairingGrantCameraPermission = "Allow Camera",
    pairingUrlFieldLabel = "WebSocket URL or IP:Port",
    pairingInvalidEndpoint = "Invalid connection endpoint",
    pairingConnectButton = "Connect"
)

object DeckI18n {
    fun strings(language: AppLanguage, locale: Locale = Locale.getDefault()): DeckStrings {
        return when (language.resolve(locale)) {
            AppLanguage.JAPANESE -> JapaneseStrings
            AppLanguage.ENGLISH, AppLanguage.SYSTEM -> EnglishStrings
        }
    }
}

val LocalDeckStrings = staticCompositionLocalOf { EnglishStrings }
