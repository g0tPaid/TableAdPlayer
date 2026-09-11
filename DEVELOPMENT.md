# Development plan

Phased delivery. Complete each phase before depending on the next. This file is updated after each phase.

## Current

- **Phase 1 complete** — Gradle app, diagnostics, JSON export, docs, `assembleDebug`.
- **Phase 2 complete** — DEMO MODE immersive player, sample playlist, admin long-press stub.
- **Phase 3 complete** — Room schema + DAOs, `files/media/` cache, atomic ingest, cleanup that never drops the active playlist.
- **Phase 4 complete** — Offline `ScheduleEvaluator` (`startDate`/`endDate`/`startTime`/`endTime`/`daysOfWeek`); no schedule → play normally; unit tests.
- **Phase 5 complete** — Device registration + API abstraction (`Device`/`Content`/`Reporting`/`Sync` repositories), `X-Device-Id` auth (no Play Services), fixture + mock API, heartbeat outbox.
- **Phase 6 complete** — `SyncWorker` playlist fetch, `MEDIA_DOWNLOAD` → `MediaCache.ingest`, pin-swap only when every item is READY, free-space reserve, DEMO/cached playback when offline.
- **Phase 7 complete** — Playback events (play/skip/error/completed) persist in Room via a non-blocking `ReportingQueue`; drain to `POST /v1/device/events` with heartbeat-style backoff. Reporting failures never join the playlist loop. Offline outbox + drain when online (`ReportingWorker` / connectivity).
- **Phase 8 complete** — `BOOT_COMPLETED` starts cached/DEMO playback immediately; sync is background. `PlayerWatchdogService` (mediaPlayback FGS) for OEM boot reliability. `Watchdog` + `CrashGuard` stop rapid restart loops (3 in 2 minutes → diagnostics). OEM caveats in `KIOSK_SETUP.md`. No lock-screen bypass.
- **Phase 9 complete** — Hidden admin (long-press): Device / Player / Sync / Diagnostics / Controls — sync now, pending/failed downloads, export bundle, restart player/app, clear cache (keeps active playlist), reload playlist, brightness, Exit Lock Task only if DPC permits. Large targets, dark UI for 800×1280 portrait.
- **Phase 10 leftover** — instrumented player skip tests, mock API contract tests, R8 keep rules for release.

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

Hilt is still deferred. Room uses KSP; `AppContainer` + `Application` hold the database, `MediaCache`, repositories, and Retrofit/fixture API.

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

## Phase 4 — Scheduler (done)

- Evaluate `startDate` / `endDate` / `startTime` / `endTime` / `daysOfWeek` offline
- Null/empty schedule → play normally
- Empty/out-of-window → idle + poll (`ScheduleEvaluator.POLL_MS`), not a deadlock
- Unit tests in `app/src/test/.../scheduler/`

## Phase 5 — API + registration (done)

- `TableAdApi`: register, config, playlist, heartbeat, events
- Repositories: `DeviceRepository`, `ContentRepository`, `ReportingRepository`, `SyncRepository`
- Device auth header `X-Device-Id` / `X-Device-Token` (no Play Services)
- First launch `UNREGISTERED` → `REGISTERED`; Device ID, status, Server URL on admin + diagnostics
- Heartbeat payload + Room outbox when offline (`Backoff` retries)
- Placeholder `API_BASE_URL` → `FixtureTableAdApi` (`assets/fixtures`); DEMO MODE needs no server
- `server/mock_api.py` serves register/config/playlist/heartbeat/media
- Documented in `API.md`

## Phase 6 — Sync + atomic downloads (done)

- `SyncWorker` periodic (15 min) + one-shot on process start / boot
- Fetch playlist, enqueue `MEDIA_DOWNLOAD`, `MediaCache.ingest`, pin-swap only when every required item is READY at `{id}_v{version}`
- Free-space reserve (default 200 MB): stop **nonessential** downloads below threshold; essential continue until a critical ~8 MB floor
- Exponential backoff on failed jobs (`Backoff`) and WorkManager retries
- Offline: never blank the screen — keep playing cached files or DEMO assets
- Do not play a remote item until `MediaCache.isReady` (state READY + matching version file)

## Phase 7 — Reporting queue (done)

- `ReportingQueue.offer` is non-blocking (drop-oldest at 512); playlist loop never `join()`s drain
- `ReportingPump` persists to Room `playback_events`; types `play` / `skip` / `error` / `completed` (heartbeats stay a separate drain)
- `ReportingRepository.drainPlaybackEvents` → `POST /v1/device/events`; API failures schedule `Backoff` retries and **do not throw** to callers
- Connectivity + `ReportingWorker` drain when online; `SyncCoordinator` also drains (isolated with `runCatching`)
- Unit tests: queue capacity, drain success/failure isolation, poison heartbeat rows, emitIsolated

## Phase 8 — Boot / kiosk (done)

- `BootCompletedReceiver` (`BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `USER_UNLOCKED`): start cached/DEMO player immediately, `SyncScheduler.enqueueNow` in the background
- `PlayerWatchdogService` — `FOREGROUND_SERVICE_MEDIA_PLAYBACK` for OEM boot reliability (not a Keyguard bypass)
- Room/API deferred until user unlock (credential-encrypted storage)
- `WatchdogPolicy`: 3 watchdog restarts in 2 minutes → safe mode; 5 s debounce for sticky redelivery
- Device-owner + Lock Task remains documented (`KIOSK_SETUP.md`); no reflection / accessibility overlays

## Phase 9 — Admin menu (done)

- Long-press top-left still opens admin (no PIN required)
- Sections: Device, Player, Sync, Diagnostics, Controls
- Actions: sync now, pending/failed downloads, export service bundle, restart player, restart app, clear cache (active playlist protected), reload playlist, brightness, Exit Lock Task **only** if `isInLockTaskMode` && DPC `isLockTaskPermitted`
- Touch targets ≥ 64 dp; dark kiosk theme; 800×1280 portrait

## Phase 10 — Tests + mock server polish (leftover)

- Instrumented player skip tests
- Mock API contract tests
- Enable R8 for release with serialization keep rules

## Local commands

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lint
```
