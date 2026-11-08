package com.clopes.tuner

import android.Manifest
import android.app.Activity
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.text.style.SuperscriptSpan
import android.text.style.TypefaceSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.log2

class MainActivity : Activity() {

    private lateinit var engine: TunerEngine
    private lateinit var metronome: Metronome
    private var unrealised: Typeface? = null
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var noteText: TextView
    private lateinit var prevNoteText: TextView
    private lateinit var nextNoteText: TextView
    private lateinit var noteRow: LinearLayout
    private lateinit var centsText: TextView
    private lateinit var stringText: TextView
    private lateinit var frequencyText: TextView
    private lateinit var needle: NeedleView
    private lateinit var referenceLabel: TextView
    private lateinit var instrumentName: TextView
    private lateinit var instrumentNotes: TextView
    private val tuningCells = mutableListOf<Pair<LinearLayout, ImageView>>()
    private lateinit var tempoRing: TempoRingView
    private lateinit var bpmText: TextView
    private lateinit var playButton: PlayButtonView
    private lateinit var beatBars: LinearLayout
    private lateinit var tempoBox: LinearLayout
    private lateinit var soundBox: LinearLayout
    private lateinit var speakerIcon: ImageView
    private lateinit var tapPad: LinearLayout
    private val beatBoxes = mutableListOf<BeatBoxView>()
    private var signatureIndex = 2
    private var lastTapAt = 0L
    private val tapIntervals = ArrayDeque<Long>()
    private lateinit var tunerScreen: LinearLayout
    private lateinit var metroScreen: LinearLayout
    private lateinit var tunerTab: ImageView
    private lateinit var metroTab: ImageView
    private lateinit var levelMeter: LevelMeterView
    private lateinit var overlay: FrameLayout
    private lateinit var videoTexture: TextureView
    private var player: MediaPlayer? = null
    private lateinit var scrim: View
    private var eggPlaying = false
    private var activityPaused = false
    private var eggProgress = 0
    private var lastEventNote: String? = null
    private lateinit var dbText: TextView

    private var reference = 440f
    private var tuning = Tunings.all[0]
    private var listening = false
    private var currentTab = TAB_TUNER
    private lateinit var settingsScreen: LinearLayout
    private lateinit var gearTab: ImageView
    private lateinit var eggRow: LinearLayout
    private lateinit var eggSwitch: Switch
    private lateinit var resetSwitch: Switch
    private lateinit var prefs: SharedPreferences
    private var eggUnlocked = false
    private var eggEnabled = true
    private var restoringDefaults = false
    private lateinit var meterCard: LinearLayout
    private lateinit var keepScreenRow: LinearLayout
    private lateinit var keepScreenSwitch: Switch
    private lateinit var dbRow: LinearLayout
    private lateinit var dbSwitch: Switch
    private lateinit var toleranceRow: LinearLayout
    private lateinit var toleranceLabel: TextView
    private lateinit var toleranceBar: SeekBar
    private var keepScreenOnPref = true
    private var showDbMeter = true
    private var eggToleranceCents = 90
    // Ultima leitura valida: o som da corda morre, mas a nota e a agulha ficam
    // paradas no lugar (como no app de referencia). Depois de 2,5 s sem sinal a
    // leitura fica mais apagada, sinalizando que e a ultima medida.
    private var lastReading: NoteReading? = null
    private var lastSeenAt = 0L

    private val staleTicker = object : Runnable {
        override fun run() {
            val reading = lastReading
            if (listening && reading != null) {
                val age = System.currentTimeMillis() - lastSeenAt
                val alpha = if (age > 2500) 0.55f else 1f
                noteRow.alpha = alpha
                needle.alpha = alpha
            }
            handler.postDelayed(this, 400)
        }
    }

