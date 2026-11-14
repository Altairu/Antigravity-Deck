// PC側WebSocketサーバーおよびHook HTTPサーバーの自動結合テスト

import assert from "node:assert/strict";
import http from "node:http";
import WebSocket from "ws";
import { SessionStore } from "../dist/state/session-store.js";
import { DeckWebSocketServer } from "../dist/server/ws-server.js";
import { DeckHookServer } from "../dist/server/hook-server.js";

async function run() {
  const store = new SessionStore();
  const calls = [];
  const bridge = {
    async approve() {
      calls.push(["approve"]);
    },
    async reject(_c, r) {
      calls.push(["reject", r]);
    },
    async sendPrompt(p) {
      calls.push(["prompt", p]);
    },
    async setReasoningEffort(e) {
      calls.push(["effort", e]);
    },
    async setModel(m) {
      calls.push(["model", m]);
    }
  };

  const wsServer = new DeckWebSocketServer({
    port: 18765,
    authToken: "test-token",
    sessionStore: store,
    commandBridge: bridge
  });

  const hookServer = new DeckHookServer({
    port: 18766,
    sessionStore: store,
    getConnectedClients: () => wsServer.getConnectedClientCount(),
    shouldWaitForMobileApproval: () => true
  });

  await wsServer.start();
  await hookServer.start();

  try {
    // 1. 不正トークンは拒否されること
    await new Promise((resolve) => {
      const badWs = new WebSocket("ws://127.0.0.1:18765?token=wrong");
      badWs.on("close", (code) => {
        assert.equal(code, 4001);
        resolve();
      });
    });

    // 2. 正しいトークンで接続し単一エージェントのstate_syncを受信すること
    const messages = [];
    const ws = new WebSocket("ws://127.0.0.1:18765?token=test-token");
    ws.on("message", (buf) => messages.push(JSON.parse(buf.toString())));
    await new Promise((resolve) => ws.on("open", resolve));

    await new Promise((r) => setTimeout(r, 100));
    assert.equal(messages[0]?.type, "state_sync");
    assert.equal(messages[0]?.status, "idle");
    assert.ok(Array.isArray(messages[0]?.available_models));
    assert.ok(messages[0].available_models.includes("gemini-2.5-pro"));

    // 3. PreToolUse Hookからwaiting_approvalが配信され、approve送信でallowが返ること
    const hookPromise = new Promise((resolve, reject) => {
      const body = JSON.stringify({
        hookEventName: "PreToolUse",
        conversationId: "conv-1",
        lastUserInput: "テスト実行",
        toolCall: { name: "run_command", args: { CommandLine: "npm test" } }
      });
      const req = http.request(
        {
          hostname: "127.0.0.1",
          port: 18766,
          path: "/hook",
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Content-Length": Buffer.byteLength(body)
          }
        },
        (res) => {
          const chunks = [];
          res.on("data", (c) => chunks.push(c));
          res.on("end", () => resolve(JSON.parse(Buffer.concat(chunks).toString())));
        }
      );
      req.on("error", reject);
      req.write(body);
      req.end();
    });

    await new Promise((r) => setTimeout(r, 150));
    const waitingMsg = messages.find(
      (m) => m.type === "state_sync" && m.status === "waiting_approval"
    );
    assert.ok(waitingMsg, "waiting_approval update should be received");
    assert.match(waitingMsg.approval_preview, /npm test/);

    ws.send(JSON.stringify({ type: "approve" }));
    const hookResult = await hookPromise;
    assert.equal(hookResult.decision, "allow");

    // 4. 推論レベル変更、モデル変更、プロンプト送信がブリッジまで届くこと
    ws.send(JSON.stringify({ type: "set_reasoning_effort", reasoning_effort: "xhigh" }));
    ws.send(JSON.stringify({ type: "set_model", model_name: "gemini-2.5-pro" }));
    ws.send(JSON.stringify({ type: "send_prompt", prompt: "次へ進む" }));
    await new Promise((r) => setTimeout(r, 120));

    assert.ok(calls.some((c) => c[0] === "approve"));
    assert.ok(calls.some((c) => c[0] === "effort" && c[1] === "xhigh"));
    assert.ok(calls.some((c) => c[0] === "model" && c[1] === "gemini-2.5-pro"));
    assert.ok(calls.some((c) => c[0] === "prompt" && c[1] === "次へ進む"));
    assert.ok(
      messages.some((m) => m.type === "state_sync" && m.model_name === "gemini-2.5-pro"),
      "model_name update should be broadcast"
    );

    ws.close();
    console.log("EXTENSION_INTEGRATION_TEST_OK");
  } finally {
    store.dispose();
    await hookServer.stop();
    await wsServer.stop();
  }
}

run().catch((err) => {
  console.error(err);
  process.exit(1);
});
