package com.clopes.tuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class PitchDetectorTest {

    private fun tone(frequency: Double, sampleRate: Int = 44100, size: Int = 4096): FloatArray =
        FloatArray(size) { index -> (0.5 * sin(2 * PI * frequency * index / sampleRate)).toFloat() }

    @Test
    fun detectsConcertA() {
        val frequency = PitchDetector.detect(tone(440.0), 44100)
        assertNotNull(frequency)
        assertEquals(440.0, frequency!!.toDouble(), 2.0)
    }

    @Test
    fun detectsGuitarLowE() {
        val frequency = PitchDetector.detect(tone(82.41), 44100)
        assertNotNull(frequency)
        assertEquals(82.41, frequency!!.toDouble(), 1.0)
    }

    @Test
    fun detectsViolinE() {
        val frequency = PitchDetector.detect(tone(659.26), 44100)
        assertNotNull(frequency)
        assertEquals(659.26, frequency!!.toDouble(), 3.0)
    }

    @Test
    fun silenceHasNoPitch() {
        assertNull(PitchDetector.detect(FloatArray(4096), 44100))
    }

    @Test
    fun mapsFrequencyToNote() {
        val reading = Notes.reading(440f, 440f)
        assertEquals("A", reading.name)
        assertEquals(4, reading.octave)
        assertEquals(0, reading.cents)
    }

    @Test
    fun respectsReferencePitch() {
        val reading = Notes.reading(442f, 442f)
        assertEquals(0, reading.cents)
    }

    @Test
    fun findsNearestGuitarString() {
        val reading = Notes.reading(110f, 440f) // A2
        val string = Notes.nearestString(reading, Tunings.all[1])
        assertEquals("A2", string)
    }
}
