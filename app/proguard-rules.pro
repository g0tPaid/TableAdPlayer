# TableAdPlayer R8 / ProGuard (release minify).
# Keep Retrofit, Room, kotlinx.serialization, Media3, WorkManager, and the app's
# JSON DTOs. Do not keep third-party advertising SDKs — this app has none.

-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepattributes *Annotation*

-keep class kotlin.Metadata { *; }
-keep class com.tableadplayer.app.BuildConfig { *; }

# --- Retrofit + OkHttp + Kotlin coroutines (suspend service methods) ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep class com.tableadplayer.app.data.remote.TableAdApi { *; }

# --- kotlinx.serialization ---
-dontwarn kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.tableadplayer.app.**$$serializer { *; }
-keepclassmembers class com.tableadplayer.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.tableadplayer.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-keep @kotlinx.serialization.Serializable class com.tableadplayer.app.** { *; }

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-keep class com.tableadplayer.app.data.local.** { *; }
-dontwarn androidx.room.paging.**
-dontwarn androidx.room.coroutines.**

# --- Media3 / ExoPlayer ---
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# --- WorkManager (SyncWorker, ReportingWorker) ---
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.tableadplayer.app.sync.SyncWorker { *; }
-keep class com.tableadplayer.app.reporting.ReportingWorker { *; }

# --- App components referenced from the manifest ---
-keep class com.tableadplayer.app.TableAdPlayerApp { *; }
-keep class com.tableadplayer.app.ui.** { *; }
-keep class com.tableadplayer.app.kiosk.** { *; }
-keep class com.tableadplayer.app.debug.** { *; }

# FileProvider paths
-keep class androidx.core.content.FileProvider { *; }

# Enums used in Room converters / queries
-keepclassmembers enum com.tableadplayer.app.data.local.** { *; }

# Coroutines
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
