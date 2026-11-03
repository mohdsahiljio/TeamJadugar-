package com.teamjadugar.apk

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

class FloatingService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: android.view.View

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 100

        val interView = TextView(this)
        interView.text = "JADUGAR ACTIVE"
        interView.setTextColor(android.graphics.Color.RED)
        interView.textSize = 20f
        interView.setBackgroundColor(android.graphics.Color.BLACK)
        interView.setPadding(20, 20, 20, 20)

        floatingView = interView
        windowManager.addView(floatingView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
