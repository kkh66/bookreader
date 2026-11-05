package com.bookreader.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bookreader.app.container
import com.bookreader.app.data.Book
import com.bookreader.app.data.BookRepository
import com.bookreader.app.data.PeriodStats
import com.bookreader.app.data.ReadingStats
import com.bookreader.app.data.StatsPeriod
import com.bookreader.app.data.StatsRepository
import com.bookreader.app.data.StatsSummary
import com.bookreader.app.data.firstDay
import com.bookreader.app.data.period
import com.bookreader.app.data.summary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** Reading history item: book + cover + total seconds read */
data class BookTime(val book: Book, val coverPath: String?, val seconds: Long)

data class StatsUiState(
    val today: LocalDate = LocalDate.now(),
    val stats: ReadingStats = ReadingStats(),
    val summary: StatsSummary = ReadingStats().summary(LocalDate.now()),
    val chart: PeriodStats = ReadingStats().period(StatsPeriod.Week, StatsPeriod.Week.startOf(LocalDate.now()), LocalDate.now()),
    /** The chart can page back to an earlier period with reading / forward until the current one */
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    /** Reading time per book, most read first; deleted books are left out */
    val history: List<BookTime> = emptyList(),
)

class StatsViewModel(stats: StatsRepository, private val books: BookRepository) : ViewModel() {

    private val period = MutableStateFlow(StatsPeriod.Week)

    /** Periods back from the current one: 0 = this week / month / year */
    private val offset = MutableStateFlow(0L)

    val state = combine(stats.stats, books.books, period, offset) { stats, list, period, offset ->
        val today = LocalDate.now()
        val start = period.shift(period.startOf(today), -offset)
        val first = stats.firstDay()
        StatsUiState(
            today = today,
            stats = stats,
            summary = stats.summary(today),
            chart = stats.period(period, start, today),
            canGoBack = first != null && start > period.startOf(first),
            canGoForward = offset > 0,
            history = stats.books.filterValues { it > 0 }.mapNotNull { (id, seconds) ->
                list.firstOrNull { it.id == id }?.let { BookTime(it, books.cover(it)?.path, seconds) }
            }.sortedByDescending { it.seconds },
        )
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    /** Switching week / month / year returns to the current period */
    fun setPeriod(value: StatsPeriod) {
        period.value = value
        offset.value = 0
    }

    /** -1 = an earlier period, 1 = a later one */
    fun shift(by: Int) {
        offset.value = (offset.value - by).coerceAtLeast(0)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = this[APPLICATION_KEY]!!.container
                StatsViewModel(container.stats, container.books)
            }
        }
    }
}
