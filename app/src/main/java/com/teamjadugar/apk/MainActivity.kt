package com.teamjadugar.apk

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {
    companion object {
        var userKey: String = "" 
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etKey = findViewById<EditText>(R.id.etKey)
        val btnVerify = findViewById<Button>(R.id.btnVerify)

        btnVerify.setOnClickListener {
            val key = etKey.text.toString().trim()
            if (key.isEmpty()) {
                Toast.makeText(this, "Enter Key First!", Toast.LENGTH_SHORT).show()
            } else {
                verifyKey(key)
            }
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
                        runOnUiThread {
                            Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show()
                            checkOverlayPermission()
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
        Toast.makeText(this, "Panel Opened in Floating Window", Toast.LENGTH_SHORT).show()
        finish()
    }
}
