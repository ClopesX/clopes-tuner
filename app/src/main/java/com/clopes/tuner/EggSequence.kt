package com.clopes.tuner

import kotlin.math.abs

/**
 * Regra do easter egg: D · D · B · D · D · D · C# (qualquer oitava).
 *
 * A comparacao e por classe de altura com tolerancia de 80 cents, entao notas
 * amarelas e vermelhas (desafinadas) tambem contam. A oitava e ignorada.
 */
object EggSequence {

    /** C=0, C#=1, D=2 ... B=11 */
    val targets = listOf(2, 2, 11, 2, 2, 2, 1)

    /**
     * Desvio maximo aceito, em semitons (0,9 = 90 cents). Acima de 50 cents a
     * nota ja seria arredondada para a vizinha; aceitamos esse caso tambem para
     * nao punir quem esta afinando de ouvido.
     */
    var tolerance = 0.9

    /** Retorno de [advance] quando a sequencia termina. */
    const val COMPLETE = -1

    fun pitchClassDistance(midi: Double, target: Int): Double {
        val pitchClass = ((midi % 12) + 12) % 12
        val raw = abs(pitchClass - target)
        return if (raw > 6.0) 12.0 - raw else raw
    }

    /**
     * Avanca o progresso com uma nota tocada. Devolve o novo progresso, ou
     * [COMPLETE] quando as sete notas foram reconhecidas na ordem.
     */
    fun advance(progress: Int, midi: Double): Int {
        val expected = targets[progress.coerceIn(0, targets.size - 1)]
        if (pitchClassDistance(midi, expected) <= tolerance) {
            val next = progress + 1
            return if (next == targets.size) COMPLETE else next
        }
        // Notas fora da sequencia sao ignoradas: o progresso se mantem para
        // nao punir quem erra uma corda no meio do caminho.
        return progress
    }
}



