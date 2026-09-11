package com.tableadplayer.app.data.remote

import com.tableadplayer.app.core.json.AppJson
import com.tableadplayer.app.testutil.RepoRoot
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Retrofit + kotlinx.serialization consume the same JSON [server/mock_api.py] serves.
 */
class MockApiRetrofitContractTest {
    @Test
    fun retrofitRoundTripAgainstFixtureDispatcher() = runTest {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path?.substringBefore("?").orEmpty()
                return when {
                    path.endsWith("/v1/device/register") ->
                        json(RepoRoot.readServerFixture("register-response.json"))
                    path.endsWith("/v1/device/config") ->
                        json(RepoRoot.readServerFixture("device-config.json"))
                    path.endsWith("/v1/playlists/current") ->
                        json(RepoRoot.readServerFixture("playlist.json"))
                    path.endsWith("/v1/device/heartbeat") || path.endsWith("/v1/device/events") ->
                        json("""{"ok":true}""")
                    else -> MockResponse().setResponseCode(404).setBody("""{"ok":false}""")
                }
            }
        }
        server.start()
        try {
            val api = ApiFactory.create(
                baseUrl = server.url("/").toString(),
                debug = false,
                client = OkHttpClient(),
            )
            val registered = api.register(
                RegisterRequestDto(
                    deviceId = "TABLE-deadbeef",
                    appVersion = "0.8.0",
                    applicationId = "com.tableadplayer.app.debug",
                    demoMode = true,
                ),
            )
            assertEquals("REGISTERED", registered.status)
            assertEquals("mock-device-token", registered.token)

            val config = api.deviceConfig("TABLE-deadbeef")
            assertEquals("UTC", config.timezone)

            val playlist = api.currentPlaylist("TABLE-deadbeef")
            assertEquals("venue-lobby", playlist.playlistId)
            assertEquals(3, playlist.items.size)

            assertTrue(api.heartbeat(HeartbeatDto("TABLE-deadbeef", "0.8.0", "2026-01-01T00:00:00Z")).ok)
            assertTrue(
                api.reportEvents(
                    EventBatchDto(
                        deviceId = "TABLE-deadbeef",
                        events = listOf(PlayerEventDto("play", "slide-welcome", "2026-01-01T00:00:01Z")),
                    ),
                ).ok,
            )

            val registerReq = server.takeRequest()
            assertEquals("POST", registerReq.method)
            assertTrue(registerReq.path!!.contains("/v1/device/register"))
            val body = AppJson.compact.parseToJsonElement(registerReq.body.readUtf8()).toString()
            assertTrue(body.contains("TABLE-deadbeef"))
            assertTrue(body.contains("demoMode"))
        } finally {
            server.shutdown()
        }
    }

    private fun json(body: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .setBody(body)
}
