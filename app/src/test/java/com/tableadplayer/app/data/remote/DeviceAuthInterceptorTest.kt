package com.tableadplayer.app.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceAuthInterceptorTest {
    @Test
    fun addsDeviceIdAndTokenHeaders() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("{}").setResponseCode(200))
        server.start()
        try {
            val client = OkHttpClient.Builder()
                .addInterceptor(DeviceAuthInterceptor({ "TABLE-abcd1234" }, { "tok-1" }))
                .build()
            client.newCall(Request.Builder().url(server.url("/v1/device/config")).build()).execute().close()
            val recorded = server.takeRequest()
            assertEquals("TABLE-abcd1234", recorded.getHeader(DeviceAuthInterceptor.HEADER_DEVICE_ID))
            assertEquals("tok-1", recorded.getHeader(DeviceAuthInterceptor.HEADER_DEVICE_TOKEN))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun omitsBlankToken() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("{}"))
        server.start()
        try {
            val client = OkHttpClient.Builder()
                .addInterceptor(DeviceAuthInterceptor({ "TABLE-abcd1234" }, { "  " }))
                .build()
            client.newCall(Request.Builder().url(server.url("/v1/ping")).build()).execute().close()
            val recorded = server.takeRequest()
            assertEquals("TABLE-abcd1234", recorded.getHeader(DeviceAuthInterceptor.HEADER_DEVICE_ID))
            assertNull(recorded.getHeader(DeviceAuthInterceptor.HEADER_DEVICE_TOKEN))
        } finally {
            server.shutdown()
        }
    }
}
