# Shelf Sweeper

A thrift-store and garage-sale scanner for Android. Point the camera at a shelf and hold still: Claude identifies each item, pins a lot tag with its eBay sold price on the live picture, and alerts you when something is over your price. Everything you scan is kept under Shelves, and the Receipt adds up your cart, your net after fees, and what you left behind.

## Install on your phone

1. Open the **Releases** page of this repository on your phone and download `ShelfSweeper.apk` from the newest build.
2. Open the downloaded file. If Android asks, allow installs from your browser ("Install unknown apps").
3. Open Shelf Sweeper, allow the camera, then tap **Settings** and paste your Claude API key from console.anthropic.com.

New builds install over the old one and keep your data.

## How it's built

A GitHub Action builds the app on every push to `main` and publishes the APK as a release.
Kotlin, Jetpack Compose and CameraX; item recognition and pricing via the Claude API (`claude-haiku-5-5` for live scans, `claude-sonnet-5-5` for careful mode and close-ups).
Your API key is stored only on the phone.
