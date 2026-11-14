"use strict";
// ペアリング用QRコードとUSB接続案内を表示するWebviewパネル
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
exports.PairingWebviewPanel = void 0;
exports.getLanIpv4Candidates = getLanIpv4Candidates;
const node_os_1 = __importDefault(require("node:os"));
const qrcode_1 = __importDefault(require("qrcode"));
const vscode = __importStar(require("vscode"));
// 仮想アダプタやループバックを避け、優先度の高いLAN IPv4アドレス一覧を返す
function getLanIpv4Candidates() {
    const interfaces = node_os_1.default.networkInterfaces();
    const candidates = [];
    for (const [name, entries] of Object.entries(interfaces)) {
        if (!entries) {
            continue;
        }
        for (const entry of entries) {
            if (entry.family !== "IPv4" || entry.internal) {
                continue;
            }
            if (entry.address.startsWith("169.254.")) {
                continue;
            }
            let score = 0;
            const lowerName = name.toLowerCase();
            if (lowerName.includes("wi-fi") || lowerName.includes("wlan") || lowerName.includes("ethernet")) {
                score += 30;
            }
            if (lowerName.includes("vethernet") ||
                lowerName.includes("wsl") ||
                lowerName.includes("virtual") ||
                lowerName.includes("docker") ||
                lowerName.includes("hyper-v")) {
                score -= 40;
            }
            if (entry.address.startsWith("192.168.")) {
                score += 20;
            }
            else if (entry.address.startsWith("10.")) {
                score += 15;
            }
            else if (/^172\.(1[6-9]|2\d|3[0-1])\./.test(entry.address)) {
                score += 10;
            }
            candidates.push({
                interfaceName: name,
                address: entry.address,
                score
            });
        }
    }
    candidates.sort((a, b) => b.score - a.score);
    return candidates.map(({ interfaceName, address }) => ({ interfaceName, address }));
}
function escapeHtml(value) {
    return value
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#39;");
}
class PairingWebviewPanel {
    static currentPanel;
    panel;
    selectedHost;
    wsPort;
    authToken;
    onSimulateLifecycle;
    constructor(panel, wsPort, authToken, onSimulateLifecycle) {
        this.panel = panel;
        this.wsPort = wsPort;
        this.authToken = authToken;
        this.onSimulateLifecycle = onSimulateLifecycle;
        const candidates = getLanIpv4Candidates();
        this.selectedHost = candidates[0]?.address || "127.0.0.1";
        this.panel.onDidDispose(() => {
            if (PairingWebviewPanel.currentPanel === this) {
                PairingWebviewPanel.currentPanel = undefined;
            }
        });
        this.panel.webview.onDidReceiveMessage((message) => {
            if (message.type === "select_host" && typeof message.host === "string") {
                this.selectedHost = message.host;
                void this.render();
            }
            else if (message.type === "refresh") {
                void this.render();
            }
            else if (message.type === "simulate") {
                this.onSimulateLifecycle();
            }
        });
    }
    static async show(wsPort, authToken, onSimulateLifecycle) {
        if (PairingWebviewPanel.currentPanel) {
            PairingWebviewPanel.currentPanel.wsPort = wsPort;
            PairingWebviewPanel.currentPanel.authToken = authToken;
            PairingWebviewPanel.currentPanel.panel.reveal(vscode.ViewColumn.Beside);
            await PairingWebviewPanel.currentPanel.render();
            return;
        }
        const panel = vscode.window.createWebviewPanel("antigravityDeckPairing", "Antigravity Deck Pairing", vscode.ViewColumn.Beside, {
            enableScripts: true,
            retainContextWhenHidden: true
        });
        const instance = new PairingWebviewPanel(panel, wsPort, authToken, onSimulateLifecycle);
        PairingWebviewPanel.currentPanel = instance;
        await instance.render();
    }
    async render() {
        const candidates = getLanIpv4Candidates();
        if (!candidates.some((c) => c.address === this.selectedHost) && candidates.length > 0) {
            this.selectedHost = candidates[0].address;
        }
        const wsUrl = `ws://${this.selectedHost}:${this.wsPort}?token=${encodeURIComponent(this.authToken)}`;
        const qrPayload = JSON.stringify({
            host: this.selectedHost,
            port: this.wsPort,
            token: this.authToken,
            wsUrl
        });
        const qrDataUrl = await qrcode_1.default.toDataURL(qrPayload, {
            width: 280,
            margin: 2,
            color: {
                dark: "#0f172a",
                light: "#ffffff"
            }
        });
        const hostOptionsHtml = (candidates.length > 0 ? candidates : [{ interfaceName: "Loopback", address: "127.0.0.1" }])
            .map((c) => {
            const selected = c.address === this.selectedHost ? "selected" : "";
            return `<option value="${escapeHtml(c.address)}" ${selected}>${escapeHtml(c.address)} (${escapeHtml(c.interfaceName)})</option>`;
        })
            .join("");
        const nonce = String(Date.now());
        this.panel.webview.html = `<!DOCTYPE html>
<html lang="ja">
<head>
  <meta charset="UTF-8" />
  <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src data:; style-src 'unsafe-inline'; script-src 'nonce-${nonce}';" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>Antigravity Deck Pairing</title>
  <style>
    body {
      font-family: var(--vscode-font-family, sans-serif);
      color: var(--vscode-foreground);
      background-color: var(--vscode-editor-background);
      margin: 0;
      padding: 24px;
      line-height: 1.5;
    }
    .container {
      max-width: 560px;
      margin: 0 auto;
    }
    .card {
      background: var(--vscode-editorWidget-background);
      border: 1px solid var(--vscode-widget-border, rgba(255,255,255,0.12));
      border-radius: 12px;
      padding: 20px;
      margin-bottom: 16px;
    }
    h1 {
      font-size: 1.3rem;
      margin: 0 0 8px 0;
    }
    h2 {
      font-size: 1rem;
      margin: 0 0 12px 0;
    }
    .qr-wrap {
      display: flex;
      justify-content: center;
      background: #ffffff;
      border-radius: 12px;
      padding: 16px;
      margin: 16px auto;
      width: fit-content;
    }
    .qr-wrap img {
      display: block;
      width: 240px;
      height: 240px;
    }
    label {
      display: block;
      font-size: 0.85rem;
      opacity: 0.85;
      margin-bottom: 6px;
    }
    select {
      width: 100%;
      padding: 8px 10px;
      border-radius: 6px;
      border: 1px solid var(--vscode-input-border, #555);
      background: var(--vscode-input-background);
      color: var(--vscode-input-foreground);
      margin-bottom: 12px;
    }
    pre, code {
      font-family: var(--vscode-editor-font-family, monospace);
      background: var(--vscode-textCodeBlock-background, rgba(0,0,0,0.25));
      border-radius: 6px;
      padding: 8px 10px;
      display: block;
      overflow-x: auto;
      font-size: 0.85rem;
    }
    .actions {
      display: flex;
      gap: 10px;
      margin-top: 12px;
    }
    button {
      border: none;
      border-radius: 6px;
      padding: 8px 14px;
      cursor: pointer;
      background: var(--vscode-button-background);
      color: var(--vscode-button-foreground);
      font-weight: 600;
    }
    button:hover {
      background: var(--vscode-button-hoverBackground);
    }
    button.secondary {
      background: var(--vscode-button-secondaryBackground);
      color: var(--vscode-button-secondaryForeground);
    }
  </style>
</head>
<body>
  <div class="container">
    <div class="card">
      <h1>Antigravity Deck ペアリング</h1>
      <p>AndroidコンパニオンアプリのQRスキャナーで以下のコードを読み取るとLAN経由で接続できます。</p>
      <label for="hostSelect">PC側のネットワークアドレス</label>
      <select id="hostSelect">${hostOptionsHtml}</select>
      <div class="qr-wrap">
        <img src="${qrDataUrl}" alt="Pairing QR Code" />
      </div>
      <code>${escapeHtml(wsUrl)}</code>
      <div class="actions">
        <button id="simulateBtn">ライフサイクルをシミュレート</button>
        <button id="refreshBtn" class="secondary">IP一覧を再取得</button>
      </div>
    </div>

    <div class="card">
      <h2>USB有線接続のプリセット手順</h2>
      <p>USBデバッグ中のスマートフォンでは、PC側で以下のコマンドを実行した後にアプリ内のUSB接続ボタンを押してください。</p>
      <pre>adb reverse tcp:${this.wsPort} tcp:${this.wsPort}</pre>
      <p>接続先: <code>ws://127.0.0.1:${this.wsPort}?token=${escapeHtml(this.authToken)}</code></p>
    </div>
  </div>
  <script nonce="${nonce}">
    const vscode = acquireVsCodeApi();
    document.getElementById("hostSelect").addEventListener("change", (event) => {
      vscode.postMessage({ type: "select_host", host: event.target.value });
    });
    document.getElementById("refreshBtn").addEventListener("click", () => {
      vscode.postMessage({ type: "refresh" });
    });
    document.getElementById("simulateBtn").addEventListener("click", () => {
      vscode.postMessage({ type: "simulate" });
    });
  </script>
</body>
</html>`;
    }
}
exports.PairingWebviewPanel = PairingWebviewPanel;
//# sourceMappingURL=pairing-webview.js.map