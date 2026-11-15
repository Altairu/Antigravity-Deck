"use strict";
// AntigravityのLifecycle Hooksおよびテスト操作を受け付けるローカルHTTPサーバー
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.DeckHookServer = void 0;
const node_http_1 = __importDefault(require("node:http"));
const antigravity_bindings_1 = require("../config/antigravity-bindings");
const transcript_reader_1 = require("../state/transcript-reader");
class DeckHookServer {
    server = null;
    port;
    sessionStore;
    getConnectedClients;
    shouldWaitForMobileApproval;
    isAntigravityTurboEnabled;
    onLog;
    brainRootDir;
    responseRefreshTimers = new Set();
    constructor(options) {
        this.port = options.port;
        this.sessionStore = options.sessionStore;
        this.getConnectedClients = options.getConnectedClients;
        this.shouldWaitForMobileApproval = options.shouldWaitForMobileApproval;
        this.isAntigravityTurboEnabled = options.isAntigravityTurboEnabled || (async () => false);
        this.onLog = options.onLog || (() => { });
        this.brainRootDir = options.brainRootDir;
    }
    updatePort(port) {
        this.port = port;
    }
    isRunning() {
        return this.server !== null;
    }
    async start() {
        if (this.server) {
            return;
        }
        await new Promise((resolve, reject) => {
            const server = node_http_1.default.createServer((req, res) => {
                this.handleRequest(req, res).catch((err) => {
                    const message = err instanceof Error ? err.message : String(err);
                    this.onLog(`Hook HTTPリクエスト処理エラー: ${message}`);
                    if (!res.headersSent) {
                        this.writeJson(res, 500, { error: message });
                    }
                });
            });
            const onError = (err) => {
                server.removeListener("listening", onListening);
                reject(err);
            };
            const onListening = () => {
                server.removeListener("error", onError);
                this.server = server;
                this.onLog(`Hook HTTPサーバーを 127.0.0.1:${this.port} で起動しました`);
                resolve();
            };
            server.once("error", onError);
            server.once("listening", onListening);
            server.listen(this.port, "127.0.0.1");
        });
    }
    async stop() {
        this.clearResponseRefreshTimers();
        if (!this.server) {
            return;
        }
        const server = this.server;
        this.server = null;
        await new Promise((resolve) => {
            server.close(() => resolve());
        });
        this.onLog("Hook HTTPサーバーを停止しました");
    }
    async handleRequest(req, res) {
        const method = req.method || "GET";
        const urlPath = (req.url || "/").split("?")[0];
        if (method === "GET" && urlPath === "/status") {
            this.writeJson(res, 200, {
                ok: true,
                connected_clients: this.getConnectedClients(),
                available_models: this.sessionStore.getAvailableModels(),
                session: this.sessionStore.getState()
            });
            return;
        }
        if (method === "POST" && urlPath === "/simulate") {
            const body = await this.readJsonBody(req);
            const thinkingMs = typeof body.thinkingMs === "number" ? body.thinkingMs : undefined;
            const autoApproveDelayMs = typeof body.autoApproveDelayMs === "number" ? body.autoApproveDelayMs : undefined;
            const finishMs = typeof body.finishMs === "number" ? body.finishMs : undefined;
            this.sessionStore.simulateAgentLifecycle({
                thinkingMs,
                autoApproveDelayMs,
                finishMs
            });
            this.writeJson(res, 200, { ok: true });
            return;
        }
        if (method === "POST" && urlPath === "/hook") {
            const payload = (await this.readJsonBody(req));
            this.sessionStore.setConversationId(payload.conversationId);
            const transition = (0, antigravity_bindings_1.resolveHookTransition)(payload);
            const eventName = payload.hookEventName || "";
            const [history, artifacts, conversations, antigravityTurbo] = await Promise.all([
                this.loadHistoryWithLastUserInput(payload.conversationId, payload.lastUserInput),
                (0, transcript_reader_1.readConversationArtifacts)(payload.conversationId, this.brainRootDir),
                (0, transcript_reader_1.listConversations)(this.brainRootDir),
                eventName === "PreToolUse"
                    ? this.isAntigravityTurboEnabled().catch(() => false)
                    : Promise.resolve(this.sessionStore.getState().antigravity_turbo)
            ]);
            this.sessionStore.setAntigravityTurbo(antigravityTurbo);
            const autoApproveActive = this.sessionStore.getMobileAutoApprove() || antigravityTurbo;
            // PreToolUseかつモバイル承認待ちが有効で、自動承認・ターボモードでなければ応答を待つ
            const canWaitForMobile = !payload.nonBlocking &&
                eventName === "PreToolUse" &&
                transition.requiresApproval &&
                !autoApproveActive &&
                this.shouldWaitForMobileApproval() &&
                this.getConnectedClients() > 0;
            if (canWaitForMobile) {
                this.sessionStore.updateState({
                    ...(transition.reasoningEffort ? { reasoning_effort: transition.reasoningEffort } : {}),
                    ...(history.length > 0 ? { messages: history } : {}),
                    ...(payload.conversationId ? { conversation_id: payload.conversationId } : {}),
                    artifacts,
                    ...(conversations.length > 0 ? { conversations } : {})
                });
                const preview = transition.approvalPreview || "ツール実行の承認待ち";
                const decision = await this.sessionStore.requestApproval(preview, transition.title, 290000, false, transition.modelName);
                this.writeJson(res, 200, decision);
                return;
            }
            let lastResponse;
            if (eventName === "Stop") {
                this.clearResponseRefreshTimers();
                lastResponse = await (0, transcript_reader_1.readLatestAgentResponse)(payload.conversationId, this.brainRootDir);
                this.scheduleDelayedResponseRefresh(payload.conversationId, transition.status);
            }
            const effectiveStatus = autoApproveActive && transition.status === "waiting_approval" ? "thinking" : transition.status;
            const effectiveApprovalPreview = autoApproveActive && transition.status === "waiting_approval"
                ? null
                : transition.approvalPreview;
            this.sessionStore.updateState({
                ...(transition.title ? { title: transition.title } : {}),
                ...(transition.modelName ? { model_name: transition.modelName } : {}),
                ...(transition.reasoningEffort ? { reasoning_effort: transition.reasoningEffort } : {}),
                ...(lastResponse ? { last_response: lastResponse } : {}),
                ...(history.length > 0 ? { messages: history } : {}),
                ...(payload.conversationId ? { conversation_id: payload.conversationId } : {}),
                artifacts,
                ...(conversations.length > 0 ? { conversations } : {}),
                status: effectiveStatus,
                approval_preview: effectiveApprovalPreview
            });
            if (eventName === "PreToolUse") {
                if (autoApproveActive) {
                    this.onLog(antigravityTurbo
                        ? "Antigravityターボモード検出のため PreToolUse を即座に許可しました"
                        : "スマートフォン自動承認モードのため PreToolUse を即座に許可しました");
                }
                this.writeJson(res, 200, { decision: "allow" });
                return;
            }
            this.writeJson(res, 200, {});
            return;
        }
        this.writeJson(res, 404, { error: "Not Found" });
    }
    // トランスクリプト履歴に未反映の最新ユーザー発話があれば末尾へ補完する
    async loadHistoryWithLastUserInput(conversationId, lastUserInput) {
        const history = await (0, transcript_reader_1.readConversationHistory)(conversationId, this.brainRootDir);
        if (!lastUserInput) {
            return history;
        }
        const cleanedInput = (0, transcript_reader_1.cleanUserInputContent)(lastUserInput);
        if (!cleanedInput) {
            return history;
        }
        const lastUserMessage = [...history].reverse().find((msg) => msg.role === "user");
        if (lastUserMessage?.content === cleanedInput) {
            return history;
        }
        return [
            ...history,
            {
                role: "user",
                content: cleanedInput,
                timestamp: new Date().toISOString()
            }
        ];
    }
    clearResponseRefreshTimers() {
        for (const timer of this.responseRefreshTimers) {
            clearTimeout(timer);
        }
        this.responseRefreshTimers.clear();
    }
    // Stopイベント直後のログ書き込み遅延に備えて最新返答と会話履歴・成果物を再取得する
    scheduleDelayedResponseRefresh(conversationId, expectedStatus) {
        if (!conversationId) {
            return;
        }
        for (const delayMs of [350, 1200]) {
            const timer = setTimeout(() => {
                this.responseRefreshTimers.delete(timer);
                void Promise.all([
                    (0, transcript_reader_1.readLatestAgentResponse)(conversationId, this.brainRootDir),
                    (0, transcript_reader_1.readConversationHistory)(conversationId, this.brainRootDir),
                    (0, transcript_reader_1.readConversationArtifacts)(conversationId, this.brainRootDir),
                    (0, transcript_reader_1.listConversations)(this.brainRootDir)
                ]).then(([refreshedResponse, refreshedHistory, refreshedArtifacts, refreshedConversations]) => {
                    const current = this.sessionStore.getState();
                    if (current.status !== expectedStatus) {
                        return;
                    }
                    const responseChanged = Boolean(refreshedResponse) && current.last_response !== refreshedResponse;
                    const historyChanged = refreshedHistory.length > 0 &&
                        JSON.stringify(current.messages) !== JSON.stringify(refreshedHistory);
                    const artifactsChanged = JSON.stringify(current.artifacts) !== JSON.stringify(refreshedArtifacts);
                    const conversationsChanged = refreshedConversations.length > 0 &&
                        JSON.stringify(current.conversations) !== JSON.stringify(refreshedConversations);
                    if (responseChanged || historyChanged || artifactsChanged || conversationsChanged) {
                        this.sessionStore.updateState({
                            ...(responseChanged && refreshedResponse ? { last_response: refreshedResponse } : {}),
                            ...(historyChanged ? { messages: refreshedHistory } : {}),
                            ...(artifactsChanged ? { artifacts: refreshedArtifacts } : {}),
                            ...(conversationsChanged ? { conversations: refreshedConversations } : {})
                        });
                    }
                });
            }, delayMs);
            this.responseRefreshTimers.add(timer);
        }
    }
    readJsonBody(req) {
        return new Promise((resolve, reject) => {
            const chunks = [];
            let totalSize = 0;
            req.on("data", (chunk) => {
                totalSize += chunk.length;
                if (totalSize > 1024 * 1024) {
                    reject(new Error("Request body too large"));
                    req.destroy();
                    return;
                }
                chunks.push(chunk);
            });
            req.on("end", () => {
                const text = Buffer.concat(chunks).toString("utf8").trim();
                if (!text) {
                    resolve({});
                    return;
                }
                try {
                    resolve(JSON.parse(text));
                }
                catch {
                    reject(new Error("Invalid JSON body"));
                }
            });
            req.on("error", (err) => reject(err));
        });
    }
    writeJson(res, statusCode, data) {
        const body = JSON.stringify(data);
        res.writeHead(statusCode, {
            "Content-Type": "application/json; charset=utf-8",
            "Content-Length": Buffer.byteLength(body)
        });
        res.end(body);
    }
}
exports.DeckHookServer = DeckHookServer;
//# sourceMappingURL=hook-server.js.map