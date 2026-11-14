"use strict";
// Antigravityの会話トランスクリプトから最新の返答や会話履歴を読み取るユーティリティ
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.cleanUserInputContent = cleanUserInputContent;
exports.extractConversationHistoryFromJsonl = extractConversationHistoryFromJsonl;
exports.findLatestConversationId = findLatestConversationId;
exports.readLatestAgentResponse = readLatestAgentResponse;
exports.readConversationHistory = readConversationHistory;
const node_fs_1 = require("node:fs");
const node_os_1 = __importDefault(require("node:os"));
const node_path_1 = __importDefault(require("node:path"));
const MAX_RESPONSE_LENGTH = 8000;
const MAX_HISTORY_MESSAGE_LENGTH = 6000;
function truncateText(text, maxLength) {
    if (text.length <= maxLength) {
        return text;
    }
    return `${text.slice(0, maxLength - 3)}...`;
}
// ユーザー入力文字列からシステムラッパーやメタデータタグを取り除いて本文を抽出する
function cleanUserInputContent(rawContent) {
    const userRequestMatch = rawContent.match(/<USER_REQUEST>([\s\S]*?)<\/USER_REQUEST>/);
    if (userRequestMatch) {
        const inner = userRequestMatch[1].trim();
        return inner.length > 0 ? inner : null;
    }
    if (rawContent.includes("<CONTEXT_SUMMARY>") ||
        rawContent.includes("<SYSTEM_MESSAGE>")) {
        return null;
    }
    const stripped = rawContent
        .replace(/<ADDITIONAL_METADATA>[\s\S]*?<\/ADDITIONAL_METADATA>/g, "")
        .replace(/<USER_SETTINGS_CHANGE>[\s\S]*?<\/USER_SETTINGS_CHANGE>/g, "")
        .trim();
    return stripped.length > 0 ? stripped : null;
}
// トランスクリプトJSONLファイルを末尾から走査し直近のモデル返答を抽出する
function extractLatestResponseFromJsonl(rawText) {
    const lines = rawText.split(/\r?\n/);
    for (let i = lines.length - 1; i >= 0; i -= 1) {
        const line = lines[i]?.trim();
        if (!line) {
            continue;
        }
        let entry;
        try {
            entry = JSON.parse(line);
        }
        catch {
            continue;
        }
        // 最新のユーザー入力より前のターンまでは遡らない
        if (entry.type === "USER_INPUT") {
            break;
        }
        if (entry.source === "MODEL" &&
            entry.type === "PLANNER_RESPONSE" &&
            typeof entry.content === "string") {
            const trimmed = entry.content.trim();
            if (trimmed.length > 0) {
                return truncateText(trimmed, MAX_RESPONSE_LENGTH);
            }
        }
    }
    return null;
}
// トランスクリプトJSONLを先頭から走査し会話履歴配列を抽出する
function extractConversationHistoryFromJsonl(rawText, maxMessages = 40) {
    const result = [];
    const lines = rawText.split(/\r?\n/);
    const appendMessage = (role, content, rawTimestamp) => {
        const timestamp = typeof rawTimestamp === "string" && rawTimestamp.trim()
            ? rawTimestamp.trim()
            : undefined;
        const prev = result[result.length - 1];
        if (prev && prev.role === role && prev.content === content) {
            return;
        }
        result.push({
            role,
            content,
            ...(timestamp ? { timestamp } : {})
        });
    };
    for (const rawLine of lines) {
        const line = rawLine.trim();
        if (!line) {
            continue;
        }
        let entry;
        try {
            entry = JSON.parse(line);
        }
        catch {
            continue;
        }
        if (entry.type === "USER_INPUT" && typeof entry.content === "string") {
            const cleaned = cleanUserInputContent(entry.content);
            if (cleaned) {
                appendMessage("user", truncateText(cleaned, MAX_HISTORY_MESSAGE_LENGTH), entry.created_at);
            }
            continue;
        }
        if (entry.source === "MODEL" &&
            entry.type === "PLANNER_RESPONSE" &&
            typeof entry.content === "string") {
            const trimmed = entry.content.trim();
            if (trimmed.length > 0) {
                appendMessage("assistant", truncateText(trimmed, MAX_HISTORY_MESSAGE_LENGTH), entry.created_at);
            }
        }
    }
    if (maxMessages > 0 && result.length > maxMessages) {
        return result.slice(-maxMessages);
    }
    return result;
}
// 更新時刻が最も新しいトランスクリプトを持つ会話IDを探す
async function findLatestConversationId(brainRootDir) {
    const rootDir = brainRootDir || node_path_1.default.join(node_os_1.default.homedir(), ".gemini", "antigravity", "brain");
    let dirEntries;
    try {
        dirEntries = await node_fs_1.promises.readdir(rootDir, { withFileTypes: true });
    }
    catch {
        return undefined;
    }
    let latestId;
    let latestMtimeMs = -1;
    for (const entry of dirEntries) {
        if (!entry.isDirectory()) {
            continue;
        }
        const logsDir = node_path_1.default.join(rootDir, entry.name, ".system_generated", "logs");
        for (const fileName of ["transcript_full.jsonl", "transcript.jsonl"]) {
            try {
                const stat = await node_fs_1.promises.stat(node_path_1.default.join(logsDir, fileName));
                if (stat.mtimeMs > latestMtimeMs) {
                    latestMtimeMs = stat.mtimeMs;
                    latestId = entry.name;
                }
            }
            catch {
                // 該当ログが存在しないディレクトリは無視する
            }
        }
    }
    return latestId;
}
// 会話IDに対応するトランスクリプトログから最新のエージェント返答を取得する
async function readLatestAgentResponse(conversationId, brainRootDir) {
    if (!conversationId) {
        return null;
    }
    const rootDir = brainRootDir || node_path_1.default.join(node_os_1.default.homedir(), ".gemini", "antigravity", "brain");
    const logsDir = node_path_1.default.join(rootDir, conversationId, ".system_generated", "logs");
    const candidates = [
        node_path_1.default.join(logsDir, "transcript_full.jsonl"),
        node_path_1.default.join(logsDir, "transcript.jsonl")
    ];
    for (const filePath of candidates) {
        try {
            const content = await node_fs_1.promises.readFile(filePath, "utf8");
            const extracted = extractLatestResponseFromJsonl(content);
            if (extracted) {
                return extracted;
            }
        }
        catch {
            // ファイルが存在しない場合は次の候補を試す
        }
    }
    return null;
}
// 会話IDに対応するトランスクリプトログから会話履歴を取得する
async function readConversationHistory(conversationId, brainRootDir, maxMessages = 40) {
    const targetId = conversationId || (await findLatestConversationId(brainRootDir));
    if (!targetId) {
        return [];
    }
    const rootDir = brainRootDir || node_path_1.default.join(node_os_1.default.homedir(), ".gemini", "antigravity", "brain");
    const logsDir = node_path_1.default.join(rootDir, targetId, ".system_generated", "logs");
    const candidates = [
        node_path_1.default.join(logsDir, "transcript_full.jsonl"),
        node_path_1.default.join(logsDir, "transcript.jsonl")
    ];
    for (const filePath of candidates) {
        try {
            const content = await node_fs_1.promises.readFile(filePath, "utf8");
            const messages = extractConversationHistoryFromJsonl(content, maxMessages);
            if (messages.length > 0) {
                return messages;
            }
        }
        catch {
            // ファイルが存在しない場合は次の候補を試す
        }
    }
    return [];
}
//# sourceMappingURL=transcript-reader.js.map