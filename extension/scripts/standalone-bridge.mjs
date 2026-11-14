// VS Code本体を起動せずにWebSocketおよびHook HTTPサーバーを稼働させるスタンドアロンブリッジ
// 実機テストや結合試験の自動化で使用する

import { SessionStore } from "../dist/state/session-store.js";
import { DeckWebSocketServer } from "../dist/server/ws-server.js";
import { DeckHookServer } from "../dist/server/hook-server.js";

const wsPort = Number(process.env.DECK_WS_PORT || 8765);
const hookPort = Number(process.env.DECK_HOOK_PORT || 8766);
const authToken = process.env.DECK_AUTH_TOKEN || "antigravity-local";
const shouldSimulate = process.argv.includes("--simulate");

const sessionStore = new SessionStore();

// VS Code無しの状態でも操作コマンドの受信ログを標準出力へ記録するブリッジ
const standaloneBridge = {
  async approve() {
    console.log("[Bridge] approve");
  },
  async reject(_convId, reason) {
    console.log(`[Bridge] reject (${reason || "no reason"})`);
  },
  async sendPrompt(prompt) {
    console.log(`[Bridge] send_prompt: ${prompt}`);
  },
  async setReasoningEffort(effort) {
    console.log(`[Bridge] set_reasoning_effort: ${effort}`);
  },
  async setModel(modelName) {
    console.log(`[Bridge] set_model: ${modelName}`);
  }
};

const wsServer = new DeckWebSocketServer({
  port: wsPort,
  authToken,
  sessionStore,
  commandBridge: standaloneBridge,
  onLog: (msg) => console.log(`[WS] ${msg}`)
});

const hookServer = new DeckHookServer({
  port: hookPort,
  sessionStore,
  getConnectedClients: () => wsServer.getConnectedClientCount(),
  shouldWaitForMobileApproval: () => true,
  onLog: (msg) => console.log(`[Hook] ${msg}`)
});

async function main() {
  await wsServer.start();
  await hookServer.start();
  console.log(`Antigravity Deck Bridge ready (WS:${wsPort}, Hook:${hookPort}, Token:${authToken})`);

  if (shouldSimulate) {
    setTimeout(() => {
      console.log("[Simulate] エージェントのライフサイクルシミュレーションを開始します");
      sessionStore.simulateAgentLifecycle();
    }, 1500);
  }
}

async function shutdown() {
  sessionStore.dispose();
  await hookServer.stop();
  await wsServer.stop();
  process.exit(0);
}

process.on("SIGINT", () => void shutdown());
process.on("SIGTERM", () => void shutdown());

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
