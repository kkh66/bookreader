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
import com.bookreader.app.data.ReadingStyle
import com.bookreader.app.data.SettingsRepository
import com.bookreader.app.data.StatsRepository
import com.bookreader.app.data.bookmarkId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.util.getOrElse
import java.util.UUID

class ReaderViewModel(
    private val bookId: String,
    private val books: BookRepository,
    private val stats: StatsRepository,
    private val reader: ReaderRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val session = reader[bookId]
    val book = books.books.map { list -> list.firstOrNull { it.id == bookId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, books.find(bookId))
    val themeMode = settings.themeMode
    val readingStyle = settings.readingStyle
    val lastHighlight = settings.lastHighlight
    val volumeKeyPaging = settings.volumeKeyPaging.state
    val hideStatusBar = settings.readerHideStatusBar.state
    val openLastOnLaunch = settings.openLastOnLaunch.state

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** Search state, shared by the bottom menu and the selection menu's in-book search */
    private val _searchState = MutableStateFlow(ReaderSearchState())
    internal val searchState = _searchState.asStateFlow()
    private var searchJob: Job? = null

    fun setReadingStyle(style: ReadingStyle) = settings.setReadingStyle(style)

    fun setVolumeKeyPaging(on: Boolean) = settings.volumeKeyPaging.set(on)

    fun setHideStatusBar(on: Boolean) = settings.readerHideStatusBar.set(on)

    fun setOpenLastOnLaunch(on: Boolean) = settings.openLastOnLaunch.set(on)

    fun setLastHighlight(kind: HighlightKind, color: Int) = settings.setLastHighlight(kind, color)

    fun setSearchQuery(query: String) {
        _searchState.update { it.copy(query = query) }
    }

    /** Searches resource by resource, showing hits as they come in; a new search cancels the previous one */
    @OptIn(ExperimentalReadiumApi::class)
    fun search() {
        val publication = session?.publication ?: return
        val text = _searchState.value.query.trim().ifEmpty { return }
        searchJob?.cancel()
        _searchState.update { ReaderSearchState(query = it.query, searched = text, running = true) }
        searchJob = viewModelScope.launch {
            try {
                val iterator = publication.search(text)
                if (iterator == null) {
                    _searchState.update { it.copy(error = "这本书不支持搜索") }
                    return@launch
                }
                try {
                    while (isActive) {
                        val page = iterator.next().getOrElse { error ->
                            if (isActive) _searchState.update { it.copy(error = "搜索失败：${error.message}") }
                            null
                        } ?: break
                        if (!isActive) break
                        _searchState.update {
                            val results = it.results + page.locators
                            it.copy(results = results.take(MAX_RESULTS), truncated = results.size >= MAX_RESULTS)
                        }
                        if (_searchState.value.truncated) break
                    }
                } finally {
                    iterator.close()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isActive) _searchState.update { it.copy(error = "搜索失败：${e.message.orEmpty()}") }
            } finally {
                if (isActive) _searchState.update { it.copy(running = false) }
            }
        }
    }

    fun close() {
        searchJob?.cancel()
        reader.close(bookId)
    }

    fun toggleBookmark(locator: Locator) {
        viewModelScope.launch {
            try {
                val bookmark = Bookmark(
                    id = bookmarkId(locator.href.toString(), locator.locations.position, locator.locations.progression),
                    locator = locator.toJSON().toString(),
                    title = locator.title?.takeIf { it.isNotBlank() } ?: books.find(bookId)?.title.orEmpty(),
                    progress = locator.locations.progression ?: 0.0,
                )
                val added = books.toggleBookmark(bookId, bookmark)
                _messages.send(if (added) "已添加书签" else "已移除书签")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.send("保存书签失败：${e.message.orEmpty()}")
            }
        }
    }

    fun removeBookmark(bookmarkId: String) {
        viewModelScope.launch {
            try {
                books.removeBookmark(bookId, bookmarkId)
                _messages.send("已删除书签")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.send("删除书签失败：${e.message.orEmpty()}")
            }
        }
    }

    /** Creates a highlight for the selected [locator]; returned right away so the menu can keep editing it */
    fun createHighlight(locator: Locator, kind: HighlightKind, color: Int): Highlight {
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
        saveHighlight(highlight)
        return highlight
    }

    fun saveHighlight(highlight: Highlight) {
        viewModelScope.launch { runCatching { books.saveHighlight(bookId, highlight) } }
    }

    fun removeHighlight(highlightId: String) {
        viewModelScope.launch { books.removeHighlight(bookId, highlightId) }
    }

    fun saveProgress(locator: Locator) {
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
    fun addReadingTime(seconds: Long) {
        viewModelScope.launch { stats.add(bookId, seconds.coerceAtMost(MAX_SESSION_SECONDS)) }
    }

    companion object {
        private const val MAX_SESSION_SECONDS = 2 * 60 * 60L

        fun factory(bookId: String) = viewModelFactory {
            initializer {
                val container = this[APPLICATION_KEY]!!.container
                ReaderViewModel(bookId, container.books, container.stats, container.reader, container.settings)
            }
        }
    }
}
