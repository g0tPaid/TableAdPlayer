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

See `KIOSK_SETUP.md`. This is an OEM/DPC limitation, not a missing `<receiver>`. Check:

1. The persistent “Playing cached playlist…” notification — tap it
2. OEM autostart / battery restrictions (Xiaomi, Huawei, Oppo, Vivo, Samsung)
3. Safe mode after a crash loop — Diagnostics → Retry player

Reporting failures (airplane mode, dead API) must never pause the playlist. If video hitching appeared while events were queued, file a bug against `ReportingQueue` / `PlaylistEngine` isolation.

## Cleartext HTTP to a LAN mock fails on release builds

Release `network_security_config` forbids cleartext. Use HTTPS or a debug APK.

## Export JSON does nothing

The share sheet may have no targets on a locked-down kiosk. The file is still under `files/exports/`. Pull with:

```bash
adb shell run-as com.tableadplayer.app.debug ls files/exports
```

## Release APK will not install (`INSTALL_PARSE_FAILED_NO_CERTIFICATES`)

`assembleRelease` signs with `~/.android/debug.keystore` (password `android`, alias `androiddebugkey`) unless you pass `RELEASE_STORE_FILE`. Generate a debug keystore by building a debug APK once, or:

```bash
keytool -genkeypair -v -keystore ~/.android/debug.keystore -storepass android \
  -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 \
  -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
```

This is **not** a Play Store key. Mapping file after R8: `app/build/outputs/mapping/release/mapping.txt`.

## `assembleRelease` / R8 fails with missing classes

Keep rules live in `app/proguard-rules.pro` (Retrofit, Room, kotlinx.serialization, Media3, WorkManager). If a new `@Serializable` DTO is added under another package, extend the keep glob or keep that class.

## ExoPlayer / memory (remaining risks)

`PlaylistEngine.stop()` and `PlayerViewModel.onCleared()` release ExoPlayer on the main thread (idempotent `AtomicReference`). `PlayerView` unbinds in Compose `onRelease`. Remaining risks that are **not** fully eliminated in this process:

- OEM codec / `MediaCodec` native leaks after a skip storm (engine still advances; watch `adb shell dumpsys meminfo`)
- WorkManager + OkHttp dispatcher threads live for the process lifetime (expected)
- `PlayerWatchdogService` FGS until the process dies — tap the notification or force-stop if you need a clean shutdown
- Compose `remember` of decoded image bitmaps holds RAM for the current slide only
- No LeakCanary in CI; run it locally on a debug APK if you suspect an Activity leak after long-press admin → back

Reporting / sync failures must not retain ExoPlayer. If a video keeps playing after Admin → Restart player, file a bug against `PlaylistEngine.releasePlayer`.

## Instrumented tests skip in this repo's CI

There is no emulator or tablet attached in GitHub Actions / Cloud Agent CI. `connectedDebugAndroidTest` is a local command. `assembleDebugAndroidTest` only proves the test APK compiles.
