package com.antigravity.deck.model

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

// PC側と共有するエージェント実行状態
enum class AgentStatus(val wireValue: String, val label: String) {
    IDLE("idle", "待機"),
    THINKING("thinking", "思考中"),
    WAITING_APPROVAL("waiting_approval", "承認待ち"),
    DONE("done", "完了"),
    ERROR("error", "エラー");

    companion object {
        fun parse(raw: String?): AgentStatus {
            return entries.firstOrNull { it.wireValue.equals(raw, ignoreCase = true) } ?: IDLE
        }
    }
}

// 推論の深さレベル
enum class ReasoningEffort(val wireValue: String, val label: String) {
    LOW("low", "LOW"),
    MEDIUM("medium", "MED"),
    HIGH("high", "HIGH"),
    XHIGH("xhigh", "XHIGH");

    companion object {
        fun parse(raw: String?): ReasoningEffort {
            return entries.firstOrNull { it.wireValue.equals(raw, ignoreCase = true) } ?: MEDIUM
        }
    }
}

// 単一エージェントの状態モデル
data class AgentSession(
    val title: String,
    val status: AgentStatus,
    val approvalPreview: String?,
    val reasoningEffort: ReasoningEffort,
    val modelName: String = DEFAULT_MODEL,
    val updatedAt: String? = null
) {
    companion object {
        const val DEFAULT_MODEL = "Gemini 4 Hydrogen"

        val LEGACY_MODELS = setOf(
            "auto",
            "gemini-2.5-pro",
            "gemini-2.5-flash",
            "claude-sonnet-4-5",
            "o3"
        )

        val DEFAULT_AVAILABLE_MODELS = listOf(
            "Gemini 4 Hydrogen",
            "Gemini 4 Argon",
            "Gemini 3.8 Flash",
            "Claude Sonnet 5.5",
            "Claude Opus 5.5",
            "GPT-OSS 120B",
            "Orchestrator (Argon)"
        )

        fun initial(): AgentSession {
            return AgentSession(
                title = "Antigravity Agent",
                status = AgentStatus.IDLE,
                approvalPreview = null,
                reasoningEffort = ReasoningEffort.MEDIUM,
                modelName = DEFAULT_MODEL
            )
        }
    }
}

// WebSocket接続先設定
data class DeckEndpointConfig(
    val host: String,
    val port: Int = 8765,
    val token: String = DEFAULT_TOKEN,
    val label: String = "LAN"
) {
    fun buildWebSocketUrl(): String {
        val safeToken = URLEncoder.encode(token, "UTF-8")
        return "ws://$host:$port?token=$safeToken"
    }

    companion object {
        const val DEFAULT_PORT = 8765
        const val DEFAULT_TOKEN = "antigravity-local"

        fun usbLocalhost(token: String = DEFAULT_TOKEN): DeckEndpointConfig {
            return DeckEndpointConfig(
                host = "127.0.0.1",
                port = DEFAULT_PORT,
                token = token,
                label = "USB localhost:8765"
            )
        }
    }
}

// サーバーからの受信メッセージモデル
sealed interface ServerEvent {
    data class StateSync(
        val session: AgentSession,
        val availableModels: List<String>? = null
    ) : ServerEvent

    data class AuthAck(
        val authenticated: Boolean,
        val message: String?
    ) : ServerEvent
}

// JSONメッセージの相互変換ユーティリティ
object ProtocolCodec {

    fun decodeServerEvent(jsonText: String): ServerEvent? {
        val root = try {
            JSONObject(jsonText)
        } catch (_: Exception) {
            return null
        }

        return when (root.optString("type")) {
            "state_sync" -> {
                val item = parseSession(root) ?: return null
                val availableModels = parseAvailableModels(root.optJSONArray("available_models"))
                ServerEvent.StateSync(item, availableModels)
            }

            "session_update" -> {
                val item = parseSession(root) ?: return null
                val availableModels = parseAvailableModels(root.optJSONArray("available_models"))
                ServerEvent.StateSync(item, availableModels)
            }

            "full_sync" -> {
                val array = root.optJSONArray("sessions") ?: JSONArray()
                val activeId = root.optInt("active_session_id", -1)
                var chosenObj: JSONObject? = null
                for (idx in 0 until array.length()) {
                    val obj = array.optJSONObject(idx) ?: continue
                    if (chosenObj == null || (activeId > 0 && obj.optInt("session_id", -1) == activeId)) {
                        chosenObj = obj
                    }
                }
                val item = chosenObj?.let { parseSession(it) } ?: return null
                val availableModels = parseAvailableModels(root.optJSONArray("available_models"))
                ServerEvent.StateSync(item, availableModels)
            }

            "auth_ack" -> {
                val authenticated = root.optBoolean("authenticated", false)
                val message = if (root.isNull("message")) null else root.optString("message")
                ServerEvent.AuthAck(authenticated, message)
            }

            else -> null
        }
    }

