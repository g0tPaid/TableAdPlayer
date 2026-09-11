package com.tableadplayer.app.core.device

import android.content.Context
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.first

private val Context.deviceIdentityStore: DataStore<Preferences> by preferencesDataStore(
    name = "device_identity",
)

class DeviceIdentity(private val context: Context) {

    suspend fun getOrCreate(): String {
        val existing = context.deviceIdentityStore.data.first()[KEY_DEVICE_ID]
        if (existing != null && DeviceIdFormatter.isValid(existing)) {
            return existing
        }
        val generated = generateStableId(context)
        context.deviceIdentityStore.edit { prefs ->
            prefs[KEY_DEVICE_ID] = generated
        }
        return generated
    }

    companion object {
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")

        fun generateStableId(context: Context): String {
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID,
            ).orEmpty().ifBlank { UUID.randomUUID().toString() }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("tableadplayer:$androidId".toByteArray(Charsets.UTF_8))
            return DeviceIdFormatter.fromHexBytes(digest)
        }
    }
}
