# Antigravity Deck 拡張機能

Google Antigravity 上の単一エージェント状態を `state_sync` メッセージとして Android スマートフォンへリアルタイムに同期し、リモートから承認操作や音声プロンプト送信およびモデル切り替えを受け付ける VS Code 互換拡張機能です。

## 提供機能

* WebSocket サーバー: 既定ポート `8765` で Android クライアントからの接続を受け付け、状態同期と操作コマンドの双方向通信を行います
* ローカル Hook HTTP サーバー: 既定ポート `8766` の `127.0.0.1` で待機し、`POST /hook` による Lifecycle Hooks 受信、`POST /simulate` によるライフサイクル擬似実行、`GET /status` による状態取得を提供します
* QR ペアリング表示: ローカル IP アドレスとポート番号および認証トークンを埋め込んだ QR コードを Webview パネルに表示します
* 内部コマンド連携マッピング: Antigravity のコマンド ID や Hook イベント変換を `src/config/antigravity-bindings.ts` に集約しています

## コマンドパレット一覧

* `Antigravity Deck: Show Pairing QR` : LAN 接続用のペアリング QR コードパネルを開きます
* `Antigravity Deck: Simulate Agent Lifecycle` : 思考中から承認待ちおよび完了までの状態遷移を擬似配信します
* `Antigravity Deck: Start Server` : WebSocket サーバーと Hook HTTP サーバーを手動起動します
* `Antigravity Deck: Stop Server` : WebSocket サーバーと Hook HTTP サーバーを停止します

## 設定項目

* `antigravityDeck.wsPort` : Android アプリと通信する WebSocket サーバーのポート番号です。既定値は `8765` です
* `antigravityDeck.hookPort` : Antigravity Lifecycle Hooks を受信するローカル HTTP サーバーのポート番号です。既定値は `8766` です
* `antigravityDeck.authToken` : WebSocket 接続時に検証する簡易認証トークン文字列です。既定値は `antigravity-local` です
* `antigravityDeck.waitForMobileApproval` : Android クライアント接続中に `PreToolUse` フックでスマホ側の承認応答を待機するかどうかを制御します。既定値は `true` です
* `antigravityDeck.autoStart` : 拡張機能の起動時に WebSocket サーバーと HTTP サーバーを自動起動するかどうかを制御します。既定値は `true` です

## ビルドとテスト

```powershell
npm install
npm run compile
node ./test/integration-test.mjs
npm run package
```
