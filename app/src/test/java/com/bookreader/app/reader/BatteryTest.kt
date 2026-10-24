package com.bookreader.app.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryTest {

    @Test
    fun barsFollowTheLevel() {
        assertEquals(0, batteryBars(0))
        assertEquals(0, batteryBars(9))
        assertEquals(1, batteryBars(10))
        assertEquals(3, batteryBars(50))
        assertEquals(6, batteryBars(80))
        assertEquals(6, batteryBars(94))
        assertEquals(7, batteryBars(95))
        assertEquals(7, batteryBars(100))
    }

    @Test
    fun barsNeverGoDownAsTheLevelGoesUp() {
        (1..100).forEach { assertTrue("level $it", batteryBars(it) >= batteryBars(it - 1)) }
    }

    @Test
    fun colorBandsSplitAt20And80() {
        assertEquals(BATTERY_LOW, batteryColor(0))
        assertEquals(BATTERY_LOW, batteryColor(19))
        assertEquals(BATTERY_MEDIUM, batteryColor(20))
        assertEquals(BATTERY_MEDIUM, batteryColor(79))
        assertEquals(BATTERY_HIGH, batteryColor(80))
        assertEquals(BATTERY_HIGH, batteryColor(100))
    }
}
