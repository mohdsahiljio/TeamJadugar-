package com.teamjadugar.apk

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class MainActivity : AppCompatActivity() {

    companion object {
        @Volatile var userKey: String = ""
        @Volatile var userExpiry: String = ""
        val networkExecutor = Executors.newFixedThreadPool(4)
    }

    private lateinit var prefs: SharedPreferences
    private var timer: CountDownTimer? = null
    private lateinit var layoutLogin: LinearLayout
    private lateinit var layoutDashboard: LinearLayout
    private lateinit var etKey: EditText
    private lateinit var btnVerify: Button
    private var toneGenerator: ToneGenerator? = null
    private lateinit var switchFloating: Switch
    private lateinit var imgLoginBg: ImageView
    private lateinit var imgDashBg: ImageView

    private fun playSound(type: String) {
        try {
            if (toneGenerator == null) toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            when (type) {
                "click" -> toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 50)
                "success" -> toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 150)
                "error" -> toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 150)
            }
        } catch (e: Exception) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (SecurityManager.isDeviceCompromised(this)) {
            Toast.makeText(this, "❌ SECURITY ALERT: Rooted or Emulator Detected!", Toast.LENGTH_LONG).show()
            finishAffinity()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }

        prefs = getSharedPreferences("JadugarPrefs", MODE_PRIVATE)
        userKey = prefs.getString("saved_key", "") ?: ""
        userExpiry = prefs.getString("saved_expiry", "") ?: ""
        if (userKey.isNotEmpty()) SessionStore.saveKey(this, userKey)

        imgLoginBg = findViewById(R.id.imgLoginBg)
        imgDashBg = findViewById(R.id.imgDashBg)
        layoutLogin = findViewById(R.id.layoutLogin)
        layoutDashboard = findViewById(R.id.layoutDashboard)
        etKey = findViewById(R.id.etKey)
        btnVerify = findViewById(R.id.btnVerify)
        switchFloating = findViewById(R.id.switchFloating)
        
        val tvDays = findViewById<TextView>(R.id.tvDays)
        val tvHours = findViewById<TextView>(R.id.tvHours)
        val tvMins = findViewById<TextView>(R.id.tvMins)
        val tvSecs = findViewById<TextView>(R.id.tvSecs)
        val tvDeviceId = findViewById<TextView>(R.id.tvDeviceId)
        val tvSlots = findViewById<TextView>(R.id.tvSlots)
        val tvPlanType = findViewById<TextView>(R.id.tvPlanType)
        val tvExpiryDate = findViewById<TextView>(R.id.tvExpiryDate)
        val tvDashboardExpiry = findViewById<TextView>(R.id.tvDashboardExpiry)

        val rawDeviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        tvDeviceId.text = rawDeviceId.take(8).uppercase()

        btnVerify.setOnClickListener {
            if (!btnVerify.isEnabled) return@setOnClickListener
            playSound("click")
            val key = etKey.text.toString().trim()
            if (key.isEmpty()) {
                Toast.makeText(this, "Enter Key First!", Toast.LENGTH_SHORT).show()
            } else {
                verifyKey(key, layoutLogin, layoutDashboard, tvDays, tvHours, tvMins, tvSecs, tvSlots, tvPlanType, tvExpiryDate, tvDashboardExpiry)
            }
        }

        switchFloating.setOnCheckedChangeListener { _, checked ->
            playSound("click")
            if (checked) {
                checkVpnPermission()
            } else {
                stopService(Intent(this, FloatingService::class.java))
                Toast.makeText(this, "Floating Panel Stopped", Toast.LENGTH_SHORT).show()
            }
        }

        if (userKey.isNotEmpty()) {
            silentLiveCheck(layoutLogin, layoutDashboard, tvDays, tvHours, tvMins, tvSecs, tvSlots, tvPlanType, tvExpiryDate, tvDashboardExpiry)
        } else {
            imgLoginBg.visibility = View.VISIBLE
            imgDashBg.visibility = View.GONE
            layoutLogin.visibility = View.VISIBLE
            layoutDashboard.visibility = View.GONE
        }
    }

    private fun verifyKey(key: String, layoutLogin: LinearLayout, layoutDashboard: LinearLayout, tvDays: TextView, tvHours: TextView, tvMins: TextView, tvSecs: TextView, tvSlots: TextView, tvPlanType: TextView, tvExpiryDate: TextView, tvDashboardExpiry: TextView) {
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        btnVerify.isEnabled = false
        btnVerify.text = "VERIFYING..."

        networkExecutor.execute {
            try {
                // Yahan KeyVault.getPanelUrl() use ho raha hai (Jo tera spectreapi.net hai)
                val url = URL("${KeyVault.getPanelUrl()}/connect/init")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.doOutput = true
                conn.connectTimeout = 10000; conn.readTimeout = 10000

                val body = JSONObject().apply { put("key", key); put("device_id", deviceId) }.toString()
                
                val timestamp = (System.currentTimeMillis() / 1000).toString()
                val nonce = UUID.randomUUID().toString().replace("-", "").take(16)
                
                val mac = Mac.getInstance("HmacSHA256")
                mac.init(SecretKeySpec(KeyVault.getHmacKey(), "HmacSHA256"))
                
                val tsBytes = ByteBuffer.allocate(8).putLong(timestamp.toLong()).array()
                val msgBytes = tsBytes + body.toByteArray() + deviceId.toByteArray()
                val signature = mac.doFinal(msgBytes).joinToString("") { String.format("%02x", it) }
                
                conn.setRequestProperty("X-App-Signature", signature)
                conn.setRequestProperty("X-App-Timestamp", timestamp)
                conn.setRequestProperty("X-App-Nonce", nonce)
                conn.setRequestProperty("X-Device-Id", deviceId)
                
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
                            playSound("success")
                            Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show()
                            
                            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.hideSoftInputFromWindow(etKey.windowToken, 0)
                            etKey.clearFocus()
                            findViewById<View>(android.R.id.content).requestFocus()

                            imgLoginBg.visibility = View.GONE
                            imgDashBg.visibility = View.VISIBLE

                            layoutLogin.visibility = View.GONE
                            layoutDashboard.visibility = View.VISIBLE
                            
                            tvExpiryDate.text = ": " + formatExpiryString(userExpiry)
                            tvPlanType.text = ": Premium"
                            tvDashboardExpiry.text = formatExpiryString(userExpiry) 
                            
                            startExpiryTimer(userExpiry, tvDays, tvHours, tvMins, tvSecs)
                            startSlotsRefresh(tvSlots)
                        }
                    } else {
                        runOnUiThread { 
                            playSound("error")
                            Toast.makeText(this, json.optString("msg", "Invalid Key"), Toast.LENGTH_LONG).show() 
                            btnVerify.isEnabled = true
                            btnVerify.text = "LAUNCH"
                        }
                    }
                } else {
                    runOnUiThread { 
                        btnVerify.isEnabled = true
                        btnVerify.text = "LAUNCH"
                        Toast.makeText(this, "Server Error: $code", Toast.LENGTH_SHORT).show() 
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { 
                    btnVerify.isEnabled = true
                    btnVerify.text = "LAUNCH"
                    Toast.makeText(this, "Connection failed: ${e.message}", Toast.LENGTH_LONG).show() 
                }
            }
        }
    }

    private fun silentLiveCheck(layoutLogin: LinearLayout, layoutDashboard: LinearLayout, tvDays: TextView, tvHours: TextView, tvMins: TextView, tvSecs: TextView, tvSlots: TextView, tvPlanType: TextView, tvExpiryDate: TextView, tvDashboardExpiry: TextView) {
        verifyKey(userKey, layoutLogin, layoutDashboard, tvDays, tvHours, tvMins, tvSecs, tvSlots, tvPlanType, tvExpiryDate, tvDashboardExpiry)
    }

    private fun checkVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) startActivityForResult(intent, 102)
        else checkOverlayPermission()
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), 101)
        } else {
            startFloatingServiceSafely()
        }
    }

    private fun startFloatingServiceSafely() {
        val intent = Intent(this, FloatingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Floating Panel Started!", Toast.LENGTH_SHORT).show()
    }

    private fun startExpiryTimer(expiryStr: String, tvDays: TextView, tvHours: TextView, tvMins: TextView, tvSecs: TextView) {
        timer?.cancel()
        val expiryDate = parseExpiry(expiryStr) ?: return
        val timeLeft = expiryDate.time - System.currentTimeMillis()
        if (timeLeft <= 0) return
        timer = object : CountDownTimer(timeLeft, 1000) {
            override fun onTick(ms: Long) {
                tvDays.text = String.format("%02d", (ms / 86400000L).toInt())
                tvHours.text = String.format("%02d", ((ms / 3600000L) % 24).toInt())
                tvMins.text = String.format("%02d", ((ms / 60000L) % 60).toInt())
                tvSecs.text = String.format("%02d", ((ms / 1000L) % 60).toInt())
            }
            override fun onFinish() { Toast.makeText(this@MainActivity, "Key Expired", Toast.LENGTH_LONG).show() }
        }.start()
    }

    private fun formatExpiryString(expiryStr: String): String {
        val date = parseExpiry(expiryStr) ?: return "Unknown"
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

    private fun parseExpiry(s: String): Date? {
        if (s.isBlank()) return null
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", 
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", 
            "yyyy-MM-dd'T'HH:mm:ss'Z'", 
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS", 
            "yyyy-MM-dd'T'HH:mm:ss.SSS", 
            "yyyy-MM-dd'T'HH:mm:ss", 
            "yyyy-MM-dd HH:mm:ss"
        )
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
                    val url = URL("${KeyVault.getPanelUrl()}/connect/slots")
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
        if (req == 102) {
            if (res == RESULT_OK) checkOverlayPermission()
            else Toast.makeText(this, "VPN Permission Denied", Toast.LENGTH_SHORT).show()
        } else if (req == 101) {
            if (Settings.canDrawOverlays(this)) {
                startFloatingServiceSafely()
            } else {
                Toast.makeText(this, "Overlay Permission Denied", Toast.LENGTH_SHORT).show()
                switchFloating.isChecked = false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        toneGenerator?.release()
    }
}
