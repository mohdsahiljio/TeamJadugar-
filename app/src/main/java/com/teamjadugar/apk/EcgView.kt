package com.teamjadugar.apk

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

class EcgView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF") // Cyan
        strokeWidth = 3f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        strokeWidth = 6f
        style = Paint.Style.STROKE
        maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
    }
    private var offset = 0f
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val midY = h * 0.5f
        path.reset()
        path.moveTo(0f, midY)

        for (x in 0..w.toInt() step 5) {
            val phase = (x + offset) * 0.05f
            val y = midY + (sin(phase) * 20f).toFloat()
            path.lineTo(x.toFloat(), y)
        }
        
        glowPaint.alpha = 80
        canvas.drawPath(path, glowPaint)
        canvas.drawPath(path, paint)
        
        offset += 5f
        if (offset > 1000f) offset = 0f
        postInvalidateDelayed(30)
    }
}
