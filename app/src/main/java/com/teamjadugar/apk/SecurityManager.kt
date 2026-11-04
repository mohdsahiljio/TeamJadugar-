package com.teamjadugar.apk

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import java.io.File
import java.security.MessageDigest

object SecurityManager {

    fun isDeviceCompromised(context: Context): Boolean {
        return isRooted() || isEmulator() || !isSignatureValid(context)
    }

    // 1. Root Check
    private fun isRooted(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su",
            "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
            "/system/bin/failsafe/su", "/data/local/su", "/su/bin/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }
        return false
    }

    // 2. Emulator Check
    private fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT)
    }

    // 3. Signature Check (ProGuard ke sath)
    private fun isSignatureValid(context: Context): Boolean {
        try {
            val pm = context.packageManager
            val packageName = context.packageName
            val signatures = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
            for (sig in signatures) {
                val md = MessageDigest.getInstance("SHA")
                md.update(sig.toByteArray())
                val currentSignature = Base64.encodeToString(md.digest(), Base64.DEFAULT).trim()
                // Yahan tu apna asli signature daal sakta hai (Build > Generate Signed APK ke baad milega)
                // Abhi ke liye hum bas check kar rahe hain ki signatures khaali na hon
                if (currentSignature.isNotEmpty()) return true
            }
        } catch (e: Exception) { return false }
        return false
    }
}
