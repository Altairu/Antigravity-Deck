"use strict";
// 対象ワークスペースへ Antigravity Lifecycle Hooks 設定とリレースクリプトを自動配置するモジュール
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.buildHooksConfigEntry = buildHooksConfigEntry;
exports.buildDeckHookScript = buildDeckHookScript;
exports.hasWorkspaceHooks = hasWorkspaceHooks;
exports.setupWorkspaceHooks = setupWorkspaceHooks;
const promises_1 = __importDefault(require("node:fs/promises"));
const node_path_1 = __importDefault(require("node:path"));
function isPlainObject(value) {
    return value !== null && typeof value === "object" && !Array.isArray(value);
}
// .agents/hooks.json の "antigravity-deck" エントリ定義を生成する
function buildHooksConfigEntry() {
    return {
        enabled: true,
        SessionStart: [
            {
                type: "command",
                command: "node ./scripts/deck-hook.mjs",
                timeout: 5
            }
        ],
        PreInvocation: [
            {
                type: "command",
                command: "node ./scripts/deck-hook.mjs",
                timeout: 5
            }
        ],
        PreToolUse: [
            {
                matcher: ".*",
                hooks: [
                    {
                        type: "command",
                        command: "node ./scripts/deck-hook.mjs",
                        timeout: 300000
                    }
                ]
            }
        ],
        PostToolUse: [
            {
                matcher: ".*",
                hooks: [
                    {
                        type: "command",
                        command: "node ./scripts/deck-hook.mjs",
                        timeout: 5
                    }
                ]
            }
        ],
        Stop: [
            {
                type: "command",
                command: "node ./scripts/deck-hook.mjs",
                timeout: 5
            }
        ]
    };
}
// .agents/scripts/deck-hook.mjs のスクリプト本体を生成する
function buildDeckHookScript(defaultHookPort = 8766) {
    const safePort = Number.isFinite(defaultHookPort) && defaultHookPort > 0
        ? Math.floor(defaultHookPort)
        : 8766;
    return `// AntigravityのLifecycle Hooksから標準入力でイベントJSONを受け取り、
// ローカルHTTPサーバーへ通知して判定結果を標準出力へ返すスクリプト

import http from "node:http";

// 拡張機能のHTTP Hook受信ポート設定
const HOOK_HOST = process.env.ANTIGRAVITY_DECK_HOOK_HOST || "127.0.0.1";
const HOOK_PORT = Number(process.env.ANTIGRAVITY_DECK_HOOK_PORT || ${safePort});

// 除外対象の会話IDではツール実行をブロックしないための設定
const BUILDER_CONVERSATION_ID =
  process.env.ANTIGRAVITY_DECK_IGNORE_CONVERSATION_ID || "bdfbea35-8a91-4b48-82bc-7461647191ba";

// 標準入力をすべて文字列として読み込む
async function readStdin() {
  const chunks = [];
  for await (const chunk of process.stdin) {
    chunks.push(chunk);
  }
  return Buffer.concat(chunks).toString("utf8");
}

// ローカルHTTPサーバーへイベントをPOST送信する
function postHookEvent(payload, timeoutMs) {
  return new Promise((resolve, reject) => {
    const body = JSON.stringify(payload);
    const req = http.request(
      {
        hostname: HOOK_HOST,
        port: HOOK_PORT,
        path: "/hook",
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Content-Length": Buffer.byteLength(body)
        }
      },
      (res) => {
        const chunks = [];
        res.on("data", (chunk) => chunks.push(chunk));
        res.on("end", () => {
          const text = Buffer.concat(chunks).toString("utf8").trim();
          if (!text) {
            resolve({});
            return;
          }
          try {
            resolve(JSON.parse(text));
          } catch {
            resolve({});
          }
        });
      }
    );

    req.setTimeout(timeoutMs, () => {
      req.destroy(new Error("Hook server request timed out"));
    });
    req.on("error", (err) => reject(err));
    req.write(body);
    req.end();
  });
}

// イベント種別に応じたフォールバック応答を返す
function getFallbackOutput(eventName) {
  if (eventName === "PreToolUse") {
    return { decision: "allow" };
  }
  return {};
}

async function main() {
  const rawInput = await readStdin();
  let payload = {};
  try {
    payload = rawInput.trim() ? JSON.parse(rawInput) : {};
  } catch {
    process.stdout.write("{}\\n");
    return;
  }

  const eventName = payload.hookEventName || "";
  const conversationId = payload.conversationId || "";

  // 無限待機を回避する会話IDの場合は待機せず即座に通過させる
  if (BUILDER_CONVERSATION_ID && conversationId === BUILDER_CONVERSATION_ID) {
    try {
      await postHookEvent({ ...payload, nonBlocking: true }, 300);
    } catch {
      // サーバー未起動時は無視する
    }
    process.stdout.write(\`\${JSON.stringify(getFallbackOutput(eventName))}\\n\`);
    return;
  }

  // スマホからの承認待ちを許容するためPreToolUseには長いタイムアウトを設定する
  const timeoutMs = eventName === "PreToolUse" ? 295000 : 3000;

  try {
    const result = await postHookEvent(payload, timeoutMs);
    if (eventName === "PreToolUse") {
      // decisionが未指定であれば安全のためallowまたはaskを補完する
      const decision = result && typeof result.decision === "string" ? result.decision : "allow";
      const output = result.reason
        ? { decision, reason: result.reason }
        : { decision };
      process.stdout.write(\`\${JSON.stringify(output)}\\n\`);
      return;
    }
    process.stdout.write(\`\${JSON.stringify(result || {})}\\n\`);
  } catch {
    // 拡張機能のサーバーが未起動またはタイムアウト時はローカル操作を妨げない
    process.stdout.write(\`\${JSON.stringify(getFallbackOutput(eventName))}\\n\`);
  }
}

main().catch(() => {
  process.stdout.write("{}\\n");
});
`;
}
// 対象ワークスペースに Antigravity Deck の Hooks が導入済みかを確認する
async function hasWorkspaceHooks(workspaceDir) {
    const hooksJsonPath = node_path_1.default.join(workspaceDir, ".agents", "hooks.json");
    const scriptPath = node_path_1.default.join(workspaceDir, ".agents", "scripts", "deck-hook.mjs");
    try {
        await promises_1.default.access(scriptPath);
        const rawHooks = await promises_1.default.readFile(hooksJsonPath, "utf8");
        const parsed = JSON.parse(rawHooks);
        return isPlainObject(parsed) && isPlainObject(parsed["antigravity-deck"]);
    }
    catch {
        return false;
    }
}
// 対象ワークスペースへ .agents/hooks.json と .agents/scripts/deck-hook.mjs を書き出す
async function setupWorkspaceHooks(workspaceDir, options) {
    const agentsDir = node_path_1.default.join(workspaceDir, ".agents");
    const scriptsDir = node_path_1.default.join(agentsDir, "scripts");
    const hooksJsonPath = node_path_1.default.join(agentsDir, "hooks.json");
    const scriptPath = node_path_1.default.join(scriptsDir, "deck-hook.mjs");
    await promises_1.default.mkdir(scriptsDir, { recursive: true });
    let baseHooksConfig = {};
    let mergedExisting = false;
    try {
        const existingRaw = await promises_1.default.readFile(hooksJsonPath, "utf8");
        const parsed = JSON.parse(existingRaw);
        if (isPlainObject(parsed)) {
            baseHooksConfig = { ...parsed };
            mergedExisting = true;
        }
    }
    catch {
        // 既存ファイルが存在しない、または不正なJSONの場合は新規作成する
    }
    const nextHooksConfig = {
        ...baseHooksConfig,
        "antigravity-deck": buildHooksConfigEntry()
    };
    await promises_1.default.writeFile(hooksJsonPath, `${JSON.stringify(nextHooksConfig, null, 2)}\n`, "utf8");
    await promises_1.default.writeFile(scriptPath, buildDeckHookScript(options?.hookPort), "utf8");
    return {
        hooksJsonPath,
        scriptPath,
        mergedExisting
    };
}
//# sourceMappingURL=workspace-hooks.js.map