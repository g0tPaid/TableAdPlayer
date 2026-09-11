# Mock API

Stdlib HTTP server that serves the Phase 5–7 contract: register, config, current playlist, heartbeat, events, and fixture media under `/v1/media/`.

```bash
python3 server/mock_api.py
```

Listens on `0.0.0.0:8787`. Point a debug build at `http://<lan-ip>:8787/` using `-PAPI_BASE_URL=...` (emulator: `http://10.0.2.2:8787/`).

Fixtures live in `server/fixtures/` and must stay in sync with `app/src/main/assets/fixtures/`.

