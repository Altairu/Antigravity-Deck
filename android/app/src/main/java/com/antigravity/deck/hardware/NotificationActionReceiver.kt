package com.antigravity.deck.hardware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class NotificationActionEvent(
    val approved: Boolean
)

// 通知アクションボタンからのイベントをViewModelへ伝えるプロセス内バス
object NotificationActionBus {
    private val _actions = MutableSharedFlow<NotificationActionEvent>(extraBufferCapacity = 16)
    val actions: SharedFlow<NotificationActionEvent> = _actions.asSharedFlow()

    fun emitAction(event: NotificationActionEvent) {
        _actions.tryEmit(event)
    }
}

// 通知バー上のApprove/Rejectボタンタップを受け取るBroadcastReceiver
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val approved = when (intent.action) {
            ACTION_APPROVE -> true
            ACTION_REJECT -> false
            else -> return
        }

        NotificationManagerCompat.from(context)
            .cancel(DeckNotificationManager.NOTIFICATION_ID)

        NotificationActionBus.emitAction(
            NotificationActionEvent(approved = approved)
        )
    }

    companion object {
        const val ACTION_APPROVE = "com.antigravity.deck.action.NOTIFICATION_APPROVE"
        const val ACTION_REJECT = "com.antigravity.deck.action.NOTIFICATION_REJECT"
    }
}
