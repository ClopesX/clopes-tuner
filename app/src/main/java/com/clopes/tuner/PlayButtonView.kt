package com.clopes.tuner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Botao de play desenhado por codigo: circulo branco + triangulo azul.
 *
 * O triangulo e equilatero e os tres vertices ficam na mesma distancia do
 * centro, entao a margem entre cada ponta e a borda do circulo e identica.
 */
class PlayButtonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var triangleRatio = 0.62f
        set(value) {
            field = value
            invalidate()
        }

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val trianglePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Palette.accent
    }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f - dp(2f)
        canvas.drawCircle(cx, cy, radius, circlePaint)

        val triangleRadius = radius * triangleRatio
        path.reset()
        for (index in 0 until 3) {
            val angle = Math.toRadians((index * 120).toDouble())
            val x = cx + (triangleRadius * cos(angle)).toFloat()
            val y = cy + (triangleRadius * sin(angle)).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, trianglePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            performClick()
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

