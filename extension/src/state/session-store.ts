// 単一エージェントの状態管理、承認待機解決、およびデバッグ用シミュレーション制御

import { EventEmitter } from "node:events";
import {
  AgentStatus,
  DEFAULT_AVAILABLE_MODELS,
  DEFAULT_MODEL,
  parseAntigravityModelId,
  ReasoningEffort
} from "../config/antigravity-bindings";

export interface AgentSession {
  title: string;
  status: AgentStatus;
  approval_preview: string | null;
  reasoning_effort: ReasoningEffort;
  model_name: string;
  updated_at: string;
}

export interface ApprovalDecision {
  decision: "allow" | "deny" | "ask";
  reason?: string;
}

interface PendingApprovalEntry {
  resolve: (result: ApprovalDecision) => void;
  timer: NodeJS.Timeout;
  isSimulation: boolean;
}

export class SessionStore extends EventEmitter {
  private session: AgentSession;
  private conversationId?: string;
  private pendingApproval: PendingApprovalEntry | null = null;
  private simulationTimers: NodeJS.Timeout[] = [];
  private simulationGeneration = 0;
  private readonly availableModels: string[] = [...DEFAULT_AVAILABLE_MODELS];

  constructor() {
    super();
    this.session = {
      title: "待機中",
      status: "idle",
      approval_preview: null,
      reasoning_effort: "medium",
      model_name: DEFAULT_MODEL,
      updated_at: new Date().toISOString()
    };
  }

  public getAvailableModels(): string[] {
    return [...this.availableModels];
  }

  public getState(): AgentSession {
    return { ...this.session };
  }

  public getConversationId(): string | undefined {
    return this.conversationId;
  }

  public setConversationId(conversationId?: string): void {
    if (conversationId) {
      this.conversationId = conversationId;
    }
  }

  // 単一セッションの状態を更新し変更イベントを発行する
  public updateState(
    patch: Partial<Omit<AgentSession, "updated_at">>
  ): AgentSession {
    this.session = {
      ...this.session,
      ...patch,
      updated_at: new Date().toISOString()
    };
    this.emit("state_updated", { ...this.session });
    return { ...this.session };
  }

  public setReasoningEffort(effort: ReasoningEffort): AgentSession {
    return this.updateState({ reasoning_effort: effort });
  }

  public setModel(modelName: string): AgentSession {
    const parsed = parseAntigravityModelId(modelName);
    const normalized = parsed.modelName || DEFAULT_MODEL;
    if (!this.availableModels.includes(normalized)) {
      this.availableModels.push(normalized);
    }
    return this.updateState({
      model_name: normalized,
      ...(parsed.effort ? { reasoning_effort: parsed.effort } : {})
    });
  }

  public recordPrompt(prompt: string): AgentSession {
    const trimmed = prompt.trim();
    const title = trimmed.length > 44 ? `${trimmed.slice(0, 41)}...` : trimmed;
    return this.updateState({
      title: title || this.session.title,
      status: "thinking",
      approval_preview: null
    });
  }

  // スマホ側からの承認待ちPromiseを登録する
  public requestApproval(
    preview: string,
    title?: string,
    timeoutMs = 290000,
    isSimulation = false,
    modelName?: string
  ): Promise<ApprovalDecision> {
    this.clearPendingApproval({ decision: "ask", reason: "新しい承認要求に置き換わりました" });

    this.updateState({
      ...(title ? { title } : {}),
      ...(modelName ? { model_name: modelName } : {}),
      status: "waiting_approval",
      approval_preview: preview
    });

    return new Promise<ApprovalDecision>((resolve) => {
      const timer = setTimeout(() => {
        this.pendingApproval = null;
        if (!isSimulation) {
          this.updateState({
            status: "idle",
            approval_preview: null
          });
        }
        resolve({
          decision: "ask",
          reason: "スマートフォンからの応答がタイムアウトしました"
        });
      }, timeoutMs);

      this.pendingApproval = { resolve, timer, isSimulation };
    });
  }

  // Androidからの承認または拒否コマンドで待機中のHookを解決する
  public resolveApproval(approved: boolean, reason?: string): boolean {
    const entry = this.pendingApproval;
    if (entry) {
      clearTimeout(entry.timer);
      this.pendingApproval = null;

      if (approved) {
        this.updateState({
          status: "thinking",
          approval_preview: null
        });
        entry.resolve({ decision: "allow" });
      } else {
        this.updateState({
          status: "error",
          approval_preview: reason || "スマートフォンから拒否されました"
        });
        entry.resolve({
          decision: "deny",
          reason: reason || "スマートフォンから拒否されました"
        });
      }
      return true;
    }

    if (this.session.status === "waiting_approval") {
      if (approved) {
        this.updateState({ status: "thinking", approval_preview: null });
      } else {
        this.updateState({
          status: "error",
          approval_preview: reason || "スマートフォンから拒否されました"
        });
      }
      return true;
    }

    return false;
  }

  private clearPendingApproval(fallback: ApprovalDecision): void {
    const entry = this.pendingApproval;
    if (!entry) {
      return;
    }
    clearTimeout(entry.timer);
    this.pendingApproval = null;
    entry.resolve(fallback);
  }

  private clearSimulationTimers(): void {
    this.simulationGeneration += 1;
    for (const timer of this.simulationTimers) {
      clearTimeout(timer);
    }
    this.simulationTimers = [];
  }

  // 実機テスト用: 思考中 -> 承認待ち -> 完了 のライフサイクルを順次シミュレートする
  public simulateAgentLifecycle(options?: {
    thinkingMs?: number;
    autoApproveDelayMs?: number;
    finishMs?: number;
  }): void {
    this.clearSimulationTimers();
    this.clearPendingApproval({ decision: "ask" });
    const generation = this.simulationGeneration;

    const thinkingMs = options?.thinkingMs ?? 2000;
    const autoApproveDelayMs = options?.autoApproveDelayMs ?? 12000;
    const finishMs = options?.finishMs ?? 1500;

    this.updateState({
      title: "Antigravity Deck 結合テスト",
      status: "thinking",
      approval_preview: null
    });

    const t1 = setTimeout(() => {
      if (this.simulationGeneration !== generation) {
        return;
      }

      const preview = [
        "$ ./gradlew assembleDebug && adb reverse tcp:8765 tcp:8765",
        "--- extension/src/server/ws-server.ts",
        "+++ extension/src/server/ws-server.ts",
        "@@ -42,1 +42,3 @@",
        "+  this.broadcastStateSync();"
      ].join("\n");

      const approvalPromise = this.requestApproval(
        preview,
        "Antigravity Deck 結合テスト",
        autoApproveDelayMs,
        true
      );

      approvalPromise.then((res) => {
        if (this.simulationGeneration !== generation) {
          return;
        }
        if (res.decision === "deny") {
          return;
        }
        if (res.decision === "ask") {
          this.updateState({
            status: "thinking",
            approval_preview: null
          });
        }
        const tDone = setTimeout(() => {
          if (this.simulationGeneration !== generation) {
            return;
          }
          this.updateState({
            status: "done",
            approval_preview: null
          });
        }, finishMs);
        this.simulationTimers.push(tDone);
      });
    }, thinkingMs);

    this.simulationTimers.push(t1);
  }

  public dispose(): void {
    this.clearSimulationTimers();
    this.clearPendingApproval({ decision: "ask" });
  }
}
