package com.tableadplayer.app.core.device

object DeviceIdFormatter {
    private val pattern = Regex("^TABLE-[0-9a-f]{8}$")

    fun isValid(id: String): Boolean = pattern.matches(id)

    fun fromHexBytes(bytes: ByteArray): String {
        require(bytes.size >= 4) { "Need at least 4 bytes for TABLE-xxxxxxxx" }
        val hex = bytes.take(4).joinToString("") { b -> "%02x".format(b.toInt() and 0xFF) }
        return "TABLE-$hex"
    }
}
