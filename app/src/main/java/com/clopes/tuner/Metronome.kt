package com.clopes.tuner

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Metronomo com amostras reais (família Stick ou Snare) e um estado por tempo
 * do compasso: normal, acentuado ou mudo. O primeiro tempo usa a amostra de
 * abertura (stick_03 / snare_04), o acento usa stick_02 / snare_02 e o normal
 * usa stick_01 / snare_01.
 */
class Metronome(context: Context) {

    enum class Sound(val label: String) {
        STICK("Stick"),
        SNARE("Snare"),
    }

    data class Beat(val accent: Boolean, val muted: Boolean)

    @Volatile
    var bpm: Int = 128

    @Volatile
    var sound: Sound = Sound.STICK

    @Volatile
    var muted: Boolean = false

    /** Estado de cada tempo; o tamanho define o compasso. */
    @Volatile
    var beats: List<Beat> = List(4) { index -> Beat(accent = index == 0, muted = false) }

    @Volatile
    var onBeat: ((Int) -> Unit)? = null

    private val pool: SoundPool
    private val ids: Map<String, Int>
    private var loadedSamples = 0
    private var thread: Thread? = null

    @Volatile
    private var running = false

    init {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(attributes)
            .build()
        pool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) loadedSamples += 1
        }
        ids = mapOf(
            // primeiro tempo usa a amostra de acento "cheia"
            "stickFirst" to pool.load(context, R.raw.stick1, 1),
            "stickAccent" to pool.load(context, R.raw.stick2, 1),
            "stickNormal" to pool.load(context, R.raw.stick3, 1),
            "snareFirst" to pool.load(context, R.raw.snare2, 1),
            "snareAccent" to pool.load(context, R.raw.snare3, 1),
            "snareNormal" to pool.load(context, R.raw.snare1, 1),
        )
    }

    val isRunning: Boolean
        get() = running

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "clopes-metronome").also {
            it.isDaemon = true
            it.start()
        }
    }

    fun stop() {
        running = false
        thread?.let { runCatching { it.join(400) } }
        thread = null
    }

    /** Toca a amostra do primeiro tempo uma vez (preview / tap tempo). */
    fun preview() {
        if (muted || loadedSamples < ids.size) return
        val id = ids[sampleKey(0)] ?: return
        if (id > 0) pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun release() {
        stop()
        runCatching { pool.release() }
    }

    private fun sampleKey(index: Int): String {
        val beat = beats.getOrNull(index) ?: beats.lastOrNull() ?: Beat(true, false)
        val prefix = if (sound == Sound.STICK) "stick" else "snare"
        return when {
            index == 0 -> "${prefix}First"
            beat.accent -> "${prefix}Accent"
            else -> "${prefix}Normal"
        }
    }

    private fun play(index: Int) {
        if (muted || loadedSamples < ids.size) return
        val beat = beats.getOrNull(index) ?: return
        if (beat.muted) return
        val id = ids[sampleKey(index)] ?: return
        if (id > 0) pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    private fun loop() {
        var beat = 0
        while (running) {
            play(beat)
            onBeat?.invoke(beat)
            beat = (beat + 1) % beats.size.coerceAtLeast(1)

            val interval = (60_000.0 / bpm.coerceIn(30, 300)).toLong()
            if (!sleepUntil(interval)) break
        }
    }

    private fun sleepUntil(milliseconds: Long): Boolean {
        val end = System.currentTimeMillis() + milliseconds
        while (running) {
            val remaining = end - System.currentTimeMillis()
            if (remaining <= 0) return true
            runCatching { Thread.sleep(minOf(remaining, 20)) }
        }
        return false
    }
}


