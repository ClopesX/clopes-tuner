package com.clopes.tuner

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pendulo do metronomo: balanca para os lados a cada batida e da um pulso de
 * luz no corpo, sincronizado com o clique.
 */
class PendulumView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var angle = 0f
    private var targetAngle = 0f
    private var swing: ValueAnimator? = null
    private var pulse: ValueAnimator? = null
    private var pulseAlpha = 0f

    private val maxAngle = 24f

    private val rodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.accent
        strokeWidth = dp(5f)
        strokeCap = Paint.Cap.ROUND
    }
    private val bobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.text
        style = Paint.Style.FILL
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.ok
        style = Paint.Style.FILL
    }
    private val pivotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.border
        style = Paint.Style.FILL
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.track
        style = Paint.Style.FILL
    }
    private val triangle = Path()

    fun swing() {
        targetAngle = if (targetAngle >= 0f) -maxAngle else maxAngle
        swing?.cancel()
        swing = ValueAnimator.ofFloat(angle, targetAngle).apply {
            duration = 210
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                angle = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun beat() {
        pulse?.cancel()
        pulseAlpha = 0.45f
        pulse = ValueAnimator.ofFloat(0.45f, 0f).apply {
            duration = 280
            addUpdateListener {
                pulseAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun reset() {
        swing?.cancel()
        pulse?.cancel()
        angle = 0f
        targetAngle = 0f
        pulseAlpha = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val pivotY = dp(20f)
        val length = height - dp(56f)
        if (length <= 0f) return

        // base
        canvas.drawCircle(cx, pivotY, dp(7f), pivotPaint)

        val radians = Math.toRadians(angle.toDouble())
        val bobX = cx + (length * sin(radians)).toFloat()
        val bobY = pivotY + (length * cos(radians)).toFloat()
        val bobRadius = dp(15f)

        // rastro do movimento
        triangle.reset()
        triangle.moveTo(cx, pivotY)
        triangle.lineTo(bobX, bobY)
        canvas.drawLine(cx, pivotY, bobX, bobY, rodPaint)

        // pulso luminoso na batida
        if (pulseAlpha > 0f) {
            glowPaint.alpha = (pulseAlpha * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(bobX, bobY, bobRadius * 2.1f, glowPaint)
            glowPaint.alpha = 255
        }

        bobPaint.color = if (pulseAlpha > 0.2f) Palette.ok else Palette.text
        canvas.drawCircle(bobX, bobY, bobRadius, bobPaint)
        canvas.drawCircle(bobX, bobY, bobRadius * 0.55f, trackPaint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}

