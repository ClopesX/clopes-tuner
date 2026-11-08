package com.clopes.tuner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable

/**
 * Paleta de um tema do app.
 *
 * `bgGradient` nulo = fundo solido; com 2+ cores o fundo vira gradiente (base do
 * glassmorphism). `cardGlass` liga o cartao translucido com borda em gradiente.
 */
data class AppTheme(
    val id: String,
    val label: String,
    val bg: Int,
    val bgGradient: List<Int>?,
    val card: Int,
    val cardGlass: Boolean,
    val accent: Int,
    val text: Int,
    val muted: Int,
    val tab: Int,
    val ok: Int,
    val warn: Int,
    val bad: Int,
    val track: Int = 0xFF1B2430.toInt(),
    val border: Int = 0xFF2B3644.toInt(),
    val selected: Int = 0xFF16233A.toInt(),
)

/**
 * Paleta global consultada pelos desenhos em Canvas (arco, agulha, medidor).
 * Definida por [apply] antes da tela ser montada.
 */
object Palette {
    var bg = 0xFF0B0F14.toInt()
    var card = 0xFF111823.toInt()
    var text = 0xFFE8EEF7.toInt()
    var muted = 0xFF7D8CA1.toInt()
    var accent = 0xFF4C8DFF.toInt()
    var ok = 0xFF37D67A.toInt()
    var warn = 0xFFF5A623.toInt()
    var bad = 0xFFFF5C5C.toInt()
    var track = 0xFF1B2430.toInt()
    var border = 0xFF2B3644.toInt()
    var selected = 0xFF16233A.toInt()

    fun apply(theme: AppTheme) {
        bg = theme.bg
        card = theme.card
        text = theme.text
        muted = theme.muted
        accent = theme.accent
        ok = theme.ok
        warn = theme.warn
        bad = theme.bad
        track = theme.track
        border = theme.border
        selected = theme.selected
    }
}

object Themes {
    private const val PREFS = "clopes_tuner"
    private const val KEY = "app_theme"

    val dark = AppTheme(
        id = "dark",
        label = "Escuro",
        bg = 0xFF0B0F14.toInt(),
        bgGradient = null,
        card = 0xFF111823.toInt(),
        cardGlass = false,
        accent = 0xFF4C8DFF.toInt(),
        text = 0xFFE8EEF7.toInt(),
        muted = 0xFF7D8CA1.toInt(),
        tab = 0xFF1B2B45.toInt(),
        ok = 0xFF37D67A.toInt(),
        warn = 0xFFF5A623.toInt(),
        bad = 0xFFFF5C5C.toInt(),
    )

    /** Glassmorphism: fundo vibrante, cartoes translucidos, borda em gradiente. */
    val glass = AppTheme(
        id = "glass",
        label = "Vidro",
        bg = 0xFF101A2C.toInt(),
        bgGradient = listOf(0xFF1B2F55.toInt(), 0xFF3A1F52.toInt(), 0xFF0E1626.toInt()),
        card = 0x33FFFFFF,
        cardGlass = true,
        accent = 0xFF7FB0FF.toInt(),
        text = Color.WHITE,
        muted = 0xFFB9C6DA.toInt(),
        tab = 0x33FFFFFF,
        ok = 0xFF4CE0A0.toInt(),
        warn = 0xFFFFC24D.toInt(),
        bad = 0xFFFF7A7A.toInt(),
        track = 0x26FFFFFF,
        border = 0x40FFFFFF,
        selected = 0x4DFFFFFF,
    )

    /** Preto com acento roxo/ciano e cartoes de vidro. */
    val neon = AppTheme(
        id = "neon",
        label = "Neon",
        bg = 0xFF08030F.toInt(),
        bgGradient = listOf(0xFF140626.toInt(), 0xFF071A2E.toInt(), 0xFF08030F.toInt()),
        card = 0x33FFFFFF,
        cardGlass = true,
        accent = 0xFFC77DFF.toInt(),
        text = 0xFFEDE7FF.toInt(),
        muted = 0xFF9A8FC4.toInt(),
        tab = 0x33FFFFFF,
        ok = 0xFF39FF88.toInt(),
        warn = 0xFFFFD166.toInt(),
        bad = 0xFFFF4D6D.toInt(),
        track = 0x24FFFFFF,
        border = 0x3DFFFFFF,
        selected = 0x33C77DFF,
    )

