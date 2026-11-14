// Antigravityの会話トランスクリプトから最新の返答テキストを読み取るユーティリティ

import { promises as fs } from "node:fs";
import os from "node:os";
import path from "node:path";

const MAX_RESPONSE_LENGTH = 8000;

interface TranscriptLine {
  source?: string;
  type?: string;
  content?: unknown;
}

// トランスクリプトJSONLファイルを末尾から走査し直近のモデル返答を抽出する
function extractLatestResponseFromJsonl(rawText: string): string | null {
  const lines = rawText.split(/\r?\n/);
  for (let i = lines.length - 1; i >= 0; i -= 1) {
    const line = lines[i]?.trim();
    if (!line) {
      continue;
    }

    let entry: TranscriptLine;
    try {
      entry = JSON.parse(line) as TranscriptLine;
    } catch {
      continue;
    }

    // 最新のユーザー入力より前のターンまでは遡らない
    if (entry.type === "USER_INPUT") {
      break;
    }

    if (
      entry.source === "MODEL" &&
      entry.type === "PLANNER_RESPONSE" &&
      typeof entry.content === "string"
    ) {
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
export async function readLatestAgentResponse(
  conversationId?: string,
  brainRootDir?: string
): Promise<string | null> {
  if (!conversationId) {
    return null;
  }

  const rootDir = brainRootDir || path.join(os.homedir(), ".gemini", "antigravity", "brain");
  const logsDir = path.join(rootDir, conversationId, ".system_generated", "logs");
  const candidates = [
    path.join(logsDir, "transcript_full.jsonl"),
    path.join(logsDir, "transcript.jsonl")
  ];

  for (const filePath of candidates) {
    try {
      const content = await fs.readFile(filePath, "utf8");
      const extracted = extractLatestResponseFromJsonl(content);
      if (extracted) {
        return extracted;
      }
    } catch {
      // ファイルが存在しない場合は次の候補を試す
    }
  }

  return null;
}
