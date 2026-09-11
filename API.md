# API

The Android app talks to **your** REST origin. The origin is `BuildConfig.API_BASE_URL` (Gradle property `API_BASE_URL`). It is not hard-coded to any production advertising CDN.

Default (invalid placeholder): `https://api.example.invalid/`

When the origin host is `api.example.invalid` (or `*.example.invalid`), the app **does not** open a network connection. DEMO MODE uses `FixtureTableAdApi` (`assets/fixtures/`) so first-launch registration, config, playlist, and heartbeat succeed offline.

```bash
./gradlew assembleDebug -PAPI_BASE_URL=https://ops.example.com/tablead/
# Emulator → LAN mock:
./gradlew assembleDebug -PAPI_BASE_URL=http://10.0.2.2:8787/
```

## Endpoints (v1)

All JSON, UTF-8. Times in UTC ISO-8601.

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/v1/device/register` | First launch: `UNREGISTERED` → `REGISTERED`. Returns optional device token. |
| GET | `/v1/device/config?deviceId=` | Heartbeat/sync intervals, timezone |
| GET | `/v1/playlists/current?deviceId=` | Current playlist revision + media list |
| GET | `/v1/media/{file}` | Optional static media (mock server). Production may use absolute CDN URLs. |
| POST | `/v1/device/heartbeat` | Liveness; queued locally when offline; must not be on the playback critical path |
| POST | `/v1/device/events` | Batched playback/skip/error/completed events (Phase 7 drain) |

See Kotlin DTOs in `app/src/main/java/com/tableadplayer/app/data/remote/TableAdApi.kt` and fixtures:

- `app/src/main/assets/fixtures/register-response.json`
- `app/src/main/assets/fixtures/device-config.json`
- `app/src/main/assets/fixtures/playlist.json`
- `app/src/main/assets/fixtures/heartbeat.json`
- `server/fixtures/` (served by `python3 server/mock_api.py`)

## Register

Request:

```json
{
  "deviceId": "TABLE-abcd1234",
  "manufacturer": "Lenovo",
  "model": "TB-X606F",
  "androidVersion": "11",
  "apiLevel": 30,
  "appVersion": "0.6.0",
  "applicationId": "com.tableadplayer.app.debug",
  "demoMode": true
}
```

Response:

```json
{
  "deviceId": "TABLE-abcd1234",
  "status": "REGISTERED",
  "token": "mock-device-token",
  "timezone": "UTC",
  "heartbeatIntervalSec": 60,
  "syncIntervalSec": 300
}
```

Unknown `status` values are stored as-is. Empty/omitted status is treated as `REGISTERED` on HTTP success. Failures leave the device `UNREGISTERED`; DEMO playback continues.

## Heartbeat

Posted by `ReportingRepository`. If the call fails, the payload is kept in Room `playback_events` (`type=heartbeat`) and retried with exponential backoff (`Backoff.delayMs`). Oldest heartbeats are dropped when the outbox exceeds 64.

```json
{
  "deviceId": "TABLE-abcd1234",
  "appVersion": "0.6.0",
  "capturedAt": "2026-09-11T16:00:00Z",
  "playbackItemId": "slide-welcome",
  "status": "REGISTERED",
  "playlistId": "venue-lobby",
  "playlistRevision": 2,
  "storageFreeBytes": 2147483648,
  "network": "wifi"
}
```

Ack: `{ "ok": true }`.

## Playback events

Posted by `ReportingRepository.drainPlaybackEvents` in batches (`EventBatchDto`). The playlist engine only `offer`s to `ReportingQueue` (drop-oldest at 512) and never waits on this call. Offline rows stay in Room (`type` = `play` / `skip` / `error` / `completed`) until a later drain; API failures use the same `Backoff` as heartbeats. Oldest playback events are dropped when the outbox exceeds 512. Heartbeat rows are **not** mixed into this batch.

```json
{
  "deviceId": "TABLE-abcd1234",
  "events": [
    {"type": "play", "itemId": "slide-welcome", "at": "2026-09-11T16:00:01Z"},
    {"type": "completed", "itemId": "slide-welcome", "at": "2026-09-11T16:00:06Z"},
    {"type": "error", "itemId": "slide-missing", "at": "2026-09-11T16:00:06Z", "detail": "unreadable"},
    {"type": "skip", "itemId": "slide-missing", "at": "2026-09-11T16:00:06Z", "detail": "unreadable"}
  ]
}
```

Ack: `{ "ok": true }`. Unknown event types should be ignored by the server (`ok: true` still drains the batch).

## Media items

Each playlist item should include:

- `id`, `type` (`image` \| `video`), `url` (absolute `http(s)` or origin-relative, e.g. `/v1/media/welcome.png`)
- `sha256` (hex) for atomic download verification
- `durationMs` for images
- optional `startAt` / `endAt` (ISO-8601) for simple bounds
- optional schedule (evaluated **on device**, Phase 4): `startDate`, `endDate` (`yyyy-MM-dd`), `startTime`, `endTime` (`HH:mm`), `daysOfWeek` (ISO `1=Mon … 7=Sun`, e.g. `"1,2,3,4,5"`). Omitted / empty = play normally.

The player must ignore unknown fields (`ignoreUnknownKeys = true`).

`SyncWorker` downloads each item through `MediaCache.ingest` (`*.part` → checksum → rename). The active playlist pin swaps **only** when every required item is READY at the expected `{id}_v{version}` file. Incomplete revisions never replace DEMO / the previous pin.

## Auth

No Play Services. Every request may include:

| Header | Value |
| --- | --- |
| `X-Device-Id` | Persistent `TABLE-xxxxxxxx` |
| `X-Device-Token` | Token from register (omitted until registered) |

Do not embed third-party private API keys.

## Repositories

| Interface | Role |
| --- | --- |
| `DeviceRepository` | `UNREGISTERED` → `REGISTERED`, config, auth store |
| `ContentRepository` | Current playlist |
| `ReportingRepository` | Heartbeat + playback-event enqueue + drain |
| `SyncRepository` | `SyncCoordinator.sync()` |

Live origin → Retrofit `TableAdApi`. Placeholder origin → `FixtureTableAdApi`.

## Mock server

```bash
python3 server/mock_api.py
# POST http://127.0.0.1:8787/v1/device/register
# GET  http://127.0.0.1:8787/v1/device/config?deviceId=TABLE-abcd1234
# GET  http://127.0.0.1:8787/v1/playlists/current
# GET  http://127.0.0.1:8787/v1/media/welcome.png
# POST http://127.0.0.1:8787/v1/device/heartbeat
# POST http://127.0.0.1:8787/v1/device/events
```

Point a **debug** build at `http://10.0.2.2:8787/` (emulator) or the LAN IP (device). Debug `network_security_config` allows cleartext; release does not.
