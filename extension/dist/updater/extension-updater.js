"use strict";
// VS Code / Antigravity IDE上でGitHub Releasesからの拡張機能セルフアップデートを実行する
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
exports.checkExtensionUpdates = checkExtensionUpdates;
const node_fs_1 = require("node:fs");
const node_path_1 = __importDefault(require("node:path"));
const vscode = __importStar(require("vscode"));
const github_release_1 = require("./github-release");
async function checkExtensionUpdates(options) {
    const { currentVersion, output, silent, repoSlug = github_release_1.DEFAULT_GITHUB_REPO } = options;
    try {
        const latest = await (0, github_release_1.fetchLatestVsixRelease)(repoSlug);
        if (!latest) {
            output.appendLine("[Updater] 公開済みのリリースは見つかりませんでした");
            if (!silent) {
                vscode.window.showInformationMessage(`Antigravity Deck (v${currentVersion}): GitHub Releases に公開済みのリリースはまだありません`);
            }
            return;
        }
        if ((0, github_release_1.compareSemver)(currentVersion, latest.version) >= 0) {
            output.appendLine(`[Updater] 最新バージョンです (current=${currentVersion}, latest=${latest.version})`);
            if (!silent) {
                vscode.window.showInformationMessage(`Antigravity Deck は最新バージョンです (v${currentVersion})`);
            }
            return;
        }
        const choice = await vscode.window.showInformationMessage(`Antigravity Deck の新しいバージョン (${latest.tagName}) が利用可能です。GitHub Releases から更新しますか？`, "今すぐ更新", "リリースページを開く", "あとで");
        if (choice === "リリースページを開く" && latest.htmlUrl) {
            await vscode.env.openExternal(vscode.Uri.parse(latest.htmlUrl));
            return;
        }
        if (choice !== "今すぐ更新") {
            return;
        }
        await vscode.window.withProgress({
            location: vscode.ProgressLocation.Notification,
            title: `Antigravity Deck ${latest.tagName} をダウンロードしてインストール中...`,
            cancellable: false
        }, async () => {
            const vsixPath = await (0, github_release_1.downloadVsixToTemp)(latest);
            try {
                await vscode.commands.executeCommand("workbench.extensions.installExtension", vscode.Uri.file(vsixPath));
                output.appendLine(`[Updater] ${latest.assetName} をインストールしました`);
            }
            finally {
                await node_fs_1.promises.rm(node_path_1.default.dirname(vsixPath), { recursive: true, force: true }).catch(() => { });
            }
        });
        const reloadChoice = await vscode.window.showInformationMessage(`Antigravity Deck を ${latest.tagName} へ更新しました。反映するにはウィンドウを再読み込みしてください。`, "再読み込み", "あとで");
        if (reloadChoice === "再読み込み") {
            await vscode.commands.executeCommand("workbench.action.reloadWindow");
        }
    }
    catch (err) {
        const msg = err instanceof Error ? err.message : String(err);
        output.appendLine(`[Updater] 更新確認エラー: ${msg}`);
        if (!silent) {
            vscode.window.showWarningMessage(`Antigravity Deck の更新確認に失敗しました: ${msg}`);
        }
    }
}
//# sourceMappingURL=extension-updater.js.map