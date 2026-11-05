package com.bookreader.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

/** Reading time (seconds), accumulated per day and per book */
@Serializable
data class ReadingStats(
    val days: Map<String, Long> = emptyMap(),
    val books: Map<String, Long> = emptyMap(),
)

data class DayStat(val date: LocalDate, val seconds: Long)

data class StatsSummary(
    val todaySeconds: Long,
    val totalSeconds: Long,
    val bookCount: Int,
    val streakDays: Int,
)

private fun ReadingStats.secondsOn(date: LocalDate) = days[date.toString()] ?: 0L

/** Pure computation, easy to unit test */
fun ReadingStats.summary(today: LocalDate): StatsSummary {
    // Not having read yet today doesn't break the streak; count back from yesterday
    var day = if (secondsOn(today) > 0) today else today.minusDays(1)
    var streak = 0
    while (secondsOn(day) > 0) {
        streak++
        day = day.minusDays(1)
    }
    return StatsSummary(
        todaySeconds = secondsOn(today),
        totalSeconds = days.values.sum(),
        bookCount = books.count { it.value > 0 },
        streakDays = streak,
    )
}

/** Span of the stats chart: weeks start on Monday */
enum class StatsPeriod {
    Week, Month, Year;

    /** First day of the period containing [date] */
    fun startOf(date: LocalDate): LocalDate = when (this) {
        Week -> date.with(DayOfWeek.MONDAY)
        Month -> date.withDayOfMonth(1)
        Year -> date.withDayOfYear(1)
    }

    /** First day of the period [count] periods after the one starting at [start] (negative goes back) */
    fun shift(start: LocalDate, count: Long): LocalDate = when (this) {
        Week -> start.plusWeeks(count)
        Month -> start.plusMonths(count)
        Year -> start.plusYears(count)
    }
}

/** A chart bar: one day in the week / month views, one month in the year view; [daysRead] counts days with any reading */
data class StatsBar(val start: LocalDate, val seconds: Long, val daysRead: Int)

data class PeriodStats(
    val period: StatsPeriod,
    val start: LocalDate,
    /** Last day, inclusive */
    val end: LocalDate,
    val bars: List<StatsBar>,
    val totalSeconds: Long,
    /** Per bar (day, or month in the year view), over the part of the period up to today */
    val averageSeconds: Long,
)

/** Reading time of the [period] starting at [start], bar by bar */
fun ReadingStats.period(period: StatsPeriod, start: LocalDate, today: LocalDate): PeriodStats {
    val end = period.shift(start, 1).minusDays(1)
    fun daysOf(from: LocalDate, to: LocalDate) = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }
    val bars = when (period) {
        StatsPeriod.Year -> (0L until 12L).map { month ->
            val first = start.plusMonths(month)
            val seconds = daysOf(first, first.plusMonths(1).minusDays(1)).map { secondsOn(it) }.toList()
            StatsBar(first, seconds.sum(), seconds.count { it > 0 })
        }
        else -> daysOf(start, end).map { StatsBar(it, secondsOn(it), if (secondsOn(it) > 0) 1 else 0) }.toList()
    }
    val total = bars.sumOf { it.seconds }
    // Bars up to and including today's; a past period counts all of them
    val elapsed = bars.count { !it.start.isAfter(today) }
    return PeriodStats(period, start, end, bars, total, if (elapsed > 0) total / elapsed else 0L)
}

/** Reading calendar: [weeks] columns of Monday..Sunday ending with this week; days after today are null */
fun ReadingStats.calendar(today: LocalDate, weeks: Int): List<List<DayStat?>> {
    val first = StatsPeriod.Week.startOf(today).minusWeeks(weeks - 1L)
    return (0 until weeks).map { week ->
        (0 until 7).map { day ->
            first.plusDays(week * 7L + day).takeUnless { it.isAfter(today) }?.let { DayStat(it, secondsOn(it)) }
        }
    }
}

/** Shade of a calendar day: 0 = no reading, then under 15 / 30 / 60 minutes, 4 = an hour or more */
fun calendarLevel(seconds: Long): Int = when {
    seconds <= 0 -> 0
    seconds < 15 * 60 -> 1
    seconds < 30 * 60 -> 2
    seconds < 60 * 60 -> 3
    else -> 4
}

/** Earliest day with reading recorded, null if none; the chart doesn't page back past it */
fun ReadingStats.firstDay(): LocalDate? = days.filterValues { it > 0 }.keys.minOrNull()?.let(LocalDate::parse)

/**
 * Reading time statistics, stored in stats.json
 */
class StatsRepository(private val file: File) {

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private val _stats = MutableStateFlow(load())
    val stats: StateFlow<ReadingStats> = _stats.asStateFlow()

    suspend fun add(bookId: String, seconds: Long, day: LocalDate = LocalDate.now()) {
        if (seconds <= 0) return
        mutex.withLock {
            val current = _stats.value
            val key = day.toString()
            val next = current.copy(
                days = current.days + (key to (current.days[key] ?: 0L) + seconds),
                books = current.books + (bookId to (current.books[bookId] ?: 0L) + seconds),
            )
            withContext(Dispatchers.IO) { file.writeAtomically(json.encodeToString(next)) }
            _stats.value = next
        }
    }

    private fun load(): ReadingStats =
        file.readAtomically { json.decodeFromString<ReadingStats>(it) } ?: ReadingStats()
}
