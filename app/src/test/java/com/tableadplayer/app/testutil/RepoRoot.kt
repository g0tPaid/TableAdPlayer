package com.tableadplayer.app.testutil

import java.io.File

/** Locates the Git repo root from Gradle's app-module working directory. */
object RepoRoot {
    fun find(): File {
        var dir = File(".").canonicalFile
        repeat(8) {
            val marker = File(dir, "server/mock_api.py")
            val settings = File(dir, "settings.gradle.kts")
            if (marker.isFile && settings.isFile) return dir
            dir = dir.parentFile ?: return File(".").canonicalFile
        }
        return File(".").canonicalFile
    }

    fun serverFixture(name: String): File = File(find(), "server/fixtures/$name")

    fun assetFixture(name: String): File = File(find(), "app/src/main/assets/fixtures/$name")

    fun readServerFixture(name: String): String = serverFixture(name).readText()
}
