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
import {
  listConversations,
  readConversationArtifacts,
  readConversationHistory
} from "../dist/state/transcript-reader.js";
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

  const tempBrainRoot = await fs.mkdtemp(path.join(os.tmpdir(), "deck-brain-test-"));
  const tempFreshWorkspace = await fs.mkdtemp(path.join(os.tmpdir(), "deck-hooks-fresh-"));
  const tempMergeWorkspace = await fs.mkdtemp(path.join(os.tmpdir(), "deck-hooks-merge-"));

  const wsServer = new DeckWebSocketServer({
    port: 18765,
    authToken: "test-token",
    sessionStore: store,
    commandBridge: bridge,
    brainRootDir: tempBrainRoot
  });

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

    // 6. Stop Hook時にトランスクリプトから最新返答(last_response)、会話履歴(messages)、成果物(artifacts)が配信されること
    const conv1Dir = path.join(tempBrainRoot, "conv-1");
    const logsDir = path.join(conv1Dir, ".system_generated", "logs");
    await fs.mkdir(logsDir, { recursive: true });
    await fs.writeFile(
      path.join(conv1Dir, "implementation_plan.md"),
      "# 実装計画\n\n* 機能Aを実装する\n",
      "utf8"
    );
    await fs.writeFile(
      path.join(conv1Dir, "implementation_plan.md.metadata.json"),
      JSON.stringify({
        summary: "機能Aの実装計画書",
        updatedAt: "2026-11-15T01:00:00Z"
      }),
      "utf8"
    );
    await fs.writeFile(path.join(conv1Dir, "task.md"), "# タスク一覧\n\n- [x] Step 1\n", "utf8");

    const transcriptLines = [
      JSON.stringify({
        source: "USER_EXPLICIT",
        type: "USER_INPUT",
        content: "<CONTEXT_SUMMARY>要約</CONTEXT_SUMMARY>",
        created_at: "2026-11-15T00:00:00Z"
      }),
      JSON.stringify({
        source: "USER_EXPLICIT",
        type: "USER_INPUT",
        content: "<USER_REQUEST>\n最初の質問\n</USER_REQUEST>\n<ADDITIONAL_METADATA>ignored</ADDITIONAL_METADATA>",
        created_at: "2026-11-15T00:01:00Z"
      }),
      JSON.stringify({
        source: "MODEL",
        type: "PLANNER_RESPONSE",
        content: "最初の回答です。",
        created_at: "2026-11-15T00:01:10Z"
      }),
      JSON.stringify({
        source: "USER_EXPLICIT",
        type: "USER_INPUT",
        content: "<USER_REQUEST>\nテスト質問\n</USER_REQUEST>",
        created_at: "2026-11-15T00:02:00Z"
      }),
      JSON.stringify({
        source: "MODEL",
        type: "PLANNER_RESPONSE",
        content: "スマートフォンへ返す最新の回答テキストです。",
        created_at: "2026-11-15T00:02:10Z"
      })
    ].join("\n");
    await fs.writeFile(path.join(logsDir, "transcript_full.jsonl"), transcriptLines, "utf8");

    // 2件目の過去セッションも用意しておく
    const conv2Dir = path.join(tempBrainRoot, "conv-2");
    const conv2LogsDir = path.join(conv2Dir, ".system_generated", "logs");
    await fs.mkdir(conv2LogsDir, { recursive: true });
    await fs.writeFile(
      path.join(conv2Dir, "walkthrough.md"),
      "# 過去の作業記録\n\n修正とテストを完了しました。\n",
      "utf8"
    );
    await fs.writeFile(
      path.join(conv2LogsDir, "transcript_full.jsonl"),
      [
        JSON.stringify({
          source: "USER_EXPLICIT",
          type: "USER_INPUT",
          content: "<USER_REQUEST>\n前回のバグを直して\n</USER_REQUEST>",
          created_at: "2026-11-14T20:00:00Z"
        }),
        JSON.stringify({
          source: "MODEL",
          type: "PLANNER_RESPONSE",
          content: "前回のバグを修正しました。",
          created_at: "2026-11-14T20:01:00Z"
        })
      ].join("\n"),
      "utf8"
    );

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

    await new Promise((r) => setTimeout(r, 100));
    const stopSync = [...messages]
      .reverse()
      .find(
        (m) =>
          m.type === "state_sync" &&
          m.status === "done" &&
          m.last_response === "スマートフォンへ返す最新の回答テキストです。"
      );
    assert.ok(stopSync, "last_response should be extracted and broadcast on Stop hook");
    assert.equal(stopSync.conversation_id, "conv-1");
    assert.ok(Array.isArray(stopSync.messages), "state_sync should include messages array");
    assert.deepEqual(
      stopSync.messages.map((item) => ({ role: item.role, content: item.content })),
      [
        { role: "user", content: "最初の質問" },
        { role: "assistant", content: "最初の回答です。" },
        { role: "user", content: "テスト質問" },
        { role: "assistant", content: "スマートフォンへ返す最新の回答テキストです。" }
      ]
    );
    assert.ok(Array.isArray(stopSync.artifacts), "state_sync should include artifacts array");
    assert.equal(stopSync.artifacts.length, 2);
    assert.equal(stopSync.artifacts[0].name, "implementation_plan.md");
    assert.equal(stopSync.artifacts[0].title, "実装計画");
    assert.equal(stopSync.artifacts[0].summary, "機能Aの実装計画書");
    assert.equal(stopSync.artifacts[1].name, "task.md");

    assert.ok(Array.isArray(stopSync.conversations), "state_sync should include conversations list");
    assert.equal(stopSync.conversations.length, 2);
    assert.ok(stopSync.conversations.some((c) => c.id === "conv-1" && c.artifact_count === 2));
    assert.ok(stopSync.conversations.some((c) => c.id === "conv-2" && c.artifact_count === 1));

    const autoHistory = await readConversationHistory(undefined, tempBrainRoot);
    assert.ok(autoHistory.length >= 2);
    const listed = await listConversations(tempBrainRoot);
    assert.equal(listed.length, 2);
    const conv1Artifacts = await readConversationArtifacts("conv-1", tempBrainRoot);
    assert.equal(conv1Artifacts.length, 2);

    // 6.5. select_conversationコマンドで別セッション(conv-2)の履歴・成果物へ切り替えられること
    ws.send(JSON.stringify({ type: "select_conversation", conversation_id: "conv-2" }));
    await new Promise((r) => setTimeout(r, 120));
    const switchedSync = [...messages]
      .reverse()
      .find((m) => m.type === "state_sync" && m.conversation_id === "conv-2");
    assert.ok(switchedSync, "select_conversation should broadcast switched session state");
    assert.equal(switchedSync.last_response, "前回のバグを修正しました。");
    assert.deepEqual(
      switchedSync.messages.map((item) => ({ role: item.role, content: item.content })),
      [
        { role: "user", content: "前回のバグを直して" },
        { role: "assistant", content: "前回のバグを修正しました。" }
      ]
    );
    assert.equal(switchedSync.artifacts.length, 1);
    assert.equal(switchedSync.artifacts[0].name, "walkthrough.md");
    assert.equal(switchedSync.artifacts[0].title, "過去の作業記録");

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

    // 8. ワークスペースHooks自動導入が新規作成と既存設定マージ、およびGit除外設定への自動追記を正しく処理すること
    assert.equal(await hasWorkspaceHooks(tempFreshWorkspace), false);
    const freshResult = await setupWorkspaceHooks(tempFreshWorkspace);
    assert.equal(freshResult.mergedExisting, false);
    assert.equal(freshResult.gitIgnoreTarget, "gitignore");
    assert.equal(freshResult.gitIgnoreUpdated, true);
    assert.equal(await hasWorkspaceHooks(tempFreshWorkspace), true);
    const freshScript = await fs.readFile(freshResult.scriptPath, "utf8");
    assert.match(freshScript, /8766/);
    const freshGitIgnore = await fs.readFile(path.join(tempFreshWorkspace, ".gitignore"), "utf8");
    assert.match(freshGitIgnore, /^\.agents\/hooks\.json$/m);
    assert.match(freshGitIgnore, /^\.agents\/scripts\/deck-hook\.mjs$/m);

    // 同じワークスペースで再実行しても .gitignore が重複追記されないこと
    const rerunResult = await setupWorkspaceHooks(tempFreshWorkspace);
    assert.equal(rerunResult.gitIgnoreUpdated, false);

    await fs.mkdir(path.join(tempMergeWorkspace, ".agents"), { recursive: true });
    await fs.writeFile(
      path.join(tempMergeWorkspace, ".agents", "hooks.json"),
      JSON.stringify({ "custom-hook": { enabled: true } }, null, 2),
      "utf8"
    );
    await fs.writeFile(path.join(tempMergeWorkspace, ".gitignore"), "node_modules/", "utf8");
    assert.equal(await hasWorkspaceHooks(tempMergeWorkspace), false);
    const mergeResult = await setupWorkspaceHooks(tempMergeWorkspace, { hookPort: 18766 });
    assert.equal(mergeResult.mergedExisting, true);
    assert.equal(mergeResult.gitIgnoreUpdated, true);
    assert.equal(await hasWorkspaceHooks(tempMergeWorkspace), true);

    const mergedHooksJson = JSON.parse(await fs.readFile(mergeResult.hooksJsonPath, "utf8"));
    assert.deepEqual(mergedHooksJson["custom-hook"], { enabled: true });
    assert.equal(mergedHooksJson["antigravity-deck"]?.enabled, true);
    assert.ok(Array.isArray(mergedHooksJson["antigravity-deck"]?.PreToolUse));

    const mergedScript = await fs.readFile(mergeResult.scriptPath, "utf8");
    assert.match(mergedScript, /18766/);
    const mergedGitIgnore = await fs.readFile(path.join(tempMergeWorkspace, ".gitignore"), "utf8");
    assert.ok(mergedGitIgnore.startsWith("node_modules/\n"));
    assert.match(mergedGitIgnore, /^\.agents\/hooks\.json$/m);
    assert.match(mergedGitIgnore, /^\.agents\/scripts\/deck-hook\.mjs$/m);

    // gitExclude と none の各モードが設定通りに動作すること
    const tempExcludeWorkspace = await fs.mkdtemp(path.join(os.tmpdir(), "deck-hooks-exclude-"));
    const tempNoneWorkspace = await fs.mkdtemp(path.join(os.tmpdir(), "deck-hooks-none-"));
    try {
      await fs.mkdir(path.join(tempExcludeWorkspace, ".git"), { recursive: true });
      const excludeResult = await setupWorkspaceHooks(tempExcludeWorkspace, {
        gitIgnoreTarget: "gitExclude"
      });
      assert.equal(excludeResult.gitIgnoreUpdated, true);
      const excludeContent = await fs.readFile(
        path.join(tempExcludeWorkspace, ".git", "info", "exclude"),
        "utf8"
      );
      assert.match(excludeContent, /^\.agents\/hooks\.json$/m);
      assert.match(excludeContent, /^\.agents\/scripts\/deck-hook\.mjs$/m);

      const noneResult = await setupWorkspaceHooks(tempNoneWorkspace, {
        gitIgnoreTarget: "none"
      });
      assert.equal(noneResult.gitIgnoreUpdated, false);
      await assert.rejects(fs.access(path.join(tempNoneWorkspace, ".gitignore")));
    } finally {
      await fs.rm(tempExcludeWorkspace, { recursive: true, force: true });
      await fs.rm(tempNoneWorkspace, { recursive: true, force: true });
    }

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