    private fun parseAvailableModels(modelsArray: JSONArray?): List<String>? {
        if (modelsArray == null || modelsArray.length() == 0) {
            return null
        }
        val list = buildList {
            for (idx in 0 until modelsArray.length()) {
                val name = modelsArray.optString(idx, "").trim()
                if (name.isNotEmpty()) add(name)
            }
        }
        if (list.isEmpty()) {
            return null
        }
        val isAllLegacy = list.all { AgentSession.LEGACY_MODELS.contains(it.lowercase()) }
        return if (isAllLegacy) AgentSession.DEFAULT_AVAILABLE_MODELS else list
    }

    private fun parseSession(obj: JSONObject): AgentSession? {
        val title = obj.optString("title", "").ifBlank { "Antigravity Agent" }
        val status = AgentStatus.parse(obj.optString("status", "idle"))
        val preview = if (obj.isNull("approval_preview")) {
            null
        } else {
            obj.optString("approval_preview").takeIf { it.isNotEmpty() }
        }
        val effort = ReasoningEffort.parse(obj.optString("reasoning_effort", "medium"))
        val rawModel = obj.optString("model_name", AgentSession.DEFAULT_MODEL)
            .trim()
            .ifBlank { AgentSession.DEFAULT_MODEL }
        val modelName = if (AgentSession.LEGACY_MODELS.contains(rawModel.lowercase())) {
            AgentSession.DEFAULT_MODEL
        } else {
            rawModel
        }
        val updatedAt = if (obj.isNull("updated_at")) null else obj.optString("updated_at")
        return AgentSession(
            title = title,
            status = status,
            approvalPreview = preview,
            reasoningEffort = effort,
            modelName = modelName,
            updatedAt = updatedAt
        )
    }

    fun commandApprove(): String =
        JSONObject().put("type", "approve").toString()

    fun commandReject(reason: String = "スマートフォンから拒否されました"): String =
        JSONObject()
            .put("type", "reject")
            .put("reason", reason)
            .toString()

    fun commandSendPrompt(prompt: String): String =
        JSONObject()
            .put("type", "send_prompt")
            .put("prompt", prompt)
            .toString()

    fun commandSetReasoningEffort(effort: ReasoningEffort): String =
        JSONObject()
            .put("type", "set_reasoning_effort")
            .put("reasoning_effort", effort.wireValue)
            .toString()

    fun commandSetModel(modelName: String): String =
        JSONObject()
            .put("type", "set_model")
            .put("model_name", modelName)
            .toString()

    fun commandSimulateLifecycle(): String =
        JSONObject()
            .put("type", "simulate_lifecycle")
            .toString()

    // QRコード文字列や手入力文字列を接続設定へ変換する
    fun parsePairingInput(rawInput: String): DeckEndpointConfig? {
        val text = rawInput.trim()
        if (text.isEmpty()) return null

        if (text.startsWith("{")) {
            val obj = try {
                JSONObject(text)
            } catch (_: Exception) {
                return null
            }
            val wsUrl = obj.optString("wsUrl", "")
            if (wsUrl.startsWith("ws://") || wsUrl.startsWith("wss://")) {
                parseWsUrl(wsUrl)?.let { return it }
            }
            val host = obj.optString("host", "").trim()
            if (host.isEmpty()) return null
            val port = obj.optInt("port", DeckEndpointConfig.DEFAULT_PORT)
            val token = obj.optString("token", DeckEndpointConfig.DEFAULT_TOKEN)
            return DeckEndpointConfig(host = host, port = port, token = token, label = "LAN ($host:$port)")
        }

        if (text.startsWith("ws://") || text.startsWith("wss://")) {
            return parseWsUrl(text)
        }

        val hostPort = text.split(":")
        val host = hostPort.firstOrNull()?.trim().orEmpty()
        if (host.isEmpty()) return null
        val port = hostPort.getOrNull(1)?.toIntOrNull() ?: DeckEndpointConfig.DEFAULT_PORT
        val label = if (host == "127.0.0.1" || host == "localhost") "USB $host:$port" else "LAN ($host:$port)"
        return DeckEndpointConfig(host = host, port = port, token = DeckEndpointConfig.DEFAULT_TOKEN, label = label)
    }

    private fun parseWsUrl(wsUrl: String): DeckEndpointConfig? {
        val uri = try {
            URI(wsUrl)
        } catch (_: Exception) {
            return null
        }
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else DeckEndpointConfig.DEFAULT_PORT
        var token = DeckEndpointConfig.DEFAULT_TOKEN
        uri.rawQuery?.split("&")?.forEach { pair ->
            val sep = pair.indexOf('=')
            if (sep > 0) {
                val key = URLDecoder.decode(pair.substring(0, sep), "UTF-8")
                if (key == "token") {
                    token = URLDecoder.decode(pair.substring(sep + 1), "UTF-8")
                }
            }
        }
        val label = if (host == "127.0.0.1" || host == "localhost") "USB $host:$port" else "LAN ($host:$port)"
        return DeckEndpointConfig(host = host, port = port, token = token, label = label)
    }
}
