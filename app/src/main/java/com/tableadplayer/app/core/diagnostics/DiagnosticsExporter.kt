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
        val dir = File(context.filesDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val safeId = snapshot.deviceId.replace(Regex("[^A-Za-z0-9_-]"), "")
        val file = File(dir, "diagnostics-$safeId-$stamp.json")
        file.writeText(AppJson.pretty.encodeToString(DiagnosticsSnapshot.serializer(), snapshot))
        file
    }
}
