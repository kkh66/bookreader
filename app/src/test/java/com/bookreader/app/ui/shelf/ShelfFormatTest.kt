package com.bookreader.app.ui.shelf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ShelfFormatTest {

    private val zone = ZoneId.of("Asia/Kuala_Lumpur")

    @Test
    fun unreadBookShowsZero() {
        assertEquals("0小时, 0字/分钟", ShelfFormat.readingLine(0, 0.0, 120_000))
    }

    @Test
    fun hoursAndSpeed() {
        // 0.97 h = 3492 s = 58.2 min; 83% of 100000 characters = 83000 characters → about 1426 characters/min
        assertEquals("0.97小时, 1426字/分钟", ShelfFormat.readingLine(3492, 0.83, 100_000))
    }

    @Test
    fun trailingZerosTrimmed() {
        assertEquals("1.5小时, 0字/分钟", ShelfFormat.readingLine(5400, 0.0, 100_000))
    }

    @Test
    fun noSpeedWithoutCharCountOrUnderOneMinute() {
        assertEquals("0.01小时, 0字/分钟", ShelfFormat.readingLine(30, 0.5, 100_000))
        assertEquals("1小时, 0字/分钟", ShelfFormat.readingLine(3600, 0.5, null))
    }

    @Test
    fun dateRange() {
        val first = ZonedDateTime.of(2026, 10, 14, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        val last = ZonedDateTime.of(2026, 10, 21, 23, 30, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("2026年10月14日 - 2026年10月21日", ShelfFormat.dateRange(first, last, zone))
        assertEquals("2026年10月21日 - 2026年10月21日", ShelfFormat.dateRange(0, last, zone))
        assertNull(ShelfFormat.dateRange(0, 0, zone))
    }

    @Test
    fun percent() {
        assertEquals("83%", ShelfFormat.percent(0.8349))
        assertEquals("100%", ShelfFormat.percent(1.0))
    }

    @Test
    fun readingHoursAlone() {
        assertEquals("0.97小时", ShelfFormat.readingHours(3492))
        assertEquals("2小时", ShelfFormat.readingHours(7200))
    }
}
