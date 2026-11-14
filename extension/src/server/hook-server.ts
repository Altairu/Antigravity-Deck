// AntigravityのLifecycle Hooksおよびテスト操作を受け付けるローカルHTTPサーバー

import http from "node:http";
import {
  AntigravityHookPayload,
  resolveHookTransition
} from "../config/antigravity-bindings";
import { SessionStore } from "../state/session-store";
import {
  cleanUserInputContent,
  ConversationMessage,
  readConversationHistory,
  readLatestAgentResponse
} from "../state/transcript-reader";

export interface HookServerOptions {
  port: number;
  sessionStore: SessionStore;
  getConnectedClients: () => number;
  shouldWaitForMobileApproval: () => boolean;
  onLog?: (message: string) => void;
  brainRootDir?: string;
}

export class DeckHookServer {
  private server: http.Server | null = null;
  private port: number;
  private readonly sessionStore: SessionStore;
  private readonly getConnectedClients: () => number;
  private readonly shouldWaitForMobileApproval: () => boolean;
  private readonly onLog: (message: string) => void;
  private readonly brainRootDir?: string;
  private readonly responseRefreshTimers = new Set<NodeJS.Timeout>();

  constructor(options: HookServerOptions) {
    this.port = options.port;
    this.sessionStore = options.sessionStore;
    this.getConnectedClients = options.getConnectedClients;
    this.shouldWaitForMobileApproval = options.shouldWaitForMobileApproval;
    this.onLog = options.onLog || (() => {});
    this.brainRootDir = options.brainRootDir;
  }

  public updatePort(port: number): void {
    this.port = port;
  }

  public isRunning(): boolean {
    return this.server !== null;
  }

  public async start(): Promise<void> {
    if (this.server) {
      return;
    }

    await new Promise<void>((resolve, reject) => {
      const server = http.createServer((req, res) => {
        this.handleRequest(req, res).catch((err: unknown) => {
          const message = err instanceof Error ? err.message : String(err);
          this.onLog(`Hook HTTPリクエスト処理エラー: ${message}`);
          if (!res.headersSent) {
            this.writeJson(res, 500, { error: message });
          }
        });
      });

      const onError = (err: Error) => {
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

  public async stop(): Promise<void> {
    this.clearResponseRefreshTimers();
    if (!this.server) {
      return;
    }

    const server = this.server;
    this.server = null;

    await new Promise<void>((resolve) => {
      server.close(() => resolve());
    });
    this.onLog("Hook HTTPサーバーを停止しました");
  }

  private async handleRequest(req: http.IncomingMessage, res: http.ServerResponse): Promise<void> {
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
      const autoApproveDelayMs =
        typeof body.autoApproveDelayMs === "number" ? body.autoApproveDelayMs : undefined;
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
      const payload = (await this.readJsonBody(req)) as AntigravityHookPayload;
      this.sessionStore.setConversationId(payload.conversationId);

      const transition = resolveHookTransition(payload);
      const eventName = payload.hookEventName || "";
      const history = await this.loadHistoryWithLastUserInput(
        payload.conversationId,
        payload.lastUserInput
      );

      // PreToolUseかつモバイル承認待ちが有効で、Androidクライアント接続中なら応答を待つ
      const canWaitForMobile =
        !payload.nonBlocking &&
        eventName === "PreToolUse" &&
        transition.requiresApproval &&
        this.shouldWaitForMobileApproval() &&
        this.getConnectedClients() > 0;

      if (canWaitForMobile) {
        if (transition.reasoningEffort || history.length > 0) {
          this.sessionStore.updateState({
            ...(transition.reasoningEffort ? { reasoning_effort: transition.reasoningEffort } : {}),
            ...(history.length > 0 ? { messages: history } : {})
          });
        }
        const preview = transition.approvalPreview || "ツール実行の承認待ち";
        const decision = await this.sessionStore.requestApproval(
          preview,
          transition.title,
          290000,
          false,
          transition.modelName
        );
        this.writeJson(res, 200, decision);
        return;
      }

      let lastResponse: string | null | undefined;
      if (eventName === "Stop") {
        this.clearResponseRefreshTimers();
        lastResponse = await readLatestAgentResponse(payload.conversationId, this.brainRootDir);
        this.scheduleDelayedResponseRefresh(payload.conversationId, transition.status);
      }

      this.sessionStore.updateState({
        ...(transition.title ? { title: transition.title } : {}),
        ...(transition.modelName ? { model_name: transition.modelName } : {}),
        ...(transition.reasoningEffort ? { reasoning_effort: transition.reasoningEffort } : {}),
        ...(lastResponse ? { last_response: lastResponse } : {}),
        ...(history.length > 0 ? { messages: history } : {}),
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

  // トランスクリプト履歴に未反映の最新ユーザー発話があれば末尾へ補完する
  private async loadHistoryWithLastUserInput(
    conversationId?: string,
    lastUserInput?: string
  ): Promise<ConversationMessage[]> {
    const history = await readConversationHistory(conversationId, this.brainRootDir);
    if (!lastUserInput) {
      return history;
    }

    const cleanedInput = cleanUserInputContent(lastUserInput);
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

  private clearResponseRefreshTimers(): void {
    for (const timer of this.responseRefreshTimers) {
      clearTimeout(timer);
    }
    this.responseRefreshTimers.clear();
  }

  // Stopイベント直後のログ書き込み遅延に備えて最新返答と会話履歴を再取得する
  private scheduleDelayedResponseRefresh(
    conversationId: string | undefined,
    expectedStatus: string
  ): void {
    if (!conversationId) {
      return;
    }
    for (const delayMs of [350, 1200]) {
      const timer = setTimeout(() => {
        this.responseRefreshTimers.delete(timer);
        void Promise.all([
          readLatestAgentResponse(conversationId, this.brainRootDir),
          readConversationHistory(conversationId, this.brainRootDir)
        ]).then(([refreshedResponse, refreshedHistory]) => {
          const current = this.sessionStore.getState();
          if (current.status !== expectedStatus) {
            return;
          }
          const responseChanged =
            Boolean(refreshedResponse) && current.last_response !== refreshedResponse;
          const historyChanged =
            refreshedHistory.length > 0 &&
            JSON.stringify(current.messages) !== JSON.stringify(refreshedHistory);

          if (responseChanged || historyChanged) {
            this.sessionStore.updateState({
              ...(responseChanged && refreshedResponse ? { last_response: refreshedResponse } : {}),
              ...(historyChanged ? { messages: refreshedHistory } : {})
            });
          }
        });
      }, delayMs);
      this.responseRefreshTimers.add(timer);
    }
  }

  private readJsonBody(req: http.IncomingMessage): Promise<Record<string, unknown>> {
    return new Promise((resolve, reject) => {
      const chunks: Buffer[] = [];
      let totalSize = 0;

      req.on("data", (chunk: Buffer) => {
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
          resolve(JSON.parse(text) as Record<string, unknown>);
        } catch {
          reject(new Error("Invalid JSON body"));
        }
      });

      req.on("error", (err) => reject(err));
    });
  }

  private writeJson(res: http.ServerResponse, statusCode: number, data: unknown): void {
    const body = JSON.stringify(data);
    res.writeHead(statusCode, {
      "Content-Type": "application/json; charset=utf-8",
      "Content-Length": Buffer.byteLength(body)
    });
    res.end(body);
  }
}
