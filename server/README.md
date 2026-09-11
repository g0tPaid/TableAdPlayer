# Mock API

Stdlib HTTP server that serves the Phase 5 contract.

```bash
python3 server/mock_api.py
```

Listens on `0.0.0.0:8787`. Point a debug build at `http://<lan-ip>:8787/` using `-PAPI_BASE_URL=...`.
