package com.tableadplayer.app.data.local

import androidx.room.TypeConverter

class AppTypeConverters {
    @TypeConverter
    fun mediaStateToString(value: MediaState): String = value.name

    @TypeConverter
    fun mediaStateFromString(value: String): MediaState = MediaState.valueOf(value)
}
