#!/usr/bin/env python3
"""Minimal LAN mock for TableAdPlayer. Stdlib only."""

from __future__ import annotations

import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parent / "fixtures"
HOST = "0.0.0.0"
PORT = 8787


def load(name: str) -> bytes:
    return (ROOT / name).read_bytes()


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt: str, *args) -> None:
        print("[%s] %s" % (self.log_date_time_string(), fmt % args))

    def _json(self, code: int, body: bytes) -> None:
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self) -> None:  # noqa: N802
        path = urlparse(self.path).path.rstrip("/")
        if path == "/v1/device/config":
            self._json(200, load("device-config.json"))
        elif path == "/v1/playlists/current":
            self._json(200, load("playlist.json"))
        elif path in ("", "/health"):
            self._json(200, json.dumps({"ok": True}).encode())
        else:
            self._json(404, json.dumps({"ok": False, "error": "not_found"}).encode())

    def do_POST(self) -> None:  # noqa: N802
        path = urlparse(self.path).path.rstrip("/")
        length = int(self.headers.get("Content-Length", "0") or "0")
        if length:
            self.rfile.read(length)
        if path in ("/v1/device/heartbeat", "/v1/device/events"):
            self._json(200, json.dumps({"ok": True}).encode())
        else:
            self._json(404, json.dumps({"ok": False, "error": "not_found"}).encode())


def main() -> None:
    httpd = ThreadingHTTPServer((HOST, PORT), Handler)
    print(f"TableAdPlayer mock API on http://{HOST}:{PORT}/")
    httpd.serve_forever()


if __name__ == "__main__":
    main()
