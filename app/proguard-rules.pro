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

# Apache Commons Compress (pulled in by youtubedl-android) unzips the Python/yt-dlp payloads during
# YoutubeDL.init(). Its ExtraFieldUtils static initialiser REFLECTIVELY instantiates every
# ZipExtraField implementation (Class.newInstance()). Because nothing in the app constructs those
# classes directly, R8 turned them abstract and renamed them, so the registry blew up with
# "class ...zip.a is not a concrete class" -> ExceptionInInitializerError -> the engine could never
# initialise and the app crashed on every launch. Keep the implementations concrete, named and
# default-constructible.
-keep class org.apache.commons.compress.archivers.zip.ZipExtraField
-keep class * implements org.apache.commons.compress.archivers.zip.ZipExtraField {
    <init>();
    *;
}
-keep class org.apache.commons.compress.archivers.zip.ExtraFieldUtils { *; }
-dontwarn org.apache.commons.compress.**
-dontwarn org.apache.commons.io.**

# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# Hilt / Dagger generated code is handled by their own consumer rules.

# Keep enum values used by serialization.
-keepclassmembers enum * { *; }
