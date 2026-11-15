// Antigravity Deck VS Code / Antigravity拡張機能のエントリポイント

import * as vscode from "vscode";
import {
  ANTIGRAVITY_COMMAND_BINDINGS,
  CommandBridge,
  parseAntigravityModelId,
  ReasoningEffort,
  resolveAntigravityModelId
} from "./config/antigravity-bindings";
import { DeckHookServer } from "./server/hook-server";
import { DeckWebSocketServer } from "./server/ws-server";
import { readAntigravityTurboState } from "./config/antigravity-settings-reader";
import { HooksGitIgnoreTarget, setupWorkspaceHooks } from "./hooks/workspace-hooks";
import { SessionStore } from "./state/session-store";
import { PairingWebviewPanel } from "./ui/pairing-webview";
import { checkExtensionUpdates } from "./updater/extension-updater";

// 候補コマンド一覧のうちIDEに登録済みの最初のコマンドを実行する
async function executeFirstAvailableCommand(
  candidates: readonly string[],
  args: unknown[],
  output: vscode.OutputChannel
): Promise<boolean> {
  const registered = new Set(await vscode.commands.getCommands(true));
  for (const commandId of candidates) {
    if (!registered.has(commandId)) {
      continue;
    }
    try {
      await vscode.commands.executeCommand(commandId, ...args);
      output.appendLine(`[Command] 実行成功: ${commandId}`);
      return true;
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      output.appendLine(`[Command] 実行失敗 (${commandId}): ${msg}`);
    }
  }
  return false;
}

// コマンド候補がない場合はVS Code設定キーへの反映を試みるヘルパー
async function tryUpdateSettingValue(
  settingKeys: readonly string[],
  value: string,
  output: vscode.OutputChannel
): Promise<boolean> {
  const config = vscode.workspace.getConfiguration();
  for (const key of settingKeys) {
    if (config.inspect(key)?.defaultValue !== undefined) {
      try {
        await config.update(key, value, vscode.ConfigurationTarget.Global);
        output.appendLine(`[Bridge] 設定 ${key} を ${value} に更新しました`);
        return true;
      } catch (err) {
        const msg = err instanceof Error ? err.message : String(err);
        output.appendLine(`[Bridge] 設定更新失敗 (${key}): ${msg}`);
      }
    }
  }
  return false;
}

