# --- Flipper the Ripper ProGuard/R8 rules ---

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class io.celox.flipperripper.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.celox.flipperripper.**$$serializer { *; }

# youtubedl-android relies on reflection into its native/asset payloads.
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.ffmpeg.** { *; }
-keep class com.yausername.aria2c.** { *; }
-dontwarn com.yausername.**

# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# Hilt / Dagger generated code is handled by their own consumer rules.

# Keep enum values used by serialization.
-keepclassmembers enum * { *; }
