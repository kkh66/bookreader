package com.bookreader.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bookreader.app.container
import com.bookreader.app.data.Book
import com.bookreader.app.data.BookRepository
import com.bookreader.app.data.ReadingStats
import com.bookreader.app.data.StatsRepository
import com.bookreader.app.data.StatsSummary
import com.bookreader.app.data.summary
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
    val summary: StatsSummary = ReadingStats().summary(LocalDate.now()),
    /** Reading time per book; deleted books are left out */
    val history: List<BookTime> = emptyList(),
)

class StatsViewModel(stats: StatsRepository, private val books: BookRepository) : ViewModel() {

    val state = combine(stats.stats, books.books) { stats, list ->
        val today = LocalDate.now()
        StatsUiState(
            today = today,
            summary = stats.summary(today),
            history = stats.books.filterValues { it > 0 }.mapNotNull { (id, seconds) ->
                list.firstOrNull { it.id == id }?.let { BookTime(it, books.cover(it)?.path, seconds) }
            }.sortedByDescending { it.seconds },
        )
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = this[APPLICATION_KEY]!!.container
                StatsViewModel(container.stats, container.books)
            }
        }
    }
}