// Antigravity内部コマンドとVS Code設定へ接続するブリッジを生成する
function createVsCodeCommandBridge(
  output: vscode.OutputChannel,
  sessionStore: SessionStore
): CommandBridge {
  return {
    async approve(conversationId?: string): Promise<void> {
      const arg = conversationId ? { conversationId } : {};
      const executed = await executeFirstAvailableCommand(
        ANTIGRAVITY_COMMAND_BINDINGS.approve,
        [arg],
        output
      );
      if (!executed) {
        output.appendLine("[Bridge] 承認を受け付けました");
      }
      vscode.window.showInformationMessage("Antigravity Deck: 実行が承認されました");
    },

    async reject(conversationId?: string, reason?: string): Promise<void> {
      const arg = conversationId ? { conversationId, reason } : { reason };
      const executed = await executeFirstAvailableCommand(
        ANTIGRAVITY_COMMAND_BINDINGS.reject,
        [arg],
        output
      );
      if (!executed) {
        output.appendLine("[Bridge] 拒否を受け付けました");
      }
      vscode.window.showWarningMessage("Antigravity Deck: 実行が拒否されました");
    },

    async sendPrompt(prompt: string, conversationId?: string): Promise<void> {
      const registered = new Set(await vscode.commands.getCommands(true));
      if (
        registered.has(ANTIGRAVITY_COMMAND_BINDINGS.chatAddContextCommand) &&
        registered.has(ANTIGRAVITY_COMMAND_BINDINGS.chatTriggerSendCommand)
      ) {
        if (registered.has(ANTIGRAVITY_COMMAND_BINDINGS.chatPanelFocusCommand)) {
          try {
            await vscode.commands.executeCommand(
              ANTIGRAVITY_COMMAND_BINDINGS.chatPanelFocusCommand
            );
          } catch (err) {
            const msg = err instanceof Error ? err.message : String(err);
            output.appendLine(`[Command] パネルフォーカス警告: ${msg}`);
          }
        }
        try {
          await vscode.commands.executeCommand(
            ANTIGRAVITY_COMMAND_BINDINGS.chatAddContextCommand,
            prompt
          );
          // Webview側へテキストチャンクが反映されるまで短時間待機してから送信する
          await new Promise((resolve) => setTimeout(resolve, 80));
          await vscode.commands.executeCommand(
            ANTIGRAVITY_COMMAND_BINDINGS.chatTriggerSendCommand
          );
          output.appendLine(
            `[Command] チャット送信成功: ${ANTIGRAVITY_COMMAND_BINDINGS.chatAddContextCommand} -> ${ANTIGRAVITY_COMMAND_BINDINGS.chatTriggerSendCommand}`
          );
          vscode.window.showInformationMessage(`Antigravity Deck: ${prompt}`);
          return;
        } catch (err) {
          const msg = err instanceof Error ? err.message : String(err);
          output.appendLine(`[Command] Antigravityチャット送信失敗: ${msg}`);
        }
      }

      const executed = await executeFirstAvailableCommand(
        ANTIGRAVITY_COMMAND_BINDINGS.sendPrompt,
        [{ query: prompt, prompt, conversationId }],
        output
      );
      if (!executed) {
        output.appendLine(`[Bridge] プロンプトを受信: ${prompt}`);
      }
      vscode.window.showInformationMessage(`Antigravity Deck: ${prompt}`);
    },

    async setReasoningEffort(
      effort: ReasoningEffort,
      conversationId?: string
    ): Promise<void> {
      const currentState = sessionStore.getState();
      const modelId = resolveAntigravityModelId(currentState.model_name, effort);
      const executed = await executeFirstAvailableCommand(
        ANTIGRAVITY_COMMAND_BINDINGS.setReasoningEffort,
        [
          {
            effort,
            reasoningEffort: effort,
            model: modelId,
            modelId,
            modelName: currentState.model_name,
            conversationId
          }
        ],
        output
      );

      if (!executed) {
        const updatedEffort = await tryUpdateSettingValue(
          ANTIGRAVITY_COMMAND_BINDINGS.reasoningEffortSettingKeys,
          effort,
          output
        );
        const updatedModel = await tryUpdateSettingValue(
          ANTIGRAVITY_COMMAND_BINDINGS.modelSettingKeys,
          modelId,
          output
        );
        if (!updatedEffort && !updatedModel) {
          output.appendLine(`[Bridge] 推論レベルを ${effort} (${modelId}) に記録しました`);
        }
      }
    },

    async setModel(modelName: string, conversationId?: string): Promise<void> {
      const currentState = sessionStore.getState();
      const parsed = parseAntigravityModelId(modelName);
      const displayModelName = parsed.modelName;
      const modelId = resolveAntigravityModelId(
        displayModelName,
        currentState.reasoning_effort
      );
      const executed = await executeFirstAvailableCommand(
        ANTIGRAVITY_COMMAND_BINDINGS.setModel,
        [
          {
            model: modelId,
            modelId,
            modelName: displayModelName,
            conversationId
          }
        ],
        output
      );

      if (!executed) {
        const updated = await tryUpdateSettingValue(
          ANTIGRAVITY_COMMAND_BINDINGS.modelSettingKeys,
          modelId,
          output
        );
        if (!updated) {
          output.appendLine(`[Bridge] モデルを ${displayModelName} (${modelId}) に記録しました`);
        }
      }
      vscode.window.showInformationMessage(
        `Antigravity Deck: モデルを ${displayModelName} (${modelId}) に変更しました`
      );
    }
  };
}

