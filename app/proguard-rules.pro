# Room entities & DAOs
-keep class com.ophoto.data.local.entity.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# CameraX / Camera2 Interop
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.camera2.pipe.**

# Preserve data models serialized via org.json reflection-free mappers
-keepclassmembers class com.ophoto.** {
    public <init>(...);
}
