# Kiosk setup

TableAdPlayer is designed to **run inside** Android’s supported kiosk modes. It does not hide the lock screen by exploiting APIs, does not disable Device Policy Controller from an unmanaged app, and does not ship companion “unlock” binaries.

## Supported paths

### A. Device owner + Lock Task (recommended)

Provision the tablet with a Device Policy Controller that sets this app as a Lock Task package (`DevicePolicyManager.setLockTaskPackages`). Typical tools: Android Enterprise (QR / NFC / `adb shell dpm set-device-owner` on an *unenrolled* device during setup).

Effects:

- Back / Home / Recents can be fully suppressed
- Boot can resume the pinned task
- Exit is a DPC policy decision, not a secret gesture in this APK

The admin shell **must not** call `stopLockTask()` unless the DPC allows it. Admin shows **Exit Lock Task** only when the activity is in Lock Task **and** `DevicePolicyManager.isLockTaskPermitted(package)` is true.

### B. Screen pinning (manual)

User-facing pinning from System UI. Weaker (the user can unpin with a button combo). Acceptable for demos only. The admin Exit Lock Task control stays hidden in this mode.

### C. Default home / autostart

`BootCompletedReceiver` starts `PlayerWatchdogService` (foreground, `mediaPlayback`) and tries `PlayerActivity`. The player loads the **cached** (or DEMO) playlist immediately; `SyncWorker` runs in the background and must not delay first paint.

On Android 10+ many OEMs block background activity starts from `BOOT_COMPLETED`. If the player does not appear after reboot:

1. Prefer device-owner (A)
2. Confirm the persistent notification from `PlayerWatchdogService` — tap it to open the player when the OEM blocked `startActivity`
3. Make TableAdPlayer the default Home app via DPC `addPersistentPreferredActivity`
4. Allow autostart / “run in background” in the OEM battery menu (see caveats below)

Locked-boot (`LOCKED_BOOT_COMPLETED`): Room and Retrofit need credential-encrypted storage. The process may start the FGS, but playlist/API init waits until `USER_UNLOCKED` / `BOOT_COMPLETED`. Direct boot does **not** dismiss Keyguard.

## OEM caveats (not bugs in this APK)

| OEM / skin | Typical extra step |
| --- | --- |
| Xiaomi / HyperOS / MIUI | Enable Autostart; set Battery saver → No restrictions; lock the app in Recents |
| Huawei / Harmony / EMUI | App launch → manage manually; ignore battery optimizations |
| Oppo / Realme / ColorOS / Vivo / Funtouch | Autostart + background activity + “high background power consumption” |
| Samsung One UI | Sleeping apps off; allow background activity |
| Android 10+ AOSP | Background activity starts from receivers are restricted unless the app is the default Home, a DPC lock-task package, or the user taps the FGS notification |
| Android 12+ | Exact alarms / FGS types: this app uses `FOREGROUND_SERVICE_MEDIA_PLAYBACK` only as a boot keep-alive, not to bypass the lock screen |

Battery optimization ignore is **not** auto-requested; operators may whitelist the app in system settings.

## Watchdog / crash loops

- `CrashGuard`: 3 uncaught exceptions in 2 minutes → safe mode (diagnostics instead of ExoPlayer)
- `Watchdog`: 3 boot/service player starts in 2 minutes (debounced 5 s so sticky `onStartCommand` is not a loop) → same safe mode
- Successful playback resets the watchdog counter
- Diagnostics **Retry player** clears both

That is intentional so a bad playlist or OEM kill-restart cycle cannot brick a table overnight.

## What we will not do

- Accessibility-service overlays to eat navigation
- Reflection against `WindowManager` to dismiss Keyguard
- Root scripts
- Bundling a third-party “kiosk launcher” APK
- Using the foreground service as a lock-screen bypass

## Permissions already declared

`RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `POST_NOTIFICATIONS`.
