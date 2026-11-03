package com.teamjadugar.apk

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
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
        var userKey: String = "" 
        var userExpiry: String = ""
    }
    
    private lateinit var prefs: SharedPreferences
    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("JadugarPrefs", MODE_PRIVATE)
        userKey = prefs.getString("saved_key", "") ?: ""
        userExpiry = prefs.getString("saved_expiry", "") ?: ""

        val etKey = findViewById<EditText>(R.id.etKey)
        val btnVerify = findViewById<Button>(R.id.btnVerify)
        val tvTimer = findViewById<TextView>(R.id.tvTimer)
        val tvDeviceId = findViewById<TextView>(R.id.tvDeviceId)
        val switchFloating = findViewById<Switch>(R.id.switchFloating)

        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        tvDeviceId.text = "Device ID: $deviceId"

        if (userKey.isNotEmpty()) {
            // Already Logged In - Show Dashboard
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
            // Not Logged In - Show Redeem
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
                        val hours = (millisUntilFinished / (1000 * 60 * 60)) % 24
                        val minutes = (millisUntilFinished / (1000 * 60)) % 60
                        val seconds = (millisUntilFinished / 1000) % 60
                        tvTimer.text = String.format("TIME LEFT: %02d:%02d:%02d", hours, minutes, seconds)
                        tvTimer.setTextColor(android.graphics.Color.parseColor("#22C55E"))
                    }
                    override fun onFinish() {
                        tvTimer.text = "KEY EXPIRED"
                        tvTimer.setTextColor(android.graphics.Color.parseColor("#EF4444"))
                        prefs.edit().clear().apply()
                    }
                }.start()
            } else {
                tvTimer.text = "KEY EXPIRED"
                tvTimer.setTextColor(android.graphics.Color.parseColor("#EF4444"))
            }
        } catch (e: Exception) {
            tvTimer.text = "Time not available"
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

                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    
                    if (json.getBoolean("valid")) {
                        userKey = key 
                        userExpiry = json.optString("expires_at", "")
                        
                        prefs.edit().putString("saved_key", userKey).apply()
                        prefs.edit().putString("saved_expiry", userExpiry).apply()
                        
                        runOnUiThread {
                            Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show()
                            // Intent use kar rahe hain recreate ki jagah
                            val intent = intent
                            finish()
                            startActivity(intent)
                        }
                    } else {
                        val msg = json.optString("msg", "Invalid Key")
                        runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Network Error!", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, 101)
        } else {
            startFloatingService()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101) {
            if (Settings.canDrawOverlays(this)) {
                startFloatingService()
            } else {
                Toast.makeText(this, "Permission Denied!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startFloatingService() {
        startService(Intent(this, FloatingService::class.java))
        Toast.makeText(this, "Floating Panel ON", Toast.LENGTH_SHORT).show()
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}
