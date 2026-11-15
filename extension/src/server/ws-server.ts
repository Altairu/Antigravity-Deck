// Androidコンパニオンアプリと通信するWebSocketサーバー実装

import http from "node:http";
import { WebSocket, WebSocketServer } from "ws";
import {
  CommandBridge,
  normalizeReasoningEffort
} from "../config/antigravity-bindings";
import { AgentSession, SessionStore } from "../state/session-store";
import {
  findLatestConversationId,
  listConversations,
  readConversationArtifacts,
  readConversationHistory,
  readLatestAgentResponse
} from "../state/transcript-reader";

interface ClientMeta {
  authenticated: boolean;
  isAlive: boolean;
  authTimer?: NodeJS.Timeout;
}

export interface WsServerOptions {
  port: number;
  authToken: string;
  sessionStore: SessionStore;
  commandBridge: CommandBridge;
  isAntigravityTurboEnabled?: () => Promise<boolean>;
  onLog?: (message: string) => void;
  onClientCountChange?: (count: number) => void;
  brainRootDir?: string;
}

export class DeckWebSocketServer {
  private wss: WebSocketServer | null = null;
  private heartbeatTimer: NodeJS.Timeout | null = null;
  private readonly clients = new Map<WebSocket, ClientMeta>();
  private port: number;
  private authToken: string;
  private readonly sessionStore: SessionStore;
  private readonly commandBridge: CommandBridge;
  private readonly isAntigravityTurboEnabled: () => Promise<boolean>;
  private readonly onLog: (message: string) => void;
  private readonly onClientCountChange?: (count: number) => void;
  private readonly brainRootDir?: string;
  private historyHydrationInFlight = false;

  private readonly handleStateUpdated = (session: AgentSession): void => {
    this.broadcast(this.buildStateSyncPayload(session));
  };

  constructor(options: WsServerOptions) {
    this.port = options.port;
    this.authToken = options.authToken;
    this.sessionStore = options.sessionStore;
    this.commandBridge = options.commandBridge;
    this.isAntigravityTurboEnabled = options.isAntigravityTurboEnabled || (async () => false);
    this.onLog = options.onLog || (() => {});
    this.onClientCountChange = options.onClientCountChange;
    this.brainRootDir = options.brainRootDir;
  }

  public updateConfig(port: number, authToken: string): void {
    this.port = port;
    this.authToken = authToken;
  }

  public isRunning(): boolean {
    return this.wss !== null;
  }

  public getPort(): number {
    return this.port;
  }

  public getAuthToken(): string {
    return this.authToken;
  }

  public getConnectedClientCount(): number {
    let count = 0;
    for (const [ws, meta] of this.clients.entries()) {
      if (meta.authenticated && ws.readyState === WebSocket.OPEN) {
        count += 1;
      }
    }
    return count;
  }

