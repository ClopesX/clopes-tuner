package com.clopes.tuner

import kotlin.math.sqrt

/**
 * Deteccao de pitch por YIN (diferenca + normalizacao cumulativa + interpolacao
 * parabolica). Robusta para cordas: tolera harmonicos fortes e ruido de fundo.
 */
object PitchDetector {
    private const val MIN_FREQ = 55.0
    private const val MAX_FREQ = 1300.0
    private const val RMS_GATE = 0.006
    private const val THRESHOLD = 0.15

    fun detect(samples: FloatArray, sampleRate: Int): Float? {
        val n = samples.size
        if (n < 512) return null

        var energy = 0.0
        for (s in samples) energy += s.toDouble() * s
        val rms = sqrt(energy / n)
        if (rms < RMS_GATE) return null

        val minTau = maxOf(2, (sampleRate / MAX_FREQ).toInt())
        val maxTau = minOf(n / 2 - 1, (sampleRate / MIN_FREQ).toInt())
        if (maxTau <= minTau + 2) return null

        val diff = DoubleArray(maxTau + 1)
        for (tau in 1..maxTau) {
            var sum = 0.0
            val limit = n - tau
            var i = 0
            while (i < limit) {
                val d = (samples[i] - samples[i + tau]).toDouble()
                sum += d * d
                i++
            }
            diff[tau] = sum
        }

        val cmnd = DoubleArray(maxTau + 1)
        var running = 0.0
        for (tau in 1..maxTau) {
            running += diff[tau]
            cmnd[tau] = if (running <= 0.0) 1.0 else diff[tau] * tau / running
        }

        var tauEstimate = -1
        var tau = minTau
        while (tau <= maxTau) {
            if (cmnd[tau] < THRESHOLD) {
                while (tau + 1 <= maxTau && cmnd[tau + 1] < cmnd[tau]) tau++
                tauEstimate = tau
                break
            }
            tau++
        }
        if (tauEstimate < 0) {
            var best = minTau
            for (t in minTau..maxTau) if (cmnd[t] < cmnd[best]) best = t
            if (cmnd[best] > 0.55) return null
            tauEstimate = best
        }

        val refined = refine(cmnd, tauEstimate, minTau, maxTau)
        if (refined <= 1.0) return null
        val frequency = sampleRate / refined
        return if (frequency < MIN_FREQ || frequency > MAX_FREQ) null else frequency.toFloat()
    }

    private fun refine(values: DoubleArray, tau: Int, min: Int, max: Int): Double {
        if (tau <= min || tau >= max) return tau.toDouble()
        val s0 = values[tau - 1]
        val s1 = values[tau]
        val s2 = values[tau + 1]
        val denom = 2.0 * (2.0 * s1 - s2 - s0)
        if (denom == 0.0) return tau.toDouble()
        return tau + (s2 - s0) / denom
    }

    fun median(values: List<Float>): Float? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }
}
