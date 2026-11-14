package com.antigravity.deck.hardware

import android.Manifest
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
import com.antigravity.deck.model.AgentSession
import com.antigravity.deck.model.AgentStatus

// エージェントの承認待ちや完了、エラー状態を通知バーへ配信するマネージャー
class DeckNotificationManager(context: Context) {

    private val appContext = context.applicationContext
    private val managerCompat = NotificationManagerCompat.from(appContext)

    init {
        ensureChannels()
    }

    private fun ensureChannels() {
        val systemManager = appContext.getSystemService(NotificationManager::class.java) ?: return

        val alertsChannel = NotificationChannel(
            CHANNEL_ALERTS,
            "承認待ち / エラー通知",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "承認待ちやエラー発生時の緊急通知を表示します"
            enableVibration(true)
        }

        val statusChannel = NotificationChannel(
            CHANNEL_STATUS,
            "タスク完了通知",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "エージェントのタスク完了を通知します"
        }

        systemManager.createNotificationChannel(alertsChannel)
        systemManager.createNotificationChannel(statusChannel)
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

    fun notifySessionTransition(session: AgentSession) {
        if (session.status == AgentStatus.IDLE || session.status == AgentStatus.THINKING) {
            cancel()
            return
        }

        if (!hasNotificationPermission()) {
            return
        }

        val openAppIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            appContext,
            REQUEST_OPEN_APP,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = when (session.status) {
            AgentStatus.WAITING_APPROVAL -> {
                val preview = session.approvalPreview ?: "ツール実行の確認待ちです"
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
                    .setContentTitle("承認待ち: ${session.title}")
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
                NotificationCompat.Builder(appContext, CHANNEL_STATUS)
                    .setSmallIcon(R.drawable.ic_deck_notification)
                    .setContentTitle("タスク完了")
                    .setContentText("${session.title} (${session.modelName})")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setContentIntent(contentPendingIntent)
            }

            AgentStatus.ERROR -> {
                val detail = session.approvalPreview ?: session.title
                NotificationCompat.Builder(appContext, CHANNEL_ALERTS)
                    .setSmallIcon(R.drawable.ic_deck_notification)
                    .setContentTitle("エラー発生")
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
        const val NOTIFICATION_ID = 4001

        private const val REQUEST_OPEN_APP = 100
        private const val REQUEST_APPROVE = 101
        private const val REQUEST_REJECT = 102
    }
}
