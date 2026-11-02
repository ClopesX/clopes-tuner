package com.clopes.tuner

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

data class NoteReading(
    val frequency: Float,
    val midi: Int,
    val name: String,
    val octave: Int,
    val cents: Int,
    val targetFrequency: Float,
)

data class Tuning(val name: String, val notes: List<String>)

object Notes {
    private val NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun frequency(midi: Int, a4: Float): Float = (a4 * 2.0.pow((midi - 69) / 12.0)).toFloat()

    fun reading(frequency: Float, a4: Float): NoteReading {
        val midiExact = 69 + 12 * log2(frequency / a4)
        val midi = midiExact.roundToInt()
        val target = frequency(midi, a4)
        val cents = (1200 * log2(frequency / target)).roundToInt()
        val name = NAMES[((midi % 12) + 12) % 12]
        return NoteReading(frequency, midi, name, midi / 12 - 1, cents, target)
    }

    fun nameFor(midi: Int): String = NAMES[((midi % 12) + 12) % 12]

    fun octaveFor(midi: Int): Int = midi / 12 - 1

    fun midiFromName(text: String): Int? {
        val match = Regex("^([A-G]#?)(-?\\d+)$").find(text.trim().uppercase()) ?: return null
        val index = NAMES.indexOf(match.groupValues[1])
        if (index < 0) return null
        val octave = match.groupValues[2].toInt()
        return (octave + 1) * 12 + index
    }

    /** Corde mais proxima da leitura, para destacar "qual corda estou afinando". */
    fun nearestString(reading: NoteReading, tuning: Tuning): String? {
        if (tuning.notes.isEmpty()) return null
        var best: String? = null
        var bestDistance = Int.MAX_VALUE
        for (note in tuning.notes) {
            val midi = midiFromName(note) ?: continue
            val distance = abs(midi * 100 - reading.midi * 100 - reading.cents)
            if (distance < bestDistance) {
                bestDistance = distance
                best = note
            }
        }
        return best
    }
}

object Tunings {
    val all = listOf(
        Tuning("Cromatico", emptyList()),
        Tuning("Violao E A D G B E", listOf("E2", "A2", "D3", "G3", "B3", "E4")),
        Tuning("Baixo E A D G", listOf("E1", "A1", "D2", "G2")),
        Tuning("Violino G D A E", listOf("G3", "D4", "A4", "E5")),
        Tuning("Ukulele G C E A", listOf("G4", "C4", "E4", "A4")),
        Tuning("Banjo G D G B D", listOf("G4", "D3", "G3", "B3", "D4")),
        Tuning("Bandolim G D A E", listOf("G3", "D4", "A4", "E5")),
    )
}

