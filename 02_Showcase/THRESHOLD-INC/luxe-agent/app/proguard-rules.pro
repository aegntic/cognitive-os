# Luxe Threshold ProGuard / R8 rules
# Keep for Compose
-keep class androidx.compose.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }

# Gson
-keep class com.thresholdinc.luxe.** { *; }
-keep class com.google.gson.** { *; }
-keep class * implements java.io.Serializable { *; }

# For LuxeAction and schema
-keep class com.thresholdinc.luxe.LuxeAction { *; }
-keep class com.thresholdinc.luxe.LuxeSchema { *; }

# AndroidX / lifecycle
-keep class androidx.lifecycle.** { *; }

# OkHttp for LLM endpoint
-keep class okhttp3.** { *; }

# No obfuscation of main entry for handoff
-keep class com.thresholdinc.luxe.MainActivity { *; }