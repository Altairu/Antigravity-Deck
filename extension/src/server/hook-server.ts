// AntigravityのLifecycle Hooksおよびテスト操作を受け付けるローカルHTTPサーバー

import http from "node:http";
import {
  AntigravityHookPayload,
  resolveHookTransition
} from "../config/antigravity-bindings";
import { SessionStore } from "../state/session-store";

export interface HookServerOptions {
  port: number;
  sessionStore: SessionStore;
  getConnectedClients: () => number;
  shouldWaitForMobileApproval: () => boolean;
  onLog?: (message: string) => void;
}

export class DeckHookServer {
  private server: http.Server | null = null;
  private port: number;
  private readonly sessionStore: SessionStore;
  private readonly getConnectedClients: () => number;
  private readonly shouldWaitForMobileApproval: () => boolean;
  private readonly onLog: (message: string) => void;

  constructor(options: HookServerOptions) {
    this.port = options.port;
    this.sessionStore = options.sessionStore;
    this.getConnectedClients = options.getConnectedClients;
    this.shouldWaitForMobileApproval = options.shouldWaitForMobileApproval;
    this.onLog = options.onLog || (() => {});
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

      // PreToolUseかつモバイル承認待ちが有効で、Androidクライアント接続中なら応答を待つ
      const canWaitForMobile =
        !payload.nonBlocking &&
        eventName === "PreToolUse" &&
        transition.requiresApproval &&
        this.shouldWaitForMobileApproval() &&
        this.getConnectedClients() > 0;

      if (canWaitForMobile) {
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