function readExtensionConfig() {
  const cfg = vscode.workspace.getConfiguration("antigravityDeck");
  const rawGitIgnoreTarget = cfg.get<string>("hooksGitIgnoreTarget", "gitignore");
  const hooksGitIgnoreTarget: HooksGitIgnoreTarget =
    rawGitIgnoreTarget === "gitExclude" || rawGitIgnoreTarget === "none"
      ? rawGitIgnoreTarget
      : "gitignore";

  return {
    wsPort: cfg.get<number>("wsPort", 8765),
    hookPort: cfg.get<number>("hookPort", 8766),
    authToken: cfg.get<string>("authToken", "antigravity-local"),
    waitForMobileApproval: cfg.get<boolean>("waitForMobileApproval", true),
    autoStart: cfg.get<boolean>("autoStart", true),
    autoCheckUpdates: cfg.get<boolean>("autoCheckUpdates", true),
    autoSetupWorkspaceHooks: cfg.get<boolean>("autoSetupWorkspaceHooks", false),
    hooksGitIgnoreTarget
  };
}

export async function activate(context: vscode.ExtensionContext): Promise<void> {
  const output = vscode.window.createOutputChannel("Antigravity Deck");
  const statusBar = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 100);
  statusBar.command = "antigravityDeck.showPairingQr";

  const sessionStore = new SessionStore();
  const commandBridge = createVsCodeCommandBridge(output, sessionStore);
  let currentConfig = readExtensionConfig();

  const updateStatusBar = (wsRunning: boolean, clientCount: number) => {
    if (wsRunning) {
      statusBar.text = `$(device-mobile) Deck :${currentConfig.wsPort} (${clientCount})`;
      statusBar.tooltip = `Antigravity Deck 稼働中 / Hook :${currentConfig.hookPort} / 接続台数: ${clientCount}`;
    } else {
      statusBar.text = "$(device-mobile) Deck (停止中)";
      statusBar.tooltip = "クリックしてペアリング画面を開くかサーバーを起動します";
    }
    statusBar.show();
  };

  const wsServer = new DeckWebSocketServer({
    port: currentConfig.wsPort,
    authToken: currentConfig.authToken,
    sessionStore,
    commandBridge,
    isAntigravityTurboEnabled: () => readAntigravityTurboState(),
    onLog: (msg) => output.appendLine(`[WS] ${msg}`),
    onClientCountChange: (count) => updateStatusBar(wsServer.isRunning(), count)
  });

  const hookServer = new DeckHookServer({
    port: currentConfig.hookPort,
    sessionStore,
    getConnectedClients: () => wsServer.getConnectedClientCount(),
    shouldWaitForMobileApproval: () => readExtensionConfig().waitForMobileApproval,
    isAntigravityTurboEnabled: () => readAntigravityTurboState(),
    onLog: (msg) => output.appendLine(`[Hook] ${msg}`)
  });

  const startServers = async () => {
    currentConfig = readExtensionConfig();
    wsServer.updateConfig(currentConfig.wsPort, currentConfig.authToken);
    hookServer.updatePort(currentConfig.hookPort);

    try {
      await wsServer.start();
      await hookServer.start();
      updateStatusBar(true, wsServer.getConnectedClientCount());
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      output.appendLine(`[Error] サーバー起動失敗: ${msg}`);
      vscode.window.showErrorMessage(`Antigravity Deck サーバーの起動に失敗しました: ${msg}`);
      updateStatusBar(wsServer.isRunning(), wsServer.getConnectedClientCount());
    }
  };

  const stopServers = async () => {
    await hookServer.stop();
    await wsServer.stop();
    updateStatusBar(false, 0);
  };

  const triggerSimulation = () => {
    sessionStore.simulateAgentLifecycle();
    vscode.window.showInformationMessage(
      "Antigravity Deck でエージェント状態のシミュレーションを開始しました"
    );
  };

  // 開かれているワークスペースへ .agents/hooks.json と deck-hook.mjs を配置する
  const runSetupWorkspaceHooks = async (silent: boolean) => {
    const folders = vscode.workspace.workspaceFolders;
    if (!folders || folders.length === 0) {
      if (!silent) {
        vscode.window.showWarningMessage("ワークスペースフォルダが開かれていません");
      }
      return;
    }

    const latestConfig = readExtensionConfig();
    const updatedNames: string[] = [];

    for (const folder of folders) {
      try {
        const result = await setupWorkspaceHooks(folder.uri.fsPath, {
          hookPort: latestConfig.hookPort,
          gitIgnoreTarget: latestConfig.hooksGitIgnoreTarget
        });
        const modeLabel = result.mergedExisting ? "既存設定へマージ" : "新規作成";
        const ignoreNote =
          result.gitIgnoreUpdated && result.gitIgnorePath
            ? ` / Git除外設定へ追記: ${result.gitIgnorePath}`
            : "";
        output.appendLine(
          `[Hooks] ${folder.name} へ配置完了 (${modeLabel}): ${result.hooksJsonPath}${ignoreNote}`
        );
        updatedNames.push(folder.name);
      } catch (err) {
        const msg = err instanceof Error ? err.message : String(err);
        output.appendLine(`[Error] Workspace Hooks 配置失敗 (${folder.name}): ${msg}`);
        if (!silent) {
          vscode.window.showErrorMessage(
            `Antigravity Deck Hooks の配置に失敗しました (${folder.name}): ${msg}`
          );
        }
      }
    }

    if (!silent && updatedNames.length > 0) {
      vscode.window.showInformationMessage(
        `Antigravity Deck の Workspace Hooks を導入しました (${updatedNames.join(", ")})`
      );
    }
  };

  const currentVersion =
    typeof context.extension.packageJSON?.version === "string"
      ? context.extension.packageJSON.version
      : "0.1.0";

  context.subscriptions.push(
    output,
    statusBar,
    vscode.commands.registerCommand("antigravityDeck.startServer", async () => {
      await startServers();
      vscode.window.showInformationMessage("Antigravity Deck サーバーを起動しました");
    }),
    vscode.commands.registerCommand("antigravityDeck.stopServer", async () => {
      await stopServers();
      vscode.window.showInformationMessage("Antigravity Deck サーバーを停止しました");
    }),
    vscode.commands.registerCommand("antigravityDeck.showPairingQr", async () => {
      if (!wsServer.isRunning()) {
        await startServers();
      }
      await PairingWebviewPanel.show(
        wsServer.getPort(),
        wsServer.getAuthToken(),
        triggerSimulation,
        () => {
          void runSetupWorkspaceHooks(false);
        }
      );
    }),
    vscode.commands.registerCommand("antigravityDeck.simulateLifecycle", () => {
      triggerSimulation();
    }),
    vscode.commands.registerCommand("antigravityDeck.setupWorkspaceHooks", async () => {
      await runSetupWorkspaceHooks(false);
    }),
    vscode.commands.registerCommand("antigravityDeck.checkForUpdates", async () => {
      await checkExtensionUpdates({
        currentVersion,
        output,
        silent: false
      });
    }),
    {
      dispose: () => {
        sessionStore.dispose();
        void stopServers();
      }
    }
  );

  updateStatusBar(false, 0);
  if (currentConfig.autoStart) {
    await startServers();
  }

  if (currentConfig.autoSetupWorkspaceHooks) {
    await runSetupWorkspaceHooks(true);
  }

  if (currentConfig.autoCheckUpdates) {
    const updateTimer = setTimeout(() => {
      void checkExtensionUpdates({
        currentVersion,
        output,
        silent: true
      });
    }, 5000);
    context.subscriptions.push({
      dispose: () => clearTimeout(updateTimer)
    });
  }
}

export async function deactivate(): Promise<void> {
  // Disposable登録側で停止処理を実行するためここでは待機不要
}
