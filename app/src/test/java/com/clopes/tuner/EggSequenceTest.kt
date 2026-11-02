package com.clopes.tuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EggSequenceTest {

    /** Frequencia -> midi exato (A4 = 440). */
    private fun midiOf(frequency: Double): Double = 69.0 + 12.0 * kotlin.math.log2(frequency / 440.0)

    private fun frequency(note: String, cents: Double = 0.0): Double {
        val base = when (note) {
            "C" -> 261.63
            "C#" -> 277.18
            "D" -> 293.66
            "D#" -> 311.13
            "E" -> 329.63
            "B" -> 246.94
            else -> error("nota nao mapeada: $note")
        }
        return base * Math.pow(2.0, cents / 1200.0)
    }

    private fun play(notes: List<String>, cents: Double = 0.0): Int {
        var progress = 0
        for (note in notes) {
            val result = EggSequence.advance(progress, midiOf(frequency(note, cents)))
            if (result == EggSequence.COMPLETE) return EggSequence.COMPLETE
            progress = result
        }
        return progress
    }

    @Test
    fun completesExactSequence() {
        val result = play(listOf("D", "D", "B", "D", "D", "D", "C#"))
        assertEquals(EggSequence.COMPLETE, result)
    }

    @Test
    fun acceptsSlightlyFlatNotes() {
        val result = play(listOf("D", "D", "B", "D", "D", "D", "C#"), cents = -45.0)
        assertEquals(EggSequence.COMPLETE, result)
    }

    @Test
    fun acceptsSlightlySharpNotes() {
        val result = play(listOf("D", "D", "B", "D", "D", "D", "C#"), cents = 70.0)
        assertEquals(EggSequence.COMPLETE, result)
    }

    @Test
    fun acceptsNeighborNoteBeyondHalfStep() {
        // 85 cents: o nome arredondado ja seria o vizinho, mas ainda vale.
        val result = play(listOf("D", "D", "B", "D", "D", "D", "C#"), cents = 85.0)
        assertEquals(EggSequence.COMPLETE, result)
    }

    @Test
    fun rejectsNotesFarOutOfTune() {
        val result = play(listOf("D", "D", "B", "D", "D", "D", "C#"), cents = 140.0)
        assertTrue("sem tolerancia de 140 cents nao deve completar", result != EggSequence.COMPLETE)
    }

    @Test
    fun ignoresNoteOutsideSequence() {
        var progress = 0
        progress = EggSequence.advance(progress, midiOf(frequency("D")))
        progress = EggSequence.advance(progress, midiOf(frequency("E")))
        assertEquals(1, progress)
    }

    @Test
    fun ignoresWrongNoteBetweenExpectedOnes() {
        var progress = 0
        progress = EggSequence.advance(progress, midiOf(frequency("D")))
        progress = EggSequence.advance(progress, midiOf(frequency("B")))
        progress = EggSequence.advance(progress, midiOf(frequency("D")))
        assertEquals(2, progress)
    }

    @Test
    fun ignoreOctave() {
        // B em outra oitava continua valendo: classe de altura e o que importa.
        var progress = 0
        progress = EggSequence.advance(progress, midiOf(frequency("D")))
        progress = EggSequence.advance(progress, midiOf(frequency("D") * 2))
        assertEquals(2, progress)
    }
}



