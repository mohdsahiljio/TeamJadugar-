# 🔥 SABLE SECURITY PROGUARD RULES

# Keep AndroidX Security Crypto (EncryptedSharedPreferences)
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# Keep Gson models (JSON parsing ke liye)
-keep class com.teamjadugar.apk.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# Keep OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# Obfuscate (Hide) main app logic
-repackageclasses ''
-allowaccessmodification
