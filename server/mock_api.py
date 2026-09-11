#!/usr/bin/env python3
"""Minimal LAN mock for TableAdPlayer. Stdlib only.

Serves register / config / playlist / heartbeat / events and fixture media
so a debug build can complete Phase 5–6 against a local origin:

    python3 server/mock_api.py
    ./gradlew assembleDebug -PAPI_BASE_URL=http://10.0.2.2:8787/
"""

from __future__ import annotations

import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

ROOT = Path(__file__).resolve().parent / "fixtures"
MEDIA = ROOT / "media"
HOST = "0.0.0.0"
PORT = 8787


def load(name: str) -> bytes:
    return (ROOT / name).read_bytes()


def load_json(name: str) -> dict:
    return json.loads(load(name).decode("utf-8"))


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt: str, *args) -> None:
        print("[%s] %s" % (self.log_date_time_string(), fmt % args))

    def _json(self, code: int, payload) -> None:
        if isinstance(payload, (dict, list)):
            body = json.dumps(payload).encode("utf-8")
        else:
            body = payload
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, X-Device-Id, X-Device-Token")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.end_headers()
        self.wfile.write(body)

    def _bytes(self, code: int, body: bytes, content_type: str) -> None:
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

    def _device_id(self, body: dict | None = None) -> str:
        header = self.headers.get("X-Device-Id") or self.headers.get("x-device-id")
        if header:
            return header.strip()
        query = parse_qs(urlparse(self.path).query)
        if query.get("deviceId"):
            return query["deviceId"][0]
        if body and body.get("deviceId"):
            return str(body["deviceId"])
        return "TABLE-abcd1234"

    def _read_json(self) -> dict:
        length = int(self.headers.get("Content-Length", "0") or "0")
        raw = self.rfile.read(length) if length else b""
        if not raw:
            return {}
        try:
            parsed = json.loads(raw.decode("utf-8"))
            return parsed if isinstance(parsed, dict) else {}
        except json.JSONDecodeError:
            return {}

    def do_OPTIONS(self) -> None:  # noqa: N802
        self._json(204, b"")

    def do_GET(self) -> None:  # noqa: N802
        parsed = urlparse(self.path)
        path = parsed.path.rstrip("/") or "/"
        if path == "/v1/device/config":
            cfg = load_json("device-config.json")
            cfg["deviceId"] = self._device_id()
            self._json(200, cfg)
        elif path == "/v1/playlists/current":
            playlist = load_json("playlist.json")
            self._json(200, playlist)
        elif path.startswith("/v1/media/"):
            name = path.split("/")[-1]
            target = (MEDIA / name).resolve()
            if not str(target).startswith(str(MEDIA.resolve())) or not target.is_file():
                self._json(404, {"ok": False, "error": "media_not_found"})
                return
            suffix = target.suffix.lower()
            content_type = {
                ".png": "image/png",
                ".jpg": "image/jpeg",
                ".jpeg": "image/jpeg",
                ".mp4": "video/mp4",
                ".webm": "video/webm",
            }.get(suffix, "application/octet-stream")
            self._bytes(200, target.read_bytes(), content_type)
        elif path in ("/", "/health"):
            self._json(200, {"ok": True})
        else:
            self._json(404, {"ok": False, "error": "not_found"})

    def do_POST(self) -> None:  # noqa: N802
        path = urlparse(self.path).path.rstrip("/")
        body = self._read_json()
        device_id = self._device_id(body)
        if path == "/v1/device/register":
            template = load_json("register-response.json")
            template["deviceId"] = device_id
            template["status"] = "REGISTERED"
            if not template.get("token"):
                template["token"] = "mock-%s" % device_id
            self._json(200, template)
        elif path == "/v1/device/heartbeat":
            self._json(200, {"ok": True})
        elif path == "/v1/device/events":
            self._json(200, {"ok": True})
        else:
            self._json(404, {"ok": False, "error": "not_found"})


def main() -> None:
    httpd = ThreadingHTTPServer((HOST, PORT), Handler)
    print(f"TableAdPlayer mock API on http://{HOST}:{PORT}/")
    httpd.serve_forever()


if __name__ == "__main__":
    main()
