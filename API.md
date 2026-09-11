# API

The Android app talks to **your** REST origin. The origin is `BuildConfig.API_BASE_URL` (Gradle property `API_BASE_URL`). It is not hard-coded to any production advertising CDN.

Default (invalid placeholder): `https://api.example.invalid/`

```bash
./gradlew assembleDebug -PAPI_BASE_URL=https://ops.example.com/tablead/
```

## Endpoints (v1)

All JSON, UTF-8. Times in UTC ISO-8601.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/v1/device/config?deviceId=` | Heartbeat/sync intervals, timezone |
| GET | `/v1/playlists/current?deviceId=` | Current playlist revision + media list |
| POST | `/v1/device/heartbeat` | Liveness; must not be on the playback critical path |
| POST | `/v1/device/events` | Batched playback/skip/error events |

See Kotlin DTOs in `app/src/main/java/com/tableadplayer/app/data/remote/TableAdApi.kt` and fixtures:

- `app/src/main/assets/fixtures/device-config.json`
- `app/src/main/assets/fixtures/playlist.json`
- `server/fixtures/` (served by `python3 server/mock_api.py`)

## Media items

Each playlist item should include:

- `id`, `type` (`image` \| `video`), `url`
- `sha256` (hex) for atomic download verification
- `durationMs` for images
- optional `startAt` / `endAt` (ISO-8601) for simple bounds
- optional schedule (evaluated **on device**, Phase 4): `startDate`, `endDate` (`yyyy-MM-dd`), `startTime`, `endTime` (`HH:mm`), `daysOfWeek` (ISO `1=Mon … 7=Sun`, e.g. `"1,2,3,4,5"`). Omitted / empty = play normally.

The player must ignore unknown fields (`ignoreUnknownKeys = true`).

## Auth

Not specified in Phase 1–2. Prefer a device token header later (`X-Device-Id` + HMAC). Do not embed third-party private API keys.

## Mock server

```bash
python3 server/mock_api.py
# GET http://127.0.0.1:8787/v1/device/config?deviceId=TABLE-abcd1234
```

Point a **debug** build at `http://10.0.2.2:8787/` (emulator) or the LAN IP (device). Debug `network_security_config` allows cleartext; release does not.