  public async start(): Promise<void> {
    if (this.wss) {
      return;
    }

    await new Promise<void>((resolve, reject) => {
      const server = new WebSocketServer({
        host: "0.0.0.0",
        port: this.port
      });

      const onStartupError = (err: Error) => {
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

  public async stop(): Promise<void> {
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

    await new Promise<void>((resolve) => {
      server.close(() => resolve());
    });
    this.onLog("WebSocketサーバーを停止しました");
  }

  private setupListeners(server: WebSocketServer): void {
    this.sessionStore.on("state_updated", this.handleStateUpdated);

    server.on("connection", (ws: WebSocket, req: http.IncomingMessage) => {
      this.onConnection(ws, req);
    });

    server.on("error", (err: Error) => {
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
        if (ws.readyState === WebSocket.OPEN) {
          ws.ping();
        }
      }
    }, 30000);
  }

  private onConnection(ws: WebSocket, req: http.IncomingMessage): void {
    const tokenOk = this.verifyHandshakeToken(req);
    const meta: ClientMeta = {
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
    } else {
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

  private verifyHandshakeToken(req: http.IncomingMessage): boolean {
    if (!this.authToken) {
      return true;
    }

    try {
      const url = new URL(req.url || "/", "http://127.0.0.1");
      const queryToken = url.searchParams.get("token");
      if (queryToken === this.authToken) {
        return true;
      }
    } catch {
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

  private async handleMessage(ws: WebSocket, raw: string): Promise<void> {
    let parsed: Record<string, unknown>;
    try {
      parsed = JSON.parse(raw) as Record<string, unknown>;
    } catch {
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
        const effort = normalizeReasoningEffort(parsed.reasoning_effort);
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

      case "select_conversation": {
        const targetId =
          typeof parsed.conversation_id === "string" ? parsed.conversation_id.trim() : "";
        if (!targetId) {
          return;
        }
        this.sessionStore.setConversationId(targetId);
        const { history, latestResponse, artifacts, conversations } =
          await this.loadConversationSnapshot(targetId);
        const summary = conversations.find((c) => c.id === targetId);
        const lastUserText = [...history].reverse().find((m) => m.role === "user")?.content;
        const lastAssistantText =
          [...history].reverse().find((m) => m.role === "assistant")?.content || null;
        const normalizedUserTitle = lastUserText
          ? lastUserText.replace(/\s+/g, " ").trim().slice(0, 48)
          : undefined;
        const nextTitle = summary?.title || normalizedUserTitle;

        this.sessionStore.updateState({
          ...(nextTitle ? { title: nextTitle } : {}),
          conversation_id: targetId,
          last_response: latestResponse || lastAssistantText,
          messages: history,
          artifacts,
          conversations
        });
        this.onLog(`会話セッションを切り替えました: ${targetId}`);
        break;
      }

      case "set_auto_approve": {
        const enabled = parsed.enabled === true;
        this.sessionStore.setMobileAutoApprove(enabled);
        this.onLog(`スマートフォン側の自動承認モードを ${enabled ? "有効" : "無効"} に設定しました`);
        if (enabled && this.sessionStore.hasPendingApproval()) {
          this.sessionStore.resolveApproval(true);
          await this.commandBridge.approve(conversationId);
        }
        break;
      }

      case "request_sync": {
        await this.sendStateSync(ws);
        break;
      }

      default:
        this.onLog(`未知のコマンドを受信しました: ${commandType}`);
    }
  }

  private buildStateSyncPayload(session: AgentSession): Record<string, unknown> {
    return {
      type: "state_sync",
      timestamp: new Date().toISOString(),
      title: session.title,
      status: session.status,
      approval_preview: session.approval_preview,
      last_response: session.last_response,
      messages: session.messages,
      conversation_id: session.conversation_id,
      conversations: session.conversations,
      artifacts: session.artifacts,
      reasoning_effort: session.reasoning_effort,
      model_name: session.model_name,
      available_models: this.sessionStore.getAvailableModels(),
      auto_approve: session.auto_approve,
      antigravity_turbo: session.antigravity_turbo,
      updated_at: session.updated_at
    };
  }

  private async loadConversationSnapshot(conversationId?: string) {
    const [history, latestResponse, artifacts, conversations] = await Promise.all([
      readConversationHistory(conversationId, this.brainRootDir),
      readLatestAgentResponse(conversationId, this.brainRootDir),
      readConversationArtifacts(conversationId, this.brainRootDir),
      listConversations(this.brainRootDir)
    ]);
    return { history, latestResponse, artifacts, conversations };
  }

  private async sendStateSync(ws: WebSocket): Promise<void> {
    try {
      const turboEnabled = await this.isAntigravityTurboEnabled();
      this.sessionStore.setAntigravityTurbo(turboEnabled);
    } catch {
      // 読み取りに失敗した場合は既存の状態をそのまま配信する
    }
    const state = this.sessionStore.getState();
    this.sendJson(ws, this.buildStateSyncPayload(state));
    if (state.messages.length === 0 || state.conversations.length === 0) {
      this.hydrateHistoryIfEmpty();
    }
  }

  // セッション履歴や成果物が空のときにローカルログから復元して配信する
  private hydrateHistoryIfEmpty(): void {
    if (this.historyHydrationInFlight) {
      return;
    }
    this.historyHydrationInFlight = true;

    void (async () => {
      try {
        const resolvedId =
          this.sessionStore.getConversationId() ||
          (await findLatestConversationId(this.brainRootDir));
        const { history, latestResponse, artifacts, conversations } =
          await this.loadConversationSnapshot(resolvedId);

        const current = this.sessionStore.getState();
        if (current.messages.length > 0 && current.conversations.length > 0) {
          return;
        }
        if (history.length === 0 && artifacts.length === 0 && conversations.length === 0) {
          return;
        }

        if (resolvedId) {
          this.sessionStore.setConversationId(resolvedId);
        }
        const summary = resolvedId ? conversations.find((c) => c.id === resolvedId) : undefined;
        const lastAssistant =
          latestResponse ||
          [...history].reverse().find((item) => item.role === "assistant")?.content;

        this.sessionStore.updateState({
          ...(current.title === "待機中" && summary?.title ? { title: summary.title } : {}),
          ...(resolvedId ? { conversation_id: resolvedId } : {}),
          ...(lastAssistant && !current.last_response ? { last_response: lastAssistant } : {}),
          ...(current.messages.length === 0 && history.length > 0 ? { messages: history } : {}),
          ...(current.artifacts.length === 0 && artifacts.length > 0 ? { artifacts } : {}),
          ...(current.conversations.length === 0 && conversations.length > 0
            ? { conversations }
            : {})
        });
      } catch {
        // 履歴読み取りに失敗した場合は現在状態のまま継続する
      } finally {
        this.historyHydrationInFlight = false;
      }
    })();
  }

  private broadcast(payload: unknown): void {
    const serialized = JSON.stringify(payload);
    for (const [ws, meta] of this.clients.entries()) {
      if (meta.authenticated && ws.readyState === WebSocket.OPEN) {
        ws.send(serialized);
      }
    }
  }

  private sendJson(ws: WebSocket, payload: unknown): void {
    if (ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify(payload));
    }
  }

  private notifyClientCount(): void {
    if (this.onClientCountChange) {
      this.onClientCountChange(this.getConnectedClientCount());
    }
  }
}
