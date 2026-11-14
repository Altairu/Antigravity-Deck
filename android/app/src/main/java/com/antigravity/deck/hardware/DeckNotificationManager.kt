package com.antigravity.deck.hardware

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.antigravity.deck.MainActivity
import com.antigravity.deck.R
import com.antigravity.deck.i18n.AppLanguage
import com.antigravity.deck.i18n.DeckI18n
import com.antigravity.deck.i18n.DeckStrings
import com.antigravity.deck.model.AgentSession
import com.antigravity.deck.model.AgentStatus
import com.antigravity.deck.model.NotificationPreferences
import com.antigravity.deck.network.ConnectionStatus

// エージェントの承認待ちや完了、エラー状態および常駐通知を配信するマネージャー
class DeckNotificationManager(context: Context) {

    private val appContext = context.applicationContext
    private val managerCompat = NotificationManagerCompat.from(appContext)

    init {
        ensureChannels(DeckI18n.strings(AppLanguage.SYSTEM))
    }

    fun refreshChannels(strings: DeckStrings) {
        ensureChannels(strings)
    }

    private fun ensureChannels(strings: DeckStrings) {
        val systemManager = appContext.getSystemService(NotificationManager::class.java) ?: return

        val alertsChannel = NotificationChannel(
            CHANNEL_ALERTS,
            strings.channelAlertsName,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = strings.channelAlertsDesc
            enableVibration(true)
        }

        val statusChannel = NotificationChannel(
            CHANNEL_STATUS,
            strings.channelStatusName,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = strings.channelStatusDesc
        }

        val foregroundChannel = NotificationChannel(
            CHANNEL_FOREGROUND,
            strings.channelForegroundName,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = strings.channelForegroundDesc
            setShowBadge(false)
        }

        systemManager.createNotificationChannel(alertsChannel)
        systemManager.createNotificationChannel(statusChannel)
        systemManager.createNotificationChannel(foregroundChannel)
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun cancel() {
        managerCompat.cancel(NOTIFICATION_ID)
    }

    fun buildForegroundNotification(
        session: AgentSession,
        connectionStatus: ConnectionStatus,
        strings: DeckStrings = DeckI18n.strings(AppLanguage.SYSTEM)
    ): Notification {
        val contentPendingIntent = createOpenAppPendingIntent()

        val title = if (connectionStatus is ConnectionStatus.Connected) {
            strings.fgTitleConnected(strings.statusLabel(session.status))
        } else {
            strings.fgTitleStandby
        }
        val body = "${session.title} / ${session.modelName}"

        return NotificationCompat.Builder(appContext, CHANNEL_FOREGROUND)
            .setSmallIcon(R.drawable.ic_deck_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(contentPendingIntent)
            .build()
    }

    fun updateForegroundNotification(
        session: AgentSession,
        connectionStatus: ConnectionStatus,
        strings: DeckStrings = DeckI18n.strings(AppLanguage.SYSTEM)
    ) {
        if (!hasNotificationPermission()) {
            return
        }
        try {
            managerCompat.notify(
                FOREGROUND_NOTIFICATION_ID,
                buildForegroundNotification(session, connectionStatus, strings)
            )
        } catch (_: SecurityException) {
            // 通知権限が取り消されていた場合は無視する
        }
    }

    fun notifySessionTransition(
        session: AgentSession,
        prefs: NotificationPreferences = NotificationPreferences(),
        strings: DeckStrings = DeckI18n.strings(AppLanguage.SYSTEM)
    ) {
        if (!prefs.shouldNotify(session.status)) {
            cancel()
            return
        }

        if (!hasNotificationPermission()) {
            return
        }

        val contentPendingIntent = createOpenAppPendingIntent()

        val builder = when (session.status) {
            AgentStatus.WAITING_APPROVAL -> {
                val preview = if (prefs.showApprovalPreviewInNotification) {
                    session.approvalPreview ?: strings.notifyApprovalPreviewFallback
                } else {
                    strings.notifyApprovalHiddenPreview
                }
                val approveIntent = createActionPendingIntent(
                    action = NotificationActionReceiver.ACTION_APPROVE,
                    requestCode = REQUEST_APPROVE
                )
                val rejectIntent = createActionPendingIntent(
                    action = NotificationActionReceiver.ACTION_REJECT,
                    requestCode = REQUEST_REJECT
                )

                NotificationCompat.Builder(appContext, CHANNEL_ALERTS)
                    .setSmallIcon(R.drawable.ic_deck_notification)
                    .setContentTitle(strings.notifyApprovalTitle(session.title))
                    .setContentText(preview)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setAutoCancel(true)
                    .setContentIntent(contentPendingIntent)
                    .addAction(0, "Approve", approveIntent)
                    .addAction(0, "Reject", rejectIntent)
            }

            AgentStatus.DONE -> {
                val summary = session.lastResponse?.takeIf { it.isNotBlank() }
                    ?: "${session.title} (${session.modelName})"
                NotificationCompat.Builder(appContext, CHANNEL_STATUS)
                    .setSmallIcon(R.drawable.ic_deck_notification)
                    .setContentTitle(strings.notifyDoneTitle(session.title))
                    .setContentText(summary)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setContentIntent(contentPendingIntent)
            }

            AgentStatus.ERROR -> {
                val detail = session.approvalPreview ?: session.title
                NotificationCompat.Builder(appContext, CHANNEL_ALERTS)
                    .setSmallIcon(R.drawable.ic_deck_notification)
                    .setContentTitle(strings.notifyErrorTitle)
                    .setContentText(detail)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_ERROR)
                    .setAutoCancel(true)
                    .setContentIntent(contentPendingIntent)
            }

            AgentStatus.IDLE,
            AgentStatus.THINKING -> return
        }

        try {
            managerCompat.notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // 通知権限が取り消されていた場合は無視する
        }
    }

    private fun createOpenAppPendingIntent(): PendingIntent {
        val openAppIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            appContext,
            REQUEST_OPEN_APP,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createActionPendingIntent(
        action: String,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(appContext, NotificationActionReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_ALERTS = "deck_alerts"
        const val CHANNEL_STATUS = "deck_status"
        const val CHANNEL_FOREGROUND = "deck_foreground_service"
        const val FOREGROUND_NOTIFICATION_ID = 4000
        const val NOTIFICATION_ID = 4001

        private const val REQUEST_OPEN_APP = 100
        private const val REQUEST_APPROVE = 101
        private const val REQUEST_REJECT = 102
    }
}
