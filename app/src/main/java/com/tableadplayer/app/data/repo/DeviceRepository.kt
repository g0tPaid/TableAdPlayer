package com.tableadplayer.app.data.repo

import android.os.Build
import com.tableadplayer.app.BuildConfig
import com.tableadplayer.app.core.device.DeviceIdentity
import com.tableadplayer.app.data.local.AppConfigEntity
import com.tableadplayer.app.data.local.AppConfigKeys
import com.tableadplayer.app.data.local.DeviceEntity
import com.tableadplayer.app.data.local.DeviceStatus
import com.tableadplayer.app.data.local.TableAdDatabase
import com.tableadplayer.app.data.remote.ApiOrigin
import com.tableadplayer.app.data.remote.DeviceAuthStore
import com.tableadplayer.app.data.remote.DeviceConfigDto
import com.tableadplayer.app.data.remote.RegisterRequestDto
import com.tableadplayer.app.data.remote.TableAdApi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * First-launch registration: [DeviceStatus.UNREGISTERED] → [DeviceStatus.REGISTERED].
 * Fixture API succeeds without a network; live Retrofit uses the device auth header.
 */
class DeviceRepository(
    private val api: TableAdApi,
    private val db: TableAdDatabase,
    private val identity: DeviceIdentity,
    private val auth: DeviceAuthStore,
    private val baseUrl: String = BuildConfig.API_BASE_URL,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun deviceId(): String {
        val id = identity.getOrCreate()
        auth.deviceId = id
        return id
    }

    suspend fun hydrateAuth() {
        auth.deviceId = identity.getOrCreate()
        auth.token = db.appConfigDao().getValue(AppConfigKeys.DEVICE_TOKEN)
    }

    suspend fun registration(): DeviceRegistration {
        hydrateAuth()
        val id = auth.deviceId
        val status = db.appConfigDao().getValue(AppConfigKeys.DEVICE_STATUS) ?: DeviceStatus.UNREGISTERED
        return DeviceRegistration(
            deviceId = id,
            status = status,
            token = db.appConfigDao().getValue(AppConfigKeys.DEVICE_TOKEN),
            serverUrl = db.appConfigDao().getValue(AppConfigKeys.SERVER_URL) ?: baseUrl,
            liveApi = ApiOrigin.usesLiveNetwork(baseUrl),
            timezone = db.appConfigDao().getValue(AppConfigKeys.TIMEZONE) ?: "UTC",
            lastHeartbeatAt = db.appConfigDao().getValue(AppConfigKeys.LAST_HEARTBEAT_AT),
        )
    }

    /**
     * Idempotent. Already-registered devices refresh the Room row and return.
     * Failures leave the device [DeviceStatus.UNREGISTERED] so DEMO playback continues.
     */
    suspend fun ensureRegistered(): DeviceRegistration {
        val current = registration()
        if (current.isRegistered) {
            persistServerUrl()
            return current
        }
        val now = clock()
        val id = current.deviceId
        val request = RegisterRequestDto(
            deviceId = id,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            appVersion = BuildConfig.VERSION_NAME,
            applicationId = BuildConfig.APPLICATION_ID,
            demoMode = BuildConfig.DEMO_MODE,
        )
        val response = runCatching { api.register(request) }.getOrElse {
            persistStatus(DeviceStatus.UNREGISTERED, token = null, now = now)
            persistServerUrl(now)
            return registration()
        }
        val status = RegistrationPolicy.nextStatus(
            current = DeviceStatus.UNREGISTERED,
            success = true,
            responseStatus = response.status,
        )
        val token = response.token?.takeIf { it.isNotBlank() }
        persistStatus(status, token, now)
        persistServerUrl(now)
        putConfig(AppConfigKeys.TIMEZONE, response.timezone, now)
        putConfig(AppConfigKeys.HEARTBEAT_INTERVAL_SEC, response.heartbeatIntervalSec.toString(), now)
        putConfig(AppConfigKeys.SYNC_INTERVAL_SEC, response.syncIntervalSec.toString(), now)
        auth.token = token
        val existing = db.deviceDao().get(id)
        db.deviceDao().upsert(
            DeviceEntity(
                id = id,
                displayName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                timezone = response.timezone,
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                lastSeenAt = now,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
        return registration()
    }

    suspend fun fetchAndStoreConfig(): Result<DeviceConfigDto> {
        val id = deviceId()
        return runCatching { api.deviceConfig(id) }.onSuccess { cfg ->
            val now = clock()
            putConfig(AppConfigKeys.TIMEZONE, cfg.timezone, now)
            putConfig(AppConfigKeys.HEARTBEAT_INTERVAL_SEC, cfg.heartbeatIntervalSec.toString(), now)
            putConfig(AppConfigKeys.SYNC_INTERVAL_SEC, cfg.syncIntervalSec.toString(), now)
            persistServerUrl(now)
            val existing = db.deviceDao().get(id)
            if (existing != null) {
                db.deviceDao().upsert(existing.copy(timezone = cfg.timezone, lastSeenAt = now, updatedAt = now))
            }
        }
    }

    suspend fun markHeartbeatSent(iso: String = utcNow()) {
        putConfig(AppConfigKeys.LAST_HEARTBEAT_AT, iso, clock())
        val id = auth.deviceId.ifBlank { deviceId() }
        val existing = db.deviceDao().get(id)
        if (existing != null) {
            val now = clock()
            db.deviceDao().upsert(existing.copy(lastSeenAt = now, updatedAt = now))
        }
    }

    private suspend fun persistStatus(status: String, token: String?, now: Long) {
        putConfig(AppConfigKeys.DEVICE_STATUS, status, now)
        if (token != null) {
            putConfig(AppConfigKeys.DEVICE_TOKEN, token, now)
        }
    }

    private suspend fun persistServerUrl(now: Long = clock()) {
        putConfig(AppConfigKeys.SERVER_URL, baseUrl, now)
    }

    private suspend fun putConfig(key: String, value: String, now: Long) {
        db.appConfigDao().upsert(AppConfigEntity(key = key, value = value, updatedAt = now))
    }

    companion object {
        fun utcNow(atMs: Long = System.currentTimeMillis()): String {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            return fmt.format(Date(atMs))
        }
    }
}

object RegistrationPolicy {
    fun nextStatus(current: String?, success: Boolean, responseStatus: String?): String {
        if (!success) return current?.ifBlank { DeviceStatus.UNREGISTERED } ?: DeviceStatus.UNREGISTERED
        val remote = responseStatus?.trim()?.uppercase().orEmpty()
        return if (remote.isEmpty() || remote == DeviceStatus.REGISTERED) {
            DeviceStatus.REGISTERED
        } else {
            remote
        }
    }
}
