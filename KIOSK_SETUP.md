# Kiosk setup

TableAdPlayer is designed to **run inside** Android’s supported kiosk modes. It does not hide the lock screen by exploiting APIs, does not disable Device Policy Controller from an unmanaged app, and does not ship companion “unlock” binaries.

## Supported paths

### A. Device owner + Lock Task (recommended)

Provision the tablet with a Device Policy Controller that sets this app as a Lock Task package (`DevicePolicyManager.setLockTaskPackages`). Typical tools: Android Enterprise (QR / NFC / `adb shell dpm set-device-owner` on an *unenrolled* device during setup).

Effects:

- Back / Home / Recents can be fully suppressed
- Boot can resume the pinned task
- Exit is a DPC policy decision, not a secret gesture in this APK

The admin shell **must not** call `stopLockTask()` unless the DPC allows it. Phase 9 will gate that behind device-owner checks.

### B. Screen pinning (manual)

User-facing pinning from System UI. Weaker (the user can unpin with a button combo). Acceptable for demos only.

### C. Default home / autostart

`BootCompletedReceiver` starts `PlayerActivity`. On Android 10+ many OEMs block background activity starts from `BOOT_COMPLETED`. If the player does not appear after reboot:

1. Prefer device-owner (A)
2. Or a `FOREGROUND_SERVICE` started from the receiver that then launches the activity when the user unlocks — still not a lock-screen bypass
3. Make TableAdPlayer the default Home app via DPC `addPersistentPreferredActivity`

## What we will not do

- Accessibility-service overlays to eat navigation
- Reflection against `WindowManager` to dismiss Keyguard
- Root scripts
- Bundling a third-party “kiosk launcher” APK

## Permissions already declared

`RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`. Battery optimization ignore is **not** auto-requested; operators may whitelist the app in system settings.

## Crash loops

Three uncaught exceptions in two minutes → safe mode (diagnostics). That is intentional so a bad playlist cannot brick a table overnight.
