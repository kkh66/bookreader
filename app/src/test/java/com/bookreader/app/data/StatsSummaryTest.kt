package com.bookreader.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StatsSummaryTest {

    private val today = LocalDate.of(2026, 10, 21)

    private fun stats(vararg days: Pair<String, Long>, books: Map<String, Long> = emptyMap()) =
        ReadingStats(days = days.toMap(), books = books)

    @Test
    fun todayTotalAndBookCount() {
        val summary = stats(
            "2026-10-21" to 1260L,
            "2026-10-10" to 600L,
            books = mapOf("a" to 1500L, "b" to 360L, "c" to 0L),
        ).summary(today)
        assertEquals(1260L, summary.todaySeconds)
        assertEquals(1860L, summary.totalSeconds)
        assertEquals(2, summary.bookCount)
    }

    @Test
    fun last7DaysEndWithTodayAndSkipOlderDays() {
        val summary = stats("2026-10-15" to 60L, "2026-10-14" to 999L, "2026-10-21" to 30L).summary(today)
        assertEquals(LocalDate.of(2026, 10, 15), summary.last7.first().date)
        assertEquals(today, summary.last7.last().date)
        assertEquals(7, summary.last7.size)
        assertEquals(90L, summary.last7Seconds)
    }

    @Test
    fun streakCountsBackFromToday() {
        val summary = stats("2026-10-21" to 10L, "2026-10-20" to 10L, "2026-10-19" to 10L, "2026-10-17" to 10L)
            .summary(today)
        assertEquals(3, summary.streakDays)
    }

    @Test
    fun streakNotBrokenWhenTodayNotReadYet() {
        assertEquals(2, stats("2026-10-20" to 10L, "2026-10-19" to 10L).summary(today).streakDays)
    }

    @Test
    fun noReadingMeansZeroStreak() {
        assertEquals(0, ReadingStats().summary(today).streakDays)
    }
}
