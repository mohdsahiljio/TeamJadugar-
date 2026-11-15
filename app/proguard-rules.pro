# 🩸 SPECTRE API - PROGUARD RULES 🩸

# 1. Keep basic Android components
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver

# 2. Keep your custom App package (Taki network/API logic safe rahe)
-keep class com.teamjadugar.apk.** { *; }

# 3. Keep JSON Parsing (org.json)
-keep class org.json.** { *; }

# 4. Keep Coroutines (Kyunki network calls background thread me ho rahi hain)
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keep class kotlinx.coroutines.** { *; }

# 5. Keep Java Networking (HttpURLConnection)
-keep class java.net.** { *; }

# 6. Keep Cryptography (HMAC SHA256 ki security ke liye)
-keep class javax.crypto.** { *; }
-keep class java.security.** { *; }

# 7. Suppress warnings (Chhoti moti warnings ignore karne ke liye)
-dontwarn kotlinx.coroutines.**
-dontwarn org.json.**
-dontwarn javax.crypto.**
