package com.clopes.tuner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Caixa de um tempo do compasso, com duas linhas:
 *
 *   NORMAL   -> so a linha de baixo acesa (som fraco)
 *   ACENTO   -> as duas linhas acesas (som forte)
 *   MUDO     -> nenhuma linha acesa (tempo silencioso)
 *
 * Tocar na caixa alterna o estado.
 */
class BeatBoxView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    enum class State { NORMAL, ACCENT, MUTED }

    var state = State.NORMAL
        set(value) {
            field = value
            invalidate()
        }

    /** Pisca quando o metrônomo toca este tempo. */
    var flashing = false
        set(value) {
            field = value
            invalidate()
        }

    var onToggle: (() -> Unit)? = null

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val topLine = RectF()
    private val bottomLine = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val padding = dp(3f)
        val gap = dp(4f)
        val lineHeight = (height - padding * 2 - gap) / 2f

        topLine.set(padding, padding, width - padding, padding + lineHeight)
        bottomLine.set(padding, padding + lineHeight + gap, width - padding, height - padding)

        val radius = dp(7f)
        val borderColor = if (flashing) Color.WHITE else Palette.border
        val onColor = Color.WHITE
        val offColor = Palette.card

        border.color = borderColor

        // linha de cima: apenas no acento
        fill.color = if (state == State.ACCENT) onColor else offColor
        canvas.drawRoundRect(topLine, radius, radius, fill)
        border.color = borderColor
        canvas.drawRoundRect(topLine, radius, radius, border)

        // linha de baixo: normal ou acento (some no mudo)
        fill.color = if (state == State.MUTED) offColor else onColor
        canvas.drawRoundRect(bottomLine, radius, radius, fill)
        border.color = borderColor
        canvas.drawRoundRect(bottomLine, radius, radius, border)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            performClick()
            onToggle?.invoke()
            return true
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}

