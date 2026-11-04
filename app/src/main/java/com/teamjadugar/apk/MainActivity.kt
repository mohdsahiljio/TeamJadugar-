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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("JadugarPrefs", MODE_PRIVATE)
        userKey = prefs.getString("saved_key", "") ?: ""
        userExpiry = prefs.getString("saved_expiry", "") ?: ""
        
        // Key ko SessionStore mein save kar diya taaki attack ke time gayab na ho
        if (userKey.isNotEmpty()) SessionStore.saveKey(this, userKey)

        val layoutLogin = findViewById<LinearLayout>(R.id.layoutLogin)
        val layoutDashboard = findViewById<LinearLayout>(R.id.layoutDashboard)
        val etKey = findViewById<EditText>(R.id.etKey)
        val btnVerify = findViewById<Button>(R.id.btnVerify)
        val tvDays = findViewById<TextView>(R.id.tvDays)
        val tvHours = findViewById<TextView>(R.id.tvHours)
        val tvMins = findViewById<TextView>(R.id.tvMins)
        val tvSecs = findViewById<TextView>(R.id.tvSecs)
        val tvDeviceId = findViewById<TextView>(R.id.tvDeviceId)
        val tvSlots = findViewById<TextView>(R.id.tvSlots)
        val switchFloating = findViewById<Switch>(R.id.switchFloating)

        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        tvDeviceId.text = deviceId.take(10) + "..."

        if (userKey.isNotEmpty()) {
            layoutLogin.visibility = LinearLayout.GONE
            layoutDashboard.visibility = LinearLayout.VISIBLE
            startExpiryTimer(userExpiry, tvDays, tvHours, tvMins, tvSecs)
            startSlotsRefresh(tvSlots)
        } else {
            layoutLogin.visibility = LinearLayout.VISIBLE
            layoutDashboard.visibility = LinearLayout.GONE
            
            btnVerify.setOnClickListener {
                val key = etKey.text.toString().trim()
                if (key.isEmpty()) Toast.makeText(this, "Enter Key First!", Toast.LENGTH_SHORT).show()
                else verifyKey(key, layoutLogin, layoutDashboard, tvDays, tvHours, tvMins, tvSecs, tvSlots)
            }
        }

        switchFloating.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) checkVpnPermission() else stopService(Intent(this, FloatingService::class.java))
        }
    }

    private fun checkVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) startActivityForResult(intent, 102) else checkOverlayPermission()
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), 101)
        } else {
            startService(Intent(this, FloatingService::class.java))
        }
    }

    // Timer parse karne ka naya robust tarika (jo panel ka format samajhta hai)
    private fun parseExpiry(s: String): Date? {
        if (s.isBlank()) return null
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
            "yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss")
        for (p in patterns) {
            try {
                val sdf = SimpleDateFormat(p, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                sdf.parse(s)?.let { return it }
            } catch (_: Exception) {}
        }
        return s.toLongOrNull()?.let { Date(if (it < 10_000_000_000L) it * 1000 else it) }
    }

    private fun startExpiryTimer(expiryStr: String, tvDays: TextView, tvHours: TextView, tvMins: TextView, tvSecs: TextView) {
        timer?.cancel()
        val expiryDate = parseExpiry(expiryStr)
        if (expiryDate == null) {
            tvDays.text = "00"; tvHours.text = "00"; tvMins.text = "00"; tvSecs.text = "00"
            return
        }
        
        val timeLeft = expiryDate.time - System.currentTimeMillis()
        if (timeLeft <= 0) {
            tvDays.text = "00"; tvHours.text = "00"; tvMins.text = "00"; tvSecs.text = "00"
            return
        }
        
        timer = object : CountDownTimer(timeLeft, 1000) {
            override fun onTick(ms: Long) {
                tvDays.text = String.format("%02d", (ms / 86400000L).toInt())
                tvHours.text = String.format("%02d", ((ms / 3600000L) % 24).toInt())
                tvMins.text = String.format("%02d", ((ms / 60000L) % 60).toInt())
                tvSecs.text = String.format("%02d", ((ms / 1000L) % 60).toInt())
            }
            override fun onFinish() {
                tvDays.text = "00"; tvHours.text = "00"; tvMins.text = "00"; tvSecs.text = "00"
                prefs.edit().clear().apply()
                SessionStore.clear(this@MainActivity)
            }
        }.start()
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

    private fun verifyKey(key: String, layoutLogin: LinearLayout, layoutDashboard: LinearLayout, tvDays: TextView, tvHours: TextView, tvMins: TextView, tvSecs: TextView, tvSlots: TextView) {
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val panelUrl = "http://13.211.179.172:5000/connect/init"

        Thread {
            try {
                val url = URL(panelUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.doOutput = true
                conn.connectTimeout = 10000; conn.readTimeout = 10000
                
                val body = JSONObject().apply {
                    put("key", key); put("device_id", deviceId)
                }.toString()
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
                            // Activity restart nahi kar rahe, direct UI change kar rahe hain
                            layoutLogin.visibility = LinearLayout.GONE
                            layoutDashboard.visibility = LinearLayout.VISIBLE
                            startExpiryTimer(userExpiry, tvDays, tvHours, tvMins, tvSecs)
                            startSlotsRefresh(tvSlots)
                        }
                    } else {
                        runOnUiThread { Toast.makeText(this, json.optString("msg", "Invalid Key"), Toast.LENGTH_LONG).show() }
                    }
                } else {
                    runOnUiThread { Toast.makeText(this, "Panel error: HTTP $code", Toast.LENGTH_LONG).show() }
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Connection failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 102) {
            if (resultCode == RESULT_OK) checkOverlayPermission()
        } else if (requestCode == 101) {
            if (Settings.canDrawOverlays(this)) {
                startService(Intent(this, FloatingService::class.java))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}
