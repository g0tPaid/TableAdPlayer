# Troubleshooting

## Gradle cannot find the SDK

Create `local.properties` (gitignored):

```
sdk.dir=/home/you/Android/Sdk
```

or `export ANDROID_HOME=...`. Run `./scripts/setup-android-sdk.sh` on Linux if you do not have Android Studio.

## `assembleDebug` fails on plugin versions

Pins live in `gradle/libs.versions.toml`. AGP 9.4.0 + Gradle 9.6.0 + Kotlin 2.2.10 is the supported set. AGP 9 ships Kotlin — do not also apply `org.jetbrains.kotlin.android` or you will get a duplicate `kotlin` extension.

## App starts diagnostics instead of the player

Safe mode after repeated crashes. Open diagnostics, confirm storage/media, tap **Retry player** (clears `CrashGuard`).

## Player is a black screen

- DEMO assets missing from the APK (`assets/demo/`)
- ExoPlayer codec missing for a future remote file — engine should skip; file a bug if it hangs > 15 minutes (video cap)
- Safe mode (above)
- Cached remote item is not `READY` (Phase 6): engine must skip, not freeze; `PlaylistResolver` falls back to DEMO assets if nothing cached is playable

## Cache grew or leftover `*.part` files

Media lives under `files/media/`. Incomplete downloads use `*.part` and are deleted on app start / `SyncWorker` unless they belong to a DOWNLOADING row on the **active** playlist. Cleanup never removes media required by that playlist. Pull with:

```bash
adb shell run-as com.tableadplayer.app.debug ls files/media
```

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
