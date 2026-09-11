# Device setup

Target: ~8" portrait Android tablet, ~800×1280, no Google Play Services required.

## 1. Install

Debug:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.tableadplayer.app.debug/com.tableadplayer.app.ui.player.PlayerActivity
```

Release (R8, still DEMO MODE, debug-keystore unless you pass `RELEASE_STORE_FILE`):

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -n com.tableadplayer.app/com.tableadplayer.app.ui.player.PlayerActivity
```

Debug `applicationId` is `com.tableadplayer.app.debug`. Release is `com.tableadplayer.app`.

## 2. First launch

The player starts in DEMO MODE (bundled assets). Confirm:

- Fullscreen, no system bars (swipe edge may peek them transiently)
- Screen stays on
- Images change on a timer; the sample video plays to the end
- A missing slide is skipped (no freeze)
- Long-press **top-left** opens Admin (Device / Player / Sync / Diagnostics / Controls)

## 3. Diagnostics export

On the diagnostics screen tap **Export Diagnostics JSON**. The file is written under the app `files/exports/` directory and offered via the system document picker / share sheet. Send it to ops for inventory.

Fields include Android version, API, manufacturer, model, device name, resolution, density, RAM, storage total/free, CPU ABI(s), battery, charging, Wi‑Fi, Ethernet, Android ID, app version, and `TABLE-xxxxxxxx`.

SSID may be unavailable without a location permission; this app does not request location.

## 4. API origin

Rebuild with `-PAPI_BASE_URL=...` or set `API_BASE_URL` in `gradle.properties`. Confirm the value on diagnostics (`API base URL`). Never bake a third-party CDN into source.

## 5. Stay awake / unattended

- Keep the tablet plugged in (diagnostics shows charging).
- Disable system screen timeout or use kiosk provisioning (`KIOSK_SETUP.md`).
- The player sets `FLAG_KEEP_SCREEN_ON` while in the foreground.

## 6. Boot

`RECEIVE_BOOT_COMPLETED` is registered. The player starts from the **cached or DEMO** playlist immediately; sync is background. Whether the activity actually appears after reboot depends on the OEM — `PlayerWatchdogService` is the foreground keep-alive. Device-owner Lock Task is the supported 24/7 path (`KIOSK_SETUP.md`).
