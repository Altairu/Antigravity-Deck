package com.antigravity.deck.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

// プロトコルのJSONエンコードおよびデコードの整合性テスト
class ProtocolModelsTest {

    @Test
    fun decodeStateSync_parsesSingleAgentAndAvailableModels() {
        val raw = """
            {
              "type": "state_sync",
              "timestamp": "2026-11-14T20:45:00.000Z",
              "title": "認証修正",
              "status": "waiting_approval",
              "approval_preview": "$ npm test",
              "last_response": "テストを準備しています。",
              "reasoning_effort": "xhigh",
              "model_name": "Gemini 4 Hydrogen",
              "available_models": ["Gemini 4 Hydrogen", "Gemini 4 Argon", "Claude Sonnet 5.5"]
            }
        """.trimIndent()

        val event = ProtocolCodec.decodeServerEvent(raw) as? ServerEvent.StateSync
        assertNotNull(event)
        assertEquals(
            listOf("Gemini 4 Hydrogen", "Gemini 4 Argon", "Claude Sonnet 5.5"),
            event!!.availableModels
        )
        assertEquals("認証修正", event.session.title)
        assertEquals(AgentStatus.WAITING_APPROVAL, event.session.status)
        assertEquals("$ npm test", event.session.approvalPreview)
        assertEquals("テストを準備しています。", event.session.lastResponse)
        assertEquals(ReasoningEffort.XHIGH, event.session.reasoningEffort)
        assertEquals("Gemini 4 Hydrogen", event.session.modelName)
    }

    @Test
    fun decodeStateSync_handlesNullApprovalPreview() {
        val raw = """
            {
              "type": "state_sync",
              "title": "ビルド確認",
              "status": "thinking",
              "approval_preview": null,
              "reasoning_effort": "high",
              "model_name": "Gemini 4 Argon"
            }
        """.trimIndent()

        val event = ProtocolCodec.decodeServerEvent(raw) as? ServerEvent.StateSync
        assertNotNull(event)
        assertEquals(AgentStatus.THINKING, event!!.session.status)
        assertEquals("Gemini 4 Argon", event.session.modelName)
        assertNull(event.session.approvalPreview)
    }

    @Test
    fun decodeStateSync_replacesLegacyModelsWithCurrentCatalog() {
        val raw = """
            {
              "type": "state_sync",
              "title": "旧サーバー互換確認",
              "status": "idle",
              "approval_preview": null,
              "reasoning_effort": "medium",
              "model_name": "gemini-2.5-pro",
              "available_models": ["auto", "gemini-2.5-pro", "gemini-2.5-flash", "claude-sonnet-4-5", "o3"]
            }
        """.trimIndent()

        val event = ProtocolCodec.decodeServerEvent(raw) as? ServerEvent.StateSync
        assertNotNull(event)
        assertEquals(AgentSession.DEFAULT_MODEL, event!!.session.modelName)
        assertEquals(AgentSession.DEFAULT_AVAILABLE_MODELS, event.availableModels)
    }

    @Test
    fun commands_encodeWithoutSessionId() {
        val modelObj = JSONObject(ProtocolCodec.commandSetModel("Claude Sonnet 5.5"))
        assertEquals("set_model", modelObj.getString("type"))
        assertEquals("Claude Sonnet 5.5", modelObj.getString("model_name"))
        assertFalse(modelObj.has("session_id"))

        val approveObj = JSONObject(ProtocolCodec.commandApprove())
        assertEquals("approve", approveObj.getString("type"))
        assertFalse(approveObj.has("session_id"))
    }

    @Test
    fun parsePairingInput_supportsJsonAndWsUrl() {
        val jsonPayload = """{"host":"192.168.10.5","port":8765,"token":"secret-1"}"""
        val fromJson = ProtocolCodec.parsePairingInput(jsonPayload)
        assertNotNull(fromJson)
        assertEquals("192.168.10.5", fromJson!!.host)
        assertEquals(8765, fromJson.port)
        assertEquals("secret-1", fromJson.token)

        val wsPayload = "ws://127.0.0.1:8765?token=antigravity-local"
        val fromWs = ProtocolCodec.parsePairingInput(wsPayload)
        assertNotNull(fromWs)
        assertEquals("127.0.0.1", fromWs!!.host)
        assertEquals("antigravity-local", fromWs.token)
    }

    @Test
    fun notificationPreferences_filtersByStatusAndHaptics() {
        val prefs = NotificationPreferences(
            notifyOnApproval = false,
            showApprovalPreviewInNotification = false,
            notifyOnDone = true,
            notifyOnError = true,
            hapticsEnabled = true
        )
        assertFalse(prefs.shouldNotify(AgentStatus.WAITING_APPROVAL))
        assertFalse(prefs.shouldVibrate(AgentStatus.WAITING_APPROVAL))
        assertEquals(true, prefs.shouldNotify(AgentStatus.DONE))
        assertEquals(true, prefs.shouldVibrate(AgentStatus.DONE))

        val silentPrefs = prefs.copy(hapticsEnabled = false)
        assertEquals(true, silentPrefs.shouldNotify(AgentStatus.DONE))
        assertFalse(silentPrefs.shouldVibrate(AgentStatus.DONE))
    }

    @Test
    fun decodeStateSync_parsesConversationMessages() {
        val raw = """
            {
              "type": "state_sync",
              "timestamp": "2026-11-15T02:00:00.000Z",
              "title": "履歴同期テスト",
              "status": "done",
              "approval_preview": null,
              "last_response": "修正が完了しました。",
              "messages": [
                { "role": "user", "content": "ビルドエラーを直して", "timestamp": "2026-11-15T01:59:00Z" },
                { "role": "assistant", "content": "修正が完了しました。", "timestamp": "2026-11-15T02:00:00Z" },
                { "role": "assistant", "content": "   " }
              ],
              "reasoning_effort": "medium",
              "model_name": "Gemini 4 Hydrogen"
            }
        """.trimIndent()

        val event = ProtocolCodec.decodeServerEvent(raw) as? ServerEvent.StateSync
        assertNotNull(event)
        val messages = event!!.session.messages
        assertEquals(2, messages.size)
        assertEquals(MessageRole.USER, messages[0].role)
        assertEquals("ビルドエラーを直して", messages[0].content)
        assertEquals("2026-11-15T01:59:00Z", messages[0].timestamp)
        assertEquals(MessageRole.ASSISTANT, messages[1].role)
        assertEquals("修正が完了しました。", messages[1].content)
    }
}