    private lateinit var theme: AppTheme
    private var bgColor = Palette.bg
    private var cardColor = Palette.card
    private var activeTabColor = Color.parseColor("#1b2b45")
    private var textColor = Palette.text
    private var mutedColor = Color.parseColor("#7d8ca1")
    private var accent = Palette.accent
    private var okColor = Palette.ok
    private var warnColor = Palette.warn
    private var badColor = Palette.bad

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("clopes_tuner", MODE_PRIVATE)
        theme = Themes.load(this)
        applyTheme(theme)
        eggUnlocked = prefs.getBoolean(KEY_EGG_UNLOCKED, false)
        eggEnabled = prefs.getBoolean(KEY_EGG_ENABLED, true)
        keepScreenOnPref = prefs.getBoolean(KEY_KEEP_SCREEN, true)
        showDbMeter = prefs.getBoolean(KEY_SHOW_DB, true)
        eggToleranceCents = prefs.getInt(KEY_EGG_TOLERANCE, 90)
        EggSequence.tolerance = eggToleranceCents / 100.0
        unrealised = runCatching { resources.getFont(R.font.unrealised) }.getOrNull()
        metronome = Metronome(this)
        engine = TunerEngine { frequency, level, bands -> onPitch(frequency, level, bands) }
        metronome.onBeat = { beat ->
            handler.post { updateBeatBars(beat) }
        }
        setContentView(buildScreen())
        applySystemBars()
    }

    override fun onResume() {
        super.onResume()
        activityPaused = false
        if (currentTab == TAB_TUNER) startListening()
    }

    override fun onPause() {
        // Sair do app nao pode deixar o video do easter egg tocando audio.
        activityPaused = true
        if (eggPlaying) dismissEgg()
        eggProgress = 0
        lastEventNote = null
        stopListening()
        metronome.stop()
        super.onPause()
    }

    override fun onDestroy() {
        if (eggPlaying) dismissEgg()
        stopListening()
        metronome.release()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ audio

    private fun startListening() {
        if (listening) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
            return
        }
        listening = true
        updateKeepScreenOn()
        lastReading = null
        lastSeenAt = 0L
        noteRow.alpha = 1f
        needle.alpha = 1f
        engine.start()
        centsText.text = "toque uma corda"
        centsText.setTextColor(mutedColor)
        handler.removeCallbacks(staleTicker)
        handler.post(staleTicker)
    }

    private fun stopListening() {
        if (!listening) return
        listening = false
        updateKeepScreenOn()
        handler.removeCallbacks(staleTicker)
        lastReading = null
        lastSeenAt = 0L
        noteRow.alpha = 1f
        needle.alpha = 1f
        engine.stop()
        showNote(false)
        centsText.text = "microfone parado"
        centsText.setTextColor(mutedColor)
        frequencyText.text = ""
        stringText.text = ""
        needle.update(null)
        levelMeter.reset()
        dbText.text = "--"
    }

    private fun onPitch(frequency: Float?, level: Float, bands: FloatArray) {
        handler.post {
            if (!listening) return@post

            levelMeter.update(bands)
            val dbfs = if (level <= 0.0002f) -120.0 else 20 * log10(level.toDouble())
            dbText.text = if (dbfs <= -110.0) "--" else (dbfs + 100).coerceIn(0.0, 120.0).toInt().toString()

            if (frequency == null) {
                // Sem sinal: se ja houve leitura, mantem nota/agulha na tela.
                if (lastReading == null) {
                    showNote(false)
                    centsText.text = if (level < 0.008f) "toque uma corda" else "sinal fraco"
                    centsText.setTextColor(mutedColor)
                    stringText.text = ""
                    frequencyText.text = ""
                    needle.update(null)
                }
                lastEventNote = null
                return@post
            }

            val reading = Notes.reading(frequency, reference)
            lastReading = reading
            lastSeenAt = System.currentTimeMillis()
            noteRow.alpha = 1f
            needle.alpha = 1f
            showNote(true)
            noteText.setTextColor(tuneColor(reading.cents))
            noteText.text = noteLabel(reading.name, reading.octave)
            prevNoteText.text = noteLabel(Notes.nameFor(reading.midi - 1), Notes.octaveFor(reading.midi - 1), displayFace = false)
            nextNoteText.text = noteLabel(Notes.nameFor(reading.midi + 1), Notes.octaveFor(reading.midi + 1), displayFace = false)

            val tuned = abs(reading.cents) <= 3
            centsText.text = if (tuned) "AFINADO" else String.format(Locale.US, "%+d cents", reading.cents)
            centsText.setTextColor(
                when {
                    abs(reading.cents) <= 3 -> okColor
                    abs(reading.cents) <= 15 -> warnColor
                    else -> badColor
                },
            )

            val string = Notes.nearestString(reading, tuning)
            stringText.text = when {
                string != null -> "corda $string"
                tuning.notes.isNotEmpty() -> "fora das cordas"
                else -> ""
            }

            frequencyText.text = String.format(
                Locale.US,
                "%.1f Hz   ·   alvo %.1f Hz",
                reading.frequency,
                reading.targetFrequency,
            )
            needle.update(reading.cents.toFloat())

            // easter egg: D D B D D D C# — tolerante a desafino (90 cents).
            if (reading.name != lastEventNote) {
                lastEventNote = reading.name
                advanceEgg(reading.frequency)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else {
            centsText.text = "sem permissão de microfone"
            centsText.setTextColor(badColor)
        }
    }

    // -------------------------------------------------------------------- UI

    private fun buildScreen(): FrameLayout {
        // Sem ScrollView: as telas cabem inteiras, entao nada de rolagem.
        val scroller = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(12))
        }

        content.addView(buildHeader(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(10) })

        tunerScreen = buildTunerScreen()
        content.addView(tunerScreen, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ))

        metroScreen = buildMetronomeScreen()
        metroScreen.visibility = View.GONE
        content.addView(metroScreen, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ))

        settingsScreen = buildSettingsScreen()
        settingsScreen.visibility = View.GONE
        content.addView(settingsScreen, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ))

        scroller.addView(content, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))

        scroller.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(0, bars.top, 0, bars.bottom)
            }
            insets
        }

        val root = FrameLayout(this).apply { background = Themes.background(theme) }
        root.addView(scroller, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        buildEggOverlay(root)
        return root
    }

    private fun buildEggOverlay(root: FrameLayout) {
        overlay = FrameLayout(this).apply {
            visibility = View.GONE
            setBackgroundColor(Color.BLACK)
            isClickable = true
            setOnClickListener { dismissEgg() }
        }
        videoTexture = TextureView(this)
        overlay.addView(videoTexture, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        scrim = View(this).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 1f
        }
        overlay.addView(scrim, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        root.addView(overlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
    }

    /** Avanca a sequencia do easter egg com a nota detectada (tolerante a cents). */
    private fun advanceEgg(frequency: Float) {
        val midi = 69.0 + 12.0 * log2(frequency.toDouble() / reference)
        val next = EggSequence.advance(eggProgress, midi)
        if (next == EggSequence.COMPLETE) {
            eggProgress = 0
            triggerEgg()
        } else {
            eggProgress = next
        }
    }
    private fun triggerEgg() {
        if (!eggEnabled || !listening || currentTab != TAB_TUNER) return
        if (eggPlaying) return
        Log.d("CLOPESEGG", "trigger")
        if (!eggUnlocked) {
            eggUnlocked = true
            prefs.edit().putBoolean(KEY_EGG_UNLOCKED, true).apply()
            refreshSettingsVisibility()
        }
        eggPlaying = true
        scrim.alpha = 1f
        overlay.visibility = View.VISIBLE
        overlay.bringToFront()

        videoTexture.post {
            if (!eggPlaying || activityPaused) return@post
            runCatching {
                val descriptor = resources.openRawResourceFd(R.raw.dance1)
                val media = MediaPlayer()
                descriptor.use {
                    media.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
                val texture = videoTexture.surfaceTexture
                if (texture == null) {
                    media.release()
                    return@runCatching
                }
                media.setSurface(Surface(texture))
                media.setOnPreparedListener { prepared ->
                    Log.d("CLOPESEGG", "prepared " + prepared.duration)
                    if (!eggPlaying || activityPaused) {
                        releasePlayer()
                        return@setOnPreparedListener
                    }
                    prepared.start()
                    ValueAnimator.ofFloat(1f, 0f).apply {
                        duration = 2000
                        addUpdateListener { scrim.alpha = it.animatedValue as Float }
                        start()
                    }
                }
                media.setOnCompletionListener {
                    Log.d("CLOPESEGG", "completion")
                    fadeOutEgg()
                }
                media.setOnErrorListener { _, what, extra ->
                    Log.e("CLOPESEGG", "erro " + what + " / " + extra)
                    dismissEgg()
                    true
                }
                player = media
                media.prepareAsync()
            }.onFailure {
                Log.e("CLOPESEGG", "falha ao preparar: " + it.message)
                dismissEgg()
            }
        }
    }

    private fun fadeOutEgg() {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3000
            addUpdateListener { scrim.alpha = it.animatedValue as Float }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    dismissEgg()
                }
            })
            start()
        }
    }

    /** Encerra o video de verdade: release() garante que nenhum audio continue. */
    private fun releasePlayer() {
        player?.let { media ->
            runCatching { if (media.isPlaying) media.stop() }
            runCatching { media.release() }
        }
        player = null
    }

    private fun dismissEgg() {
        eggPlaying = false
        releasePlayer()
        overlay.visibility = View.GONE
        scrim.alpha = 1f
    }
    private fun buildSettingsScreen(): LinearLayout {
        val screen = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val card = card()
        card.addView(sectionLabel("CONFIGURAÇÕES"))

        eggRow = settingsRow("Easter egg")
        eggSwitch = settingsSwitch(eggEnabled) { checked ->
            eggEnabled = checked
            prefs.edit().putBoolean(KEY_EGG_ENABLED, checked).apply()
        }
        eggRow.addView(eggSwitch)
        card.addView(eggRow)

        toleranceRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }
        val toleranceHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        toleranceHeader.addView(TextView(this).apply {
            text = "Tolerância do easter egg"
            setTextColor(textColor)
            textSize = 16f
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        toleranceLabel = TextView(this).apply {
            setTextColor(accent)
            textSize = 14f
        }
        toleranceHeader.addView(toleranceLabel)
        toleranceRow.addView(toleranceHeader)

        toleranceBar = SeekBar(this).apply {
            max = TOLERANCE_VALUES.size - 1
            progress = TOLERANCE_VALUES.indexOf(eggToleranceCents).coerceAtLeast(0)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    eggToleranceCents = TOLERANCE_VALUES[progress.coerceIn(0, TOLERANCE_VALUES.size - 1)]
                    EggSequence.tolerance = eggToleranceCents / 100.0
                    toleranceLabel.text = "±$eggToleranceCents cents"
                    prefs.edit().putInt(KEY_EGG_TOLERANCE, eggToleranceCents).apply()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        toleranceRow.addView(toleranceBar)
        card.addView(toleranceRow)

        keepScreenRow = settingsRow("Manter tela acesa")
        keepScreenSwitch = settingsSwitch(keepScreenOnPref) { checked ->
            keepScreenOnPref = checked
            prefs.edit().putBoolean(KEY_KEEP_SCREEN, checked).apply()
            updateKeepScreenOn()
        }
        keepScreenRow.addView(keepScreenSwitch)
        card.addView(keepScreenRow)

        dbRow = settingsRow("Mostrar medidor de dB")
        dbSwitch = settingsSwitch(showDbMeter) { checked ->
            showDbMeter = checked
            prefs.edit().putBoolean(KEY_SHOW_DB, checked).apply()
            refreshMeterVisibility()
        }
        dbRow.addView(dbSwitch)
        card.addView(dbRow)

        val aboutRow = settingsRow("Sobre")
        aboutRow.addView(TextView(this).apply {
            text = "›"
            setTextColor(mutedColor)
            textSize = 20f
        })
        aboutRow.setOnClickListener { showAbout() }
        card.addView(aboutRow)

        val themeRow = settingsRow("Tema")
        themeRow.addView(TextView(this).apply {
            text = theme.label
            setTextColor(accent)
            textSize = 14f
        })
        themeRow.setOnClickListener { showThemeDialog() }
        card.addView(themeRow)
        val resetRow = settingsRow("Restaurar padrão")
        resetSwitch = settingsSwitch(false) { checked ->
            if (checked && !restoringDefaults) {
                restoringDefaults = true
                restoreDefaults()
                resetSwitch.isChecked = false
                restoringDefaults = false
            }
        }
        resetRow.addView(resetSwitch)
        card.addView(resetRow)

        screen.addView(card)

        val referenceCard = card().apply {
            addView(sectionLabel("REFERÊNCIA A4"))
            referenceLabel = TextView(this@MainActivity).apply {
                text = referenceText()
                setTextColor(mutedColor)
                textSize = 13f
            }
            addView(referenceLabel)
            addView(SeekBar(this@MainActivity).apply {
                max = 51
                progress = (reference - 415f).toInt().coerceIn(0, 51)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        reference = 415f + progress
                        referenceLabel.text = referenceText()
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                })
            })
        }
        screen.addView(referenceCard, cardParams(top = 12))

        refreshSettingsVisibility()
        return screen
    }

    private fun settingsRow(label: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(14), 0, dp(14))
        addView(TextView(this@MainActivity).apply {
            text = label
            setTextColor(textColor)
            textSize = 16f
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun settingsSwitch(checked: Boolean, onChange: (Boolean) -> Unit): Switch =
        Switch(this).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, value -> onChange(value) }
        }

    private fun restoreDefaults() {
        eggUnlocked = false
        eggEnabled = true
        keepScreenOnPref = true
        showDbMeter = true
        eggToleranceCents = 90
        EggSequence.tolerance = 0.9
        prefs.edit()
            .putBoolean(KEY_EGG_UNLOCKED, false)
            .putBoolean(KEY_EGG_ENABLED, true)
            .putBoolean(KEY_KEEP_SCREEN, true)
            .putBoolean(KEY_SHOW_DB, true)
            .putInt(KEY_EGG_TOLERANCE, 90)
            .apply()
        refreshSettingsVisibility()
        refreshMeterVisibility()
        updateKeepScreenOn()
    }

    /** Cor das barras do sistema e icones claros/escuros conforme o tema. */
    private fun applySystemBars() {
        val light = theme.id == "light"
        val barColor = if (theme.cardGlass) android.graphics.Color.TRANSPARENT else theme.bg
        window.statusBarColor = barColor
        window.navigationBarColor = theme.bg
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val controller = window.decorView.windowInsetsController ?: return
            val mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            controller.setSystemBarsAppearance(if (light) mask else 0, mask)
        } else {
            @Suppress("DEPRECATION")
            var flags = window.decorView.systemUiVisibility
            flags = if (light) {
                flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            } else {
                flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            }
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = flags
        }
    }

    /** Aplica a paleta do tema nas cores usadas pela tela inteira. */
    private fun applyTheme(value: AppTheme) {
        theme = value
        bgColor = value.bg
        cardColor = value.card
        activeTabColor = value.tab
        textColor = value.text
        mutedColor = value.muted
        accent = value.accent
        okColor = value.ok
        warnColor = value.warn
        badColor = value.bad
        Palette.apply(value)
    }

    private fun showThemeDialog() {
        val labels = Themes.all.map { it.label }.toTypedArray()
        val selected = Themes.all.indexOfFirst { it.id == theme.id }.coerceAtLeast(0)
        android.app.AlertDialog.Builder(this)
            .setTitle("Tema")
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                Themes.save(this, Themes.all[which])
                dialog.dismiss()
                recreate()
            }
            .show()
    }

    private fun showAbout() {
        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "?"

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(28), dp(24), dp(12))
        }
        content.addView(TextView(this).apply {
            text = "Clopes Tuner"
            setTextColor(textColor)
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        content.addView(TextView(this).apply {
            text = "Versão brasileira marshmallow"
            setTextColor(mutedColor)
            textSize = 14f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(14) })

        val numberRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        numberRow.addView(TextView(this).apply {
            text = "Nº"
            setTextColor(mutedColor)
            textSize = 14f
            includeFontPadding = false
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.BOTTOM
            bottomMargin = dp(6)
            rightMargin = dp(2)
        })
        numberRow.addView(TextView(this).apply {
            text = version
            setTextColor(textColor)
            textSize = 40f
            includeFontPadding = false
        })
        content.addView(numberRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(10) })

        content.addView(TextView(this).apply {
            text = "Hiclopes Corporation LTDA."
            setTextColor(mutedColor)
            textSize = 14f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(16) })

        android.app.AlertDialog.Builder(this)
            .setView(content)
            .setPositiveButton("OK", null)
            .show()
    }

    /** "Easter egg" e a tolerância só aparecem depois do primeiro desbloqueio. */
    private fun refreshSettingsVisibility() {
        if (!::eggRow.isInitialized) return
        val unlocked = if (eggUnlocked) View.VISIBLE else View.GONE
        eggRow.visibility = unlocked
        if (::toleranceRow.isInitialized) toleranceRow.visibility = unlocked
        if (::eggSwitch.isInitialized) eggSwitch.isChecked = eggEnabled
        if (::keepScreenSwitch.isInitialized) keepScreenSwitch.isChecked = keepScreenOnPref
        if (::dbSwitch.isInitialized) dbSwitch.isChecked = showDbMeter
        if (::toleranceBar.isInitialized) {
            toleranceBar.progress = TOLERANCE_VALUES.indexOf(eggToleranceCents).coerceAtLeast(0)
            toleranceLabel.text = "±$eggToleranceCents cents"
        }
    }
    private fun buildHeader(): LinearLayout {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(this).apply {
            textSize = 26f
            typeface = unrealised ?: Typeface.create(typeface, Typeface.BOLD)
            val label = SpannableString("Clopes Tuner")
            label.setSpan(
                ForegroundColorSpan(accent),
                7,
                label.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            text = label
            setTextColor(textColor)
        }
        titles.addView(title)
        titles.addView(TextView(this).apply {
            text = "afinador cromático · YIN"
            setTextColor(mutedColor)
            textSize = 12f
        })
        header.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        gearTab = tabIcon(R.drawable.ic_gear)
        tunerTab = tabIcon(R.drawable.ic_tuner)
        metroTab = tabIcon(R.drawable.ic_metronome)
        header.addView(gearTab, LinearLayout.LayoutParams(dp(46), dp(46)).apply { rightMargin = dp(8) })
        header.addView(tunerTab, LinearLayout.LayoutParams(dp(46), dp(46)).apply { rightMargin = dp(8) })
        header.addView(metroTab, LinearLayout.LayoutParams(dp(46), dp(46)))

        gearTab.setOnClickListener { switchTab(TAB_SETTINGS) }
        tunerTab.setOnClickListener { switchTab(TAB_TUNER) }
        metroTab.setOnClickListener { switchTab(TAB_METRONOME) }
        updateTabStyles()
        return header
    }

    private fun tabIcon(drawable: Int): ImageView = ImageView(this).apply {
        setImageResource(drawable)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(10), dp(10), dp(10), dp(10))
        setBackground(rounded(activeTabColor, 14))
        isClickable = true
        alpha = 0.55f
    }

    private fun switchTab(tab: Int) {
        currentTab = tab
        tunerScreen.visibility = if (tab == TAB_TUNER) View.VISIBLE else View.GONE
        metroScreen.visibility = if (tab == TAB_METRONOME) View.VISIBLE else View.GONE
        settingsScreen.visibility = if (tab == TAB_SETTINGS) View.VISIBLE else View.GONE
        updateTabStyles()

        when (tab) {
            TAB_TUNER -> {
                metronome.stop()
                updateBeatBars(-1)
                startListening()
            }
            TAB_METRONOME -> stopListening()
            TAB_SETTINGS -> {
                metronome.stop()
                updateBeatBars(-1)
                stopListening()
            }
        }
    }

    private fun updateKeepScreenOn() {
        val active = listening || metronome.isRunning
        if (keepScreenOnPref && active) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun refreshMeterVisibility() {
        if (::meterCard.isInitialized) {
            meterCard.visibility = if (showDbMeter) View.VISIBLE else View.GONE
        }
    }
    private fun updateTabStyles() {
        val tabs = listOf(gearTab to TAB_SETTINGS, tunerTab to TAB_TUNER, metroTab to TAB_METRONOME)
        for ((view, id) in tabs) {
            val active = currentTab == id
            view.alpha = if (active) 1f else 0.55f
            view.setBackground(rounded(if (active) activeTabColor else cardColor, 14))
        }
    }

    private fun buildTunerScreen(): LinearLayout {
        val screen = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        screen.addView(buildTuningStrip())

        val reading = card()
        screen.addView(reading, cardParams(top = 12))

        val noteArea = FrameLayout(this)

        noteRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        prevNoteText = neighborNote()
        noteRow.addView(prevNoteText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        noteText = TextView(this).apply {
            text = ""
            setTextColor(Color.WHITE)
            textSize = 84f
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
            gravity = Gravity.CENTER
        }
        noteRow.addView(noteText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            leftMargin = dp(6)
            rightMargin = dp(6)
        })

        nextNoteText = neighborNote()
        noteRow.addView(nextNoteText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        noteArea.addView(noteRow, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        ))

        // altura fixa: quando a nota aparece o resto da tela nao se mexe
        reading.addView(noteArea, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(110),
        ))

        centsText = TextView(this).apply {
            text = "toque uma corda"
            setTextColor(mutedColor)
            textSize = 18f
            gravity = Gravity.CENTER
        }
        reading.addView(centsText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))

        stringText = TextView(this).apply {
            text = ""
            setTextColor(accent)
            textSize = 14f
            gravity = Gravity.CENTER
        }
        reading.addView(stringText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(2) })

        needle = NeedleView(this)
        reading.addView(needle, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(165),
        ).apply { topMargin = dp(4) })

        frequencyText = TextView(this).apply {
            text = ""
            setTextColor(mutedColor)
            textSize = 12f
            gravity = Gravity.CENTER
        }
        reading.addView(frequencyText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))

        instrumentName = TextView(this).apply {
            text = tuningTitle()
            setTextColor(textColor)
            textSize = 30f
            letterSpacing = 0.02f
            typeface = unrealised ?: Typeface.create(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        instrumentNotes = TextView(this).apply {
            text = tuningNotes()
            setTextColor(mutedColor)
            textSize = 13f
            letterSpacing = 0.28f
            gravity = Gravity.CENTER
        }
        val instrumentCard = card().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            addView(instrumentName, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
            addView(instrumentNotes, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { leftMargin = dp(14) })
        }
        screen.addView(instrumentCard, cardParams(top = 10))

        meterCard = card()
        refreshMeterVisibility()
        val meterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        levelMeter = LevelMeterView(this)
        meterRow.addView(levelMeter, LinearLayout.LayoutParams(0, dp(103), 1f))

        val dbBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        dbBox.addView(TextView(this).apply {
            text = "dB"
            setTextColor(mutedColor)
            textSize = 11f
            gravity = Gravity.CENTER
        })
        dbText = TextView(this).apply {
            text = "--"
            setTextColor(textColor)
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        dbBox.addView(dbText)
        meterRow.addView(dbBox, LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT))
        meterCard.addView(meterRow)
        screen.addView(meterCard, cardParams(top = 12))

        return screen
    }

    /** Faixa horizontal de instrumentos (rolagem lateral). */
    private fun buildTuningStrip(): HorizontalScrollView {
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), dp(2), dp(2))
        }

        val icons = listOf(
            R.drawable.ic_instr_cromatico,
            R.drawable.ic_instr_violao,
            R.drawable.ic_instr_baixo,
            R.drawable.ic_instr_violino,
            R.drawable.ic_instr_ukulele,
            R.drawable.ic_instr_banjo,
            R.drawable.ic_instr_bandolim,
        )

        Tunings.all.forEachIndexed { index, _ ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                setPadding(dp(4), dp(4), dp(4), dp(4))
                setOnClickListener { selectTuning(index) }
            }
            val icon = ImageView(this).apply {
                setImageResource(icons.getOrElse(index) { R.drawable.ic_instr_cromatico })
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dp(13), dp(13), dp(13), dp(13))
            }
            cell.addView(icon, LinearLayout.LayoutParams(dp(58), dp(58)))
            row.addView(cell, LinearLayout.LayoutParams(dp(66), dp(66)).apply {
                rightMargin = dp(10)
            })
            tuningCells.add(cell to icon)
        }

        scroll.addView(row, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        refreshTuningCells()
        return scroll
    }

    /** Nome e cordas do instrumento, sem acento e em maiusculas (fonte do logo). */
    private fun tuningTitle(): String =
        tuning.name.substringBefore(" ").uppercase(Locale.US)

    private fun tuningNotes(): String =
        tuning.name.substringAfter(" ", "").uppercase(Locale.US)

    private fun selectTuning(index: Int) {
        tuning = Tunings.all.getOrNull(index) ?: return
        if (::instrumentName.isInitialized) instrumentName.text = tuningTitle()
        if (::instrumentNotes.isInitialized) instrumentNotes.text = tuningNotes()
        refreshTuningCells()
    }

    private fun refreshTuningCells() {
        val selectedIndex = Tunings.all.indexOf(tuning)
        tuningCells.forEachIndexed { index, (cell, icon) ->
            val selected = index == selectedIndex
            cell.setBackground(
                rounded(if (selected) Palette.selected else cardColor, 18),
            )
            icon.setColorFilter(if (selected) accent else Palette.muted)
        }
    }

    private fun buildMetronomeScreen(): LinearLayout {
        val screen = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        screen.setPadding(0, dp(10), 0, 0)
        screen.addView(sectionLabel("METRÔNOMO"))

        beatBars = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        screen.addView(beatBars, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(54),
        ).apply { topMargin = dp(8) })
        rebuildBeatBars()

        val ringRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val leftArrow = ImageView(this).apply {
            setImageResource(R.drawable.ic_arrow_left)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackground(rounded(cardColor, 40))
            setOnClickListener { changeBpm(-1) }
        }
        ringRow.addView(leftArrow, LinearLayout.LayoutParams(dp(44), dp(44)).apply { rightMargin = dp(12) })

        val ringFrame = FrameLayout(this)
        tempoRing = TempoRingView(this).apply {
            minBpm = 30
            maxBpm = 300
            setBpm(metronome.bpm)
            onChange = { value ->
                metronome.bpm = value
                bpmText.text = value.toString()
            }
        }
        ringFrame.addView(tempoRing, FrameLayout.LayoutParams(dp(220), dp(220), Gravity.CENTER))

        bpmText = TextView(this).apply {
            text = metronome.bpm.toString()
            setTextColor(textColor)
            textSize = 52f
            typeface = runCatching { resources.getFont(R.font.avebury) }.getOrNull()
                ?: Typeface.create("sans-serif-black", Typeface.BOLD)
            includeFontPadding = false
            gravity = Gravity.CENTER
        }
        bpmText.translationY = 0f
        ringFrame.addView(bpmText, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        ))

        ringRow.addView(ringFrame, LinearLayout.LayoutParams(0, dp(220), 1f))

        val rightArrow = ImageView(this).apply {
            setImageResource(R.drawable.ic_arrow_left)
            scaleX = -1f
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackground(rounded(cardColor, 40))
            setOnClickListener { changeBpm(1) }
        }
        ringRow.addView(rightArrow, LinearLayout.LayoutParams(dp(44), dp(44)).apply { leftMargin = dp(12) })
        screen.addView(ringRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(16) })

        playButton = PlayButtonView(this).apply {
            triangleRatio = 0.60f
            setOnClickListener { toggleMetronome() }
        }
        screen.addView(playButton, LinearLayout.LayoutParams(dp(96), dp(96)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = -dp(74)
        })

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        tempoBox = smallCard("TEMPO", signatureLabel())
        controls.addView(tempoBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        speakerIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_sound)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setOnClickListener {
                metronome.muted = !metronome.muted
                alpha = if (metronome.muted) 0.35f else 1f
            }
        }
        controls.addView(speakerIcon, LinearLayout.LayoutParams(dp(56), dp(56)).apply {
            leftMargin = dp(10)
            rightMargin = dp(10)
        })

        soundBox = smallCard("SOM", metronome.sound.label)
        soundBox.setOnClickListener {
            metronome.sound = if (metronome.sound == Metronome.Sound.SNARE) {
                Metronome.Sound.STICK
            } else {
                Metronome.Sound.SNARE
            }
            (soundBox.getChildAt(1) as TextView).text = metronome.sound.label
            metronome.preview()
        }
        controls.addView(soundBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        screen.addView(controls, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(28) })

        tapPad = card().apply {
            gravity = Gravity.CENTER
            minimumHeight = dp(210)
            // Ripple estilo Material: acende no ponto do toque, recortado no card.
            background = RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#24FFFFFF")),
                rounded(cardColor, 16),
                rounded(Color.WHITE, 16),
            )
            setOnClickListener { onTapTempo() }
            addView(TextView(this@MainActivity).apply {
                text = "TOCAR"
                setTextColor(mutedColor)
                textSize = 30f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
            addView(TextView(this@MainActivity).apply {
                text = "toque no ritmo para definir o tempo"
                setTextColor(mutedColor)
                textSize = 12f
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(6) })
        }
        screen.addView(tapPad, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ).apply { topMargin = dp(16) })

        tempoBox.setOnClickListener { showTempoDialog() }

        return screen
    }

    private fun smallCard(label: String, value: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackground(rounded(cardColor, 16))
        setPadding(dp(14), dp(12), dp(14), dp(12))
        addView(TextView(this@MainActivity).apply {
            text = label
            setTextColor(mutedColor)
            textSize = 11f
            letterSpacing = 0.14f
            gravity = Gravity.CENTER
        })
        addView(TextView(this@MainActivity).apply {
            text = value
            setTextColor(textColor)
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(2) })
    }

    private fun showTempoDialog() {
        val labels = SIGNATURES.map { (beats, unit) -> "$beats/$unit" }
        TempoSheet(this, labels, signatureIndex) { which ->
            signatureIndex = which
            val (beats, unit) = SIGNATURES[which]
            metronome.beats = List(beats) { index ->
                Metronome.Beat(accent = index == 0, muted = false)
            }
            (tempoBox.getChildAt(1) as TextView).text = "$beats/$unit"
            rebuildBeatBars()
        }.show()
    }

    private fun signatureLabel(): String {
        val (beats, unit) = SIGNATURES[signatureIndex]
        return "$beats/$unit"
    }

    private fun rebuildBeatBars() {
        if (!::beatBars.isInitialized) return
        beatBars.removeAllViews()
        beatBoxes.clear()

        val beats = metronome.beats.ifEmpty {
            List(4) { index -> Metronome.Beat(accent = index == 0, muted = false) }
        }

        beats.forEachIndexed { index, beat ->
            val box = BeatBoxView(this).apply {
                state = when {
                    beat.muted -> BeatBoxView.State.MUTED
                    beat.accent -> BeatBoxView.State.ACCENT
                    else -> BeatBoxView.State.NORMAL
                }
                onToggle = {
                    cycleBeat(index)
                }
            }
            beatBars.addView(box, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                leftMargin = dp(4)
                rightMargin = dp(4)
            })
            beatBoxes.add(box)
        }
    }

    /**
     * Primeiro tempo alterna acento <-> mudo. Os demais alternam
     * normal -> acento -> mudo -> normal.
     */
    private fun cycleBeat(index: Int) {
        val box = beatBoxes.getOrNull(index) ?: return
        val next = when {
            index == 0 -> if (box.state == BeatBoxView.State.MUTED) {
                BeatBoxView.State.ACCENT
            } else {
                BeatBoxView.State.MUTED
            }
            box.state == BeatBoxView.State.NORMAL -> BeatBoxView.State.ACCENT
            box.state == BeatBoxView.State.ACCENT -> BeatBoxView.State.MUTED
            else -> BeatBoxView.State.NORMAL
        }
        box.state = next

        val updated = metronome.beats.toMutableList()
        if (index < updated.size) {
            updated[index] = Metronome.Beat(
                accent = next == BeatBoxView.State.ACCENT,
                muted = next == BeatBoxView.State.MUTED,
            )
            metronome.beats = updated
        }
    }

    private fun updateBeatBars(active: Int) {
        beatBoxes.forEachIndexed { index, box ->
            box.flashing = index == active
        }
    }

    private fun onTapTempo() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (lastTapAt > 0L) {
            val delta = now - lastTapAt
            if (delta in 150L..2000L) {
                tapIntervals.addLast(delta)
                while (tapIntervals.size > 4) tapIntervals.removeFirst()
                val average = tapIntervals.average()
                val newBpm = (60_000.0 / average).toInt().coerceIn(30, 300)
                metronome.bpm = newBpm
                tempoRing.setBpm(newBpm)
                bpmText.text = newBpm.toString()
            } else {
                tapIntervals.clear()
            }
        }
        lastTapAt = now
    }
    private fun changeBpm(delta: Int) {
        val value = (metronome.bpm + delta).coerceIn(30, 300)
        metronome.bpm = value
        tempoRing.setBpm(value)
        bpmText.text = value.toString()
    }

    private fun toggleMetronome() {
        if (metronome.isRunning) {
            metronome.stop()
            playButton.alpha = 1f
            updateBeatBars(-1)
        } else {
            metronome.start()
            playButton.alpha = 0.6f
        }
        updateKeepScreenOn()
    }
    private fun tuneColor(cents: Int): Int = when {
        abs(cents) <= 3 -> okColor
        abs(cents) <= 15 -> warnColor
        else -> badColor
    }

    private fun neighborNote(): TextView = TextView(this).apply {
        text = ""
        setTextColor(Palette.muted)
        textSize = 26f
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        includeFontPadding = false
    }

    /** Nome + oitava em sobrescrito menor, como no mockup. */
    private fun noteLabel(name: String, octave: Int, displayFace: Boolean = true): CharSequence {
        val label = SpannableString("$name$octave")
        if (displayFace) unrealised?.let { face ->
            label.setSpan(
                TypefaceSpan(face),
                0,
                name.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        label.setSpan(
            RelativeSizeSpan(0.42f),
            name.length,
            label.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        label.setSpan(
            SuperscriptSpan(),
            name.length,
            label.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        return label
    }

    private fun showNote(visible: Boolean) {
        noteRow.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackground(if (theme.cardGlass) GlassCardDrawable(dp(18).toFloat(), (1.5f * resources.displayMetrics.density)) else rounded(cardColor, 18))
        setPadding(dp(16), dp(14), dp(16), dp(14))
    }

    private fun cardParams(top: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(top) }

    private fun sectionLabel(value: String): TextView = TextView(this).apply {
        text = value
        setTextColor(mutedColor)
        textSize = 11f
        letterSpacing = 0.14f
    }

    private fun referenceText(): String =
        String.format(Locale.US, "Referência A4 = %.0f Hz", reference)

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val KEY_EGG_ENABLED = "egg_enabled"
        private const val KEY_KEEP_SCREEN = "keep_screen"
        private const val KEY_SHOW_DB = "show_db"
        private const val KEY_EGG_TOLERANCE = "egg_tolerance"
        private const val REQUEST_AUDIO = 42
        private val TOLERANCE_VALUES = listOf(50, 70, 90, 120)
        private val SIGNATURES = listOf(2 to 4, 3 to 4, 4 to 4, 5 to 4, 6 to 8)
        private const val TAB_TUNER = 0
        private const val TAB_METRONOME = 1
        private const val TAB_SETTINGS = 2
        private const val KEY_EGG_UNLOCKED = "egg_unlocked"
    }
}























































































