"use strict";
// Antigravity固有の内部コマンドID、Lifecycle Hooksイベント、および承認プレビュー変換のマッピング定義
Object.defineProperty(exports, "__esModule", { value: true });
exports.APPROVAL_REQUIRED_TOOLS = exports.HOOK_EVENT_STATUS_MAP = exports.ANTIGRAVITY_COMMAND_BINDINGS = exports.DEFAULT_AVAILABLE_MODELS = exports.DEFAULT_MODEL = void 0;
exports.parseAntigravityModelId = parseAntigravityModelId;
exports.resolveAntigravityModelId = resolveAntigravityModelId;
exports.normalizeReasoningEffort = normalizeReasoningEffort;
exports.buildApprovalPreview = buildApprovalPreview;
exports.resolveHookTransition = resolveHookTransition;
// 既定のAIモデル表示名
exports.DEFAULT_MODEL = "Gemini 4 Hydrogen";
// Antigravityで利用可能な現行モデルと内部モデルIDのマッピング定義
const MODEL_CATALOG = [
    {
        displayName: "Gemini 4 Hydrogen",
        idPrefix: "gemini-4-hydrogen-",
        supportedEfforts: ["low", "medium", "high", "xhigh"],
        aliases: ["gemini 4 hydrogen", "gemini-4-hydrogen"]
    },
    {
        displayName: "Gemini 4 Argon",
        idPrefix: "gemini-4-argon-",
        supportedEfforts: ["low", "medium", "high"],
        aliases: ["gemini 4 argon", "gemini-4-argon"]
    },
    {
        displayName: "Gemini 3.8 Flash",
        idPrefix: "gemini-3.8-flash-",
        supportedEfforts: ["low", "medium", "high"],
        aliases: ["gemini 3.8 flash", "gemini-3.8-flash"]
    },
    {
        displayName: "Claude Sonnet 5.5",
        idPrefix: "claude-sonnet-5-5-",
        supportedEfforts: ["low", "medium", "high"],
        aliases: ["claude sonnet 5.5", "claude-sonnet-5-5", "claude-sonnet-5.5"]
    },
    {
        displayName: "Claude Opus 5.5",
        idPrefix: "claude-opus-5-5-",
        supportedEfforts: ["low", "medium", "high"],
        aliases: ["claude opus 5.5", "claude-opus-5-5", "claude-opus-5.5"]
    },
    {
        displayName: "GPT-OSS 120B",
        idPrefix: "gpt-oss-120b-",
        supportedEfforts: ["medium"],
        fixedId: "gpt-oss-120b-medium",
        aliases: ["gpt-oss 120b", "gpt-oss-120b", "gpt-oss-120b-medium"]
    },
    {
        displayName: "Orchestrator (Argon)",
        idPrefix: "gemini-pro-orchestrator-",
        supportedEfforts: [],
        fixedId: "gemini-pro-orchestrator-g1-pro",
        aliases: ["orchestrator (argon)", "orchestrator", "gemini-pro-orchestrator-g1-pro"]
    }
];
// 既定で選択可能なAIモデル一覧
exports.DEFAULT_AVAILABLE_MODELS = MODEL_CATALOG.map((entry) => entry.displayName);
// 内部モデルIDや表示名から表示モデル名と推論レベルを抽出する
function parseAntigravityModelId(rawModel) {
    const trimmed = rawModel.trim();
    if (!trimmed) {
        return { modelName: exports.DEFAULT_MODEL };
    }
    const lower = trimmed.toLowerCase();
    const displaySuffixMatch = lower.match(/^(.+?)\s*\((low|medium|high|xhigh)\)$/);
    const baseName = displaySuffixMatch ? displaySuffixMatch[1].trim() : lower;
    const suffixEffort = displaySuffixMatch
        ? normalizeReasoningEffort(displaySuffixMatch[2])
        : null;
    for (const entry of MODEL_CATALOG) {
        if (entry.fixedId && lower === entry.fixedId) {
            return { modelName: entry.displayName };
        }
        if (entry.aliases.includes(baseName)) {
            return suffixEffort
                ? { modelName: entry.displayName, effort: suffixEffort }
                : { modelName: entry.displayName };
        }
        if (lower.startsWith(entry.idPrefix)) {
            const idSuffix = lower.slice(entry.idPrefix.length);
            const effort = normalizeReasoningEffort(idSuffix);
            return effort
                ? { modelName: entry.displayName, effort }
                : { modelName: entry.displayName };
        }
    }
    return { modelName: trimmed };
}
// 表示モデル名と推論レベルからAntigravity内部モデルIDを解決する
function resolveAntigravityModelId(modelName, effort) {
    const parsed = parseAntigravityModelId(modelName);
    const entry = MODEL_CATALOG.find((item) => item.displayName === parsed.modelName);
    if (!entry) {
        return modelName.trim() || "gemini-4-hydrogen-medium";
    }
    if (entry.fixedId) {
        return entry.fixedId;
    }
    const targetEffort = effort === "xhigh" && !entry.supportedEfforts.includes("xhigh")
        ? "high"
        : entry.supportedEfforts.includes(effort)
            ? effort
            : entry.supportedEfforts[0] || "medium";
    return `${entry.idPrefix}${targetEffort}`;
}
// Antigravityのバージョンや環境差を吸収するため、優先順でコマンドID候補を列挙する
exports.ANTIGRAVITY_COMMAND_BINDINGS = {
    approve: [
        "antigravity.agent.approveStep",
        "antigravity.agent.acceptChanges",
        "antigravity.acceptDiff",
        "chatEditing.acceptAllFiles",
        "inlineChat.acceptChanges"
    ],
    reject: [
        "antigravity.agent.rejectStep",
        "antigravity.agent.rejectChanges",
        "antigravity.rejectDiff",
        "chatEditing.discardAllFiles",
        "inlineChat.discardHunk"
    ],
    sendPrompt: [
        "antigravity.agent.sendPrompt",
        "antigravity.chat.send",
        "workbench.action.chat.open"
    ],
    setReasoningEffort: [
        "antigravity.agent.setReasoningEffort",
        "antigravity.model.setReasoningEffort"
    ],
    setModel: [
        "antigravity.agent.setModel",
        "antigravity.model.select",
        "workbench.action.chat.changeModel"
    ],
    // VS Code設定キーの候補
    reasoningEffortSettingKeys: [
        "antigravity.reasoningEffort",
        "antigravity.agent.reasoningEffort"
    ],
    modelSettingKeys: [
        "antigravity.model",
        "antigravity.agent.model"
    ]
};
// Lifecycle Hooksのイベント名と基本ステータスのマッピング
exports.HOOK_EVENT_STATUS_MAP = {
    SessionStart: "thinking",
    PreInvocation: "thinking",
    PreToolUse: "waiting_approval",
    PostInvocation: "thinking",
    PostToolUse: "thinking",
    Stop: "done"
};
// PreToolUse時にモバイル側での承認確認対象とするツール名の一覧
exports.APPROVAL_REQUIRED_TOOLS = new Set([
    "run_command",
    "write_file",
    "edit_file",
    "replace_file_content",
    "multi_replace_file_content",
    "send_command_input",
    "delete_file"
]);
// 推論レベル文字列のバリデーションと正規化
function normalizeReasoningEffort(value) {
    if (typeof value !== "string") {
        return null;
    }
    const normalized = value.trim().toLowerCase();
    if (normalized === "low" ||
        normalized === "medium" ||
        normalized === "high" ||
        normalized === "xhigh") {
        return normalized;
    }
    return null;
}
// 文字列を指定長で安全に省略する
function truncateText(text, maxLength) {
    if (text.length <= maxLength) {
        return text;
    }
    return `${text.slice(0, maxLength - 3)}...`;
}
// PreToolUseのツール名と引数からAndroidのApproval Preview向け要約テキストを生成する
function buildApprovalPreview(toolName, args) {
    const name = toolName || "unknown_tool";
    const safeArgs = args || {};
    if (name === "run_command") {
        const commandLine = String(safeArgs.CommandLine || safeArgs.command || "");
        const cwd = safeArgs.Cwd ? ` (cwd: ${String(safeArgs.Cwd)})` : "";
        return `$ ${commandLine}${cwd}`.trim();
    }
    if (name === "edit_file" || name === "replace_file_content") {
        const targetFile = String(safeArgs.Path || safeArgs.TargetFile || safeArgs.path || "");
        const target = String(safeArgs.Target || safeArgs.TargetContent || "");
        const replacement = String(safeArgs.Replacement || safeArgs.ReplacementContent || "");
        const lines = [`edit: ${targetFile}`];
        if (target) {
            lines.push(`- ${truncateText(target.replace(/\r?\n/g, " "), 120)}`);
        }
        if (replacement) {
            lines.push(`+ ${truncateText(replacement.replace(/\r?\n/g, " "), 120)}`);
        }
        return lines.join("\n");
    }
    if (name === "write_file") {
        const targetFile = String(safeArgs.Path || safeArgs.TargetFile || "");
        const content = String(safeArgs.Content || safeArgs.CodeContent || "");
        const previewBody = truncateText(content, 240);
        return `write: ${targetFile}\n${previewBody}`;
    }
    const serialized = JSON.stringify(safeArgs, null, 2);
    return `${name}\n${truncateText(serialized, 320)}`;
}
// Hookペイロード全体を解釈して次のエージェント状態と承認要否を決定する
function resolveHookTransition(payload) {
    const eventName = payload.hookEventName || "";
    const title = payload.lastUserInput
        ? truncateText(payload.lastUserInput.replace(/\s+/g, " ").trim(), 48)
        : undefined;
    const parsedModel = typeof payload.modelName === "string" && payload.modelName.trim().length > 0
        ? parseAntigravityModelId(payload.modelName)
        : undefined;
    const modelName = parsedModel?.modelName;
    const reasoningEffort = parsedModel?.effort;
    if (eventName === "SessionStart" || eventName === "PreInvocation" || eventName === "PostInvocation") {
        return {
            status: "thinking",
            title,
            modelName,
            reasoningEffort,
            approvalPreview: null,
            requiresApproval: false
        };
    }
    if (eventName === "PreToolUse") {
        const toolName = payload.toolCall?.name || "";
        const requiresApproval = exports.APPROVAL_REQUIRED_TOOLS.has(toolName);
        const preview = buildApprovalPreview(toolName, payload.toolCall?.args);
        return {
            status: requiresApproval ? "waiting_approval" : "thinking",
            title,
            modelName,
            reasoningEffort,
            approvalPreview: requiresApproval ? preview : `ツール実行中: ${toolName}`,
            requiresApproval
        };
    }
    if (eventName === "PostToolUse") {
        if (payload.error) {
            return {
                status: "error",
                title,
                modelName,
                reasoningEffort,
                approvalPreview: truncateText(String(payload.error), 320),
                requiresApproval: false
            };
        }
        return {
            status: "thinking",
            title,
            modelName,
            reasoningEffort,
            approvalPreview: null,
            requiresApproval: false
        };
    }
    if (eventName === "Stop") {
        const reason = (payload.terminationReason || "").toUpperCase();
        const hasError = Boolean(payload.error) || reason === "ERROR";
        if (hasError) {
            return {
                status: "error",
                title,
                modelName,
                reasoningEffort,
                approvalPreview: payload.error ? truncateText(String(payload.error), 320) : "エージェントがエラー終了しました",
                requiresApproval: false
            };
        }
        if (reason === "USER_CANCELED") {
            return {
                status: "idle",
                title,
                modelName,
                reasoningEffort,
                approvalPreview: null,
                requiresApproval: false
            };
        }
        return {
            status: "done",
            title,
            modelName,
            reasoningEffort,
            approvalPreview: null,
            requiresApproval: false
        };
    }
    const mappedStatus = exports.HOOK_EVENT_STATUS_MAP[eventName] || "thinking";
    return {
        status: mappedStatus,
        title,
        modelName,
        reasoningEffort,
        approvalPreview: null,
        requiresApproval: false
    };
}
//# sourceMappingURL=antigravity-bindings.js.map