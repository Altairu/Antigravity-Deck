"use strict";
// 単一エージェントの状態管理、承認待機解決、およびデバッグ用シミュレーション制御
Object.defineProperty(exports, "__esModule", { value: true });
exports.SessionStore = void 0;
const node_events_1 = require("node:events");
const antigravity_bindings_1 = require("../config/antigravity-bindings");
class SessionStore extends node_events_1.EventEmitter {
    session;
    conversationId;
    pendingApproval = null;
    simulationTimers = [];
    simulationGeneration = 0;
    availableModels = [...antigravity_bindings_1.DEFAULT_AVAILABLE_MODELS];
    constructor() {
        super();
        this.session = {
            title: "待機中",
            status: "idle",
            approval_preview: null,
            last_response: null,
            messages: [],
            reasoning_effort: "medium",
            model_name: antigravity_bindings_1.DEFAULT_MODEL,
            updated_at: new Date().toISOString()
        };
    }
    getAvailableModels() {
        return [...this.availableModels];
    }
    getState() {
        return {
            ...this.session,
            messages: [...this.session.messages]
        };
    }
    getConversationId() {
        return this.conversationId;
    }
    setConversationId(conversationId) {
        if (conversationId) {
            this.conversationId = conversationId;
        }
    }
    // 単一セッションの状態を更新し変更イベントを発行する
    updateState(patch) {
        const nextMessages = patch.messages ? [...patch.messages] : [...this.session.messages];
        this.session = {
            ...this.session,
            ...patch,
            messages: nextMessages,
            updated_at: new Date().toISOString()
        };
        this.emit("state_updated", this.getState());
        return this.getState();
    }
    withAppendedMessage(role, content, timestamp = new Date().toISOString()) {
        const current = [...this.session.messages];
        const trimmed = content.trim();
        if (!trimmed) {
            return current;
        }
        const last = current[current.length - 1];
        if (last && last.role === role && last.content === trimmed) {
            return current;
        }
        current.push({ role, content: trimmed, timestamp });
        return current;
    }
    setReasoningEffort(effort) {
        return this.updateState({ reasoning_effort: effort });
    }
    setModel(modelName) {
        const parsed = (0, antigravity_bindings_1.parseAntigravityModelId)(modelName);
        const normalized = parsed.modelName || antigravity_bindings_1.DEFAULT_MODEL;
        if (!this.availableModels.includes(normalized)) {
            this.availableModels.push(normalized);
        }
        return this.updateState({
            model_name: normalized,
            ...(parsed.effort ? { reasoning_effort: parsed.effort } : {})
        });
    }
    recordPrompt(prompt) {
        const trimmed = prompt.trim();
        const title = trimmed.length > 44 ? `${trimmed.slice(0, 41)}...` : trimmed;
        const nextMessages = this.withAppendedMessage("user", trimmed);
        return this.updateState({
            title: title || this.session.title,
            status: "thinking",
            approval_preview: null,
            last_response: null,
            messages: nextMessages
        });
    }
    // スマホ側からの承認待ちPromiseを登録する
    requestApproval(preview, title, timeoutMs = 290000, isSimulation = false, modelName) {
        this.clearPendingApproval({ decision: "ask", reason: "新しい承認要求に置き換わりました" });
        this.updateState({
            ...(title ? { title } : {}),
            ...(modelName ? { model_name: modelName } : {}),
            status: "waiting_approval",
            approval_preview: preview
        });
        return new Promise((resolve) => {
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
    resolveApproval(approved, reason) {
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
            }
            else {
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
            }
            else {
                this.updateState({
                    status: "error",
                    approval_preview: reason || "スマートフォンから拒否されました"
                });
            }
            return true;
        }
        return false;
    }
    clearPendingApproval(fallback) {
        const entry = this.pendingApproval;
        if (!entry) {
            return;
        }
        clearTimeout(entry.timer);
        this.pendingApproval = null;
        entry.resolve(fallback);
    }
    clearSimulationTimers() {
        this.simulationGeneration += 1;
        for (const timer of this.simulationTimers) {
            clearTimeout(timer);
        }
        this.simulationTimers = [];
    }
    // 実機テスト用: 思考中 -> 承認待ち -> 完了 のライフサイクルを順次シミュレートする
    simulateAgentLifecycle(options) {
        this.clearSimulationTimers();
        this.clearPendingApproval({ decision: "ask" });
        const generation = this.simulationGeneration;
        const thinkingMs = options?.thinkingMs ?? 2000;
        const autoApproveDelayMs = options?.autoApproveDelayMs ?? 12000;
        const finishMs = options?.finishMs ?? 1500;
        const simStartMessages = this.withAppendedMessage("user", "Antigravity Deck の結合テストを実行して結果を教えて");
        this.updateState({
            title: "Antigravity Deck 結合テスト",
            status: "thinking",
            approval_preview: null,
            messages: simStartMessages
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
            const approvalPromise = this.requestApproval(preview, "Antigravity Deck 結合テスト", autoApproveDelayMs, true);
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
                    const doneText = "結合テストが完了しました。変更箇所のビルドと検証に問題はありません。";
                    const simDoneMessages = this.withAppendedMessage("assistant", doneText);
                    this.updateState({
                        status: "done",
                        approval_preview: null,
                        last_response: doneText,
                        messages: simDoneMessages
                    });
                }, finishMs);
                this.simulationTimers.push(tDone);
            });
        }, thinkingMs);
        this.simulationTimers.push(t1);
    }
    dispose() {
        this.clearSimulationTimers();
        this.clearPendingApproval({ decision: "ask" });
    }
}
exports.SessionStore = SessionStore;
//# sourceMappingURL=session-store.js.map