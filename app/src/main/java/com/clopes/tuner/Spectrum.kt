package com.clopes.tuner

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Analisador de espectro simples para o medidor de nivel: janela de Hann +
 * FFT radix-2 e agrupamento em bandas logaritmicas (60 Hz a 8 kHz).
 */
object Spectrum {

    private const val SIZE = 1024
    private const val MIN_FREQ = 60.0
    private const val MAX_FREQ = 8000.0

    fun bands(samples: FloatArray, sampleRate: Int, count: Int = 12): FloatArray {
        val n = SIZE
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        val offset = maxOf(0, samples.size - n)

        for (index in 0 until n) {
            val sample = if (offset + index < samples.size) samples[offset + index] else 0f
            val window = 0.5 - 0.5 * cos(2.0 * PI * index / (n - 1))
            re[index] = sample * window
        }
        fft(re, im)

        val output = FloatArray(count)
        val ratio = MAX_FREQ / MIN_FREQ
        for (band in 0 until count) {
            val low = MIN_FREQ * Math.pow(ratio, band.toDouble() / count)
            val high = MIN_FREQ * Math.pow(ratio, (band + 1).toDouble() / count)
            var peak = 0.0
            for (bin in 0 until n / 2) {
                val frequency = bin * sampleRate.toDouble() / n
                if (frequency >= low && frequency < high) {
                    val magnitude = sqrt(re[bin] * re[bin] + im[bin] * im[bin])
                    if (magnitude > peak) peak = magnitude
                }
            }
            val db = 20 * log10(peak / n + 1e-9)
            output[band] = ((db + 70.0) / 55.0).coerceIn(0.0, 1.0).toFloat()
        }
        return output
    }

    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }

        var length = 2
        while (length <= n) {
            val angle = -2.0 * PI / length
            val wRe = cos(angle)
            val wIm = kotlin.math.sin(angle)
            var start = 0
            while (start < n) {
                var curRe = 1.0
                var curIm = 0.0
                for (k in 0 until length / 2) {
                    val evenIndex = start + k
                    val oddIndex = start + k + length / 2
                    val oddRe = re[oddIndex] * curRe - im[oddIndex] * curIm
                    val oddIm = re[oddIndex] * curIm + im[oddIndex] * curRe
                    re[oddIndex] = re[evenIndex] - oddRe
                    im[oddIndex] = im[evenIndex] - oddIm
                    re[evenIndex] += oddRe
                    im[evenIndex] += oddIm
                    val nextRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                }
                start += length
            }
            length = length shl 1
        }
    }
}


