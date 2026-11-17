package com.teamjadugar.apk

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.util.Base64
import android.view.MotionEvent
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
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class MainActivity : AppCompatActivity() {

    companion object {
        @Volatile var userKey: String = ""
        @Volatile var userExpiry: String = ""
        val networkExecutor = Executors.newFixedThreadPool(4)
        
        // 🔥 Server Response Decryption Key (Ye key server aur app dono mein same honi chahiye)
        private const val AES_KEY = "SableSecure2026!" // 16 chars for AES-128
        private const val AES_IV = "SecureInitVector"  // 16 chars
    }

    private lateinit var prefs: SharedPreferences
    private var timer: CountDownTimer? = null
    private lateinit var layoutLogin: LinearLayout
    private lateinit var layoutDashboard: LinearLayout
    private lateinit var etKey: EditText
    private var toneGenerator: ToneGenerator? = null
    private lateinit var imgLoginBg: ImageView
    private lateinit var imgDashBg: ImageView

    @Volatile private var isRefreshingSlots = true

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
            Toast.makeText(this, "❌ SECURITY ALERT: Device Compromised!", Toast.LENGTH_LONG).show()
            finishAffinity()
            return
        }

        prefs = getSharedPreferences("AETHPrefs", MODE_PRIVATE)
        userKey = prefs.getString("saved_key", "") ?: ""
        userExpiry = prefs.getString("saved_expiry", "") ?: ""
        if (userKey.isNotEmpty()) SessionStore.saveKey(this, userKey)

        imgLoginBg = findViewById(R.id.imgLoginBg)
        imgDashBg = findViewById(R.id.imgDashBg)
        layoutLogin = findViewById(R.id.layoutLogin)
        layoutDashboard = findViewById(R.id.layoutDashboard)
        etKey = findViewById(R.id.etKey)

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

        setupCustomSlider(findViewById(R.id.sliderVerify), "SLIDE TO VERIFY") {
            val key = etKey.text.toString().trim()
            if (key.isEmpty()) {
                Toast.makeText(this, "Enter Key First!", Toast.LENGTH_SHORT).show()
            } else {
                verifyKey(key, layoutLogin, layoutDashboard, tvDays, tvHours, tvMins, tvSecs, tvSlots, tvPlanType, tvExpiryDate, tvDashboardExpiry)
            }
        }

        setupCustomSlider(findViewById(R.id.sliderFloating), "SLIDE TO LAUNCH") { checkVpnPermission() }
        setupCustomSlider(findViewById(R.id.sliderGame), "SLIDE TO OPEN") { openBGMI() }

        if (userKey.isNotEmpty()) {
            verifyKey(userKey, layoutLogin, layoutDashboard, tvDays, tvHours, tvMins, tvSecs, tvSlots, tvPlanType, tvExpiryDate, tvDashboardExpiry)
        } else {
            imgLoginBg.visibility = View.VISIBLE
            imgDashBg.visibility = View.GONE
            layoutLogin.visibility = View.VISIBLE
            layoutDashboard.visibility = View.GONE
        }
    }

    private fun setupCustomSlider(sliderContainer: View, textHint: String, onSlideComplete: () -> Unit) {
        val thumb = sliderContainer.findViewById<ImageView>(R.id.btnThumb)
        val text = sliderContainer.findViewById<TextView>(R.id.tvSliderText)
        text.text = textHint

        thumb.setOnTouchListener(object : View.OnTouchListener {
            var initialX = 0f
            var isSliding = false
            var isCompleted = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (isCompleted) return false

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = event.rawX
                        isSliding = true
                        v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100).start()
                        text.animate().alpha(0.3f).setDuration(100).start()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isSliding) {
                            val dx = event.rawX - initialX
                            var newX = v.x + dx
                            val maxX = sliderContainer.width - v.width.toFloat()
                            if (newX < 0f) newX = 0f
                            if (newX > maxX) newX = maxX
                            v.x = newX
                            initialX = event.rawX
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isSliding = false
                        v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                        text.animate().alpha(1f).setDuration(100).start()
                        val maxX = sliderContainer.width - v.width.toFloat()
                        if (v.x >= maxX * 0.85f) {
                            isCompleted = true
                            v.animate().x(maxX).setDuration(100).withEndAction {
                                playSound("success")
                                onSlideComplete()
                                v.postDelayed({
                                    v.animate().x(0f).setDuration(300).start()
                                    isCompleted = false
                                }, 500)
                            }.start()
                        } else {
                            v.animate().x(0f).setDuration(300).start()
                        }
                    }
                }
                return true
            }
        })
    }

    private fun verifyKey(key: String, layoutLogin: LinearLayout, layoutDashboard: LinearLayout, tvDays: TextView, tvHours: TextView, tvMins: TextView, tvSecs: TextView, tvSlots: TextView, tvPlanType: TextView, tvExpiryDate: TextView, tvDashboardExpiry: TextView) {
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        
        networkExecutor.execute {
            try {
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
                    // 🔥 SERVER RESPONSE DECRYPTION (Bypass Rokne Ka Asli Tarika)
                    val json = try {
                        JSONObject(decryptAES(resp))
                    } catch (e: Exception) {
                        // Fallback agar server plain JSON bhej raha hai (abhi ke liye)
                        JSONObject(resp)
                    }
                    
                    if (json.optBoolean("valid", false)) {
                        userKey = key
                        userExpiry = json.optString("expires_at", "")
                        prefs.edit().putString("saved_key", userKey).apply()
                        prefs.edit().putString("saved_expiry", userExpiry).apply()
                        SessionStore.saveKey(this@MainActivity, userKey)

                        runOnUiThread {
                            playSound("success")
                            Toast.makeText(this@MainActivity, "Login Successful!", Toast.LENGTH_SHORT).show()
                            
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
                            Toast.makeText(this@MainActivity, json.optString("msg", "Invalid Key"), Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Server Error: $code", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Connection failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // 🔥 AES Decryption Function (Server Response ko decrypt karne ke liye)
    private fun decryptAES(encryptedData: String): String {
        try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(AES_KEY.toByteArray(), "AES")
            val ivSpec = IvParameterSpec(AES_IV.toByteArray())
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val decodedBytes = Base64.decode(encryptedData, Base64.DEFAULT)
            return String(cipher.doFinal(decodedBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            throw Exception("Decryption failed")
        }
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
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
            "yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss"
        )
        for (p in patterns) {
            try {
                val sdf = SimpleDateFormat(p, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val date = sdf.parse(s)
                if (date != null) {
                    val offset = TimeZone.getDefault().getOffset(date.time)
                    return Date(date.time + offset)
                }
            } catch (_: Exception) {}
        }
        return s.toLongOrNull()?.let { 
            val d = Date(if (it < 10_000_000_000L) it * 1000 else it)
            val offset = TimeZone.getDefault().getOffset(d.time)
            Date(d.time + offset)
        }
    }

    private fun startSlotsRefresh(tvSlots: TextView) {
        Thread {
            while (isRefreshingSlots) {
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
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRefreshingSlots = false
        timer?.cancel()
        toneGenerator?.release()
    }
}
