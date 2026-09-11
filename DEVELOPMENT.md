# Development plan

Phased delivery. Complete each phase before depending on the next. This file is updated after each phase.

## Current

- **Phase 1 complete** — Gradle app, diagnostics, JSON export, docs, `assembleDebug`.
- **Phase 2 complete** — DEMO MODE immersive player, sample playlist, admin long-press stub.
- **Phases 3–10** — interfaces, fixtures, receivers, and docs are scaffolded; not production-complete.

## Tooling

| Item | Pin |
| --- | --- |
| AGP | 9.4.0 (current stable; built-in Kotlin — do not also apply `org.jetbrains.kotlin.android`) |
| Gradle | 9.6.0 (wrapper) |
| Kotlin | 2.2.10 (Compose + serialization plugins) |
| Compose BOM | 2026.08.00 |
| Media3 | 1.8.0 |
| Retrofit / OkHttp | 2.11.0 / 4.12.0 |
| minSdk / compileSdk / targetSdk | 24 / 37 / 36 |

Hilt is deferred until Room/WorkManager injection pays for the KSP surface. Manual constructors + Application are enough through Phase 2.

## Phase 1 — Project + diagnostics (done)

- Application module `com.tableadplayer.app`
- Device Diagnostics: OS, API, manufacturer, model, device name, resolution, density, RAM, storage, ABI(s), battery/charging, Wi‑Fi, Ethernet, Android ID, app version, `TABLE-xxxxxxxx`
- Export JSON via app files + SAF `CreateDocument` + share sheet
- Debug launcher entries
- `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`

## Phase 2 — DEMO MODE player (done)

- Portrait, sticky immersive, keep-screen-on
- Asset playlist, sequential loop
- Image durations, video to end, skip bad media
- Long-press top-left → admin stub (diagnostics, device id, API URL)

## Phase 3 — Room + disk cache

- `@Database` for `CachedMediaRow`, `PlaylistPinRow`, `OutboxEventRow`
- Cache directory under `files/media/` with size accounting
- Do not play a remote item until `complete == true`

## Phase 4 — Scheduler

- Evaluate `startAt`/`endAt` offline
- Empty/out-of-window → branded idle slide, not a deadlock

## Phase 5 — API + fixtures

- Finish `TableAdApi` against a real backend
- Keep `server/mock_api.py` and `assets/fixtures` in sync
- Auth header if the operator requires it (not Play Services)

## Phase 6 — Sync + atomic downloads

- `SyncWorker` periodic + on-demand
- Atomic `*.part` + sha256 + pin swap
- Free-space reserve; exponential backoff (`Backoff`)

## Phase 7 — Reporting queue

- Persist outbox in Room
- Heartbeat + playback events
- Drop-oldest when full; **never** join() from the playlist loop

## Phase 8 — Boot / kiosk

- Document device-owner + Lock Task (`KIOSK_SETUP.md`)
- Foreground service if OEM blocks boot activities
- Do not use reflection/hacks to dismiss the lock screen

## Phase 9 — Admin menu

- PIN or hardware long-press already in place — add: sync now, brightness, playlist pin, safe reboot, exit Lock Task **only** if device-owner APIs allow it

## Phase 10 — Tests + mock server polish

- Instrumented player skip tests
- Mock API contract tests
- Enable R8 for release with serialization keep rules

## Local commands

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lint
```
