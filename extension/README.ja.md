[English](./README.md) | 日本語

# Antigravity Deck

Google Antigravity 上で動いているエージェントの状態を、手元の Android スマートフォンから監視と操作できるようにするコンパニオン拡張機能です。

PC の前から離れているときでも、コマンド実行の承認やチャット送信、エージェントからの返答確認をスマホ上で行えます。

## できること

* エージェントの状態監視: 待機、思考中、承認待ち、完了、エラーの状態変化をリアルタイムでスマホへ同期します
* 承認と拒否の操作: `PreToolUse` フックで捕捉した実行予定コマンドやファイル差分をスマホでプレビューし、画面や通知バーからそのまま承認と拒否を返せます
* 会話履歴とセッション切り替えおよび成果物表示: 過去の会話セッション一覧から別のスレッドへ切り替えられるほか、会話履歴や `implementation_plan.md` / `task.md` / `walkthrough.md` などのアーティファクトを専用タブで閲覧でき、キーボードや音声入力からプロンプト送信も行えます
* モデルと推論レベルの切り替え: 利用中のモデルや推論の深さをスマホ側から変更できます
* 通知と言語の制御: 承認待ちや会話終了、エラー発生ごとにスマホ側の通知とバイブレーションを個別にオンとオフで切り替えられ、アプリの表示言語も切り替えられます
* 自動アップデート: Visual Studio Marketplace および Open VSX Registry 経由の更新に加えて、GitHub Releases からの更新確認コマンドにも対応しています

## 使い始める手順

* PC 側の設定: Antigravity IDE の拡張機能パネルから `Altairu.antigravity-deck` (`Antigravity Deck`) をインストールします
* Android アプリの準備: [GitHub Releases](https://github.com/Altairu/Antigravity-Deck/releases) から最新の `antigravity-deck-*.apk` をダウンロードして端末へインストールします
* Lifecycle Hooks の有効化: コマンドパレットから `Antigravity Deck: Setup Workspace Hooks` を実行するか、ペアリング画面の導入ボタンを押すと、既存のフック設定を保ったまま `.agents/hooks.json` と `.agents/scripts/deck-hook.mjs` が対象ワークスペースへ配置されます
* スマホとの接続:
  * 同じ Wi-Fi に繋いでいる場合は、コマンドパレットから `Antigravity Deck: Show Pairing QR` を実行し、Android アプリの QR ボタンから読み取ります
  * USB デバッグ接続を使う場合は PC 側で `adb reverse tcp:8765 tcp:8765` を実行し、Android アプリ上部の USB ボタンを押します

## コマンドパレット一覧

* `Antigravity Deck: Show Pairing QR`: ペアリング用の QR コードと接続情報を表示します
* `Antigravity Deck: Setup Workspace Hooks`: 開いているワークスペースへ `.agents/hooks.json` と `.agents/scripts/deck-hook.mjs` を作成またはマージします
* `Antigravity Deck: Check for Updates`: GitHub Releases 上の新しい `.vsix` が公開されているか確認します
* `Antigravity Deck: Simulate Agent Lifecycle`: 思考中から承認待ちと完了までの動きをテスト配信します
* `Antigravity Deck: Start Server`: WebSocket サーバーと Hook 受信サーバーを起動します
* `Antigravity Deck: Stop Server`: 稼働中のサーバーを停止します

## 設定項目

| 設定キー | 既定値 | 内容 |
| --- | --- | --- |
| `antigravityDeck.wsPort` | `8765` | Android アプリと通信する WebSocket サーバーのポート番号 |
| `antigravityDeck.hookPort` | `8766` | Lifecycle Hooks を受け取るローカル HTTP サーバーのポート番号 |
| `antigravityDeck.authToken` | `antigravity-local` | WebSocket 接続時に検証する簡易認証トークン |
| `antigravityDeck.waitForMobileApproval` | `true` | Android 接続中に `PreToolUse` でスマホからの承認応答を待つか |
| `antigravityDeck.autoStart` | `true` | IDE 起動時にサーバーを自動で立ち上げるか |
| `antigravityDeck.autoCheckUpdates` | `true` | 起動時に GitHub Releases の更新を自動確認するか |
| `antigravityDeck.autoSetupWorkspaceHooks` | `false` | ワークスペースを開いた際に `.agents/hooks.json` と `deck-hook.mjs` を自動配置するか |

## 関連リンク

* リポジトリとソースコード: https://github.com/Altairu/Antigravity-Deck
* Android アプリのダウンロード: https://github.com/Altairu/Antigravity-Deck/releases
* 不具合報告や要望: https://github.com/Altairu/Antigravity-Deck/issues

## ライセンス

MIT License で公開しています。詳細は [LICENSE](./LICENSE) を確認してください。
