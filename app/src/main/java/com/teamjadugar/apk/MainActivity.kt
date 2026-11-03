package com.teamjadugar.apk

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.util.Base64
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class MainActivity : AppCompatActivity() {
    companion object {
        var userKey: String = "" 
        var userExpiry: String = ""
        private const val SECRET_KEY = "JadugarSecretKey1234567890123456" // 32 bytes for AES-256
    }
    
    private lateinit var prefs: SharedPreferences
    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("JadugarPrefs", MODE_PRIVATE)
        userKey = decrypt(prefs.getString("saved_key", "") ?: "")
        userExpiry = prefs.getString("saved_expiry", "") ?: ""

        val etKey = findViewById<EditText>(R.id.etKey)
        val btnVerify = findViewById<Button>(R.id.btnVerify)
        val tvTimer = findViewById<TextView>(R.id.tvTimer)
        val tvDeviceId = findViewById<TextView>(R.id.tvDeviceId)
        val switchFloating = findViewById<Switch>(R.id.switchFloating)

        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        tvDeviceId.text = "Device ID: $deviceId"

        if (userKey.isNotEmpty()) {
            etKey.visibility = EditText.GONE
            btnVerify.visibility = Button.GONE
            tvTimer.visibility = TextView.VISIBLE
            switchFloating.visibility = Switch.VISIBLE
            
            startExpiryTimer(userExpiry, tvTimer)

            switchFloating.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    checkOverlayPermission()
                } else {
                    stopService(Intent(this, FloatingService::class.java))
                }
            }
        } else {
            tvTimer.visibility = TextView.GONE
            switchFloating.visibility = Switch.GONE
            
            btnVerify.setOnClickListener {
                val key = etKey.text.toString().trim()
                if (key.isEmpty()) {
                    Toast.makeText(this, "Enter Key First!", Toast.LENGTH_SHORT).show()
                } else {
                    verifyKey(key)
                }
            }
        }
    }

    private fun startExpiryTimer(expiryStr: String, tvTimer: TextView) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val expiryDate = sdf.parse(expiryStr) ?: return
            val timeLeft = expiryDate.time - System.currentTimeMillis()

            if (timeLeft > 0) {
                timer = object : CountDownTimer(timeLeft, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        val h = (millisUntilFinished / (1000 * 60 * 60)) % 24
                        val m = (millisUntilFinished / (1000 * 60)) % 60
                        val s = (millisUntilFinished / 1000) % 60
                        tvTimer.text = String.format("ACTIVE: %02d:%02d:%02d", h, m, s)
                        tvTimer.setTextColor(android.graphics.Color.parseColor("#00FFCC"))
                    }
                    override fun onFinish() {
                        tvTimer.text = "ACCESS EXPIRED"
                        tvTimer.setTextColor(android.graphics.Color.parseColor("#FF003C"))
                        prefs.edit().clear().apply()
                    }
                }.start()
            } else {
                tvTimer.text = "ACCESS EXPIRED"
                tvTimer.setTextColor(android.graphics.Color.parseColor("#FF003C"))
            }
        } catch (e: Exception) {
            tvTimer.text = "Time Error"
        }
    }

    private fun verifyKey(key: String) {
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val panelUrl = "http://13.211.179.172:5000/connect/init"

        Thread {
            try {
                val url = URL(panelUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val jsonInputString = """{"key": "$key", "device_id": "$deviceId"}"""
                conn.outputStream.use { os -> os.write(jsonInputString.toByteArray()) }

                if (conn.responseCode == 200) {
                    val json = JSONObject(conn.inputStream.bufferedReader().readText())
                    if (json.getBoolean("valid")) {
                        userKey = key 
                        userExpiry = json.optString("expires_at", "")
                        
                        // Saving Encrypted Key
                        prefs.edit().putString("saved_key", encrypt(userKey)).apply()
                        prefs.edit().putString("saved_expiry", userExpiry).apply()
                        
                        runOnUiThread {
                            Toast.makeText(this, "Access Granted!", Toast.LENGTH_SHORT).show()
                            val intent = intent
                            finish()
                            startActivity(intent)
                        }
                    } else {
                        runOnUiThread { Toast.makeText(this, json.optString("msg", "Invalid Key"), Toast.LENGTH_LONG).show() }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Network Error!", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun encrypt(data: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        val keySpec = SecretKeySpec(SECRET_KEY.toByteArray(), "AES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        return Base64.encodeToString(cipher.doFinal(data.toByteArray()), Base64.DEFAULT)
    }

    private fun decrypt(data: String): String {
        if (data.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            val keySpec = SecretKeySpec(SECRET_KEY.toByteArray(), "AES")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            String(cipher.doFinal(Base64.decode(data, Base64.DEFAULT)))
        } catch (e: Exception) { "" }
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), 101)
        } else {
            startService(Intent(this, FloatingService::class.java))
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101 && Settings.canDrawOverlays(this)) {
            startService(Intent(this, FloatingService::class.java))
            finish()
        }
    }
}
