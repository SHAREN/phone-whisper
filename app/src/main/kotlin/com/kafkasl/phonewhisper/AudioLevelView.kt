package com.kafkasl.phonewhisper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import kotlin.math.min

class AudioLevelView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var targetLevel = 0f
    private var renderedLevel = 0f

    fun setLevel(level: Float) {
        targetLevel = level.coerceIn(0f, 1f)
        if (visibility == VISIBLE) invalidate()
    }

    fun reset() {
        targetLevel = 0f
        renderedLevel = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        renderedLevel += (targetLevel - renderedLevel) * 0.28f
        val level = renderedLevel
        val cx = width / 2f
        val cy = height / 2f
        val base = min(width, height) * 0.10f

        drawPulse(canvas, cx, cy, base * (1.02f + level * 3.85f), 0x33FFFFFF)
        drawPulse(canvas, cx, cy, base * (0.84f + level * 2.94f), 0x44FFB1A6)
        drawPulse(canvas, cx, cy, base * (0.66f + level * 1.96f), 0x55FFFFFF)

        if (visibility == VISIBLE && (targetLevel > 0.01f || renderedLevel > 0.01f)) {
            postInvalidateOnAnimation()
        }
    }

    private fun drawPulse(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = maxOf(2f, width * 0.035f)
        paint.color = color
        canvas.drawCircle(cx, cy, radius, paint)
    }
}
