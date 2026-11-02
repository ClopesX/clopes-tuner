package com.clopes.tuner

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.sqrt

/**
 * Captura do microfone em blocos de 4096 amostras e entrega o pitch ja filtrado
 * (mediana das ultimas leituras), evitando a agulha tremer.
 */
class TunerEngine(private val onReading: (frequency: Float?, level: Float, bands: FloatArray) -> Unit) {

    private val sampleRate = 44100
    private val blockSize = 4096
    private val history = ArrayDeque<Float>()

    @Volatile
    private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        history.clear()
        thread = Thread({ loop() }, "clopes-tuner-audio").also {
            it.isDaemon = true
            it.start()
        }
    }

    fun stop() {
        running = false
        thread?.let { runCatching { it.join(400) } }
        thread = null
        history.clear()
    }

    @SuppressLint("MissingPermission")
    private fun loop() {
        val minimum = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minimum <= 0) {
            onReading(null, 0f, FloatArray(12))
            return
        }

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minimum, blockSize * 2),
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            onReading(null, 0f, FloatArray(12))
            record.release()
            return
        }

        val buffer = ShortArray(blockSize)
        record.startRecording()
        try {
            while (running) {
                val read = record.read(buffer, 0, buffer.size)
                if (read <= 0) continue

                var energy = 0.0
                val samples = FloatArray(read)
                var i = 0
                while (i < read) {
                    val value = buffer[i] / 32768f
                    samples[i] = value
                    energy += value.toDouble() * value
                    i++
                }
                val level = sqrt(energy / read).toFloat()

                val frequency = PitchDetector.detect(samples, sampleRate)
                val bands = Spectrum.bands(samples, sampleRate)
                if (frequency != null && frequency in 40f..1500f) {
                    history.addLast(frequency)
                    while (history.size > 7) history.removeFirst()
                    onReading(PitchDetector.median(history.toList()), level, bands)
                } else {
                    if (level < 0.01f) history.clear()
                    // sempre reporta o nivel: o medidor de dB depende disso
                    onReading(null, level, bands)
                }
            }
        } finally {
            runCatching { record.stop() }
            runCatching { record.release() }
        }
    }
}

