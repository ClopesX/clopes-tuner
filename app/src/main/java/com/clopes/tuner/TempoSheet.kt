package com.clopes.tuner

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Menu de tempo no formato bottom sheet, sem AndroidX:
 *
 *  - sobe deslizando da base (translationY + DecelerateInterpolator);
 *  - arrastar a alcinha move o card junto com o dedo;
 *  - soltar acima de 30% da altura (ou com velocidade) fecha deslizando;
 *  - tocar num item aplica o compasso e fecha.
 */
class TempoSheet(
    private val context: Context,
    private val options: List<String>,
    private val selectedIndex: Int,
    private val onPick: (Int) -> Unit,
) {

    private val dialog = Dialog(context)
    private val accent = Palette.accent
    private val cardColor = Palette.card
    private val textColor = Color.WHITE
    private val mutedColor = Palette.muted
    private val dividerColor = Palette.border
    private val trackColor = Palette.track

    private lateinit var sheet: LinearLayout
    private var dismissing = false

    fun show() {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
        }

        sheet = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(cardColor)
                cornerRadii = floatArrayOf(
                    dp(24f), dp(24f),
                    dp(24f), dp(24f),
                    0f, 0f,
                    0f, 0f,
                )
            }
            elevation = dp(18f)
            setPadding(0, dp(10f).toInt(), 0, dp(12f).toInt())
        }
        root.addView(sheet, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))

        // alcinha (area de arrasto)
        val handleArea = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(28f).toInt(),
            )
        }
        handleArea.addView(View(context).apply {
            background = GradientDrawable().apply {
                setColor(Palette.border)
                cornerRadius = dp(3f)
            }
        }, LinearLayout.LayoutParams(dp(54f).toInt(), dp(5f).toInt()))

        attachDrag(handleArea)
        sheet.addView(handleArea)

        options.forEachIndexed { index, label ->
            sheet.addView(buildRow(index, label), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(58f).toInt(),
            ))
            if (index < options.lastIndex) {
                sheet.addView(View(context).apply { setBackgroundColor(dividerColor) },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(1f).toInt(),
                    ))
            }
        }

        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(root)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setWindowAnimations(0)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.6f)
        }

        dialog.setOnShowListener {
            sheet.post {
                val height = sheet.height.toFloat().coerceAtLeast(dp(220f))
                sheet.translationY = height
                sheet.animate()
                    .translationY(0f)
                    .setDuration(260)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }
        dialog.show()
    }

    private fun buildRow(index: Int, label: String): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24f).toInt(), 0, dp(24f).toInt(), 0)
            isClickable = true
            setOnClickListener {
                onPick(index)
                animateOut()
            }
        }
        row.addView(TextView(context).apply {
            text = label
            setTextColor(textColor)
            textSize = 17f
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f,
        ))
        row.addView(DotView(context, selected = index == selectedIndex), LinearLayout.LayoutParams(
            dp(22f).toInt(),
            dp(22f).toInt(),
        ))
        return row
    }

    private fun attachDrag(view: View) {
        val tracker = VelocityTracker.obtain()
        var startY = 0f
        var dragging = false

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    tracker.clear()
                    tracker.addMovement(event)
                    startY = event.rawY
                    dragging = true
                    sheet.animate().cancel()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (dragging) {
                        tracker.addMovement(event)
                        val dy = (event.rawY - startY).coerceAtLeast(0f)
                        sheet.translationY = dy
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        dragging = false
                        tracker.addMovement(event)
                        tracker.computeCurrentVelocity(1000)
                        val velocity = tracker.yVelocity
                        val threshold = sheet.height * 0.3f
                        if (sheet.translationY > threshold || velocity > 900f) {
                            animateOut()
                        } else {
                            sheet.animate()
                                .translationY(0f)
                                .setDuration(200)
                                .setInterpolator(DecelerateInterpolator())
                                .start()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun animateOut() {
        if (dismissing) return
        dismissing = true
        sheet.animate()
            .translationY(sheet.height.toFloat())
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { runCatching { dialog.dismiss() } }
            .start()
    }

    private fun dp(value: Float): Float = value * context.resources.displayMetrics.density

    /** Bolinha de seleção desenhada (evita o estilo padrão do RadioButton). */
    private class DotView(context: Context, private val selected: Boolean) : View(context) {
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * resources.displayMetrics.density
            color = if (selected) Palette.accent else Palette.muted
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Palette.accent
        }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val radius = minOf(width, height) / 2f - ring.strokeWidth
            canvas.drawCircle(cx, cy, radius, ring)
            if (selected) {
                canvas.drawCircle(cx, cy, radius * 0.55f, fill)
            }
        }
    }
}


