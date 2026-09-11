# Architecture

TableAdPlayer is a kiosk-style advertising client. The player process must keep showing media even when the network, disk, or remote API is unhealthy.

## Principles

1. **Offline-first.** After a playlist revision is fully cached, playback does not need the network. Scheduling, heartbeats, and event upload degrade independently.
2. **Atomic downloads.** Bytes land in `*.part`, are fsynced, checksummed, then renamed. The playlist pin swaps only when every required item is complete. See `AtomicFileStore`.
3. **The playlist engine never freezes.** Bad or missing media is skipped. If every item fails, the engine backs off (`PlaylistAdvance.ALL_FAILED_BACKOFF_MS`) instead of spinning. Images use a duration timer; videos advance on `STATE_ENDED` or error, with a max cap.
4. **Scheduling is local.** Once a windowed playlist is pinned, start/end times are evaluated on-device (Phase 4).
5. **Heartbeats and events are queued.** `ReportingQueue` / future Room outbox. Reporting **never** blocks `PlaylistEngine`.
6. **Storage safeguards.** Refuse downloads when free space is below a reserve (Phase 6). Prefer deleting incomplete `*.part` files before touching complete media.
7. **Exponential backoff.** `Backoff.delayMs` for sync and heartbeat. WorkManager retry for `SyncWorker`.
8. **Crash recovery without rapid loops.** `CrashGuard` counts uncaught exceptions in a 2-minute window. After 3 crashes, the next launch opens diagnostics (safe mode) instead of ExoPlayer.
9. **Lock Task / device-owner is documented, not bypassed.** `BootCompletedReceiver` may try to start the player; OEM background-activity limits are real. Kiosk lockdown is a provisioning step (`KIOSK_SETUP.md`), not an app exploit.

## Module map (single Gradle app)

```
com.tableadplayer.app
  ui/            Compose screens (player, diagnostics, admin)
  playback/      Playlist engine + demo loader (Media3)
  core/          Device id, diagnostics, crash guard, immersive helpers
  data/remote    Retrofit contract + OkHttp (BuildConfig.API_BASE_URL)
  data/local     Future Room rows (not wired yet)
  data/cache     Atomic file writer
  sync/          WorkManager stub + backoff
  reporting/     In-memory outbox
  kiosk/         Boot receiver
```

UI talks to ViewModels. ViewModels own `PlaylistEngine`. Network I/O is confined to sync/reporting coroutines. Playback holds no Retrofit types.

## Device identity

A stable id `TABLE-` + 8 lowercase hex chars is stored in DataStore. The first value is derived from `ANDROID_ID` (SHA-256 prefix) so reinstalls on the same device usually keep the same id. Android ID is also exposed on the diagnostics screen as a separate field (kiosk inventory, not ad tracking).

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
| All items failed | Idle + 5 s backoff, then retry |

Portrait + sticky immersive + `FLAG_KEEP_SCREEN_ON`. Long-press a 96 dp hit target in the **top-left** to open the admin stub.

## Threading

- Playlist loop: `viewModelScope` (main) with IO for asset reads.
- ExoPlayer created/released on main.
- Sync/reporting: WorkManager / background dispatchers only.

## What this is not

This is not a clone of a third-party APK. Layouts, assets, and APIs are original. Behavior (fullscreen loop, diagnostics, kiosk) matches common digital-signage expectations.
