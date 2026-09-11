#!/usr/bin/env python3
"""HTTP contract tests against server/mock_api.py (stdlib unittest).

    python3 -m unittest server.test_mock_api
    python3 -m unittest discover -s server -p 'test_*.py'
"""

from __future__ import annotations

import http.client
import json
import threading
import unittest

from http.server import ThreadingHTTPServer

from mock_api import ROOT, Handler


class MockApiContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.httpd = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        cls.port = cls.httpd.server_address[1]
        cls.thread = threading.Thread(target=cls.httpd.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls) -> None:
        cls.httpd.shutdown()
        cls.httpd.server_close()

    def _conn(self) -> http.client.HTTPConnection:
        return http.client.HTTPConnection("127.0.0.1", self.port, timeout=5)

    def _json(self, method: str, path: str, body: dict | None = None, headers: dict | None = None):
        payload = json.dumps(body).encode("utf-8") if body is not None else None
        hdrs = {"Accept": "application/json"}
        if payload is not None:
            hdrs["Content-Type"] = "application/json"
        if headers:
            hdrs.update(headers)
        conn = self._conn()
        try:
            conn.request(method, path, body=payload, headers=hdrs)
            resp = conn.getresponse()
            raw = resp.read()
            parsed = json.loads(raw.decode("utf-8")) if raw else {}
            return resp.status, parsed
        finally:
            conn.close()

    def test_health(self) -> None:
        status, body = self._json("GET", "/health")
        self.assertEqual(200, status)
        self.assertTrue(body.get("ok"))

    def test_register_echoes_device_id(self) -> None:
        status, body = self._json(
            "POST",
            "/v1/device/register",
            body={"deviceId": "TABLE-deadbeef", "appVersion": "0.8.0", "applicationId": "x", "demoMode": True},
            headers={"X-Device-Id": "TABLE-deadbeef"},
        )
        self.assertEqual(200, status)
        self.assertEqual("TABLE-deadbeef", body["deviceId"])
        self.assertEqual("REGISTERED", body["status"])
        self.assertTrue(body.get("token"))

    def test_config_echoes_header_device_id(self) -> None:
        status, body = self._json(
            "GET",
            "/v1/device/config?deviceId=TABLE-ffff0000",
            headers={"X-Device-Id": "TABLE-ffff0000"},
        )
        self.assertEqual(200, status)
        self.assertEqual("TABLE-ffff0000", body["deviceId"])
        self.assertEqual(60, body["heartbeatIntervalSec"])

    def test_current_playlist_matches_fixture(self) -> None:
        status, body = self._json("GET", "/v1/playlists/current")
        self.assertEqual(200, status)
        fixture = json.loads((ROOT / "playlist.json").read_text(encoding="utf-8"))
        self.assertEqual(fixture["playlistId"], body["playlistId"])
        self.assertEqual(fixture["revision"], body["revision"])
        self.assertEqual(len(fixture["items"]), len(body["items"]))
        for item in body["items"]:
            self.assertIn(item["type"], ("image", "video"))
            self.assertTrue(item["url"].startswith("/v1/media/"))
            self.assertNotIn("cnszfyd", item["url"])

    def test_heartbeat_and_events_ack(self) -> None:
        status, body = self._json(
            "POST",
            "/v1/device/heartbeat",
            body={"deviceId": "TABLE-abcd1234", "appVersion": "0.8.0", "capturedAt": "2026-01-01T00:00:00Z"},
            headers={"X-Device-Id": "TABLE-abcd1234"},
        )
        self.assertEqual(200, status)
        self.assertTrue(body.get("ok"))
        status, body = self._json(
            "POST",
            "/v1/device/events",
            body={
                "deviceId": "TABLE-abcd1234",
                "events": [{"type": "play", "itemId": "slide-welcome", "at": "2026-01-01T00:00:01Z"}],
            },
            headers={"X-Device-Id": "TABLE-abcd1234"},
        )
        self.assertEqual(200, status)
        self.assertTrue(body.get("ok"))

    def test_missing_media_is_404(self) -> None:
        status, body = self._json("GET", "/v1/media/does-not-exist.png")
        self.assertEqual(404, status)
        self.assertEqual("media_not_found", body.get("error"))

    def test_unknown_path_is_404(self) -> None:
        status, body = self._json("GET", "/v1/secret")
        self.assertEqual(404, status)
        self.assertEqual("not_found", body.get("error"))

    def test_media_path_does_not_escape_fixtures(self) -> None:
        status, body = self._json("GET", "/v1/media/../playlist.json")
        self.assertEqual(404, status)


if __name__ == "__main__":
    unittest.main()
