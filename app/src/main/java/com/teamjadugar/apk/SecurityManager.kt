package com.teamjadugar.apk

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest

object SecurityManager {

    fun isDeviceCompromised(context: Context): Boolean {
        // Agar koi bhi check fail hua toh true return karega (App band ho jayegi)
        return isRooted() || isEmulator() || isDebugged() || isFridaRunning() || isXposedActive() || isHooked() || !verifySignature(context)
    }

    private fun isRooted(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su",
            "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
            "/system/bin/failsafe/su", "/data/local/su", "/su/bin/su",
            "/system/xbin/daemonsu", "/data/adb/magisk", "/sbin/.magisk"
        )
        for (path in paths) { if (File(path).exists()) return true }
        return false
    }

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

    private fun isDebugged(): Boolean {
        if (Debug.isDebuggerConnected()) return true
        try {
            BufferedReader(FileReader("/proc/self/status")).use { br ->
                var line = br.readLine()
                while (line != null) {
                    if (line.startsWith("TracerPid:")) {
                        if ((line.substring(10).trim().toIntOrNull() ?: 0) != 0) return true
                    }
                    line = br.readLine()
                }
            }
        } catch (_: Exception) {}
        return false
    }

    private fun isFridaRunning(): Boolean {
        val ports = intArrayOf(27042, 27043, 4444)
        for (port in ports) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress("127.0.0.1", port), 100)
                socket.close()
                return true
            } catch (_: Exception) {}
        }
        return false
    }

    private fun isXposedActive(): Boolean {
        try {
            Class.forName("de.robv.android.xposed.XposedBridge")
            return true
        } catch (_: ClassNotFoundException) {}
        return false
    }

    private fun isHooked(): Boolean {
        try {
            throw Exception("check")
        } catch (e: Exception) {
            for (el in e.stackTrace) {
                val cls = el.className.lowercase()
                if (cls.contains("xposed") || cls.contains("substrate") || cls.contains("lsposed") || cls.contains("frida")) {
                    return true
                }
            }
        }
        return false
    }

    private fun verifySignature(context: Context): Boolean {
        // 🔥 IMPORTANT: Jab tera final release APK ban jaye, tab isko hash se replace karna.
        // Niche tarika diya hai hash nikalne ka.
        val expectedHash = "APNA_APK_HASH_YAHAN"
        if (expectedHash == "APNA_APK_HASH_YAHAN") return true

        try {
            @Suppress("DEPRECATION")
            val pi = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            for (sig in pi.signatures) {
                val md = MessageDigest.getInstance("SHA-256")
                val hash = md.digest(sig.toByteArray())
                val hex = hash.joinToString("") { "%02x".format(it) }
                if (hex.equals(expectedHash, ignoreCase = true)) return true
            }
        } catch (_: Exception) {}
        return false
    }
}
