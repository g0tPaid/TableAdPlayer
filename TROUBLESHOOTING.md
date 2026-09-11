# Troubleshooting

## Gradle cannot find the SDK

Create `local.properties` (gitignored):

```
sdk.dir=/home/you/Android/Sdk
```

or `export ANDROID_HOME=...`. Run `./scripts/setup-android-sdk.sh` on Linux if you do not have Android Studio.

## `assembleDebug` fails on plugin versions

Pins live in `gradle/libs.versions.toml`. AGP 8.13.2 + Gradle 8.13 + Kotlin 2.1.20 is the supported set. AGP 9.x needs a newer Gradle and is intentionally not used yet (see DEVELOPMENT.md).

## App starts diagnostics instead of the player

Safe mode after repeated crashes. Open diagnostics, confirm storage/media, tap **Retry player** (clears `CrashGuard`).

## Player is a black screen

- DEMO assets missing from the APK (`assets/demo/`)
- ExoPlayer codec missing for a future remote file — engine should skip; file a bug if it hangs > 15 minutes (video cap)
- Safe mode (above)

## Wi‑Fi SSID is blank

Expected without location permission on modern Android. Connectivity (`wifi` true/false) and interface addresses are still reported.

## Ethernet not shown

`NetworkCapabilities.TRANSPORT_ETHERNET` is only true when the active network is Ethernet. USB-C dongles vary by OEM.

## Boot does not resume playback

See `KIOSK_SETUP.md`. This is an OEM/DPC limitation, not a missing `<receiver>`.

## Cleartext HTTP to a LAN mock fails on release builds

Release `network_security_config` forbids cleartext. Use HTTPS or a debug APK.

## Export JSON does nothing

The share sheet may have no targets on a locked-down kiosk. The file is still under `files/exports/`. Pull with:

```bash
adb shell run-as com.tableadplayer.app.debug ls files/exports
```
