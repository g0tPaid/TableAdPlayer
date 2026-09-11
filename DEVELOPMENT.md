# Development plan

Phased delivery. Complete each phase before depending on the next. This file is updated after each phase.

## Current

- **Phase 1 complete** — Gradle app, diagnostics, JSON export, docs, `assembleDebug`.
- **Phase 2 complete** — DEMO MODE immersive player, sample playlist, admin long-press stub.
- **Phase 3 complete** — Room schema + DAOs, `files/media/` cache, atomic ingest, cleanup that never drops the active playlist.
- **Phase 4 complete** — Offline `ScheduleEvaluator` (`startDate`/`endDate`/`startTime`/`endTime`/`daysOfWeek`); no schedule → play normally; unit tests.
- **Phases 5–10** — live API, SyncWorker downloads, reporting drain, kiosk hardening, admin, instrumented tests.

## Tooling

| Item | Pin |
| --- | --- |
| AGP | 9.4.0 (current stable; built-in Kotlin — do not also apply `org.jetbrains.kotlin.android`) |
| Gradle | 9.6.0 (wrapper) |
| Kotlin | 2.2.10 (Compose + serialization plugins) |
| KSP | 2.3.6 (Room compiler; required for AGP 9 built-in Kotlin) |
| Compose BOM | 2026.08.00 |
| Media3 | 1.8.0 |
| Room | 2.7.2 |
| Retrofit / OkHttp | 2.11.0 / 4.12.0 |
| minSdk / compileSdk / targetSdk | 24 / 37 / 36 |

Hilt is still deferred. Room uses KSP; `AppContainer` + `Application` hold the database and `MediaCache`.

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

## Phase 3 — Room + disk cache (done)

- `@Database` version 1 for `Device`, `Media`, `Playlist`, `PlaylistItem`, `Schedule`, `PlaybackEvent`, `SyncJob`, `AppConfig`
- Schema exported under `app/schemas/`; `ALL_MIGRATIONS` is the only upgrade path (no destructive fallback)
- `MediaState`: REMOTE, DOWNLOADING, READY, FAILED, EXPIRED, DELETED
- Cache directory `files/media/` with checksum, file size, downloadedAt, lastAccessedAt, version
- `MediaCache.ingest` wires `AtomicFileStore` (`*.part` → verify → rename)
- Cleanup prefers deleting `*.part`, then unused complete files; **never** deletes media required by the active playlist
- DEMO MODE still plays `assets/demo/` offline; seeder pins that playlist in Room so cleanup has a pin
- `SyncWorker` stays network-stubbed; it only runs cache cleanup

## Phase 4 — Scheduler (done)

- Evaluate `startDate` / `endDate` / `startTime` / `endTime` / `daysOfWeek` offline
- Null/empty schedule → play normally
- Empty/out-of-window → idle + poll (`ScheduleEvaluator.POLL_MS`), not a deadlock
- Unit tests in `app/src/test/.../scheduler/`

## Phase 5 — API + fixtures

- Finish `TableAdApi` against a real backend
- Keep `server/mock_api.py` and `assets/fixtures` in sync
- Auth header if the operator requires it (not Play Services)

## Phase 6 — Sync + atomic downloads

- `SyncWorker` periodic + on-demand
- Drain `sync_jobs` (`MEDIA_DOWNLOAD`): HTTP GET → `MediaCache.ingest` → pin swap when every item is READY
- Free-space reserve; exponential backoff (`Backoff`)
- Do not play a remote item until `state == READY`

## Phase 7 — Reporting queue

- Persist outbox in Room (`playback_events` table already exists)
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
