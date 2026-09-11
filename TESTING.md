# Testing

## Unit (Phase 1–6)

```bash
./gradlew assembleDebug testDebugUnitTest
```

Covers device-id formatting, playlist wrap/backoff, sync backoff, media states, atomic file writes, cache cleanup (never drops the active playlist), the offline scheduler, API origin/fixture registration, device auth headers, pin-swap, free-space reserve, and heartbeat outbox policy.

## Manual — diagnostics (Phase 1)

1. Launch **TableAd Diagnostics** (debug) or Admin → Device diagnostics.
2. Confirm `TABLE-xxxxxxxx`, Android version, API, manufacturer, model, resolution, RAM, storage, ABI, battery, networks.
3. Export JSON; open the file and check it pretty-prints with `deviceId` and `capturedAt`.

## Manual — DEMO player (Phase 2)

1. Launch **TableAdPlayer** with **no network**. DEMO MODE must still loop.
2. Portrait only; system bars hidden; screen stays on.
3. Welcome image (~5 s) → sample video to the end → admin hint image → offline image → skip missing file → loop.
4. Long-press top-left → admin stub → diagnostics → back to player.

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

## Later (Phases 7–10)
- Reporting: airplane mode, play 10 items, reconnect; outbox drains without hitching video
- CrashGuard: throw 3 times quickly; fourth launch is diagnostics
- Instrumented Compose tests on an 800×1280 emulator AVD

There is no Play Services test lab dependency.
