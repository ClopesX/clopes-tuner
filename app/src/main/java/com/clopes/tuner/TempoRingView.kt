package com.clopes.tuner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Anel de BPM: arco de 270 graus com botao arrastavel. Arrastar o botao (ou
 * tocar em qualquer ponto do anel) muda o andamento entre [minBpm] e [maxBpm].
 */
class TempoRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var minBpm = 30
    var maxBpm = 300
    var onChange: ((Int) -> Unit)? = null

    private var bpm = 90
    private val startAngle = 135f
    private val sweepTotal = 270f

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(22f)
        color = Palette.border
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(22f)
        color = Palette.accent
    }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val arc = RectF()

    fun setBpm(value: Int) {
        val clamped = value.coerceIn(minBpm, maxBpm)
        if (clamped == bpm) return
        bpm = clamped
        invalidate()
    }

    fun currentBpm(): Int = bpm

    private fun fraction(): Float = (bpm - minBpm).toFloat() / (maxBpm - minBpm).toFloat()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = minOf(width, height).toFloat()
        val inset = dp(20f)
        arc.set(
            (width - size) / 2f + inset,
            (height - size) / 2f + inset,
            (width + size) / 2f - inset,
            (height + size) / 2f - inset,
        )

        canvas.drawArc(arc, startAngle, sweepTotal, false, trackPaint)
        val sweep = sweepTotal * fraction().coerceIn(0f, 1f)
        canvas.drawArc(arc, startAngle, sweep, false, progressPaint)

        val radius = arc.width() / 2f
        val centerX = arc.centerX()
        val centerY = arc.centerY()
        val angle = Math.toRadians((startAngle + sweep).toDouble())
        val knobX = centerX + (radius * cos(angle)).toFloat()
        val knobY = centerY + (radius * sin(angle)).toFloat()
        canvas.drawCircle(knobX, knobY, dp(17f), knobPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val size = minOf(width, height).toFloat()
                val inset = dp(20f)
                val centerX = width / 2f
                val centerY = height / 2f

                val dx = event.x - centerX
                val dy = event.y - centerY
                var degrees = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                var relative = (degrees - startAngle + 360f) % 360f
                if (relative > sweepTotal) {
                    // fora do arco: encosta na extremidade mais proxima
                    relative = if (relative > sweepTotal + (360f - sweepTotal) / 2f) 0f else sweepTotal
                }
                val value = minBpm + ((maxBpm - minBpm) * (relative / sweepTotal))
                val rounded = value.toInt().coerceIn(minBpm, maxBpm)
                if (rounded != bpm) {
                    bpm = rounded
                    invalidate()
                    onChange?.invoke(rounded)
                }
                return true
            }
        }
        return true
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}


