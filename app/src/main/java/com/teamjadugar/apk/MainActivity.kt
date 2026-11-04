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
        val tvDays = findViewById<TextView>(R.id.tvDays)
        val tvHours = findViewById<TextView>(R.id.tvHours)
        val tvMins = findViewById<TextView>(R.id.tvMins)
        val tvSecs = findViewById<TextView>(R.id.tvSecs)
        val tvDeviceId = findViewById<TextView>(R.id.tvDeviceId)
        val switchFloating = findViewById<Switch>(R.id.switchFloating)

        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        tvDeviceId.text = deviceId.take(10) + "..."

        if (userKey.isNotEmpty()) {
            layoutLogin.visibility = LinearLayout.GONE
            layoutDashboard.visibility = LinearLayout.VISIBLE
            startExpiryTimer(userExpiry, tvDays, tvHours, tvMins, tvSecs)

            switchFloating.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) checkVpnPermission() else stopService(Intent(this, FloatingService::class.java))
            }
        } else {
            layoutLogin.visibility = LinearLayout.VISIBLE
            layoutDashboard.visibility = LinearLayout.GONE
            
            btnVerify.setOnClickListener {
                val key = etKey.text.toString().trim()
                if (key.isEmpty()) Toast.makeText(this, "Enter Key First!", Toast.LENGTH_SHORT).show()
                else verifyKey(key)
            }
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
            startFloatingService()
        }
    }

    private fun startExpiryTimer(expiryStr: String, tvDays: TextView, tvHours: TextView, tvMins: TextView, tvSecs: TextView) {
        try {
            // 100% FIX: Panel UTC me time bhejta hai, use direct UTC parse karo
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val expiryDate = sdf.parse(expiryStr) ?: return
            
            // Current UTC time nikaalo
            val currentUtcTime = System.currentTimeMillis()
            val timeLeft = expiryDate.time - currentUtcTime

            if (timeLeft > 0) {
                timer = object : CountDownTimer(timeLeft, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        val d = (millisUntilFinished / (1000 * 60 * 60 * 24)).toInt()
                        val h = ((millisUntilFinished / (1000 * 60 * 60)) % 24).toInt()
                        val m = ((millisUntilFinished / (1000 * 60)) % 60).toInt()
                        val s = ((millisUntilFinished / 1000) % 60).toInt()
                        
                        tvDays.text = String.format("%02d", d)
                        tvHours.text = String.format("%02d", h)
                        tvMins.text = String.format("%02d", m)
                        tvSecs.text = String.format("%02d", s)
                    }
                    override fun onFinish() {
                        tvDays.text = "00"; tvHours.text = "00"; tvMins.text = "00"; tvSecs.text = "00"
                        prefs.edit().clear().apply()
                    }
                }.start()
            }
        } catch (e: Exception) {}
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
            } catch (e: Exception) {}
        }.start()
    }

    private fun startFloatingService() {
        startService(Intent(this, FloatingService::class.java))
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 102) {
            if (resultCode == RESULT_OK) checkOverlayPermission()
        } else if (requestCode == 101) {
            if (Settings.canDrawOverlays(this)) startFloatingService()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}
