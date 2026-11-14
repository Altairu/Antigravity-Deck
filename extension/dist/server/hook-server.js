"use strict";
// AntigravityのLifecycle Hooksおよびテスト操作を受け付けるローカルHTTPサーバー
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.DeckHookServer = void 0;
const node_http_1 = __importDefault(require("node:http"));
const antigravity_bindings_1 = require("../config/antigravity-bindings");
class DeckHookServer {
    server = null;
    port;
    sessionStore;
    getConnectedClients;
    shouldWaitForMobileApproval;
    onLog;
    constructor(options) {
        this.port = options.port;
        this.sessionStore = options.sessionStore;
        this.getConnectedClients = options.getConnectedClients;
        this.shouldWaitForMobileApproval = options.shouldWaitForMobileApproval;
        this.onLog = options.onLog || (() => { });
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
            // PreToolUseかつモバイル承認待ちが有効で、Androidクライアント接続中なら応答を待つ
            const canWaitForMobile = !payload.nonBlocking &&
                eventName === "PreToolUse" &&
                transition.requiresApproval &&
                this.shouldWaitForMobileApproval() &&
                this.getConnectedClients() > 0;
            if (canWaitForMobile) {
                const preview = transition.approvalPreview || "ツール実行の承認待ち";
                const decision = await this.sessionStore.requestApproval(preview, transition.title, 290000, false, transition.modelName);
                this.writeJson(res, 200, decision);
                return;
            }
            this.sessionStore.updateState({
                ...(transition.title ? { title: transition.title } : {}),
                ...(transition.modelName ? { model_name: transition.modelName } : {}),
                status: transition.status,
                approval_preview: transition.approvalPreview
            });
            if (eventName === "PreToolUse") {
                this.writeJson(res, 200, { decision: "allow" });
                return;
            }
            this.writeJson(res, 200, {});
            return;
        }
        this.writeJson(res, 404, { error: "Not Found" });
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