English | [日本語](https://github.com/Altairu/Antigravity-Deck/blob/main/extension/README.ja.md)

# Antigravity Deck

A companion extension that lets you monitor and control Google Antigravity agents directly from your Android phone.

Even when you step away from your PC, you can approve or reject tool executions, send prompts, and read agent responses on your phone.

## Features

* Real-time Status Sync: Syncs idle, thinking, waiting approval, done, and error states to your phone in real time
* Mobile Approval Control: Preview pending commands and file diffs captured by `PreToolUse` hooks and approve or reject from the app or notification bar
* Chat & Markdown View: Send prompts via keyboard or voice input and read formatted Markdown responses from Antigravity
* Model & Reasoning Effort Switching: Change the active model and reasoning effort level remotely from your phone
* Notification & Language Preferences: Toggle notifications and vibration per event, and switch the Android app language between System, Japanese, and English
* Update Checks: Supports updates via Open VSX Registry as well as GitHub Releases update checks

## Quick Start

* PC Extension: Install `Altairu.antigravity-deck` (`Antigravity Deck`) from the Antigravity IDE Extensions panel
* Android App: Download the latest `antigravity-deck-*.apk` from [GitHub Releases](https://github.com/Altairu/Antigravity-Deck/releases) and install it on your phone
* Lifecycle Hooks: Run `Antigravity Deck: Setup Workspace Hooks` from the Command Palette or click the setup button in the Pairing view to place `.agents/hooks.json` and `.agents/scripts/deck-hook.mjs` into your workspace without overwriting existing hooks
* Connect Your Phone:
  * Over Wi-Fi: Run `Antigravity Deck: Show Pairing QR` from the Command Palette and scan it using the QR button in the Android app
  * Over USB: Run `adb reverse tcp:8765 tcp:8765` on your PC with USB debugging enabled, then tap the USB button in the Android app

## Commands

* `Antigravity Deck: Show Pairing QR`: Displays the pairing QR code and connection details
* `Antigravity Deck: Setup Workspace Hooks`: Creates or merges `.agents/hooks.json` and writes `.agents/scripts/deck-hook.mjs` into open workspace folders
* `Antigravity Deck: Check for Updates`: Checks GitHub Releases for a newer `.vsix` package
* `Antigravity Deck: Simulate Agent Lifecycle`: Broadcasts a simulated thinking, approval, and completion sequence
* `Antigravity Deck: Start Server`: Starts the WebSocket and hook HTTP servers
* `Antigravity Deck: Stop Server`: Stops the running servers

## Extension Settings

| Setting | Default | Description |
| --- | --- | --- |
| `antigravityDeck.wsPort` | `8765` | WebSocket server port for Android client connections |
| `antigravityDeck.hookPort` | `8766` | Local HTTP server port for Lifecycle Hooks |
| `antigravityDeck.authToken` | `antigravity-local` | Authentication token verified during WebSocket connection |
| `antigravityDeck.waitForMobileApproval` | `true` | Wait for mobile approval on `PreToolUse` when Android is connected |
| `antigravityDeck.autoStart` | `true` | Automatically start servers on IDE startup |
| `antigravityDeck.autoCheckUpdates` | `true` | Automatically check GitHub Releases for extension updates on startup |
| `antigravityDeck.autoSetupWorkspaceHooks` | `false` | Automatically configure `.agents/hooks.json` and `deck-hook.mjs` when opening a workspace |

## Links

* Repository: https://github.com/Altairu/Antigravity-Deck
* Android APK Downloads: https://github.com/Altairu/Antigravity-Deck/releases
* Issues & Feedback: https://github.com/Altairu/Antigravity-Deck/issues

## License

Released under the MIT License. See [LICENSE](./LICENSE) for details.
