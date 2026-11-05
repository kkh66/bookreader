package com.bookreader.app.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderBrightnessTest {

    @Test
    fun backlightRunsFromLowestToFull() {
        assertEquals(1f, backlightFor(1f), 0.0001f)
        assertEquals(0.01f, backlightFor(DIM_RANGE), 0.0001f)
        assertEquals(0.01f, backlightFor(0f), 0.0001f)
    }

    @Test
    fun veilOnlyBelowTheDimRange() {
        assertEquals(0f, dimFor(1f), 0.0001f)
        assertEquals(0f, dimFor(DIM_RANGE), 0.0001f)
        assertTrue(dimFor(DIM_RANGE / 2) > 0f)
        assertEquals(0.6f, dimFor(0f), 0.0001f)
    }

    @Test
    fun brightnessNeverGoesUpAsTheSliderGoesDown() {
        (1..100).map { it / 100f }.forEach { level ->
            val lower = level - 0.01f
            assertTrue("level $level", backlightFor(lower) <= backlightFor(level))
            assertTrue("level $level", dimFor(lower) >= dimFor(level))
        }
    }
}
