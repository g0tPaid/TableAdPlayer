# Testing

CI / this repo has **no on-device farm**. Unit tests and the mock-API contract run on the JVM/Python. Instrumented tests compile (`assembleDebugAndroidTest`) and run only when you attach a tablet or emulator.

## Unit (Phase 1–10)

```bash
./gradlew assembleDebug testDebugUnitTest
```

Covers device-id formatting, playlist wrap/backoff, sync backoff, **SyncCoordinator download/pin/DEMO policy**, media states, atomic file writes, **MediaCache readiness / stale `*.part` / enqueue**, cache cleanup (never drops the active playlist), the offline scheduler, API origin/fixture registration, **DeviceRepository registration + utcNow**, device auth headers, pin-swap, free-space reserve, heartbeat outbox policy, reporting queue drain / failure isolation, watchdog restart policy, Lock Task exit gating, and **mock fixture / Retrofit contract** tests against `server/fixtures`.

## Mock API contract

Fixtures live in `server/fixtures/` (must stay in sync with `app/src/main/assets/fixtures/`). The mock process is `python3 server/mock_api.py`.

```bash
# JVM: kotlinx.serialization + Retrofit against the same JSON the mock serves
./gradlew testDebugUnitTest --tests com.tableadplayer.app.data.remote.MockApiFixtureContractTest --tests com.tableadplayer.app.data.remote.MockApiRetrofitContractTest

# Python: live HTTP against mock_api.Handler (ephemeral port)
python3 -m unittest discover -s server -p 'test_*.py'

# Shell smoke (starts mock, curls register/config/playlist/heartbeat/events)
./scripts/smoke-mock-api.sh
```

Point a **debug** APK at the mock (release forbids cleartext):

```bash
python3 server/mock_api.py
./gradlew assembleDebug -PAPI_BASE_URL=http://10.0.2.2:8787/   # emulator
# Physical tablet: use the PC's LAN IP, e.g. http://192.168.1.10:8787/
```

## Instrumented (needs a device)

Scaffolding compiles without an emulator:

```bash
./gradlew assembleDebugAndroidTest
# APK: app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

On a tablet or AVD:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew connectedDebugAndroidTest
```

Stubs:

| Class | What it checks |
| --- | --- |
| `DebugPackageInstrumentedTest` | debug `applicationId` |
| `PlayerActivityInstrumentedTest` | player (or CrashGuard diagnostics) starts |
| `AdminActivityInstrumentedTest` | admin service menu resumes |
| `TableAdDatabaseInstrumentedTest` | in-memory Room schema + active-playlist media ids |
| `PlayerScreenComposeTest` | idle chrome + DEMO badge |

There is **no** automated skip-through of the DEMO video on CI (no emulator in this environment). Do that manually below.

### 800×1280 emulator (optional)

```bash
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
sdkmanager --install "system-images;android-36;default;x86_64"
avdmanager create avd -n tablet8 --force \
  -k "system-images;android-36;default;x86_64" \
  -d "7in WSVGA (Tablet)"
emulator -avd tablet8 -skin 800x1280 -no-snapshot -no-audio
```

AOSP/`default` images are enough — the app does not need Play Services. Google APIs images also work.

## Manual — diagnostics (Phase 1)

1. Launch **TableAd Diagnostics** (debug) or Admin → Device diagnostics.
2. Confirm `TABLE-xxxxxxxx`, Android version, API, manufacturer, model, resolution, RAM, storage, ABI, battery, networks.
3. Export JSON; open the file and check it pretty-prints with `deviceId` and `capturedAt`.

## Manual — DEMO player (Phase 2)

1. Launch **TableAdPlayer** with **no network**. DEMO MODE must still loop.
2. Portrait only; system bars hidden; screen stays on.
3. Welcome image (~5 s) → sample video to the end → admin hint image → offline image → skip missing file → loop.
4. Long-press top-left → admin service menu → Device / Player / Sync / Diagnostics / Controls → back to player.

## Manual — cache (Phase 3)

1. After first launch, `files/media/` may be empty in DEMO MODE (assets are not copied). Room still has an active `demo-local` playlist so cleanup will not delete those media ids.
2. Kill the process while a hypothetical download would be writing `*.part`: on restart the part is discarded; only `READY` files are playable.
3. Confirm leftover `*.part` files disappear after `MediaCache.cleanup()` (app start or `SyncWorker`).

## Manual — scheduler (Phase 4)

Unit tests cover windows. On-device: an item with `endDate` in the past is skipped; a playlist with no schedule fields plays as today.

## Manual — registration + fixtures (Phase 5)

1. Default debug APK (placeholder `API_BASE_URL`): launch with **airplane mode**. Admin shows Device ID, **REGISTERED** (fixture path), Server URL `https://api.example.invalid/`. Diagnostics Registration section matches. DEMO player still loops.
2. `python3 server/mock_api.py` and rebuild with `-PAPI_BASE_URL=http://<lan>:8787/`. First launch goes `UNREGISTERED` → `REGISTERED` against the mock. Heartbeat POSTs appear in the mock log. Airplane mode: heartbeats stay queued; reconnect drains them.

## Manual — sync downloads (Phase 6)

1. Against the mock: after sync, `files/media/` contains `{id}_v{revision}` files; Room active playlist becomes `venue-lobby` only once **all** items verify. Kill the app mid-download: `*.part` is not playable; previous DEMO/cache keeps showing.
2. Fill the device until free space is under ~200 MB: nonessential downloads skip; DEMO/cached playback continues.

## Manual — reporting (Phase 7)

1. Airplane mode: let DEMO play through several items. Admin → Sync shows queued play/skip/completed (and errors for the missing demo file). Video must not hitch.
2. Reconnect (or tap **Sync now**): pending playback count drains; mock API logs `POST /v1/device/events`. A failing origin must leave the playlist looping.

## Manual — boot / watchdog (Phase 8)

1. Reboot: player should show cached or DEMO media without waiting for the network. Sync runs in the background (notification from the keep-alive service on OEMs that block activity starts).
2. CrashGuard: throw 3 times quickly; fourth launch is diagnostics. **Retry player** clears safe mode.
3. Confirm admin does **not** offer Exit Lock Task unless the DPC listed this package.

## Manual — admin (Phase 9)

1. 800×1280 portrait: fat buttons, Device / Player / Sync / Diagnostics / Controls.
2. Sync now, export logs/diagnostics, clear cache (DEMO/active pin still plays), reload playlist, restart player.

## Release / R8 (Phase 10)

```bash
./gradlew assembleRelease
# mapping: app/build/outputs/mapping/release/mapping.txt
adb install -r app/build/outputs/apk/release/app-release.apk
```

Default signing is the Android **debug** keystore (`~/.android/debug.keystore`). For a real key:

```bash
./gradlew assembleRelease \
  -PRELEASE_STORE_FILE=/path/to/upload.jks \
  -PRELEASE_STORE_PASSWORD=... \
  -PRELEASE_KEY_ALIAS=... \
  -PRELEASE_KEY_PASSWORD=...
```

Confirm DEMO MODE still loops on the release APK with airplane mode (placeholder origin, no production CDN).

There is no Play Services test lab dependency.
