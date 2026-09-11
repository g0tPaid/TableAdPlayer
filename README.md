# TableAdPlayer

Standalone Android digital-signage / table advertising player for ~8" portrait tablets (~800×1280). No Google Play Services. Offline-first after media is cached. Auto-starts on boot. Immersive fullscreen. Remote sync through a **configurable** REST origin (`BuildConfig.API_BASE_URL`).

This repository does not depend on any other product repo. A public site may be used only as a *behavior* reference; this project does not decompile APKs, copy proprietary assets, or reverse private APIs.

## Status

| Phase | State |
| --- | --- |
| 1 Project + Device Diagnostics + JSON export | **Complete** |
| 2 DEMO MODE local immersive playlist player | **Complete** |
| 3 Room + app-private media cache | **Complete** |
| 4 Offline scheduler | **Complete** |
| 5 Device registration + API abstraction | **Complete** |
| 6 SyncWorker downloads + pin-swap | **Complete** |
| 7 Reporting drain (play/skip/error/completed), never blocks playlist | **Complete** |
| 8 Boot FGS + Watchdog / CrashGuard, OEM docs | **Complete** |
| 9 Admin service menu (Device / Player / Sync / Diagnostics / Controls) | **Complete** |
| 10 Instrumented tests, mock contract tests, R8 | Leftover — see [DEVELOPMENT.md](DEVELOPMENT.md) |

`applicationId`: `com.tableadplayer.app`  
Debug APK: `app/build/outputs/apk/debug/app-debug.apk`  
Persistent device id: `TABLE-xxxxxxxx`

## Requirements

- JDK 17+ (JDK 21 works)
- Android SDK Platform **37** (`platforms;android-37.0`; symlink to `platforms/android-37` if the CLI names it `37.0`)
- Build-Tools 36+
- Android Studio or command-line Gradle 9.6 (wrapper included)

Set `ANDROID_HOME` (or create `local.properties` with `sdk.dir=`).

```bash
# Example SDK bootstrap (Linux)
./scripts/setup-android-sdk.sh
```

## Open in Android Studio

1. File → Open the repository root (the folder that contains `settings.gradle.kts`).
2. Let Gradle sync. If prompted for an SDK, point it at your Android SDK.
3. Run configuration: `app` (debug).

Debug builds install three launcher entries:

- **TableAdPlayer** — DEMO MODE player
- **TableAd Diagnostics** — device diagnostics
- **TableAd Debug** — jump menu (`adb shell am start -a com.tableadplayer.app.DEBUG -n com.tableadplayer.app.debug/com.tableadplayer.app.debug.DebugEntryActivity`)

Release has a single player launcher. Long-press the **top-left** corner of the player to open the admin service menu (sync now, downloads, export, restart, clear cache).

## Build

```bash
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

```bash
./gradlew testDebugUnitTest
```

Override the REST origin **without** hard-coding a production CDN:

```bash
./gradlew assembleDebug -PAPI_BASE_URL=https://your-api.example.com/
```

Or set `API_BASE_URL` in `gradle.properties`. Default is `https://api.example.invalid/`. Never commit `https://ad.cnszfyd.cn`.

## Install on a tablet

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.tableadplayer.app.debug/com.tableadplayer.app.ui.player.PlayerActivity
```

Portrait, sticky immersive, keep-screen-on. Sample playlist is in `app/src/main/assets/demo/` (images with durations, one video to end, one **intentionally missing** item to prove skip).

## Docs

- [ARCHITECTURE.md](ARCHITECTURE.md) — layers, offline-first, playback, sync, kiosk
- [DEVELOPMENT.md](DEVELOPMENT.md) — phases 1–10
- [API.md](API.md) — REST contract (your backend)
- [DEVICE_SETUP.md](DEVICE_SETUP.md)
- [KIOSK_SETUP.md](KIOSK_SETUP.md) — Lock Task / device-owner; not bypassed
- [TROUBLESHOOTING.md](TROUBLESHOOTING.md)
- [TESTING.md](TESTING.md)

Optional LAN mock: `python3 server/mock_api.py`
