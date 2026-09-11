# Architecture

TableAdPlayer is a kiosk-style advertising client. The player process must keep showing media even when the network, disk, or remote API is unhealthy.

## Principles

1. **Offline-first.** After a playlist revision is fully cached, playback does not need the network. Scheduling, heartbeats, and event upload degrade independently. DEMO MODE plays bundled assets with no server.
2. **Atomic downloads.** Bytes land in `*.part`, are fsynced, checksummed, then renamed. The playlist pin swaps only when every required item is complete. See `AtomicFileStore` / `MediaFileStore`.
3. **The playlist engine never freezes.** Bad or missing media is skipped. If every item fails, the engine backs off (`PlaylistAdvance.ALL_FAILED_BACKOFF_MS`) instead of spinning. Images use a duration timer; videos advance on `STATE_ENDED` or error, with a max cap.
4. **Scheduling is local.** `ScheduleEvaluator` applies `startDate` / `endDate` / `startTime` / `endTime` / `daysOfWeek` on-device. A null or empty window plays normally. If every item is out of window, the engine shows idle and polls — it does not deadlock.
5. **Heartbeats and events are queued.** `ReportingQueue` is still in-memory; Room `playback_events` is the Phase 7 outbox. Reporting **never** blocks `PlaylistEngine`.
6. **Storage safeguards.** Cleanup deletes leftover `*.part` files first, then unused complete media. It **never** deletes media required by the active playlist (any `MediaState`). A size cap / free-space reserve is enforced more aggressively in Phase 6.
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
  data/remote    Retrofit contract + OkHttp (BuildConfig.API_BASE_URL)
  data/local     Room (Device, Media, Playlist, PlaylistItem, Schedule,
                 PlaybackEvent, SyncJob, AppConfig) — migrations, no destructive fallback
  data/cache     AtomicFileStore + MediaFileStore + MediaCache + cleanup policy
  data/seed      DEMO playlist pin into Room (assets, not a CDN)
  sync/          WorkManager stub; calls MediaCache.cleanup()
  reporting/     In-memory outbox (Room table exists for Phase 7)
  kiosk/         Boot receiver
```

UI talks to ViewModels. ViewModels own `PlaylistEngine`. Network I/O is confined to sync/reporting coroutines. Playback holds no Retrofit types.

## Device identity

A stable id `TABLE-` + 8 lowercase hex chars is stored in DataStore. The first value is derived from `ANDROID_ID` (SHA-256 prefix) so reinstalls on the same device usually keep the same id. Android ID is also exposed on the diagnostics screen as a separate field (kiosk inventory, not ad tracking). The same id is upserted into Room `devices` when DEMO seeds.

## Remote API

`BuildConfig.API_BASE_URL` is injected at build time from the `API_BASE_URL` Gradle property. The default is an invalid placeholder. **Do not** hard-code a production advertising host in source.

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
- Playback may use a cached file only when `state == READY` and the file exists (`MediaCache.playableFile`).
- `MediaCleanupPolicy` / `MediaCache.cleanup` never returns or deletes IDs referenced by the **active** playlist.

Upgrades must add a `Migration` or `@AutoMigration`. Destructive fallback is not enabled.

## Scheduler (Phase 4)

`ScheduleWindow` + `ScheduleEvaluator` (pure JVM). Unset fields mean no constraint. Times are inclusive start / exclusive end; overnight windows wrap midnight. `daysOfWeek` uses ISO `1=Mon … 7=Sun` (also `0` or `SUN`). Evaluation uses the schedule's timezone.

## Threading

- Playlist loop: `viewModelScope` (main) with IO for asset / file reads.
- ExoPlayer created/released on main.
- Room and cache: IO dispatcher (`TableAdPlayerApp` appScope, `SyncWorker`).
- Sync/reporting: WorkManager / background dispatchers only.

## What this is not

This is not a clone of a third-party APK. Layouts, assets, and APIs are original. Behavior (fullscreen loop, diagnostics, kiosk) matches common digital-signage expectations.
