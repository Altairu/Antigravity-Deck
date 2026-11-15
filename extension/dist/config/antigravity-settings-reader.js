"use strict";
// Antigravity IDE のグローバル設定 (state.vscdb) からターボモード設定を読み取るモジュール
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.CASCADE_AUTO_EXECUTION_EAGER = void 0;
exports.parseTerminalAutoExecutionPolicy = parseTerminalAutoExecutionPolicy;
exports.getDefaultAntigravityStateDbPath = getDefaultAntigravityStateDbPath;
exports.readAntigravityTurboState = readAntigravityTurboState;
const node_child_process_1 = require("node:child_process");
const node_os_1 = __importDefault(require("node:os"));
const node_path_1 = __importDefault(require("node:path"));
const node_util_1 = require("node:util");
const execFileAsync = (0, node_util_1.promisify)(node_child_process_1.execFile);
const AGENT_PREFERENCES_KEY = "antigravityUnifiedStateSync.agentPreferences";
const TERMINAL_AUTO_EXECUTION_KEY = "terminalAutoExecutionPolicySentinelKey";
// Antigravity CascadeCommandsAutoExecution enum (extension.js の定義に準拠)
exports.CASCADE_AUTO_EXECUTION_EAGER = 3;
// Protobuf の varint 値を読み取る
function readVarint(buf, offset) {
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
function decodeProtobufFields(buf) {
    const fields = [];
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
        }
        else if (wireType === 2) {
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
        }
        else if (wireType === 1) {
            if (offset + 8 > buf.length) {
                break;
            }
            offset += 8;
        }
        else if (wireType === 5) {
            if (offset + 4 > buf.length) {
                break;
            }
            offset += 4;
        }
        else {
            break;
        }
    }
    return fields;
}
// antigravityUnifiedStateSync.agentPreferences の Base64 文字列から
// terminalAutoExecutionPolicySentinelKey の数値 (3 = EAGER / ターボ) を抽出する
function parseTerminalAutoExecutionPolicy(encodedAgentPreferencesBase64) {
    const trimmed = encodedAgentPreferencesBase64.trim();
    if (!trimmed) {
        return null;
    }
    let rootBuf;
    try {
        rootBuf = Buffer.from(trimmed, "base64");
    }
    catch {
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
        const enumField = policyFields.find((item) => item.fieldNumber === 2 && typeof item.varint === "number") ??
            policyFields.find((item) => item.wireType === 0 && typeof item.varint === "number");
        return typeof enumField?.varint === "number" ? enumField.varint : null;
    }
    return null;
}
// OS ごとの Antigravity IDE state.vscdb パスを解決する
function getDefaultAntigravityStateDbPath() {
    if (process.platform === "win32") {
        const appData = process.env.APPDATA || node_path_1.default.join(node_os_1.default.homedir(), "AppData", "Roaming");
        return node_path_1.default.join(appData, "Antigravity IDE", "User", "globalStorage", "state.vscdb");
    }
    if (process.platform === "darwin") {
        return node_path_1.default.join(node_os_1.default.homedir(), "Library", "Application Support", "Antigravity IDE", "User", "globalStorage", "state.vscdb");
    }
    const configHome = process.env.XDG_CONFIG_HOME || node_path_1.default.join(node_os_1.default.homedir(), ".config");
    return node_path_1.default.join(configHome, "Antigravity IDE", "User", "globalStorage", "state.vscdb");
}
async function queryItemTableWithNodeSqlite(dbPath, key) {
    try {
        const sqliteMod = (await Promise.resolve(`${"node:sqlite"}`).then(s => __importStar(require(s))));
        if (!sqliteMod.DatabaseSync) {
            return null;
        }
        const db = new sqliteMod.DatabaseSync(dbPath, { readOnly: true });
        try {
            const row = db.prepare("SELECT value FROM ItemTable WHERE key = ?").get(key);
            return typeof row?.value === "string" ? row.value : null;
        }
        finally {
            db.close();
        }
    }
    catch {
        return null;
    }
}
async function queryItemTableWithPython(dbPath, key) {
    const script = "import sqlite3, sys; con = sqlite3.connect(f'file:{sys.argv[1]}?mode=ro', uri=True); " +
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
        }
        catch {
            // 次の Python 実行コマンドを試す
        }
    }
    return null;
}
// Antigravity IDE 側でターボモード (Terminal Auto Execution = EAGER) が有効か判定する
async function readAntigravityTurboState(stateDbPath = getDefaultAntigravityStateDbPath()) {
    const rawValue = (await queryItemTableWithNodeSqlite(stateDbPath, AGENT_PREFERENCES_KEY)) ??
        (await queryItemTableWithPython(stateDbPath, AGENT_PREFERENCES_KEY));
    if (!rawValue) {
        return false;
    }
    const policy = parseTerminalAutoExecutionPolicy(rawValue);
    return policy === exports.CASCADE_AUTO_EXECUTION_EAGER;
}
//# sourceMappingURL=antigravity-settings-reader.js.map