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
    val last7: List<DayStat>,
    val last7Seconds: Long,
    val streakDays: Int,
)

/** Pure computation, easy to unit test */
fun ReadingStats.summary(today: LocalDate): StatsSummary {
    fun secondsOn(date: LocalDate) = days[date.toString()] ?: 0L
    val last7 = (6 downTo 0).map { today.minusDays(it.toLong()) }.map { DayStat(it, secondsOn(it)) }
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
        last7 = last7,
        last7Seconds = last7.sumOf { it.seconds },
        streakDays = streak,
    )
}

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
