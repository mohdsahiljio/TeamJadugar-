package com.teamjadugar.apk

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.view.View
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

        val layoutLogin = findViewById<LinearLayout>(R.id.layoutLogin)
        val layoutDashboard = findViewById<LinearLayout>(R.id.layoutDashboard)
        val etKey = findViewById<EditText>(R.id.etKey)
        val btnVerify = findViewById<Button>(R.id.btnVerify)
        val tvTimer = findViewById<TextView>(R.id.tvTimer)
        val tvExpiryDate = findViewById<TextView>(R.id.tvExpiryDate)
        val tvDeviceId = findViewById<TextView>(R.id.tvDeviceId)
        val switchFloating = findViewById<Switch>(R.id.switchFloating)

        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        tvDeviceId.text = "Device ID: $deviceId"

        if (userKey.isNotEmpty()) {
            layoutLogin.visibility = LinearLayout.GONE
            layoutDashboard.visibility = LinearLayout.VISIBLE
            startExpiryTimer(userExpiry, tvTimer, tvExpiryDate)

            switchFloating.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    checkOverlayPermission()
                } else {
                    stopService(Intent(this, FloatingService::class.java))
                }
            }
        } else {
            layoutLogin.visibility = LinearLayout.VISIBLE
            layoutDashboard.visibility = LinearLayout.GONE
            
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

    private fun startExpiryTimer(expiryStr: String, tvTimer: TextView, tvDate: TextView) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val expiryDate = sdf.parse(expiryStr) ?: return
            
            // Format Date for Display (e.g., Friday, 12 Dec 2026)
            val displayFormat = SimpleDateFormat("EEEE, dd MMM yyyy", Locale.US)
            tvDate.text = "Expires on: ${displayFormat.format(expiryDate)}"

            val timeLeft = expiryDate.time - System.currentTimeMillis()

            if (timeLeft > 0) {
                timer = object : CountDownTimer(timeLeft, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        val d = (millisUntilFinished / (1000 * 60 * 60 * 24)).toInt()
                        val h = ((millisUntilFinished / (1000 * 60 * 60)) % 24).toInt()
                        val m = ((millisUntilFinished / (1000 * 60)) % 60).toInt()
                        val s = ((millisUntilFinished / 1000) % 60).toInt()
                        tvTimer.text = String.format("%02dd %02dh %02dm %02ds", d, h, m, s)
                    }
                    override fun onFinish() {
                        tvTimer.text = "EXPIRED"
                        tvTimer.setTextColor(android.graphics.Color.parseColor("#EF4444"))
                        prefs.edit().clear().apply()
                    }
                }.start()
            } else {
                tvTimer.text = "EXPIRED"
                tvTimer.setTextColor(android.graphics.Color.parseColor("#EF4444"))
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
                        
                        prefs.edit().putString("saved_key", userKey).apply()
                        prefs.edit().putString("saved_expiry", userExpiry).apply()
                        
                        runOnUiThread {
                            Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show()
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

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}
