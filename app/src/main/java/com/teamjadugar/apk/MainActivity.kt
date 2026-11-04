package com.teamjadugar.apk

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class MainActivity : AppCompatActivity() {

    companion object {
        @Volatile var userKey: String = ""
        @Volatile var userExpiry: String = ""
        private const val SECRET_SIGNATURE_KEY = "SPECTRE_SECURE_2026"
        
        // 🩸 APNA GITHUB USERNAME AUR REPO NAME YAHAN DAAL
        private const val GITHUB_USER = "mohdsahiljio"
        private const val GITHUB_REPO = "TeamJadugar-"
    }

    private lateinit var prefs: SharedPreferences
    private var timer: CountDownTimer? = null
    private lateinit var layoutLogin: LinearLayout
    private lateinit var layoutDashboard: LinearLayout
    private lateinit var etKey: EditText
    private lateinit var btnVerify: Button

    private var downloadID: Long = 0
    private var updateUrl: String = ""

    // 🩸 Download Complete Receiver
    private val onDownloadComplete = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == downloadID) {
                installAPK()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (SecurityManager.isDeviceCompromised(this)) {
            setContentView(TextView(this).apply {
                text = "SECURITY VIOLATION DETECTED."
                textSize = 18f
                setTextColor(android.graphics.Color.RED)
                gravity = android.view.Gravity.CENTER
            })
            return
        }

        setContentView(R.layout.activity_main)

        // 🩸 Update Check
        checkForUpdates()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(onDownloadComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), RECEIVER_EXPORTED)
        } else {
            registerReceiver(onDownloadComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }

        prefs = getSharedPreferences("JadugarPrefs", MODE_PRIVATE)
        userKey = prefs.getString("saved_key", "") ?: ""
        userExpiry = prefs.getString("saved_expiry", "") ?: ""
        if (userKey.isNotEmpty()) SessionStore.saveKey(this, userKey)

        layoutLogin = findViewById(R.id.layoutLogin)
        layoutDashboard = findViewById(R.id.layoutDashboard)
        etKey = findViewById(R.id.etKey)
        btnVerify = findViewById(R.id.btnVerify)
        val tvDays = findViewById<TextView>(R.id.tvDays)
        val tvHours = findViewById<TextView>(R.id.tvHours)
        val tvMins = findViewById<TextView>(R.id.tvMins)
        val tvSecs = findViewById<TextView>(R.id.tvSecs)
        val tvDeviceId = findViewById<TextView>(R.id.tvDeviceId)
        val tvSlots = findViewById<TextView>(R.id.tvSlots)
        val switchFloating = findViewById<Switch>(R.id.switchFloating)

        tvDeviceId.text = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).take(10) + "..."

        btnVerify.setOnClickListener {
            val key = etKey.text.toString().trim()
            if (key.isEmpty()) Toast.makeText(this, "Enter Key First!", Toast.LENGTH_SHORT).show()
            else verifyKey(key, layoutLogin, layoutDashboard, tvDays, tvHours, tvMins, tvSecs, tvSlots)
        }

        switchFloating.setOnCheckedChangeListener { _, checked ->
            if (checked) checkVpnPermission()
            else stopService(Intent(this, FloatingService::class.java))
        }

        if (userKey.isNotEmpty()) {
            silentLiveCheck(layoutLogin, layoutDashboard, tvDays, tvHours, tvMins, tvSecs, tvSlots)
        } else {
            layoutLogin.visibility = LinearLayout.VISIBLE
            layoutDashboard.visibility = LinearLayout.GONE
        }
    }

    private fun checkForUpdates() {
        Thread {
            try {
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                val currentVersionName = packageInfo.versionName ?: "1.0" 

                val url = URL("https://api.github.com/repos/$GITHUB_USER/$GITHUB_REPO/releases/latest")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    
                    val latestVersion = json.optString("tag_name", "1.0").replace("v", "").trim()
                    val downloadLink = json.optJSONArray("assets")?.optJSONObject(0)?.optString("browser_download_url", "")
                    
                    if (latestVersion != currentVersionName && latestVersion > currentVersionName && !downloadLink.isNullOrEmpty()) {
                        updateUrl = downloadLink
                        runOnUiThread { showUpdateDialog(latestVersion) }
                    }
                }
            } catch (e: Exception) {}
        }.start()
    }

    private fun showUpdateDialog(newVersion: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("🚀 Update Available (v$newVersion)")
        builder.setMessage("A new version of SPECTRE API is available. Click UPDATE to download.")
        builder.setCancelable(false)
        builder.setPositiveButton("UPDATE NOW") { _, _ -> downloadAndInstallAPK() }
        builder.show()
    }

    private fun downloadAndInstallAPK() {
        val request = DownloadManager.Request(Uri.parse(updateUrl))
            .setTitle("SPECTRE API Update")
            .setDescription("Downloading new version...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, "update.apk")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        downloadID = downloadManager.enqueue(request)
        Toast.makeText(this, "Downloading Update...", Toast.LENGTH_SHORT).show()
    }

    private fun installAPK() {
        val file = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "update.apk")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).setData(Uri.parse("package:$packageName")))
                Toast.makeText(this, "Please allow install from unknown sources and try again.", Toast.LENGTH_LONG).show()
                return
            }
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(FileProvider.getUriForFile(this@MainActivity, "$packageName.provider", file), "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        if (userKey.isNotEmpty() && layoutDashboard.visibility == LinearLayout.VISIBLE) {
            silentLiveCheck(layoutLogin, layoutDashboard, 
                findViewById(R.id.tvDays), findViewById(R.id.tvHours), 
                findViewById(R.id.tvMins), findViewById(R.id.tvSecs), 
                findViewById(R.id.tvSlots))
        }
    }

    private fun generateHMAC(jsonString: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(SECRET_SIGNATURE_KEY.toByteArray(), "HmacSHA256")
        mac.init(secretKeySpec)
        val signatureBytes = mac.doFinal(jsonString.toByteArray())
        return signatureBytes.joinToString("") { String.format("%02x", it) }
    }

    private fun silentLiveCheck(layoutLogin: LinearLayout, layoutDashboard: LinearLayout, tvDays: TextView, tvHours: TextView, tvMins: TextView, tvSecs: TextView, tvSlots: TextView) {
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        Thread {
            try {
                val url = URL("http://13.211.179.172:5000/connect/init")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.doOutput = true
                conn.connectTimeout = 5000; conn.readTimeout = 5000

                val body = JSONObject().apply { put("key", userKey); put("device_id", deviceId) }.toString()
                conn.setRequestProperty("X-App-Signature", generateHMAC(body))
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                val resp = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText() ?: ""

                if (code in 200..299) {
                    val json = JSONObject(resp)
                    if (json.optBoolean("valid", false)) {
                        userExpiry = json.optString("expires_at", "")
                        runOnUiThread {
                            if (layoutDashboard.visibility != LinearLayout.VISIBLE) {
                                layoutLogin.visibility = LinearLayout.GONE
                                layoutDashboard.visibility = LinearLayout.VISIBLE
                                startExpiryTimer(userExpiry, tvDays, tvHours, tvMins, tvSecs)
                                startSlotsRefresh(tvSlots)
                            }
                        }
                    } else {
                        runOnUiThread { logoutAndShowError(json.optString("msg", "Unauthorized Access")) }
                    }
                } else {
                    runOnUiThread { logoutAndShowError("Security Breach or Server Error") }
                }
            } catch (e: Exception) {
                runOnUiThread { logoutAndShowError("Connection failed. Please login again.") }
            }
        }.start()
    }

    private fun verifyKey(key: String, layoutLogin: LinearLayout, layoutDashboard: LinearLayout, tvDays: TextView, tvHours: TextView, tvMins: TextView, tvSecs: TextView, tvSlots: TextView) {
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        btnVerify.isEnabled = false
        btnVerify.text = "VERIFYING..."

        Thread {
            try {
                val url = URL("http://13.211.179.172:5000/connect/init")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.doOutput = true
                conn.connectTimeout = 10000; conn.readTimeout = 10000

                val body = JSONObject().apply { put("key", key); put("device_id", deviceId) }.toString()
                conn.setRequestProperty("X-App-Signature", generateHMAC(body))
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                val resp = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText() ?: ""

                if (code in 200..299) {
                    val json = JSONObject(resp)
                    if (json.optBoolean("valid", false)) {
                        userKey = key
                        userExpiry = json.optString("expires_at", "")
                        prefs.edit().putString("saved_key", userKey).apply()
                        prefs.edit().putString("saved_expiry", userExpiry).apply()
                        SessionStore.saveKey(this, userKey)

                        runOnUiThread {
                            Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show()
                            layoutLogin.visibility = LinearLayout.GONE
                            layoutDashboard.visibility = LinearLayout.VISIBLE
                            startExpiryTimer(userExpiry, tvDays, tvHours, tvMins, tvSecs)
                            startSlotsRefresh(tvSlots)
                        }
                    } else {
                        runOnUiThread { Toast.makeText(this, json.optString("msg", "Invalid Key"), Toast.LENGTH_LONG).show() }
                    }
                } else {
                    runOnUiThread { Toast.makeText(this, "Security Error / Server Down", Toast.LENGTH_LONG).show() }
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Connection failed: ${e.message}", Toast.LENGTH_LONG).show() }
            } finally {
                runOnUiThread { btnVerify.isEnabled = true; btnVerify.text = "🛡️ AUTHENTICATE" }
            }
        }.start()
    }

    private fun logoutAndShowError(msg: String) {
        stopService(Intent(this, FloatingService::class.java))
        SessionStore.clear(this)
        prefs.edit().clear().apply()
        userKey = ""
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        layoutDashboard.visibility = LinearLayout.GONE
        layoutLogin.visibility = LinearLayout.VISIBLE
        etKey.setText("")
    }

    private fun checkVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) startActivityForResult(intent, 102)
        else checkOverlayPermission()
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), 101)
        } else startService(Intent(this, FloatingService::class.java))
    }

    private fun startExpiryTimer(expiryStr: String, tvDays: TextView, tvHours: TextView, tvMins: TextView, tvSecs: TextView) {
        timer?.cancel()
        val expiryDate = parseExpiry(expiryStr)
        if (expiryDate == null) return
        
        val timeLeft = expiryDate.time - System.currentTimeMillis()
        if (timeLeft <= 0) return
        
        timer = object : CountDownTimer(timeLeft, 1000) {
            override fun onTick(ms: Long) {
                tvDays.text = String.format("%02d", (ms / 86400000L).toInt())
                tvHours.text = String.format("%02d", ((ms / 3600000L) % 24).toInt())
                tvMins.text = String.format("%02d", ((ms / 60000L) % 60).toInt())
                tvSecs.text = String.format("%02d", ((ms / 1000L) % 60).toInt())
            }
            override fun onFinish() { logoutAndShowError("Key Expired. Please buy a new one.") }
        }.start()
    }

    private fun parseExpiry(s: String): Date? {
        if (s.isBlank()) return null
        val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSSSSS", "yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss")
        for (p in patterns) {
            try {
                val sdf = SimpleDateFormat(p, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                sdf.parse(s)?.let { return it }
            } catch (_: Exception) {}
        }
        return s.toLongOrNull()?.let { Date(if (it < 10_000_000_000L) it * 1000 else it) }
    }

    private fun startSlotsRefresh(tvSlots: TextView) {
        Thread {
            while (true) {
                try {
                    val url = URL("http://13.211.179.172:5000/connect/slots")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 3000; conn.readTimeout = 3000
                    if (conn.responseCode == 200) {
                        val json = JSONObject(conn.inputStream.bufferedReader().readText())
                        val u = json.optInt("slots_used", 0)
                        val t = json.optInt("slots_total", 10)
                        runOnUiThread { tvSlots.text = "$u/$t" }
                    }
                } catch (_: Exception) {}
                try { Thread.sleep(5000) } catch (_: InterruptedException) { break }
            }
        }.apply { isDaemon = true; start() }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 102 && res == RESULT_OK) checkOverlayPermission()
        else if (req == 101 && Settings.canDrawOverlays(this)) startService(Intent(this, FloatingService::class.java))
    }

    override fun onDestroy() { 
        super.onDestroy()
        timer?.cancel()
        try { unregisterReceiver(onDownloadComplete) } catch (e: Exception) {}
    }
}
