# Antigravity Deck 拡張機能

Google Antigravity上のエージェント状態をAndroidスマートフォンへリアルタイムに同期し、リモートから承認や音声プロンプト送信を行うためのVS Code互換拡張機能です。

## 主なコマンド

* Antigravity Deck: Show Pairing QR
* Antigravity Deck: Simulate Agent Lifecycle
* Antigravity Deck: Start Server
* Antigravity Deck: Stop Server

## 設定項目

* `antigravityDeck.wsPort` : WebSocket待ち受けポート番号です。既定値は `8765` です。
* `antigravityDeck.hookPort` : Lifecycle Hooks受信HTTPポート番号です。既定値は `8766` です。
* `antigravityDeck.authToken` : WebSocket接続時のトークン文字列です。既定値は `antigravity-local` です。
* `antigravityDeck.waitForMobileApproval` : スマホ接続時に `PreToolUse` の承認待機を行う設定です。
