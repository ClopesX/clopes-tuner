package com.clopes.tuner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Medidor semicircular de cents + anel de estrobo.
 *
 * O anel externo gira proporcionalmente ao desvio: parado e verde quando esta
 * afinado, girando para a esquerda (bemol) ou direita (sustenido) quando nao.
 * A agulha e o arco colorido dao a leitura exata em cents.
 */
class NeedleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var cents: Float? = null
    private var strobeAngle = 0f
    private var strobeSpeed = 0f
    private var lastFrame = 0L

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Palette.track
        strokeWidth = dp(14f)
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(9f)
        color = Palette.accent
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.border
        strokeWidth = dp(1.5f)
    }

    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.ok
        strokeWidth = dp(3f)
        strokeCap = Paint.Cap.ROUND
    }

    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = dp(5f)
        strokeCap = Paint.Cap.ROUND
        color = Palette.ok
    }

    private val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.bg
        style = Paint.Style.FILL
    }

    private val hubRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = Palette.border
    }

    private val strobePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
        strokeWidth = dp(9f)
        color = Palette.border
    }

    private val arc = RectF()
    private val strobeArc = RectF()

    fun update(value: Float?) {
        val previousSlot = cents?.let { (it / 50f).toInt() }
        cents = value
        strobeSpeed = if (value == null) 0f else value.coerceIn(-50f, 50f) * 4.2f
        if (strobeSpeed != 0f) {
            postInvalidateOnAnimation()
        } else if (previousSlot != null) {
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val now = System.nanoTime()
        if (lastFrame != 0L && strobeSpeed != 0f) {
            val delta = ((now - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.1f)
            strobeAngle = (strobeAngle + strobeSpeed * delta) % 360f
        }
        lastFrame = now

        val cx = width / 2f
        val baseY = height - dp(14f)
        val radius = min(width * 0.42f, baseY - dp(26f))
        if (radius <= 0f) return

        val color = tuneColor()

        // anel de estrobo (externo)
        drawStrobe(canvas, cx, baseY, radius, color)

        arc.set(cx - radius, baseY - radius, cx + radius, baseY + radius)

        // trilho completo (semicirculo superior: 180 -> 360)
        canvas.drawArc(arc, 180f, 180f, false, trackPaint)

        // marcas a cada 10 cents, maiores no centro
        for (step in -50..50 step 10) {
            val angle = Math.toRadians((270f + step / 50f * 80f).toDouble())
            val inner = if (step == 0) radius - dp(26f) else radius - dp(18f)
            val outer = radius - dp(4f)
            canvas.drawLine(
                cx + (inner * cos(angle)).toFloat(),
                baseY + (inner * sin(angle)).toFloat(),
                cx + (outer * cos(angle)).toFloat(),
                baseY + (outer * sin(angle)).toFloat(),
                tickPaint,
            )
        }

        val value = cents ?: return

        val limited = value.coerceIn(-50f, 50f)
        valuePaint.color = color
        needlePaint.color = color

        // arco colorido do centro ate a leitura
        val sweep = limited / 50f * 80f
        canvas.drawArc(arc, 270f, sweep, false, valuePaint)

        // marcador do alvo (centro)
        val targetAngle = Math.toRadians(270.0)
        canvas.drawLine(
            cx + ((radius - dp(30f)) * cos(targetAngle)).toFloat(),
            baseY + ((radius - dp(30f)) * sin(targetAngle)).toFloat(),
            cx + ((radius - dp(2f)) * cos(targetAngle)).toFloat(),
            baseY + ((radius - dp(2f)) * sin(targetAngle)).toFloat(),
            targetPaint,
        )

        // agulha
        val needleAngle = Math.toRadians((270f + sweep).toDouble())
        canvas.drawLine(
            cx,
            baseY,
            cx + ((radius - dp(16f)) * cos(needleAngle)).toFloat(),
            baseY + ((radius - dp(16f)) * sin(needleAngle)).toFloat(),
            needlePaint,
        )

        canvas.drawCircle(cx, baseY, dp(9f), hubPaint)
        canvas.drawCircle(cx, baseY, dp(9f), hubRingPaint)
    }

    private fun drawStrobe(canvas: Canvas, cx: Float, baseY: Float, radius: Float, color: Int) {
        val ringRadius = radius + dp(14f)
        strobeArc.set(cx - ringRadius, baseY - ringRadius, cx + ringRadius, baseY + ringRadius)
        strobePaint.color = color
        for (index in 0 until 30) {
            val start = strobeAngle + index * 12f
            canvas.drawArc(strobeArc, start, 6.5f, false, strobePaint)
        }
    }

    private fun tuneColor(): Int {
        val value = cents ?: return Palette.border
        return when {
            abs(value) <= 5f -> Palette.ok
            abs(value) <= 15f -> Palette.warn
            else -> Palette.bad
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}


