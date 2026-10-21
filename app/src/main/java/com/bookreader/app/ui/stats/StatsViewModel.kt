package com.bookreader.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bookreader.app.container
import com.bookreader.app.data.BookRepository
import com.bookreader.app.data.ReadingStats
import com.bookreader.app.data.StatsRepository
import com.bookreader.app.data.StatsSummary
import com.bookreader.app.data.summary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class BookTime(val title: String, val author: String, val seconds: Long)

data class StatsUiState(
    val today: LocalDate = LocalDate.now(),
    val summary: StatsSummary = ReadingStats().summary(LocalDate.now()),
    /** Reading time per book; deleted books are left out */
    val history: List<BookTime> = emptyList(),
)

class StatsViewModel(stats: StatsRepository, books: BookRepository) : ViewModel() {

    val state = combine(stats.stats, books.books) { stats, list ->
        val today = LocalDate.now()
        StatsUiState(
            today = today,
            summary = stats.summary(today),
            history = stats.books.filterValues { it > 0 }.mapNotNull { (id, seconds) ->
                list.firstOrNull { it.id == id }?.let { BookTime(it.title, it.author, seconds) }
            }.sortedByDescending { it.seconds },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = this[APPLICATION_KEY]!!.container
                StatsViewModel(container.stats, container.books)
            }
        }
    }
}
