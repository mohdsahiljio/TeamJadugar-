package com.teamjadugar.apk

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        @Volatile var userKey: String = ""
        @Volatile var userExpiry: String = ""
    }

    private lateinit var prefs: SharedPreferences
    private var timer: CountDownTimer? = null

    // UI Elements
    private lateinit var layoutLogin: LinearLayout
    private lateinit var layoutDashboard: LinearLayout
    private lateinit var etKey: EditText
    private lateinit var btnVerify: Button
    private lateinit var tvDays: TextView
    private lateinit var tvHours: TextView
    private lateinit var tvMins: TextView
    private lateinit var tvSecs: TextView
    private lateinit var tvDeviceId: TextView
    private lateinit var tvSlots: TextView
    private lateinit var switchFloating: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // SECURITY CHECK
        if (SecurityManager.isDeviceCompromised(this)) {
            setContentView(TextView(this).apply {
                text = "SECURITY VIOLATION DETECTED.\nApp cannot run on Rooted/Emulator devices."
                textSize = 18f
                setTextColor(android.graphics.Color.RED)
                gravity = android.view.Gravity.CENTER
            })
            return
        }

        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("JadugarPrefs", MODE_PRIVATE)
        userKey = prefs.getString("saved_key", "") ?: ""
        userExpiry = prefs.getString("saved_expiry", "") ?: ""
        if (userKey.isNotEmpty()) SessionStore.saveKey(this, userKey)

        layoutLogin = findViewById(R.id.layoutLogin)
        layoutDashboard = findViewById(R.id.layoutDashboard)
        etKey = findViewById(R.id.etKey)
        btnVerify = findViewById(R.id.btnVerify)
        tvDays = findViewById(R.id.tvDays)
        tvHours = findViewById(R.id.tvHours)
        tvMins = findViewById(R.id.tvMins)
        tvSecs = findViewById(R.id.tvSecs)
        tvDeviceId = findViewById(R.id.tvDeviceId)
        tvSlots = findViewById(R.id.tvSlots)
        switchFloating = findViewById(R.id.switchFloating)

        tvDeviceId.text = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).take(10) + "..."

        // Login Button
        btnVerify.setOnClickListener {
            val key = etKey.text.toString().trim()
            if (key.isEmpty()) Toast.makeText(this, "Enter Key First!", Toast.LENGTH_SHORT).show()
            else verifyKey(key)
        }

        // Switch
        switchFloating.setOnCheckedChangeListener { _, checked ->
            if (checked) checkVpnPermission()
            else stopService(Intent(this, FloatingService::class.java))
        }

        // App open hote hi silent check karega. Agar key saved hai toh Dashboard dikha dega, warna Login.
        if (userKey.isNotEmpty()) {
            silentLiveCheck() 
        } else {
            layoutLogin.visibility = LinearLayout.VISIBLE
            layoutDashboard.visibility = LinearLayout.GONE
        }
    }

    // 🩸 APP BACKGROUND SE OPEN HONE PE VERIFY (The Real Check)
    override fun onResume() {
        super.onResume()
        if (userKey.isNotEmpty() && layoutDashboard.visibility == LinearLayout.VISIBLE) {
            silentLiveCheck()
        }
    }

    // 🩸 SILENT VERIFY (Jab App khule ya background se aaye)
    private fun silentLiveCheck() {
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val panelUrl = "http://13.211.179.172:5000/connect/init"

        Thread {
            try {
                val url = URL(panelUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.doOutput = true
                conn.connectTimeout = 5000; conn.readTimeout = 5000
                
                val body = JSONObject().apply { put("key", userKey); put("device_id", deviceId) }.toString()
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                val resp = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText() ?: ""

                if (code in 200..299) {
                    val json = JSONObject(resp)
                    if (json.optBoolean("valid", false)) {
                        // Agar valid hai, toh dashboard show karo (agar pehle se nahi hai)
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
                        // Key block/delete/reset ho gayi hai
                        val msg = json.optString("msg", "Unauthorized Access")
                        runOnUiThread { logoutAndShowError(msg) }
                    }
                }
            } catch (e: Exception) {
                // Agar internet nahi hai, toh dashboard hide kar ke login screen dikhao
                runOnUiThread { 
                    layoutLogin.visibility = LinearLayout.VISIBLE
                    layoutDashboard.visibility = LinearLayout.GONE
                    Toast.makeText(this, "Connection failed. Please login again.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    // 🩸 MANUAL VERIFY (Login button dabane pe)
    private fun verifyKey(key: String) {
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val panelUrl = "http://13.211.179.172:5000/connect/init"

        btnVerify.isEnabled = false
        btnVerify.text = "VERIFYING..."

        Thread {
            try {
                val url = URL(panelUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.doOutput = true
                conn.connectTimeout = 10000; conn.readTimeout = 10000
                
                val body = JSONObject().apply { put("key", key); put("device_id", deviceId) }.toString()
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
                        val msg = json.optString("msg", "Invalid Key")
                        runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
                    }
                } else {
                    runOnUiThread { Toast.makeText(this, "Panel error: HTTP $code", Toast.LENGTH_LONG).show() }
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Connection failed: ${e.message}", Toast.LENGTH_LONG).show() }
            } finally {
                runOnUiThread { 
                    btnVerify.isEnabled = true 
                    btnVerify.text = "🛡️ AUTHENTICATE"
                }
            }
        }.start()
    }

    // 🩸 LOGOUT AND ERROR (Jab key reset/block/expire ho)
    private fun logoutAndShowError(msg: String) {
        stopService(Intent(this, FloatingService::class.java))
        SessionStore.clear(this)
        prefs.edit().clear().apply()
        userKey = ""
        userExpiry = ""
        
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
            override fun onFinish() {
                logoutAndShowError("Key Expired. Please buy a new one.")
            }
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
    }
}
