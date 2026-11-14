package com.antigravity.deck.network

import com.antigravity.deck.i18n.AppLanguage
import com.antigravity.deck.i18n.DeckI18n
import com.antigravity.deck.i18n.DeckStrings
import com.antigravity.deck.model.DeckEndpointConfig
import com.antigravity.deck.model.ProtocolCodec
import com.antigravity.deck.model.ServerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlin.math.min

sealed interface ConnectionStatus {
    data object Disconnected : ConnectionStatus
    data object Connecting : ConnectionStatus
    data class Connected(val endpoint: DeckEndpointConfig) : ConnectionStatus
    data class Reconnecting(val attempt: Int, val reason: String) : ConnectionStatus
}

// OkHttpによる自動再接続機能付きWebSocketクライアント
class DeckWebSocketClient(
    private val stringsProvider: () -> DeckStrings = { DeckI18n.strings(AppLanguage.SYSTEM) }
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _endpointConfig = MutableStateFlow(DeckEndpointConfig.usbLocalhost())
    val endpointConfig: StateFlow<DeckEndpointConfig> = _endpointConfig.asStateFlow()

    private val _events = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ServerEvent> = _events.asSharedFlow()

    private var activeSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var manualDisconnect = false
    private var retryCount = 0
    private var connectionEpoch = 0L

    fun connectUsbPreset() {
        connect(DeckEndpointConfig.usbLocalhost())
    }

    fun connect(config: DeckEndpointConfig) {
        manualDisconnect = false
        retryCount = 0
        _endpointConfig.value = config
        reconnectJob?.cancel()
        openSocket(config)
    }

    fun disconnect() {
        manualDisconnect = true
        connectionEpoch += 1L
        reconnectJob?.cancel()
        activeSocket?.close(1000, "User disconnected")
        activeSocket = null
        _connectionStatus.value = ConnectionStatus.Disconnected
    }

    fun send(payloadJson: String): Boolean {
        val socket = activeSocket ?: return false
        return socket.send(payloadJson)
    }

    fun release() {
        disconnect()
        scope.cancel()
        okHttpClient.dispatcher.executorService.shutdown()
    }

    private fun openSocket(config: DeckEndpointConfig) {
        connectionEpoch += 1L
        val myEpoch = connectionEpoch
        activeSocket?.cancel()
        activeSocket = null

        _connectionStatus.value = if (retryCount == 0) {
            ConnectionStatus.Connecting
        } else {
            ConnectionStatus.Reconnecting(retryCount, stringsProvider().wsReconnectingReason)
        }

        val request = Request.Builder()
            .url(config.buildWebSocketUrl())
            .build()

        activeSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (myEpoch != connectionEpoch) {
                    webSocket.close(1000, "Superseded")
                    return
                }
                retryCount = 0
                _connectionStatus.value = ConnectionStatus.Connected(config)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (myEpoch != connectionEpoch) return
                val event = ProtocolCodec.decodeServerEvent(text) ?: return
                _events.tryEmit(event)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (myEpoch != connectionEpoch) return
                activeSocket = null
                if (code == 4001) {
                    _connectionStatus.value = ConnectionStatus.Disconnected
                    return
                }
                scheduleReconnect(config, reason.ifEmpty { stringsProvider().wsClosedReason })
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (myEpoch != connectionEpoch) return
                activeSocket = null
                scheduleReconnect(config, t.message ?: stringsProvider().wsNetworkError)
            }
        })
    }

    private fun scheduleReconnect(config: DeckEndpointConfig, reason: String) {
        if (manualDisconnect) {
            _connectionStatus.value = ConnectionStatus.Disconnected
            return
        }
        val myEpoch = connectionEpoch
        retryCount += 1
        val delayMs = min(10000L, 1000L * (1L shl min(retryCount - 1, 4)))
        _connectionStatus.value = ConnectionStatus.Reconnecting(retryCount, reason)

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delayMs)
            if (!manualDisconnect && myEpoch == connectionEpoch) {
                openSocket(config)
            }
        }
    }
}
