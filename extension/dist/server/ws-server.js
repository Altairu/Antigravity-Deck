"use strict";
// Androidコンパニオンアプリと通信するWebSocketサーバー実装
Object.defineProperty(exports, "__esModule", { value: true });
exports.DeckWebSocketServer = void 0;
const ws_1 = require("ws");
const antigravity_bindings_1 = require("../config/antigravity-bindings");
const transcript_reader_1 = require("../state/transcript-reader");
class DeckWebSocketServer {
    wss = null;
    heartbeatTimer = null;
    clients = new Map();
    port;
    authToken;
    sessionStore;
    commandBridge;
    onLog;
    onClientCountChange;
    brainRootDir;
    historyHydrationInFlight = false;
    handleStateUpdated = (session) => {
        this.broadcast(this.buildStateSyncPayload(session));
    };
    constructor(options) {
        this.port = options.port;
        this.authToken = options.authToken;
        this.sessionStore = options.sessionStore;
        this.commandBridge = options.commandBridge;
        this.onLog = options.onLog || (() => { });
        this.onClientCountChange = options.onClientCountChange;
        this.brainRootDir = options.brainRootDir;
    }
    updateConfig(port, authToken) {
        this.port = port;
        this.authToken = authToken;
    }
    isRunning() {
        return this.wss !== null;
    }
    getPort() {
        return this.port;
    }
    getAuthToken() {
        return this.authToken;
    }
    getConnectedClientCount() {
        let count = 0;
        for (const [ws, meta] of this.clients.entries()) {
            if (meta.authenticated && ws.readyState === ws_1.WebSocket.OPEN) {
                count += 1;
            }
        }
        return count;
    }
    async start() {
        if (this.wss) {
            return;
        }
        await new Promise((resolve, reject) => {
            const server = new ws_1.WebSocketServer({
                host: "0.0.0.0",
                port: this.port
            });
            const onStartupError = (err) => {
                server.removeListener("listening", onListening);
                reject(err);
            };
            const onListening = () => {
                server.removeListener("error", onStartupError);
                this.wss = server;
                this.setupListeners(server);
                this.onLog(`WebSocketサーバーをポート ${this.port} で起動しました`);
                resolve();
            };
            server.once("error", onStartupError);
            server.once("listening", onListening);
        });
    }
    async stop() {
        if (!this.wss) {
            return;
        }
        this.sessionStore.off("state_updated", this.handleStateUpdated);
        if (this.heartbeatTimer) {
            clearInterval(this.heartbeatTimer);
            this.heartbeatTimer = null;
        }
        for (const [ws, meta] of this.clients.entries()) {
            if (meta.authTimer) {
                clearTimeout(meta.authTimer);
            }
            ws.close(1001, "Server stopping");
        }
        this.clients.clear();
        this.notifyClientCount();
        const server = this.wss;
        this.wss = null;
        await new Promise((resolve) => {
            server.close(() => resolve());
        });
        this.onLog("WebSocketサーバーを停止しました");
    }
    setupListeners(server) {
        this.sessionStore.on("state_updated", this.handleStateUpdated);
        server.on("connection", (ws, req) => {
            this.onConnection(ws, req);
        });
        server.on("error", (err) => {
            this.onLog(`WebSocketサーバーエラー: ${err.message}`);
        });
        // 死活監視のPing間隔タイマーを開始する
        this.heartbeatTimer = setInterval(() => {
            for (const [ws, meta] of this.clients.entries()) {
                if (!meta.isAlive) {
                    ws.terminate();
                    this.clients.delete(ws);
                    this.notifyClientCount();
                    continue;
                }
                meta.isAlive = false;
                if (ws.readyState === ws_1.WebSocket.OPEN) {
                    ws.ping();
                }
            }
        }, 30000);
    }
    onConnection(ws, req) {
        const tokenOk = this.verifyHandshakeToken(req);
        const meta = {
            authenticated: tokenOk,
            isAlive: true
        };
        this.clients.set(ws, meta);
        ws.on("pong", () => {
            const current = this.clients.get(ws);
            if (current) {
                current.isAlive = true;
            }
        });
        if (tokenOk) {
            this.onLog(`Androidクライアントが接続しました (${req.socket.remoteAddress || "unknown"})`);
            this.sendStateSync(ws);
            this.notifyClientCount();
        }
        else {
            // クエリやヘッダーにトークンがない場合は最初のauthメッセージを5秒待機する
            meta.authTimer = setTimeout(() => {
                const current = this.clients.get(ws);
                if (current && !current.authenticated) {
                    this.onLog("認証タイムアウトのためクライアントを切断しました");
                    ws.close(4001, "Authentication required");
                }
            }, 5000);
        }
        ws.on("message", (data) => {
            this.handleMessage(ws, data.toString());
        });
        ws.on("close", () => {
            const current = this.clients.get(ws);
            if (current?.authTimer) {
                clearTimeout(current.authTimer);
            }
            this.clients.delete(ws);
            this.notifyClientCount();
        });
        ws.on("error", (err) => {
            this.onLog(`クライアント通信エラー: ${err.message}`);
        });
    }
    verifyHandshakeToken(req) {
        if (!this.authToken) {
            return true;
        }
        try {
            const url = new URL(req.url || "/", "http://127.0.0.1");
            const queryToken = url.searchParams.get("token");
            if (queryToken === this.authToken) {
                return true;
            }
        }
        catch {
            // URL解析失敗時はヘッダー検査へフォールバックする
        }
        const authHeader = req.headers.authorization;
        if (typeof authHeader === "string") {
            const match = authHeader.match(/^Bearer\s+(.+)$/i);
            if (match && match[1] === this.authToken) {
                return true;
            }
        }
        return false;
    }
    async handleMessage(ws, raw) {
        let parsed;
        try {
            parsed = JSON.parse(raw);
        }
        catch {
            this.onLog("不正なJSONメッセージを受信しました");
            return;
        }
        const commandType = typeof parsed.type === "string"
            ? parsed.type
            : typeof parsed.action === "string"
                ? parsed.action
                : "";
        const meta = this.clients.get(ws);
        if (!meta) {
            return;
        }
        // 未認証状態ではauthメッセージのみ受け付ける
        if (!meta.authenticated) {
            if (commandType === "auth" && parsed.token === this.authToken) {
                if (meta.authTimer) {
                    clearTimeout(meta.authTimer);
                    meta.authTimer = undefined;
                }
                meta.authenticated = true;
                this.sendJson(ws, { type: "auth_ack", authenticated: true });
                this.sendStateSync(ws);
                this.notifyClientCount();
                this.onLog("メッセージ認証でクライアントを受け入れました");
                return;
            }
            this.sendJson(ws, {
                type: "auth_ack",
                authenticated: false,
                message: "Invalid authentication token"
            });
            ws.close(4001, "Invalid authentication token");
            return;
        }
        const conversationId = this.sessionStore.getConversationId();
        switch (commandType) {
            case "approve": {
                this.sessionStore.resolveApproval(true);
                await this.commandBridge.approve(conversationId);
                break;
            }
            case "reject": {
                const reason = typeof parsed.reason === "string" ? parsed.reason : undefined;
                this.sessionStore.resolveApproval(false, reason);
                await this.commandBridge.reject(conversationId, reason);
                break;
            }
            case "send_prompt": {
                const prompt = typeof parsed.prompt === "string" ? parsed.prompt.trim() : "";
                if (!prompt) {
                    return;
                }
                this.sessionStore.recordPrompt(prompt);
                await this.commandBridge.sendPrompt(prompt, conversationId);
                break;
            }
            case "set_reasoning_effort": {
                const effort = (0, antigravity_bindings_1.normalizeReasoningEffort)(parsed.reasoning_effort);
                if (!effort) {
                    return;
                }
                this.sessionStore.setReasoningEffort(effort);
                await this.commandBridge.setReasoningEffort(effort, conversationId);
                break;
            }
            case "set_model": {
                const modelName = typeof parsed.model_name === "string" ? parsed.model_name.trim() : "";
                if (!modelName) {
                    return;
                }
                this.sessionStore.setModel(modelName);
                await this.commandBridge.setModel(modelName, conversationId);
                break;
            }
            case "simulate_lifecycle": {
                this.sessionStore.simulateAgentLifecycle();
                break;
            }
            case "request_sync": {
                this.sendStateSync(ws);
                break;
            }
            default:
                this.onLog(`未知のコマンドを受信しました: ${commandType}`);
        }
    }
    buildStateSyncPayload(session) {
        return {
            type: "state_sync",
            timestamp: new Date().toISOString(),
            title: session.title,
            status: session.status,
            approval_preview: session.approval_preview,
            last_response: session.last_response,
            messages: session.messages,
            reasoning_effort: session.reasoning_effort,
            model_name: session.model_name,
            available_models: this.sessionStore.getAvailableModels(),
            updated_at: session.updated_at
        };
    }
    sendStateSync(ws) {
        const state = this.sessionStore.getState();
        this.sendJson(ws, this.buildStateSyncPayload(state));
        if (state.messages.length === 0) {
            this.hydrateHistoryIfEmpty();
        }
    }
    // セッション履歴が空のときにローカルログから会話履歴を復元して配信する
    hydrateHistoryIfEmpty() {
        if (this.historyHydrationInFlight) {
            return;
        }
        this.historyHydrationInFlight = true;
        const targetConversationId = this.sessionStore.getConversationId();
        void (0, transcript_reader_1.readConversationHistory)(targetConversationId, this.brainRootDir)
            .then((history) => {
            if (history.length === 0) {
                return;
            }
            const current = this.sessionStore.getState();
            if (current.messages.length > 0) {
                return;
            }
            const lastAssistant = [...history]
                .reverse()
                .find((item) => item.role === "assistant")?.content;
            this.sessionStore.updateState({
                ...(lastAssistant && !current.last_response ? { last_response: lastAssistant } : {}),
                messages: history
            });
        })
            .catch(() => {
            // 履歴読み取りに失敗した場合は現在状態のまま継続する
        })
            .finally(() => {
            this.historyHydrationInFlight = false;
        });
    }
    broadcast(payload) {
        const serialized = JSON.stringify(payload);
        for (const [ws, meta] of this.clients.entries()) {
            if (meta.authenticated && ws.readyState === ws_1.WebSocket.OPEN) {
                ws.send(serialized);
            }
        }
    }
    sendJson(ws, payload) {
        if (ws.readyState === ws_1.WebSocket.OPEN) {
            ws.send(JSON.stringify(payload));
        }
    }
    notifyClientCount() {
        if (this.onClientCountChange) {
            this.onClientCountChange(this.getConnectedClientCount());
        }
    }
}
exports.DeckWebSocketServer = DeckWebSocketServer;
//# sourceMappingURL=ws-server.js.map