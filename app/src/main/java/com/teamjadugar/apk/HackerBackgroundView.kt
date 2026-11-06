package com.teamjadugar.apk

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

class HackerBackgroundView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    
    private val paintText = Paint().apply {
        color = Color.parseColor("#4000E5FF") // Faded Cyan Code
        textSize = 35f
        isAntiAlias = true
        typeface = Typeface.MONOSPACE
    }
    
    private val paintMap = Paint().apply {
        color = Color.parseColor("#30B026FF") // Faded Purple Map Lines
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val codeChars = "01010101ABCDEFGHIJKLMNOPQRSTUVWXYZ{}[]()<>=+*/#@$%&".toCharArray()
    private var codeList = mutableListOf<CodeParticle>()
    private val random = Random(System.currentTimeMillis())
    private var mapAngle = 0f

    data class CodeParticle(
        var x: Float,
        var y: Float,
        var speedY: Float,
        var text: String
    )

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        codeList.clear()
        for (i in 0..60) {
            codeList.add(
                CodeParticle(
                    x = random.nextFloat() * w,
                    y = random.nextFloat() * h,
                    speedY = random.nextFloat() * 6f + 2f,
                    text = generateRandomText()
                )
            )
        }
    }

    private fun generateRandomText(): String {
        val sb = StringBuilder()
        for (i in 0 until random.nextInt(5, 15)) {
            sb.append(codeChars[random.nextInt(codeChars.size)])
        }
        return sb.toString()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Draw Rotating World Map
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = width / 2.2f
        
        canvas.save()
        canvas.rotate(mapAngle, centerX, centerY)
        
        canvas.drawCircle(centerX, centerY, radius, paintMap)
        canvas.drawCircle(centerX, centerY, radius * 0.7f, paintMap)
        canvas.drawCircle(centerX, centerY, radius * 0.4f, paintMap)
        canvas.drawLine(0f, centerY, width.toFloat(), centerY, paintMap)
        canvas.drawLine(centerX, 0f, centerX, height.toFloat(), paintMap)
        
        canvas.restore()
        mapAngle += 0.2f

        // 2. Draw Falling Code
        for (particle in codeList) {
            canvas.drawText(particle.text, particle.x, particle.y, paintText)
            particle.y += particle.speedY

            if (particle.y > height) {
                particle.y = -50f
                particle.x = random.nextFloat() * width
                particle.text = generateRandomText()
            }
        }
        
        invalidate()
    }
}
