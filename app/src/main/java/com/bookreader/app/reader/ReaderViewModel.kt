package com.bookreader.app.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bookreader.app.container
import com.bookreader.app.data.BookRepository
import com.bookreader.app.data.StatsRepository
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Locator

class ReaderViewModel(
    private val books: BookRepository,
    private val stats: StatsRepository,
) : ViewModel() {

    fun saveProgress(bookId: String, locator: Locator) {
        viewModelScope.launch {
            books.updateProgress(
                id = bookId,
                locator = locator.toJSON().toString(),
                progress = locator.locations.totalProgression ?: 0.0,
                chapter = locator.title,
            )
        }
    }

    /** Records a reading session, capped at 2 hours so a screen left on doesn't inflate the stats */
    fun addReadingTime(bookId: String, seconds: Long) {
        viewModelScope.launch { stats.add(bookId, seconds.coerceAtMost(MAX_SESSION_SECONDS)) }
    }

    companion object {
        private const val MAX_SESSION_SECONDS = 2 * 60 * 60L

        val Factory = viewModelFactory {
            initializer {
                val container = this[APPLICATION_KEY]!!.container
                ReaderViewModel(container.books, container.stats)
            }
        }
    }
}
