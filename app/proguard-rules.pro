# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/local/lib/android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.

# For example, to keep all classes in this package:
# -keep class com.example.app.** { *; }

# WorkManager
-keep class androidx.work.Worker { *; }
-keep class androidx.work.WorkerParameters { *; }

# Data Classes (Used in JSON/Reflection usually, keeping for safety though not explicitly serialized yet)
-keep class com.techtedapps.bootmaster.data.** { *; }
