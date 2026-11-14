package com.antigravity.deck.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.antigravity.deck.hardware.DeckNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// バックグラウンドや画面消灯時にもWebSocket接続を維持するためのフォアグラウンドサービス
class DeckForegroundService : Service() {

    private var serviceScope: CoroutineScope? = null

    override fun onCreate() {
        super.onCreate()
        val controller = DeckSessionController.getInstance(applicationContext)
        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

        ServiceCompat.startForeground(
            this,
            DeckNotificationManager.FOREGROUND_NOTIFICATION_ID,
            controller.buildForegroundNotification(),
            foregroundType
        )

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        serviceScope = scope
        scope.launch {
            controller.state
                .map { it.session to it.connectionStatus }
                .distinctUntilChanged()
                .collect { (session, connectionStatus) ->
                    controller.updateForegroundNotification(session, connectionStatus)
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope?.cancel()
        serviceScope = null
        super.onDestroy()
    }

    companion object {
        private const val ACTION_STOP = "com.antigravity.deck.action.STOP_FOREGROUND"

        fun start(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, DeckForegroundService::class.java)
            ContextCompat.startForegroundService(appContext, intent)
        }

        fun stop(context: Context) {
            val appContext = context.applicationContext
            appContext.stopService(Intent(appContext, DeckForegroundService::class.java))
        }
    }
}
