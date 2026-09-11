package com.tableadplayer.app.core.diagnostics

import android.content.Context
import com.tableadplayer.app.core.json.AppJson
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DiagnosticsExporter(private val context: Context) {

    suspend fun writeJson(snapshot: DiagnosticsSnapshot): File = withContext(Dispatchers.IO) {
        writeNamed("diagnostics", snapshot.deviceId, AppJson.pretty.encodeToString(DiagnosticsSnapshot.serializer(), snapshot))
    }

    suspend fun writeNamed(prefix: String, deviceId: String, json: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val safeId = deviceId.replace(Regex("[^A-Za-z0-9_-]"), "").ifBlank { "device" }
        val file = File(dir, "$prefix-$safeId-$stamp.json")
        file.writeText(json)
        file
    }
}
