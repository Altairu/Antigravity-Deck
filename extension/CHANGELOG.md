# Changelog

## 0.2.0

* Added saved conversation session list (`conversations`) and remote conversation switching (`select_conversation`) from the Android companion app
* Added Markdown artifact syncing (`artifacts`) and a dedicated Artifacts viewer tab on Android for inspecting `implementation_plan.md`, `task.md`, and `walkthrough.md`
* 保存された過去の会話セッション一覧の同期と Android アプリからの会話切り替え (`select_conversation`) に対応
* `implementation_plan.md` や `task.md` などの生成アーティファクトを同期し、Android アプリ上の成果物タブから Markdown 閲覧できる機能を追加

## 0.1.9

* Added conversation history timeline (`messages`) synced from Antigravity transcripts to the Android companion app
* Added History / Latest and Approval / History view switching on Android so you can inspect prior messages even while reviewing an approval request
* Antigravity のトランスクリプトから取得した会話履歴タイムライン (`messages`) の同期に対応し、Android アプリ上で過去のやり取りと承認プレビューを切り替えて閲覧できるようになりました

## 0.1.8

* Added `Antigravity Deck: Setup Workspace Hooks` command and pairing panel button to install `.agents/hooks.json` and `.agents/scripts/deck-hook.mjs` into any workspace with one click
* Added `antigravityDeck.autoSetupWorkspaceHooks` setting to optionally set up hooks automatically when opening a workspace
* 任意のワークスペースへワンクリックで `.agents/hooks.json` と `.agents/scripts/deck-hook.mjs` を導入できる `Antigravity Deck: Setup Workspace Hooks` コマンドとペアリング画面ボタンを追加
* ワークスペースを開いた際に Hooks を自動配置する `antigravityDeck.autoSetupWorkspaceHooks` 設定を追加

## 0.1.7

* Added `CHANGELOG.md` for the Open VSX Changes tab
* Open VSX の Changes タブ向けに変更履歴を追加

## 0.1.6

* Added bilingual documentation (`README.md` and `README.ja.md`)
* Simplified the Android companion UI and added Light / Dark / System theme support
* Added in-app language switching (System / 日本語 / English) and a toggle to stop or resume background monitoring
* 英語と日本語の README を整備
* Android アプリの UI をシンプル化し、ライト / ダーク / 自動のテーマ切り替え、アプリ内言語切り替え、バックグラウンド監視の停止と再開スイッチを追加

## 0.1.4

* Changed publisher namespace to `Altairu` (`Altairu.antigravity-deck`)
* Updated marketplace documentation and search keywords
* パブリッシャー名を `Altairu` に変更し、マーケットプレイス用の説明と検索キーワードを更新

## 0.1.3

* Added MIT License, extension icon, and automated Open VSX Registry publishing
* MIT ライセンス、拡張機能アイコン、Open VSX Registry への自動公開ワークフローを追加

## 0.1.1

* Added `Antigravity Deck: Check for Updates` command and startup update checks via GitHub Releases
* Added in-app APK updates and per-event notification toggles on Android
* GitHub Releases からの拡張機能アップデート確認機能と、Android アプリ内の APK 更新および通知詳細設定を追加

## 0.1.0

* Initial release with WebSocket state sync, `PreToolUse` approval holds, Markdown response viewer, Push-to-Talk voice input, and model / reasoning effort switching
* 初回リリース: WebSocket 状態同期、`PreToolUse` の承認待機、マークダウン返答表示、Push-to-Talk 音声入力、モデルと推論レベルの切り替えに対応
