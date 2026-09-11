# Architecture

TableAdPlayer is a kiosk-style advertising client. The player process must keep showing media even when the network, disk, or remote API is unhealthy.

## Principles

1. **Offline-first.** After a playlist revision is fully cached, playback does not need the network. Scheduling, heartbeats, and event upload degrade independently. DEMO MODE plays bundled assets with no server.
2. **Atomic downloads.** Bytes land in `*.part`, are fsynced, checksummed, then renamed. The playlist pin swaps only when every required item is complete. See `AtomicFileStore` / `MediaFileStore`.
3. **The playlist engine never freezes.** Bad or missing media is skipped. If every item fails, the engine backs off (`PlaylistAdvance.ALL_FAILED_BACKOFF_MS`) instead of spinning. Images use a duration timer; videos advance on `STATE_ENDED` or error, with a max cap.
4. **Scheduling is local.** `ScheduleEvaluator` applies `startDate` / `endDate` / `startTime` / `endTime` / `daysOfWeek` on-device. A null or empty window plays normally. If every item is out of window, the engine shows idle and polls — it does not deadlock.
5. **Heartbeats and events are queued.** Heartbeats persist in Room `playback_events` (`type=heartbeat`). Playback events (`play` / `skip` / `error` / `completed`) go through an in-memory `ReportingQueue` (drop-oldest, never blocking) then the same Room outbox. `ReportingPump` / `ReportingWorker` / `SyncCoordinator` drain to the API. Reporting **never** blocks `PlaylistEngine`.
6. **Storage safeguards.** Cleanup deletes leftover `*.part` files first, then unused complete media. It **never** deletes media required by the active playlist (any `MediaState`). A 200 MB free-space reserve stops nonessential downloads; essential pending-playlist items continue until a critical ~8 MB floor.
7. **Exponential backoff.** `Backoff.delayMs` for sync and heartbeat. WorkManager retry for `SyncWorker`.
8. **Crash recovery without rapid loops.** `CrashGuard` counts uncaught exceptions in a 2-minute window. After 3 crashes, the next launch opens diagnostics (safe mode) instead of ExoPlayer.
9. **Lock Task / device-owner is documented, not bypassed.** `BootCompletedReceiver` may try to start the player; OEM background-activity limits are real. Kiosk lockdown is a provisioning step (`KIOSK_SETUP.md`), not an app exploit.

## Module map (single Gradle app)

```
com.tableadplayer.app
  ui/            Compose screens (player, diagnostics, admin)
  playback/      Playlist engine + demo loader (Media3); file or asset sources
  scheduler/     Offline schedule windows (no network)
  core/          Device id, diagnostics, crash guard, immersive helpers
  data/remote    Retrofit + FixtureTableAdApi + device auth interceptor
                 (BuildConfig.API_BASE_URL; placeholder host is never contacted)
  data/repo      Device / Content / Reporting / Sync repositories
  data/local     Room (Device, Media, Playlist, PlaylistItem, Schedule,
                 PlaybackEvent, SyncJob, AppConfig) — migrations, no destructive fallback
  data/cache     AtomicFileStore + MediaFileStore + MediaCache + cleanup policy
  data/seed      DEMO playlist pin into Room (assets, not a CDN)
  sync/          WorkManager SyncWorker + SyncCoordinator (downloads, pin-swap)
  reporting/     ReportingQueue + pump + PlaybackEventDrain; Room outbox
  kiosk/         Boot receiver, PlayerWatchdogService, connectivity, Lock Task gate
```

UI talks to ViewModels. ViewModels own `PlaylistEngine`. Network I/O is confined to repositories / `SyncCoordinator`. Playback holds no Retrofit types.

## Device identity

A stable id `TABLE-` + 8 lowercase hex chars is stored in DataStore. The first value is derived from `ANDROID_ID` (SHA-256 prefix) so reinstalls on the same device usually keep the same id. Android ID is also exposed on the diagnostics screen as a separate field (kiosk inventory, not ad tracking). The same id is upserted into Room `devices` when DEMO seeds.

## Remote API

`BuildConfig.API_BASE_URL` is injected at build time from the `API_BASE_URL` Gradle property. The default is an invalid placeholder. **Do not** hard-code a production advertising host in source.

`ApiOrigin.isPlaceholder` selects `FixtureTableAdApi` (assets) vs Retrofit. Live requests add `X-Device-Id` and optional `X-Device-Token` (no Play Services). First launch stores `UNREGISTERED` until `POST /v1/device/register` succeeds.

