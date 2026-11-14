// VS Code / Antigravity IDE上でGitHub Releasesからの拡張機能セルフアップデートを実行する

import { promises as fs } from "node:fs";
import path from "node:path";
import * as vscode from "vscode";
import {
  compareSemver,
  DEFAULT_GITHUB_REPO,
  downloadVsixToTemp,
  fetchLatestVsixRelease
} from "./github-release";

export async function checkExtensionUpdates(options: {
  currentVersion: string;
  output: vscode.OutputChannel;
  silent: boolean;
  repoSlug?: string;
}): Promise<void> {
  const { currentVersion, output, silent, repoSlug = DEFAULT_GITHUB_REPO } = options;

  try {
    const latest = await fetchLatestVsixRelease(repoSlug);
    if (!latest) {
      output.appendLine("[Updater] 公開済みのリリースは見つかりませんでした");
      if (!silent) {
        vscode.window.showInformationMessage(
          `Antigravity Deck (v${currentVersion}): GitHub Releases に公開済みのリリースはまだありません`
        );
      }
      return;
    }

    if (compareSemver(currentVersion, latest.version) >= 0) {
      output.appendLine(`[Updater] 最新バージョンです (current=${currentVersion}, latest=${latest.version})`);
      if (!silent) {
        vscode.window.showInformationMessage(
          `Antigravity Deck は最新バージョンです (v${currentVersion})`
        );
      }
      return;
    }

    const choice = await vscode.window.showInformationMessage(
      `Antigravity Deck の新しいバージョン (${latest.tagName}) が利用可能です。GitHub Releases から更新しますか？`,
      "今すぐ更新",
      "リリースページを開く",
      "あとで"
    );

    if (choice === "リリースページを開く" && latest.htmlUrl) {
      await vscode.env.openExternal(vscode.Uri.parse(latest.htmlUrl));
      return;
    }

    if (choice !== "今すぐ更新") {
      return;
    }

    await vscode.window.withProgress(
      {
        location: vscode.ProgressLocation.Notification,
        title: `Antigravity Deck ${latest.tagName} をダウンロードしてインストール中...`,
        cancellable: false
      },
      async () => {
        const vsixPath = await downloadVsixToTemp(latest);
        try {
          await vscode.commands.executeCommand(
            "workbench.extensions.installExtension",
            vscode.Uri.file(vsixPath)
          );
          output.appendLine(`[Updater] ${latest.assetName} をインストールしました`);
        } finally {
          await fs.rm(path.dirname(vsixPath), { recursive: true, force: true }).catch(() => {});
        }
      }
    );

    const reloadChoice = await vscode.window.showInformationMessage(
      `Antigravity Deck を ${latest.tagName} へ更新しました。反映するにはウィンドウを再読み込みしてください。`,
      "再読み込み",
      "あとで"
    );
    if (reloadChoice === "再読み込み") {
      await vscode.commands.executeCommand("workbench.action.reloadWindow");
    }
  } catch (err) {
    const msg = err instanceof Error ? err.message : String(err);
    output.appendLine(`[Updater] 更新確認エラー: ${msg}`);
    if (!silent) {
      vscode.window.showWarningMessage(
        `Antigravity Deck の更新確認に失敗しました: ${msg}`
      );
    }
  }
}
