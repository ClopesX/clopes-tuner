package com.clopes.tuner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Medidor de nivel em barras segmentadas: uma coluna por banda de frequencia,
 * com cores por intensidade (verde -> ambar -> vermelho) e pico decaindo.
 */
class LevelMeterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var levels = FloatArray(COLUMNS)
    private val peaks = FloatArray(COLUMNS)
    private val peakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.text
        style = Paint.Style.FILL
    }
    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    fun update(values: FloatArray) {
        levels = values
        for (index in levels.indices) {
            val value = levels[index]
            peaks[index] = if (value >= peaks[index]) value else maxOf(0f, peaks[index] - 0.012f)
        }
        invalidate()
    }

    fun reset() {
        levels = FloatArray(COLUMNS)
        peaks.fill(0f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val gap = dp(4f)
        val columnWidth = (width - gap * (COLUMNS - 1)) / COLUMNS
        if (columnWidth <= 0f) return

        val segmentHeight = dp(5f)
        val segmentGap = dp(2.5f)
        val totalHeight = height.toFloat()

        for (column in 0 until COLUMNS) {
            val left = column * (columnWidth + gap)
            val value = levels[column].coerceIn(0f, 1f)
            val lit = (value * SEGMENTS).toInt()

            for (segment in 0 until SEGMENTS) {
                val bottom = totalHeight - segment * (segmentHeight + segmentGap)
                val top = bottom - segmentHeight
                if (top < 0f) break

                val on = segment < lit
                val color = when {
                    segment >= SEGMENTS - 2 -> Palette.bad
                    segment >= SEGMENTS - 4 -> Palette.warn
                    else -> Palette.ok
                }
                segmentPaint.color = if (on) color else Palette.track
                canvas.drawRect(left, top, left + columnWidth, bottom, segmentPaint)
            }

            // pico (so aparece quando ha sinal)
            if (peaks[column] > 0.05f) {
                val peak = (peaks[column] * SEGMENTS).toInt().coerceIn(0, SEGMENTS - 1)
                val bottom = totalHeight - peak * (segmentHeight + segmentGap)
                val top = bottom - dp(2f)
                if (top > 0f) {
                    canvas.drawRect(left, top, left + columnWidth, bottom, peakPaint)
                }
            }
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private companion object {
        const val COLUMNS = 12
        const val SEGMENTS = 14
    }
}





