# Keep all domain models, cloud DTOs, and Gson serialized classes intact
-keep @androidx.annotation.Keep class * { *; }
-keep class ua.robochyichas.app.domain.model.** { *; }
-keep class ua.robochyichas.app.data.cloud.DriveFileMetadata { *; }
-keep class ua.robochyichas.app.data.cloud.DriveFileListResponse { *; }
-keep class ua.robochyichas.app.domain.engine.ImportExportEngine$LegacyBackupV2 { *; }
-keep class ua.robochyichas.app.domain.engine.ImportExportEngine$LegacyShiftV2 { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes *Annotation*

# Comprehensive Gson rules for R8 full mode
-keepclasseswithmembers class * {
  @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers class * {
  @com.google.gson.annotations.SerializedName <fields>;
  <init>(...);
}
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Room entities and DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class *
-keepclassmembers @androidx.room.Dao class * { *; }

# Coroutines & OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
