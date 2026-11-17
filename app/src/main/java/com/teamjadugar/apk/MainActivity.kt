package com.teamjadugar.apk

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class MainActivity : AppCompatActivity() {

    companion object {
        @Volatile var userKey: String = ""
        @Volatile var userExpiry: String = ""
        @Volatile var sessionToken: String = "" // Naya added
        val networkExecutor = Executors.newFixedThreadPool(4)
    }

    private lateinit var prefs: SharedPreferences
    private var timer: CountDownTimer? = null
    private lateinit var layoutLogin: LinearLayout
    private lateinit var layoutDashboard: LinearLayout
    private lateinit var etKey: EditText
    private var toneGenerator: ToneGenerator? = null
    private lateinit var imgLoginBg: ImageView
    private lateinit var imgDashBg: ImageView

    @Volatile private var isRefreshingSlots = true

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (SecurityManager.isDeviceCompromised(this)) {
            Toast.makeText(this, "❌ SECURITY ALERT: Rooted or Emulator Detected!", Toast.LENGTH_LONG).show()
            finishAffinity()
            return
        }

        prefs = getSharedPreferences("AETHPrefs", MODE_PRIVATE)
        userKey = prefs.getString("saved_key", "") ?: ""
        userExpiry = prefs.getString("saved_expiry", "") ?: ""
        sessionToken = prefs.getString("session_token", "") ?: "" // Naya added
        if (userKey.isNotEmpty()) SessionStore.saveKey(this, userKey)

        imgLoginBg = findViewById(R.id.imgLoginBg)
        imgDashBg = findViewById(R.id.imgDashBg)
        layoutLogin = findViewById(R.id.layoutLogin)
        layoutDashboard = findViewById(R.id.layoutDashboard)
        etKey = findViewById(R.id.etKey)

        val tvDays = findViewById<TextView>(R.id.tvDays)
        val tvHours = findViewById<TextView>(R.id.tvHours)
        val tvMins = findViewById<TextView>(R.id.tvMins)
        val tvSecs = findViewById<TextView>(R.id.tvSecs)
        val tvDeviceId = findViewById<TextView>(R.id.tvDeviceId)
        val tvSlots = findViewById<TextView>(R.id.tvSlots)
        val tvPlanType = findViewById<TextView>(R.id.tvPlanType)
        val tvExpiryDate = findViewById<TextView>(R.id.tvExpiryDate)
        val tvDashboardExpiry = findViewById<TextView>(R.id.tvDashboardExpiry)

        val rawDeviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        tvDeviceId.text = rawDeviceId.take(8).uppercase()

        setupCustomSlider(findViewById(R.id.sliderVerify), "SLIDE TO VERIFY") {
            val key = etKey.text.toString().trim()
            if (key.isEmpty()) {
                Toast.makeText(this, "Enter Key First!", Toast.LENGTH_SHORT).show()
            } else {
                verifyKey(key, layoutLogin, layoutDashboard, tvDays, tvHours, tvMins, tvSecs, tvSlots, tvPlanType, tvExpiryDate, tvDashboardExpiry)
            }
        }

        setupCustomSlider(findViewById(R.id.sliderFloating), "SLIDE TO LAUNCH") { checkVpnPermission() }
        setupCustomSlider(findViewById(R.id.sliderGame), "SLIDE TO OPEN") { openBGMI() }

        if (userKey.isNotEmpty() && sessionToken.isNotEmpty()) { // Check token bhi
            verifyKey(userKey, layoutLogin, layoutDashboard, tvDays, tvHours, tvMins, tvSecs, tvSlots, tvPlanType, tvExpiryDate, tvDashboardExpiry)
        } else {
            imgLoginBg.visibility = View.VISIBLE
            imgDashBg.visibility = View.GONE
            layoutLogin.visibility = View.VISIBLE
            layoutDashboard.visibility = View.GONE
        }
    }

    private fun setupCustomSlider(sliderContainer: View, textHint: String, onSlideComplete: () -> Unit) {
        val thumb = sliderContainer.findViewById<ImageView>(R.id.btnThumb)
        val text = sliderContainer.findViewById<TextView>(R.id.tvSliderText)
        text.text = textHint

        thumb.setOnTouchListener(object : View.OnTouchListener {
            var initialX = 0f
            var isSliding = false
            var isCompleted = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (isCompleted) return false

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = event.rawX
                        isSliding = true
                        v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100).start()
                        text.animate().alpha(0.3f).setDuration(100).start()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isSliding) {
                            val dx = event.rawX - initialX
                            var newX = v.x + dx
                            val maxX = sliderContainer.width - v.width.toFloat()
                            
                            if (newX < 0f) newX = 0f
                            if (newX > maxX) newX = maxX
                            
                            v.x = newX
                            initialX = event.rawX
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isSliding = false
                        v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                        text.animate().alpha(1f).setDuration(100).start()

                        val maxX = sliderContainer.width - v.width.toFloat()
                        if (v.x >= maxX * 0.85f) {
                            isCompleted = true
                            v.animate().x(maxX).setDuration(100).withEndAction {
                                playSound("success")
                                onSlideComplete()
                                v.postDelayed({
                                    v.animate().x(0f).setDuration(300).start()
                                    isCompleted = false
                                }, 500)
                            }.start()
                        } else {
                            // 🔥 FIX: Agar pura nahi kheencha, toh wapas start pe bhejo
                            v.animate().x(0f).setDuration(300).start()
                        }
                    }
                }
                return true
            }
        })
    }

    private fun openBGMI() {
        try {
            val packages = arrayOf(
                "com.pubg.im
