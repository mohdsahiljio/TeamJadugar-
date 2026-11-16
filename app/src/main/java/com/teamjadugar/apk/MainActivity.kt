package com.teamjadugar.apk

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
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
        val networkExecutor = Executors.newFixedThreadPool(4)
    }

    private lateinit var prefs: SharedPreferences
    private var timer: CountDownTimer? = null
    private lateinit var layoutLogin: LinearLayout
    private lateinit var layoutDashboard: LinearLayout
    private lateinit var etKey: EditText
    private lateinit var btnVerify: Button
    private var toneGenerator: ToneGenerator? = null
    private lateinit var imgLoginBg: ImageView
    private lateinit var imgDashBg: ImageView

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
        if (userKey.isNotEmpty()) SessionStore.saveKey(this, userKey)

        imgLoginBg = findViewById(R.id.imgLoginBg)
        imgDashBg = findViewById(R.id.imgDashBg)
        layoutLogin = findViewById(R.id.layoutLogin)
        layoutDashboard = findViewById(R.id.layoutDashboard)
        etKey = findViewById(R.id.etKey)
        btnVerify = findViewById(R.id.btnVerify)
        
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

        // SLIDER LOGIC FOR FLOATING PANEL
        val sliderFloating = findViewById<SeekBar>(R.id.sliderFloating)
        sliderFloating.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (progress == 100) {
                    playSound("success")
                    checkVpnPermission()
                    sliderFloating.postDelayed({ sliderFloating.progress = 0 }, 500)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // SLIDER LOGIC FOR ENTER GAME (BGMI)
        val sliderGame = findViewById<SeekBar>(R.id.sliderGame)
        sliderGame.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (progress == 100) {
                    playSound("success")
                    openBGMI()
                    sliderGame.postDelayed({ sliderGame.progress = 0 }, 500)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnVerify.setOnClickListener {
            if (!btnVerify.isEnabled) return@setOnClickListener
            playSound("click")
            val key = etKey.text.toString().trim()
            if (key.isEmpty()) {
                Toast.makeText(this, "Enter Key First!", Toast.LENGTH_SHORT).show()
            } else {
                verifyKey(key, layoutLogin, layoutDashboard, tvDays, tvHours, tvMins, tvSecs, tvSlots, tvPlanType, tvExpiryDate, tvDashboardExpiry)
            }
        }

        if (userKey.isNotEmpty()) {
            verifyKey(userKey, layoutLogin, layoutDashboard, tvDays, tvHours, tvMins, tvSecs, tvSlots, tvPlanType, tvExpiryDate, tvDashboardExpiry)
        } else {
            imgLoginBg.visibility = View.VISIBLE
            imgDashBg.visibility = View.GONE
            layoutLogin.visibility = View.VISIBLE
            layoutDashboard.visibility = View.GONE
        }
    }

    private fun openBGMI() {
        try {
            val intent = packageManager.getLaunchIntentForPackage("com.pubg.imobile")
            if (intent != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "BGMI is not installed!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open BGMI!", Toast.LENGTH_SHORT).show()
        }
    }

    // ... (Baaki verifyKey, startExpiryTimer, startSlotsRefresh, etc. ka same code rahega)
    // Tere paas pehle se jo MainActivity ka code hai, usme bas ye do Slider aur openBGMI() ka logic add kar de.
}
