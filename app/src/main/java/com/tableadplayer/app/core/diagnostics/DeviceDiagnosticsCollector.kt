package com.tableadplayer.app.core.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import com.tableadplayer.app.BuildConfig
import com.tableadplayer.app.core.crash.CrashGuard
import com.tableadplayer.app.core.device.DeviceIdentity
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeviceDiagnosticsCollector(
    private val context: Context,
    private val deviceIdentity: DeviceIdentity = DeviceIdentity(context.applicationContext),
) {

    suspend fun collect(): DiagnosticsSnapshot = withContext(Dispatchers.IO) {
        val deviceId = deviceIdentity.getOrCreate()
        DiagnosticsSnapshot(
            capturedAt = utcNow(),
            deviceId = deviceId,
            androidId = androidId(),
            app = app(),
            os = os(),
            hardware = hardware(),
            display = display(),
            memory = memory(),
            storage = storage(),
            battery = battery(),
            network = network(),
            safeMode = CrashGuard.inSafeMode(context),
        )
    }

    private fun utcNow(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }

    private fun androidId(): String? {
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return id?.takeIf { it.isNotBlank() }
    }

    private fun app() = AppDiagnostics(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE.toLong(),
        applicationId = BuildConfig.APPLICATION_ID,
        buildType = BuildConfig.BUILD_TYPE,
        demoMode = BuildConfig.DEMO_MODE,
        apiBaseUrl = BuildConfig.API_BASE_URL,
    )

    private fun os() = OsDiagnostics(
        androidVersion = Build.VERSION.RELEASE ?: "unknown",
        apiLevel = Build.VERSION.SDK_INT,
        securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Build.VERSION.SECURITY_PATCH
        } else {
            null
        },
        fingerprint = Build.FINGERPRINT ?: "",
    )

    private fun hardware(): HardwareDiagnostics {
        val deviceName = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            } else {
                Settings.System.getString(context.contentResolver, "device_name")
            }
        }.getOrNull()?.takeIf { !it.isNullOrBlank() }

        return HardwareDiagnostics(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            brand = Build.BRAND.orEmpty(),
            model = Build.MODEL.orEmpty(),
            device = Build.DEVICE.orEmpty(),
            product = Build.PRODUCT.orEmpty(),
            board = Build.BOARD.orEmpty(),
            hardware = Build.HARDWARE.orEmpty(),
            deviceName = deviceName,
            cpuAbis = Build.SUPPORTED_ABIS?.toList().orEmpty(),
        )
    }

    private fun display(): DisplayDiagnostics {
        val metrics = DisplayMetrics()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        var width: Int
        var height: Int
        var refresh: Float? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            width = bounds.width()
            height = bounds.height()
            refresh = wm.defaultDisplay.refreshRate
        } else {
            @Suppress("DEPRECATION")
            val display = wm.defaultDisplay
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
            width = metrics.widthPixels
            height = metrics.heightPixels
            @Suppress("DEPRECATION")
            refresh = display.refreshRate
        }
        val dm = context.resources.displayMetrics
        return DisplayDiagnostics(
            widthPx = width,
            heightPx = height,
            density = dm.density,
            densityDpi = dm.densityDpi,
            scaledDensity = dm.scaledDensity,
            refreshRateHz = refresh,
        )
    }

    private fun memory(): MemoryDiagnostics {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val runtime = Runtime.getRuntime()
        return MemoryDiagnostics(
            totalRamBytes = info.totalMem,
            availableRamBytes = info.availMem,
            lowMemory = info.lowMemory,
            runtimeMaxBytes = runtime.maxMemory(),
            runtimeUsedBytes = runtime.totalMemory() - runtime.freeMemory(),
        )
    }

    private fun storage(): StorageDiagnostics {
        val data = Environment.getDataDirectory()
        val stat = StatFs(data.path)
        val filesDir = context.filesDir
        return StorageDiagnostics(
            dataTotalBytes = stat.totalBytes,
            dataFreeBytes = stat.availableBytes,
            appFilesDirBytes = filesDir.usableSpace,
        )
    }

    private fun battery(): BatteryDiagnostics {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val percent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val cap = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            cap.takeIf { it >= 0 }
        } else {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) (level * 100) / scale else null
        }
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL ||
            plugged != 0
        return BatteryDiagnostics(
            percent = percent,
            charging = charging,
            plugged = pluggedLabel(plugged),
            status = statusLabel(status),
            health = healthLabel(health),
            temperatureC = if (temp == Int.MIN_VALUE) null else temp / 10f,
        )
    }

    private fun network(): NetworkDiagnostics {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = cm.activeNetwork
        val caps = active?.let { cm.getNetworkCapabilities(it) }
        val connected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val ethernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        val cellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val ssid = wifiSsid()
        val down = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            caps?.linkDownstreamBandwidthKbps?.takeIf { it > 0 }
        } else {
            null
        }
        return NetworkDiagnostics(
            connected = connected,
            wifi = wifi,
            ethernet = ethernet,
            cellular = cellular,
            wifiSsid = ssid,
            linkDownstreamKbps = down,
            interfaces = listInterfaces(),
        )
    }

    private fun wifiSsid(): String? {
        return try {
            @Suppress("DEPRECATION")
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            @Suppress("DEPRECATION")
            val raw = wm?.connectionInfo?.ssid
            raw?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" && it != "0x" }
        } catch (_: SecurityException) {
            null
        }
    }

    private fun listInterfaces(): List<NetworkInterfaceInfo> {
        return runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { !it.isLoopback }
                .map { ni ->
                    NetworkInterfaceInfo(
                        name = ni.name,
                        up = ni.isUp,
                        addresses = ni.inetAddresses.toList().mapNotNull { addr ->
                            addr.hostAddress?.substringBefore('%')
                        },
                    )
                }
        }.getOrElse { emptyList() }
    }

    private fun pluggedLabel(plugged: Int): String? = when (plugged) {
        0 -> "unplugged"
        BatteryManager.BATTERY_PLUGGED_AC -> "ac"
        BatteryManager.BATTERY_PLUGGED_USB -> "usb"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
        else -> "other:$plugged"
    }

    private fun statusLabel(status: Int): String? = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
        BatteryManager.BATTERY_STATUS_FULL -> "full"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
        BatteryManager.BATTERY_STATUS_UNKNOWN -> "unknown"
        else -> null
    }

    private fun healthLabel(health: Int): String? = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
        BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
        BatteryManager.BATTERY_HEALTH_COLD -> "cold"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "failure"
        else -> null
    }
}
