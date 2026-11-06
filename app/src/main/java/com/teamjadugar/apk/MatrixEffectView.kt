package com.teamjadugar.apk

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

class MatrixEffectView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val paint = Paint().apply {
        color = Color.parseColor("#B026FF") // Purple text
        textSize = 35f
        isAntiAlias = true
    }
    private val chars = "01".toCharArray()
    private var drops: IntArray = IntArray(0)
    private var columns = 0
    private val random = Random(System.currentTimeMillis())

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        columns = (w / paint.textSize).toInt() + 1
        drops = IntArray(columns) { random.nextInt(h) } // Start at random heights
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw semi-transparent black to create trail effect
        canvas.drawColor(Color.parseColor("#1505000A"))

        for (i in drops.indices) {
            val char = chars[random.nextInt(chars.size)]
            val x = i * paint.textSize
            val y = drops[i] * paint.textSize

            canvas.drawText(char.toString(), x, y, paint)

            // Randomly reset drop to top
            if (y > height && random.nextFloat() > 0.975f) {
                drops[i] = 0
            } else {
                drops[i]++
            }
        }
        invalidate()
    }
}
