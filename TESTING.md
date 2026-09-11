# Testing

## Unit (Phase 1–2)

```bash
./gradlew testDebugUnitTest
```

Covers device-id formatting, playlist wrap/backoff, sync backoff.

## Manual — diagnostics (Phase 1)

1. Launch **TableAd Diagnostics** (debug) or Admin → Device diagnostics.
2. Confirm `TABLE-xxxxxxxx`, Android version, API, manufacturer, model, resolution, RAM, storage, ABI, battery, networks.
3. Export JSON; open the file and check it pretty-prints with `deviceId` and `capturedAt`.

## Manual — DEMO player (Phase 2)

1. Launch **TableAdPlayer**.
2. Portrait only; system bars hidden; screen stays on.
3. Welcome image (~5 s) → sample video to the end → admin hint image → offline image → skip missing file → loop.
4. Long-press top-left → admin stub → diagnostics → back to player.

## Later (Phases 3–10)

- Room pin swap: kill process mid-download; on restart the incomplete `*.part` is not playable
- Scheduler: item with `endAt` in the past is not shown
- Reporting: airplane mode, play 10 items, reconnect; outbox drains without hitching video
- CrashGuard: throw 3 times quickly; fourth launch is diagnostics
- Instrumented Compose tests on an 800×1280 emulator AVD

There is no Play Services test lab dependency.
