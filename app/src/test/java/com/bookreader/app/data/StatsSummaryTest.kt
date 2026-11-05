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
    fun weekRunsMondayToSundayAndAveragesDaysSoFar() {
        // 2026-10-21 is a Wednesday
        val stats = stats("2026-10-18" to 999L, "2026-10-19" to 60L, "2026-10-21" to 30L, "2026-10-26" to 999L)
        val week = stats.period(StatsPeriod.Week, StatsPeriod.Week.startOf(today), today)
        assertEquals(LocalDate.of(2026, 10, 19), week.start)
        assertEquals(LocalDate.of(2026, 10, 25), week.end)
        assertEquals(7, week.bars.size)
        assertEquals(90L, week.totalSeconds)
        // Monday..Wednesday
        assertEquals(30L, week.averageSeconds)
    }

    @Test
    fun pastMonthAveragesAllItsDays() {
        val start = StatsPeriod.Month.shift(StatsPeriod.Month.startOf(today), -1)
        val month = stats("2026-09-01" to 300L, "2026-09-30" to 300L, "2026-10-01" to 999L).period(StatsPeriod.Month, start, today)
        assertEquals(30, month.bars.size)
        assertEquals(600L, month.totalSeconds)
        assertEquals(20L, month.averageSeconds)
    }

    @Test
    fun yearHasMonthBarsWithDaysRead() {
        val year = stats("2026-02-03" to 100L, "2026-02-04" to 200L, "2026-10-21" to 60L)
            .period(StatsPeriod.Year, StatsPeriod.Year.startOf(today), today)
        assertEquals(12, year.bars.size)
        assertEquals(300L, year.bars[1].seconds)
        assertEquals(2, year.bars[1].daysRead)
        // January..October
        assertEquals(36L, year.averageSeconds)
    }

    @Test
    fun calendarEndsThisWeekWithFutureDaysEmpty() {
        val columns = stats("2026-10-12" to 60L).calendar(today, weeks = 2)
        assertEquals(LocalDate.of(2026, 10, 12), columns[0][0]?.date)
        assertEquals(60L, columns[0][0]?.seconds)
        assertEquals(today, columns[1][2]?.date)
        assertEquals(null, columns[1][3])
    }

    @Test
    fun calendarLevelsByMinutes() {
        assertEquals(listOf(0, 1, 2, 3, 4), listOf(0L, 60L, 15 * 60L, 30 * 60L, 60 * 60L).map(::calendarLevel))
    }

    @Test
    fun firstDayIgnoresEmptyDays() {
        assertEquals(LocalDate.of(2026, 3, 1), stats("2026-01-01" to 0L, "2026-03-01" to 5L, "2026-04-01" to 5L).firstDay())
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
