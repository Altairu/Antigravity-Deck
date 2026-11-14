// PC側WebSocketサーバーおよびHook HTTPサーバーの自動結合テスト

import assert from "node:assert/strict";
import fs from "node:fs/promises";
import http from "node:http";
import os from "node:os";
import path from "node:path";
import WebSocket from "ws";
import { SessionStore } from "../dist/state/session-store.js";
import { DeckWebSocketServer } from "../dist/server/ws-server.js";
import { DeckHookServer } from "../dist/server/hook-server.js";
import { hasWorkspaceHooks, setupWorkspaceHooks } from "../dist/hooks/workspace-hooks.js";
import { compareSemver, parseLatestVsixRelease } from "../dist/updater/github-release.js";

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

  const tempBrainRoot = await fs.mkdtemp(path.join(os.tmpdir(), "deck-brain-test-"));
  const tempFreshWorkspace = await fs.mkdtemp(path.join(os.tmpdir(), "deck-hooks-fresh-"));
  const tempMergeWorkspace = await fs.mkdtemp(path.join(os.tmpdir(), "deck-hooks-merge-"));

  const hookServer = new DeckHookServer({
    port: 18766,
    sessionStore: store,
    getConnectedClients: () => wsServer.getConnectedClientCount(),
    shouldWaitForMobileApproval: () => true,
    brainRootDir: tempBrainRoot
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
    assert.equal(messages[0]?.model_name, "Gemini 4 Hydrogen");
    assert.ok(Array.isArray(messages[0]?.available_models));
    assert.ok(messages[0].available_models.includes("Gemini 4 Hydrogen"));
    assert.ok(messages[0].available_models.includes("Claude Sonnet 5.5"));

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
    ws.send(JSON.stringify({ type: "set_model", model_name: "Claude Sonnet 5.5" }));
    ws.send(JSON.stringify({ type: "send_prompt", prompt: "次へ進む" }));
    await new Promise((r) => setTimeout(r, 120));

    assert.ok(calls.some((c) => c[0] === "approve"));
    assert.ok(calls.some((c) => c[0] === "effort" && c[1] === "xhigh"));
    assert.ok(calls.some((c) => c[0] === "model" && c[1] === "Claude Sonnet 5.5"));
    assert.ok(calls.some((c) => c[0] === "prompt" && c[1] === "次へ進む"));
    assert.ok(
      messages.some((m) => m.type === "state_sync" && m.model_name === "Claude Sonnet 5.5"),
      "model_name update should be broadcast"
    );

    // 5. 内部モデルIDが来た場合も表示名と推論レベルへ正規化されること
    ws.send(JSON.stringify({ type: "set_model", model_name: "gemini-4-argon-high" }));
    await new Promise((r) => setTimeout(r, 80));
    assert.ok(
      messages.some(
        (m) =>
          m.type === "state_sync" &&
          m.model_name === "Gemini 4 Argon" &&
          m.reasoning_effort === "high"
      ),
      "internal model ID should normalize to display name and effort"
    );

    // 6. Stop Hook時にトランスクリプトから最新返答(last_response)が配信されること
    const logsDir = path.join(tempBrainRoot, "conv-1", ".system_generated", "logs");
    await fs.mkdir(logsDir, { recursive: true });
    const transcriptLines = [
      JSON.stringify({ source: "USER_EXPLICIT", type: "USER_INPUT", content: "テスト質問" }),
      JSON.stringify({
        source: "MODEL",
        type: "PLANNER_RESPONSE",
        content: "スマートフォンへ返す最新の回答テキストです。"
      })
    ].join("\n");
    await fs.writeFile(path.join(logsDir, "transcript_full.jsonl"), transcriptLines, "utf8");

    await new Promise((resolve, reject) => {
      const body = JSON.stringify({
        hookEventName: "Stop",
        conversationId: "conv-1"
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
          res.resume();
          res.on("end", resolve);
        }
      );
      req.on("error", reject);
      req.write(body);
      req.end();
    });

    await new Promise((r) => setTimeout(r, 80));
    assert.ok(
      messages.some(
        (m) =>
          m.type === "state_sync" &&
          m.status === "done" &&
          m.last_response === "スマートフォンへ返す最新の回答テキストです。"
      ),
      "last_response should be extracted and broadcast on Stop hook"
    );

    // 7. GitHub Releasesバージョンの比較とアセット解析が正しく動くこと
    assert.ok(compareSemver("0.1.0", "v0.2.0") < 0);
    assert.equal(compareSemver("0.2.0", "v0.2.0"), 0);
    assert.ok(compareSemver("0.2.1", "0.2.0") > 0);
    const parsedRelease = parseLatestVsixRelease(
      JSON.stringify({
        tag_name: "v0.2.0",
        html_url: "https://github.com/Altairu/Antigravity-Deck/releases/tag/v0.2.0",
        assets: [
          {
            name: "antigravity-deck-0.2.0.apk",
            browser_download_url: "https://example.com/app.apk"
          },
          {
            name: "antigravity-deck-0.2.0.vsix",
            browser_download_url: "https://example.com/ext.vsix"
          }
        ]
      })
    );
    assert.equal(parsedRelease?.version, "0.2.0");
    assert.equal(parsedRelease?.assetName, "antigravity-deck-0.2.0.vsix");

    // 8. ワークスペースHooks自動導入が新規作成と既存設定マージの両方を正しく処理すること
    assert.equal(await hasWorkspaceHooks(tempFreshWorkspace), false);
    const freshResult = await setupWorkspaceHooks(tempFreshWorkspace);
    assert.equal(freshResult.mergedExisting, false);
    assert.equal(await hasWorkspaceHooks(tempFreshWorkspace), true);
    const freshScript = await fs.readFile(freshResult.scriptPath, "utf8");
    assert.match(freshScript, /8766/);

    await fs.mkdir(path.join(tempMergeWorkspace, ".agents"), { recursive: true });
    await fs.writeFile(
      path.join(tempMergeWorkspace, ".agents", "hooks.json"),
      JSON.stringify({ "custom-hook": { enabled: true } }, null, 2),
      "utf8"
    );
    assert.equal(await hasWorkspaceHooks(tempMergeWorkspace), false);
    const mergeResult = await setupWorkspaceHooks(tempMergeWorkspace, { hookPort: 18766 });
    assert.equal(mergeResult.mergedExisting, true);
    assert.equal(await hasWorkspaceHooks(tempMergeWorkspace), true);

    const mergedHooksJson = JSON.parse(await fs.readFile(mergeResult.hooksJsonPath, "utf8"));
    assert.deepEqual(mergedHooksJson["custom-hook"], { enabled: true });
    assert.equal(mergedHooksJson["antigravity-deck"]?.enabled, true);
    assert.ok(Array.isArray(mergedHooksJson["antigravity-deck"]?.PreToolUse));

    const mergedScript = await fs.readFile(mergeResult.scriptPath, "utf8");
    assert.match(mergedScript, /18766/);

    ws.close();
    console.log("EXTENSION_INTEGRATION_TEST_OK");
  } finally {
    store.dispose();
    await hookServer.stop();
    await wsServer.stop();
    await fs.rm(tempBrainRoot, { recursive: true, force: true });
    await fs.rm(tempFreshWorkspace, { recursive: true, force: true });
    await fs.rm(tempMergeWorkspace, { recursive: true, force: true });
  }
}

run().catch((err) => {
  console.error(err);
  process.exit(1);
});
