"use strict";
// Antigravityの会話トランスクリプトから最新の返答テキストを読み取るユーティリティ
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.readLatestAgentResponse = readLatestAgentResponse;
const node_fs_1 = require("node:fs");
const node_os_1 = __importDefault(require("node:os"));
const node_path_1 = __importDefault(require("node:path"));
const MAX_RESPONSE_LENGTH = 8000;
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
                if (trimmed.length > MAX_RESPONSE_LENGTH) {
                    return `${trimmed.slice(0, MAX_RESPONSE_LENGTH - 3)}...`;
                }
                return trimmed;
            }
        }
    }
    return null;
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
//# sourceMappingURL=transcript-reader.js.map