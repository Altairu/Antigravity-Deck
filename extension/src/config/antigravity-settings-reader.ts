// Antigravity IDE のグローバル設定 (state.vscdb) からターボモード設定を読み取るモジュール

import { execFile } from "node:child_process";
import os from "node:os";
import path from "node:path";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);

const AGENT_PREFERENCES_KEY = "antigravityUnifiedStateSync.agentPreferences";
const TERMINAL_AUTO_EXECUTION_KEY = "terminalAutoExecutionPolicySentinelKey";

// Antigravity CascadeCommandsAutoExecution enum (extension.js の定義に準拠)
export const CASCADE_AUTO_EXECUTION_EAGER = 3;

interface ProtobufField {
  fieldNumber: number;
  wireType: number;
  varint?: number;
  bytes?: Buffer;
}

// Protobuf の varint 値を読み取る
function readVarint(buf: Buffer, offset: number): { value: number; nextOffset: number } | null {
  let result = 0;
  let shift = 0;
  let cursor = offset;

  while (cursor < buf.length && shift <= 35) {
    const byte = buf[cursor];
    result += (byte & 0x7f) * 2 ** shift;
    cursor += 1;
    if ((byte & 0x80) === 0) {
      return { value: result, nextOffset: cursor };
    }
    shift += 7;
  }
  return null;
}

// Protobuf メッセージのトップレベルフィールドを順にデコードする
function decodeProtobufFields(buf: Buffer): ProtobufField[] {
  const fields: ProtobufField[] = [];
  let offset = 0;

  while (offset < buf.length) {
    const tag = readVarint(buf, offset);
    if (!tag) {
      break;
    }
    offset = tag.nextOffset;
    const fieldNumber = tag.value >>> 3;
    const wireType = tag.value & 0x07;

    if (wireType === 0) {
      const val = readVarint(buf, offset);
      if (!val) {
        break;
      }
      fields.push({ fieldNumber, wireType, varint: val.value });
      offset = val.nextOffset;
    } else if (wireType === 2) {
      const len = readVarint(buf, offset);
      if (!len || offset + len.value > buf.length) {
        break;
      }
      const start = len.nextOffset;
      const end = start + len.value;
      if (end > buf.length) {
        break;
      }
      fields.push({ fieldNumber, wireType, bytes: buf.subarray(start, end) });
      offset = end;
    } else if (wireType === 1) {
      if (offset + 8 > buf.length) {
        break;
      }
      offset += 8;
    } else if (wireType === 5) {
      if (offset + 4 > buf.length) {
        break;
      }
      offset += 4;
    } else {
      break;
    }
  }

  return fields;
}

// antigravityUnifiedStateSync.agentPreferences の Base64 文字列から
// terminalAutoExecutionPolicySentinelKey の数値 (3 = EAGER / ターボ) を抽出する
export function parseTerminalAutoExecutionPolicy(encodedAgentPreferencesBase64: string): number | null {
  const trimmed = encodedAgentPreferencesBase64.trim();
  if (!trimmed) {
    return null;
  }

  let rootBuf: Buffer;
  try {
    rootBuf = Buffer.from(trimmed, "base64");
  } catch {
    return null;
  }

  const rootFields = decodeProtobufFields(rootBuf);
  for (const field of rootFields) {
    if (field.fieldNumber !== 1 || !field.bytes) {
      continue;
    }
    const entryFields = decodeProtobufFields(field.bytes);
    const keyBytes = entryFields.find((item) => item.fieldNumber === 1)?.bytes;
    const valueWrapperBytes = entryFields.find((item) => item.fieldNumber === 2)?.bytes;
    if (!keyBytes || !valueWrapperBytes) {
      continue;
    }

    const key = keyBytes.toString("utf8");
    if (key !== TERMINAL_AUTO_EXECUTION_KEY) {
      continue;
    }

    const innerBase64 = decodeProtobufFields(valueWrapperBytes)
      .find((item) => item.fieldNumber === 1)
      ?.bytes?.toString("utf8");
    if (!innerBase64) {
      return null;
    }

    const policyBuf = Buffer.from(innerBase64.trim(), "base64");
    const policyFields = decodeProtobufFields(policyBuf);
    const enumField =
      policyFields.find((item) => item.fieldNumber === 2 && typeof item.varint === "number") ??
      policyFields.find((item) => item.wireType === 0 && typeof item.varint === "number");
    return typeof enumField?.varint === "number" ? enumField.varint : null;
  }

  return null;
}

// OS ごとの Antigravity IDE state.vscdb パスを解決する
export function getDefaultAntigravityStateDbPath(): string {
  if (process.platform === "win32") {
    const appData = process.env.APPDATA || path.join(os.homedir(), "AppData", "Roaming");
    return path.join(appData, "Antigravity IDE", "User", "globalStorage", "state.vscdb");
  }
  if (process.platform === "darwin") {
    return path.join(
      os.homedir(),
      "Library",
      "Application Support",
      "Antigravity IDE",
      "User",
      "globalStorage",
      "state.vscdb"
    );
  }
  const configHome = process.env.XDG_CONFIG_HOME || path.join(os.homedir(), ".config");
  return path.join(configHome, "Antigravity IDE", "User", "globalStorage", "state.vscdb");
}

async function queryItemTableWithNodeSqlite(dbPath: string, key: string): Promise<string | null> {
  try {
    const sqliteMod = (await import("node:sqlite" as string)) as {
      DatabaseSync?: new (
        filePath: string,
        options?: { readOnly?: boolean }
      ) => {
        prepare: (sql: string) => { get: (...params: unknown[]) => { value?: unknown } | undefined };
        close: () => void;
      };
    };
    if (!sqliteMod.DatabaseSync) {
      return null;
    }
    const db = new sqliteMod.DatabaseSync(dbPath, { readOnly: true });
    try {
      const row = db.prepare("SELECT value FROM ItemTable WHERE key = ?").get(key);
      return typeof row?.value === "string" ? row.value : null;
    } finally {
      db.close();
    }
  } catch {
    return null;
  }
}

async function queryItemTableWithPython(dbPath: string, key: string): Promise<string | null> {
  const script =
    "import sqlite3, sys; con = sqlite3.connect(f'file:{sys.argv[1]}?mode=ro', uri=True); " +
    "row = con.execute('SELECT value FROM ItemTable WHERE key = ?', (sys.argv[2],)).fetchone(); " +
    "print(row[0] if row and row[0] else '', end='')";

  for (const bin of ["python", "python3"]) {
    try {
      const { stdout } = await execFileAsync(bin, ["-c", script, dbPath, key], {
        timeout: 1500,
        windowsHide: true
      });
      if (stdout) {
        return stdout;
      }
    } catch {
      // 次の Python 実行コマンドを試す
    }
  }
  return null;
}

// Antigravity IDE 側でターボモード (Terminal Auto Execution = EAGER) が有効か判定する
export async function readAntigravityTurboState(
  stateDbPath = getDefaultAntigravityStateDbPath()
): Promise<boolean> {
  const rawValue =
    (await queryItemTableWithNodeSqlite(stateDbPath, AGENT_PREFERENCES_KEY)) ??
    (await queryItemTableWithPython(stateDbPath, AGENT_PREFERENCES_KEY));

  if (!rawValue) {
    return false;
  }

  const policy = parseTerminalAutoExecutionPolicy(rawValue);
  return policy === CASCADE_AUTO_EXECUTION_EAGER;
}