Interfaces live in `TableAdApi`. JSON fixtures: `app/src/main/assets/fixtures/` and `server/fixtures/`.

## Playback (Phase 2)

DEMO MODE reads `assets/demo/playlist.json`. Sequential loop:

| Kind | Advance when |
| --- | --- |
| Image | `durationMs` elapses (min 500 ms) |
| Video | ExoPlayer `STATE_ENDED` |
| Missing / unreadable / player error | Skip immediately |
| Outside schedule window | Skip (not a media failure); idle + poll if none playable |
| All items failed | Idle + 5 s backoff, then retry |

Portrait + sticky immersive + `FLAG_KEEP_SCREEN_ON`. Long-press a 96 dp hit target in the **top-left** to open the admin stub.

`PlaylistItem.source` is `MediaSource.Asset` (demo) or `MediaSource.CachedFile` (Room-ready file under `files/media/`). Demo does not require a cached copy.

## Room + disk cache (Phase 3)

- Database: `tableadplayer.db`, schema version **1**, `exportSchema=true` (`app/schemas/`).
- `MediaState`: `REMOTE`, `DOWNLOADING`, `READY`, `FAILED`, `EXPIRED`, `DELETED`.
- Files live in app-private `files/media/{id}_v{version}`. Metadata (checksum, file size, downloadedAt, lastAccessedAt, version, state) lives in `media`.
- `MediaCache.ingest` is the only download path: DOWNLOADING → `AtomicFileStore` (`*.part` → verify → rename) → READY, or FAILED.
- Playback may use a cached file only when `state == READY` and the file exists (`MediaCache.playableFile`). Pin-swap additionally requires `MediaCache.isReady` (filename matches `{id}_v{version}`).
- `MediaCleanupPolicy` / `MediaCache.cleanup` never returns or deletes IDs referenced by the **active** playlist.

Upgrades must add a `Migration` or `@AutoMigration`. Destructive fallback is not enabled.

## Scheduler (Phase 4)

`ScheduleWindow` + `ScheduleEvaluator` (pure JVM). Unset fields mean no constraint. Times are inclusive start / exclusive end; overnight windows wrap midnight. `daysOfWeek` uses ISO `1=Mon … 7=Sun` (also `0` or `SUN`). Evaluation uses the schedule's timezone.

## Sync (Phase 6)

`SyncCoordinator` (via `SyncWorker` / `SyncRepository`):

1. `DeviceRepository.ensureRegistered()`
2. Fetch + store device config
3. Enqueue + drain heartbeats **and** playback events (isolated; failures do not fail the sync)
4. If the origin is live: persist playlist as pending, drain `MEDIA_DOWNLOAD` jobs, pin-swap only when every item is READY
5. Cache cleanup

Placeholder origin never downloads; DEMO `demo-local` stays pinned. Offline devices keep playing cached files or bundled DEMO assets (`PlaylistResolver`).

## Reporting (Phase 7)

`PlaylistEngine` emits play/skip/error/completed through `PlaybackReporting.emitIsolated` into `ReportingQueue.offer` (non-blocking). A background `ReportingPump` writes Room rows; drain posts `EventBatchDto` to `/v1/device/events`. Heartbeats remain a separate drain. Offline devices keep events until connectivity / the next sync. Failures increment `attempts` and `nextAttemptAt` (`Backoff`); poison / exhausted rows are dropped. The playlist job never `join()`s this work.

## Boot / kiosk (Phase 8)

`BootCompletedReceiver` starts `PlayerWatchdogService` (foreground, `mediaPlayback`) and enqueues sync **after** the player can already show the cached or DEMO playlist (`PlaylistResolver`). `Watchdog` + `CrashGuard` share a 3-in-2-minutes safe-mode policy so START_STICKY cannot spin ExoPlayer. Lock Task / device-owner is provisioning (`KIOSK_SETUP.md`), not an in-app exploit. Exit Lock Task is offered in admin only when the DPC listed this package.

## Admin (Phase 9)

Long-press top-left opens a dark service menu (Device / Player / Sync / Diagnostics / Controls) sized for 800×1280 portrait. Cache clear still respects the active-playlist pin.

## Threading

- Playlist loop: `viewModelScope` (main) with IO for asset / file reads.
- ExoPlayer created/released on main.
- Room and cache: IO dispatcher (`TableAdPlayerApp` appScope, `SyncWorker`).
- Sync/reporting: WorkManager / background dispatchers only.

## What this is not

This is not a clone of a third-party APK. Layouts, assets, and APIs are original. Behavior (fullscreen loop, diagnostics, kiosk) matches common digital-signage expectations.
