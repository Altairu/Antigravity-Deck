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
exports.readConversationArtifacts = readConversationArtifacts;
exports.listConversations = listConversations;
const node_fs_1 = require("node:fs");
const node_os_1 = __importDefault(require("node:os"));
const node_path_1 = __importDefault(require("node:path"));
const MAX_RESPONSE_LENGTH = 8000;
const MAX_HISTORY_MESSAGE_LENGTH = 6000;
const MAX_ARTIFACT_LENGTH = 16000;
function resolveBrainRoot(brainRootDir) {
    return brainRootDir || node_path_1.default.join(node_os_1.default.homedir(), ".gemini", "antigravity", "brain");
}
function isSafeConversationId(conversationId) {
    return Boolean(conversationId && !/[\\/]|\.\./.test(conversationId));
}
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
function extractMarkdownTitle(content, fallbackName) {
    const match = content.match(/^#\s+(.+)$/m);
    if (match && match[1]) {
        const title = match[1].trim();
        if (title.length > 0) {
            return truncateText(title, 80);
        }
    }
    return fallbackName;
}
function artifactSortWeight(fileName) {
    const lower = fileName.toLowerCase();
    if (lower === "implementation_plan.md")
        return 0;
    if (lower === "task.md")
        return 1;
    if (lower === "walkthrough.md")
        return 2;
    return 10;
}
// 会話ディレクトリ直下の Markdown アーティファクト一覧を取得する
async function readConversationArtifacts(conversationId, brainRootDir) {
    const resolvedId = isSafeConversationId(conversationId)
        ? conversationId
        : await findLatestConversationId(brainRootDir);
    if (!isSafeConversationId(resolvedId)) {
        return [];
    }
    const rootDir = resolveBrainRoot(brainRootDir);
    const convDir = node_path_1.default.join(rootDir, resolvedId);
    let entries;
    try {
        entries = await node_fs_1.promises.readdir(convDir, { withFileTypes: true });
    }
    catch {
        return [];
    }
    const mdEntries = entries
        .filter((entry) => entry.isFile() && entry.name.toLowerCase().endsWith(".md"))
        .sort((a, b) => {
        const weightDiff = artifactSortWeight(a.name) - artifactSortWeight(b.name);
        return weightDiff !== 0 ? weightDiff : a.name.localeCompare(b.name);
    });
    const artifacts = [];
    for (const entry of mdEntries) {
        const filePath = node_path_1.default.join(convDir, entry.name);
        try {
            const [rawContent, fileStat] = await Promise.all([
                node_fs_1.promises.readFile(filePath, "utf8"),
                node_fs_1.promises.stat(filePath)
            ]);
            const content = truncateText(rawContent.trim(), MAX_ARTIFACT_LENGTH);
            if (!content) {
                continue;
            }
            let summary;
            let updatedAt = fileStat.mtime.toISOString();
            try {
                const metaRaw = await node_fs_1.promises.readFile(`${filePath}.metadata.json`, "utf8");
                const meta = JSON.parse(metaRaw);
                if (typeof meta.summary === "string" && meta.summary.trim().length > 0) {
                    summary = truncateText(meta.summary.trim(), 240);
                }
                if (typeof meta.updatedAt === "string" && meta.updatedAt.trim().length > 0) {
                    updatedAt = meta.updatedAt.trim();
                }
            }
            catch {
                // メタデータJSONが存在しない場合はファイル更新日時のみ使う
            }
            artifacts.push({
                name: entry.name,
                title: extractMarkdownTitle(content, entry.name),
                content,
                ...(summary ? { summary } : {}),
                updated_at: updatedAt
            });
        }
        catch {
            // 読み取れないファイルは無視する
        }
    }
    return artifacts;
}
// ローカルの会話ディレクトリを走査し更新時刻順のセッション一覧を返す
async function listConversations(brainRootDir, maxSessions = 20) {
    const rootDir = resolveBrainRoot(brainRootDir);
    let dirEntries;
    try {
        dirEntries = await node_fs_1.promises.readdir(rootDir, { withFileTypes: true });
    }
    catch {
        return [];
    }
    const candidates = [];
    for (const entry of dirEntries) {
        if (!entry.isDirectory() || entry.name.startsWith(".")) {
            continue;
        }
        const convDir = node_path_1.default.join(rootDir, entry.name);
        const logsDir = node_path_1.default.join(convDir, ".system_generated", "logs");
        let latestMtimeMs = -1;
        let transcriptPath = null;
        for (const fileName of ["transcript_full.jsonl", "transcript.jsonl"]) {
            const candidatePath = node_path_1.default.join(logsDir, fileName);
            try {
                const stat = await node_fs_1.promises.stat(candidatePath);
                if (!transcriptPath) {
                    transcriptPath = candidatePath;
                }
                if (stat.mtimeMs > latestMtimeMs) {
                    latestMtimeMs = stat.mtimeMs;
                }
            }
            catch {
                // 該当ログが存在しない場合は次を確認する
            }
        }
        let mdFileNames = [];
        try {
            const files = await node_fs_1.promises.readdir(convDir, { withFileTypes: true });
            mdFileNames = files
                .filter((f) => f.isFile() && f.name.toLowerCase().endsWith(".md"))
                .map((f) => f.name);
            for (const mdName of mdFileNames) {
                try {
                    const stat = await node_fs_1.promises.stat(node_path_1.default.join(convDir, mdName));
                    if (stat.mtimeMs > latestMtimeMs) {
                        latestMtimeMs = stat.mtimeMs;
                    }
                }
                catch {
                    // stat失敗は無視する
                }
            }
        }
        catch {
            // ディレクトリ読み取り失敗は無視する
        }
        if (!transcriptPath && mdFileNames.length === 0) {
            continue;
        }
        candidates.push({
            id: entry.name,
            convDir,
            latestMtimeMs,
            transcriptPath,
            mdFileNames
        });
    }
    candidates.sort((a, b) => b.latestMtimeMs - a.latestMtimeMs);
    const topCandidates = maxSessions > 0 ? candidates.slice(0, maxSessions) : candidates;
    const summaries = [];
    for (const candidate of topCandidates) {
        let messageCount = 0;
        let derivedTitle = "";
        if (candidate.transcriptPath) {
            try {
                const rawTranscript = await node_fs_1.promises.readFile(candidate.transcriptPath, "utf8");
                const messages = extractConversationHistoryFromJsonl(rawTranscript, 0);
                messageCount = messages.length;
                const latestUserMsg = [...messages].reverse().find((m) => m.role === "user");
                if (latestUserMsg) {
                    derivedTitle = truncateText(latestUserMsg.content.replace(/\s+/g, " ").trim(), 48);
                }
            }
            catch {
                // トランスクリプト読み取り失敗時はフォールバックタイトルを使う
            }
        }
        if (!derivedTitle && candidate.mdFileNames.length > 0) {
            const sortedMd = [...candidate.mdFileNames].sort((a, b) => {
                const diff = artifactSortWeight(a) - artifactSortWeight(b);
                return diff !== 0 ? diff : a.localeCompare(b);
            });
            try {
                const firstMd = await node_fs_1.promises.readFile(node_path_1.default.join(candidate.convDir, sortedMd[0]), "utf8");
                derivedTitle = extractMarkdownTitle(firstMd, sortedMd[0]);
            }
            catch {
                derivedTitle = sortedMd[0];
            }
        }
        if (!derivedTitle) {
            derivedTitle = candidate.id.slice(0, 8);
        }
        summaries.push({
            id: candidate.id,
            title: derivedTitle,
            ...(candidate.latestMtimeMs > 0
                ? { updated_at: new Date(candidate.latestMtimeMs).toISOString() }
                : {}),
            message_count: messageCount,
            artifact_count: candidate.mdFileNames.length
        });
    }
    return summaries;
}
//# sourceMappingURL=transcript-reader.js.map