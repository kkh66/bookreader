package com.bookreader.app.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bookreader.app.container
import com.bookreader.app.data.Bookmark
import com.bookreader.app.data.BookRepository
import com.bookreader.app.data.Highlight
import com.bookreader.app.data.HighlightKind
import com.bookreader.app.data.StatsRepository
import com.bookreader.app.data.bookmarkId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Locator
import java.util.UUID

class ReaderViewModel(
    private val books: BookRepository,
    private val stats: StatsRepository,
) : ViewModel() {

    val bookList = books.books

    fun toggleBookmark(bookId: String, locator: Locator, onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val bookmark = Bookmark(
                    id = bookmarkId(locator.href.toString(), locator.locations.position, locator.locations.progression),
                    locator = locator.toJSON().toString(),
                    title = locator.title?.takeIf { it.isNotBlank() } ?: books.find(bookId)?.title.orEmpty(),
                    progress = locator.locations.progression ?: 0.0,
                )
                val added = books.toggleBookmark(bookId, bookmark)
                onResult(if (added) "已添加书签" else "已移除书签")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onResult("保存书签失败：${e.message.orEmpty()}")
            }
        }
    }

    fun removeBookmark(bookId: String, bookmarkId: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                books.removeBookmark(bookId, bookmarkId)
                onResult("已删除书签")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onResult("删除书签失败：${e.message.orEmpty()}")
            }
        }
    }

    /** Creates a highlight for the selected [locator]; returned right away so the menu can keep editing it */
    fun createHighlight(bookId: String, locator: Locator, kind: HighlightKind, color: Int): Highlight {
        val highlight = Highlight(
            id = UUID.randomUUID().toString(),
            locator = locator.toJSON().toString(),
            // Paragraph breaks in the selection become a space so the notes list stays compact
            text = locator.text.highlight.orEmpty().trim().replace(Regex("\\s+"), " "),
            kind = kind,
            color = color,
            chapter = locator.title,
            progress = locator.locations.totalProgression ?: 0.0,
            createdAt = System.currentTimeMillis(),
        )
        saveHighlight(bookId, highlight)
        return highlight
    }

    fun saveHighlight(bookId: String, highlight: Highlight) {
        viewModelScope.launch { runCatching { books.saveHighlight(bookId, highlight) } }
    }

    fun removeHighlight(bookId: String, highlightId: String) {
        viewModelScope.launch { books.removeHighlight(bookId, highlightId) }
    }

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