    /** Paleta quente, tons de madeira e ambar. */
    val amber = AppTheme(
        id = "amber",
        label = "Âmbar",
        bg = 0xFF140F08.toInt(),
        bgGradient = listOf(0xFF22160A.toInt(), 0xFF160E06.toInt()),
        card = 0xFF241A0F.toInt(),
        cardGlass = false,
        accent = 0xFFFFB74D.toInt(),
        text = 0xFFFFF3E0.toInt(),
        muted = 0xFFB39B7D.toInt(),
        tab = 0xFF33230F.toInt(),
        ok = 0xFF9CCC65.toInt(),
        warn = 0xFFFFB74D.toInt(),
        bad = 0xFFFF7043.toInt(),
        track = 0xFF2E2113.toInt(),
        border = 0xFF453218.toInt(),
        selected = 0xFF3A2A16.toInt(),
    )

    /** Verde profundo de floresta. */
    val forest = AppTheme(
        id = "forest",
        label = "Floresta",
        bg = 0xFF07120C.toInt(),
        bgGradient = listOf(0xFF0D2A1B.toInt(), 0xFF07120C.toInt()),
        card = 0xFF0F2A1C.toInt(),
        cardGlass = false,
        accent = 0xFF4ADE80.toInt(),
        text = 0xFFE3F6EC.toInt(),
        muted = 0xFF8FB8A3.toInt(),
        tab = 0xFF163A28.toInt(),
        ok = 0xFF34D399.toInt(),
        warn = 0xFFFBBF24.toInt(),
        bad = 0xFFF87171.toInt(),
        track = 0xFF12321F.toInt(),
        border = 0xFF1C4530.toInt(),
        selected = 0xFF1A3D2A.toInt(),
    )

    val light = AppTheme(
        id = "light",
        label = "Claro",
        bg = 0xFFEEF2F8.toInt(),
        bgGradient = listOf(0xFFF7FAFF.toInt(), 0xFFE6EDF8.toInt()),
        card = Color.WHITE,
        cardGlass = false,
        accent = 0xFF2F6FE0.toInt(),
        text = 0xFF14202E.toInt(),
        muted = 0xFF5B6B7F.toInt(),
        tab = 0xFFDCE6F5.toInt(),
        ok = 0xFF1FA463.toInt(),
        warn = 0xFFC77700.toInt(),
        bad = 0xFFD32F2F.toInt(),
        track = 0xFFDCE4F0.toInt(),
        border = 0xFFC9D4E5.toInt(),
        selected = 0xFFDCE6F5.toInt(),
    )

    val all = listOf(dark, glass, neon, amber, forest, light)

    fun byId(id: String?): AppTheme = all.firstOrNull { it.id == id } ?: dark

    fun load(context: Context): AppTheme =
        byId(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, dark.id))

    fun save(context: Context, theme: AppTheme) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, theme.id)
            .apply()
    }

    /** Fundo da tela: cor solida ou gradiente diagonal. */
    fun background(theme: AppTheme): Drawable {
        val gradient = theme.bgGradient
        return if (gradient == null || gradient.size < 2) {
            GradientDrawable().apply { setColor(theme.bg) }
        } else {
            GradientDrawable(GradientDrawable.Orientation.TL_BR, gradient.toIntArray())
        }
    }
}

/**
 * Cartao de vidro desenhado em Canvas:
 *  - preenchimento com gradiente translucido (branco 22% -> 8%);
 *  - borda de 1,5 dp com gradiente (branco 45% -> 12% -> 55%), como no artigo;
 *  - cantos arredondados iguais ao resto do app.
 */
class GlassCardDrawable(
    private val cornerRadius: Float,
    private val borderWidth: Float,
) : Drawable() {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = borderWidth
    }
    private val path = Path()
    private val rect = RectF()

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        super.onBoundsChange(bounds)
        rect.set(bounds)
        path.reset()
        path.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)

        fillPaint.shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            intArrayOf(0x38FFFFFF, 0x1AFFFFFF, 0x0DFFFFFF),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        borderPaint.shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            intArrayOf(0x73FFFFFF, 0x1FFFFFFF, 0x8CFFFFFF.toInt()),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(path, fillPaint)
        val inset = borderWidth / 2f
        rect.inset(inset, inset)
        val old = Path(path)
        path.reset()
        path.addRoundRect(rect, cornerRadius - inset, cornerRadius - inset, Path.Direction.CW)
        canvas.drawPath(path, borderPaint)
        path.set(old)
        rect.set(bounds)
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        borderPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        borderPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}




