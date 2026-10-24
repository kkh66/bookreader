package com.bookreader.app.reader

import android.content.Context
import android.graphics.RectF
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bookreader.app.R
import com.bookreader.app.container
import com.bookreader.app.data.BookFormat
import com.bookreader.app.data.BookRepository
import com.bookreader.app.data.Bookmark
import com.bookreader.app.data.Highlight
import com.bookreader.app.data.HighlightKind
import com.bookreader.app.data.PageAnimation
import com.bookreader.app.data.ReadingStyle
import com.bookreader.app.data.SettingsRepository
import com.bookreader.app.data.StatsRepository
import com.bookreader.app.data.UserFont
import com.bookreader.app.data.bookmarkId
import com.bookreader.app.data.decodeScaledBitmap
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.getOrElse

/** A panel opened over the page */
internal enum class ReaderPanel { Contents, Settings, Search }

/** The menu over the page: what it acts on, the latest copy of a tapped highlight (null for a fresh selection) and the marked text */
internal data class MarkMenuState(val target: MarkTarget, val current: Highlight?, val text: String)

/**
 * State and actions of the reader screen. The activity only drives Readium's navigator: it reports where the reader is
 * and what is selected, and carries out page jumps; menus, notes, panels and the book's contents live here
 */
class ReaderViewModel(
    private val bookId: String,
    private val books: BookRepository,
    private val stats: StatsRepository,
    private val reader: ReaderRepository,
    private val settings: SettingsRepository,
    private val context: Context,
) : ViewModel() {
    private val strings get() = ContextCompat.getContextForLanguage(context).resources


    val session = reader[bookId]
    val format = session?.format ?: BookFormat.EPUB
    val book = books.books.map { list -> list.firstOrNull { it.id == bookId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, books.find(bookId))
    val themeMode = settings.themeMode
    val readingStyle = settings.readingStyle
    val lastHighlight = settings.lastHighlight
    val volumeKeyPaging = settings.volumeKeyPaging.state
    val hideStatusBar = settings.readerHideStatusBar.state
    val pageAnimation = settings.pageAnimation

    /** Imported fonts registered when the book opened */
    val fonts: List<UserFont> = session?.fonts?.map { it.font }.orEmpty()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** Where the reader is, as the navigator last reported it */
    private val _locator = MutableStateFlow(session?.initialLocator)
    val locator: StateFlow<Locator?> = _locator.asStateFlow()

    /** Pages of the whole book (Readium positions), 0 until counted */
    val totalPages: StateFlow<Int> = flow { emit(session?.publication?.positions()?.size ?: 0) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Whether the current page is bookmarked */
    val bookmarked: StateFlow<Boolean> = combine(book, _locator) { book, locator ->
        locator != null && book?.bookmarks?.any { it.id == locator.bookmarkKey() } == true
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    internal val bookmarks: StateFlow<List<BookmarkItem>> = book
        .map { book -> book?.bookmarks.orEmpty().map { BookmarkItem(it, bookmarkPage(it)) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The contents, flattened: each entry's link and nesting depth */
    internal val chapterLinks: List<Pair<Link, Int>> = session?.publication?.chapters().orEmpty()

    internal val chapters: List<ChapterItem> = chapterLinks.map { (link, depth) ->
        ChapterItem(link.title?.takeIf { it.isNotBlank() } ?: link.url().toString().substringAfterLast('/'), depth)
    }

    /** Start page of each chapter, empty until computed */
    internal val chapterPages: StateFlow<List<Int?>> =
        flow { emit(session?.publication?.let { chapterPages(it, chapterLinks) }.orEmpty()) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Chapter being read, as last worked out when the contents opened; -1 when unknown */
    private val _currentChapter = MutableStateFlow(-1)
    val currentChapter: StateFlow<Int> = _currentChapter.asStateFlow()

    private val _panel = MutableStateFlow<ReaderPanel?>(null)
    internal val panel: StateFlow<ReaderPanel?> = _panel.asStateFlow()

    internal val settingsState: StateFlow<ReaderSettingsState> = combine(
        readingStyle,
        combine(book.map { it?.rightToLeft ?: false }, pageAnimation, ::Pair),
        volumeKeyPaging,
        hideStatusBar,
        settings.openLastOnLaunch.state,
    ) { style, (rightToLeft, animation), volumeKeys, hideStatus, openLast ->
        ReaderSettingsState(style, rightToLeft, animation, volumeKeys, hideStatus, openLast)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ReaderSettingsState())

    /** What the menu over the page acts on: a text selection or a tapped highlight */
    private val _markTarget = MutableStateFlow<MarkTarget?>(null)

    internal val markMenu: StateFlow<MarkMenuState?> = combine(_markTarget, book) { target, book ->
        target?.let { MarkMenuState(it, currentHighlight(it, book?.highlights), markedText(it, book?.highlights)) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Highlight whose note is being edited; kept here so a note being written survives the activity being recreated */
    private val _editingNote = MutableStateFlow<Highlight?>(null)

    /** The note's highlight in its latest saved form, falling back to the one the dialog opened with until the save lands */
    val editingNote: StateFlow<Highlight?> = combine(_editingNote, book) { editing, book ->
        editing?.let { e -> book?.highlights?.firstOrNull { it.id == e.id } ?: e }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Search state, shared by the search panel and the selection menu's in-book search */
    private val _searchState = MutableStateFlow(ReaderSearchState())
    internal val searchState = _searchState.asStateFlow()
    private var searchJob: Job? = null

    /** Long-pressed illustration shown full screen */
    private val _viewingImage = MutableStateFlow<ImageBitmap?>(null)
    val viewingImage = _viewingImage.asStateFlow()

    fun setReadingStyle(style: ReadingStyle) = settings.setReadingStyle(style)

    fun setVolumeKeyPaging(on: Boolean) = settings.volumeKeyPaging.set(on)

    fun setPageAnimation(animation: PageAnimation) = settings.setPageAnimation(animation)

    fun setHideStatusBar(on: Boolean) = settings.readerHideStatusBar.set(on)

    fun setOpenLastOnLaunch(on: Boolean) = settings.openLastOnLaunch.set(on)

    /** Page turn direction of this PDF / comic: right to left for Japanese manga */
    fun setRightToLeft(on: Boolean) {
        viewModelScope.launch {
            try {
                books.setRightToLeft(bookId, on)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.send(strings.getString(R.string.direction_save_failed, e.message.orEmpty()))
            }
        }
    }

    /** The navigator moved: remembered for the menus and saved as reading progress */
    fun onLocationChanged(locator: Locator) {
        _locator.value = locator
        saveProgress(locator)
    }

    /** Where a whole-book progression (0..1) falls, for the progress slider */
    suspend fun locate(progression: Double): Locator? = session?.publication?.locateProgression(progression)

    fun setCurrentChapter(index: Int) {
        _currentChapter.value = index
    }

    internal fun showPanel(panel: ReaderPanel) {
        _panel.value = panel
    }

    fun closePanel() {
        _panel.value = null
    }

    /** The page's text selection, reported while the reader drags its handles; a tapped highlight's menu stays put */
    internal fun onSelectionChanged(selection: MarkTarget.Selection?) {
        if (_markTarget.value !is MarkTarget.Existing) _markTarget.value = selection
    }

    /** The page's text selection is gone, and so is its menu */
    fun onSelectionEnded() {
        if (_markTarget.value is MarkTarget.Selection) _markTarget.value = null
    }

    /** A highlight tapped on the page opens its menu next to [rect]; false when it isn't one of this book's */
    internal fun openHighlightMenu(id: String, rect: RectF): Boolean {
        val highlight = book.value?.highlights?.firstOrNull { it.id == id } ?: return false
        _markTarget.value = MarkTarget.Existing(highlight, rect)
        return true
    }

    fun closeMarkMenu() {
        _markTarget.value = null
    }

    /** Closes the menu over the page if one is open; true when it was, so the tap that closed it does nothing else */
    fun dismissMarkMenu(): Boolean {
        if (_markTarget.value == null) return false
        closeMarkMenu()
        return true
    }

    /**
     * A kind or color tapped in the menu: remembered for next time and applied to the highlight being edited;
     * on a fresh selection it creates the highlight, and the menu stays open to keep adjusting it
     */
    fun applyMarkStyle(kind: HighlightKind, color: Int) {
        settings.setLastHighlight(kind, color)
        when (val target = _markTarget.value) {
            is MarkTarget.Existing -> currentHighlight(target, book.value?.highlights)
                ?.let { saveHighlight(it.copy(kind = kind, color = color)) }
            is MarkTarget.Selection -> _markTarget.value = MarkTarget.Existing(createHighlight(target.locator, kind, color), target.rect)
            null -> Unit
        }
    }

    /** 高亮: marks the selection in the last style used */
    fun highlightSelection() {
        (_markTarget.value as? MarkTarget.Selection)?.let { createHighlightInLastStyle(it.locator) }
        closeMarkMenu()
    }

    /** 标注 / 笔记: edits the note of the tapped highlight, or of a new one made from the selection */
    fun editMarkNote() {
        _editingNote.value = when (val target = _markTarget.value) {
            is MarkTarget.Existing -> currentHighlight(target, book.value?.highlights)
            is MarkTarget.Selection -> createHighlightInLastStyle(target.locator)
            null -> null
        }
        closeMarkMenu()
    }

    /** 删除 in the menu of a tapped highlight */
    fun deleteMarked() {
        (_markTarget.value as? MarkTarget.Existing)?.let { removeHighlight(it.highlight.id) }
        closeMarkMenu()
    }

    /** 书内搜索: searches the book for the marked text */
    fun searchMarked() {
        val target = _markTarget.value ?: return
        setSearchQuery(markedText(target, book.value?.highlights))
        search()
        _panel.value = ReaderPanel.Search
        closeMarkMenu()
    }

    fun saveNote(note: String) {
        editingNote.value?.let { saveHighlight(it.copy(note = note)) }
        _editingNote.value = null
    }

    fun closeNote() {
        _editingNote.value = null
    }

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
                    _searchState.update { it.copy(error = strings.getString(R.string.search_unsupported)) }
                    return@launch
                }
                try {
                    while (isActive) {
                        val page = iterator.next().getOrElse { error ->
                            if (isActive) _searchState.update { it.copy(error = strings.getString(R.string.search_failed, error.message)) }
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
                if (isActive) _searchState.update { it.copy(error = strings.getString(R.string.search_failed, e.message.orEmpty())) }
            } finally {
                if (isActive) _searchState.update { it.copy(running = false) }
            }
        }
    }

    fun close() {
        searchJob?.cancel()
        session?.let(reader::close)
    }

    /** Reads the image a page loads from [src] out of the book, decoded at about [width] × [height], and shows it full screen */
    fun openImage(src: String, width: Int, height: Int) {
        val session = session ?: return
        val url = AbsoluteUrl(src) ?: return
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                session.read(url)?.let { decodeScaledBitmap(it, width, height) }
            }
            if (bitmap != null) _viewingImage.value = bitmap.asImageBitmap() else _messages.send(strings.getString(R.string.image_open_failed))
        }
    }

    fun closeImage() {
        _viewingImage.value = null
    }

    /** Adds or removes the bookmark of the current page */
    fun toggleBookmark() {
        val locator = _locator.value ?: return
        viewModelScope.launch {
            try {
                val bookmark = Bookmark(
                    id = locator.bookmarkKey(),
                    locator = locator.toJSON().toString(),
                    title = locator.title?.takeIf { it.isNotBlank() } ?: books.find(bookId)?.title.orEmpty(),
                    progress = locator.locations.progression ?: 0.0,
                )
                val added = books.toggleBookmark(bookId, bookmark)
                _messages.send(if (added) strings.getString(R.string.bookmark_added) else strings.getString(R.string.bookmark_removed))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.send(strings.getString(R.string.bookmark_save_failed, e.message.orEmpty()))
            }
        }
    }

    fun removeBookmark(bookmarkId: String) {
        viewModelScope.launch {
            try {
                books.removeBookmark(bookId, bookmarkId)
                _messages.send(strings.getString(R.string.bookmark_deleted))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.send(strings.getString(R.string.bookmark_delete_failed, e.message.orEmpty()))
            }
        }
    }

    /** Creates a highlight for the selected [locator]; returned right away so the menu can keep editing it */
    private fun createHighlight(locator: Locator, kind: HighlightKind, color: Int): Highlight {
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

    private fun createHighlightInLastStyle(locator: Locator): Highlight =
        lastHighlight.value.let { (kind, color) -> createHighlight(locator, kind, color) }

    private fun saveHighlight(highlight: Highlight) {
        launchCatching(strings.getString(R.string.highlight_save_failed)) { books.saveHighlight(bookId, highlight) }
    }

    fun removeHighlight(highlightId: String) {
        launchCatching(strings.getString(R.string.highlight_delete_failed)) { books.removeHighlight(bookId, highlightId) }
    }

    private fun saveProgress(locator: Locator) {
        launchCatching(strings.getString(R.string.progress_save_failed)) {
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
        launchCatching(strings.getString(R.string.reading_time_save_failed)) { stats.add(bookId, seconds.coerceAtMost(MAX_SESSION_SECONDS)) }
    }

    private fun launchCatching(message: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.send("$message：${e.message.orEmpty()}")
            }
        }
    }

    /** Page of a bookmark in a PDF or comic; null in an EPUB, where its chapter progress says where it is */
    private fun bookmarkPage(bookmark: Bookmark): Int? {
        if (format == BookFormat.EPUB) return null
        return runCatching { JSONObject(bookmark.locator).getJSONObject("locations").optInt("position") }
            .getOrNull()?.takeIf { it > 0 }
    }

    companion object {
        private const val MAX_SESSION_SECONDS = 2 * 60 * 60L

        fun factory(bookId: String) = viewModelFactory {
            initializer {
                val container = this[APPLICATION_KEY]!!.container
                ReaderViewModel(bookId, container.books, container.stats, container.reader, container.settings, this[APPLICATION_KEY]!!)
            }
        }
    }
}

/** The latest saved copy of a tapped highlight, or the one the menu opened with until the save lands; null for a selection */
private fun currentHighlight(target: MarkTarget, highlights: List<Highlight>?): Highlight? =
    (target as? MarkTarget.Existing)?.let { t -> highlights?.firstOrNull { it.id == t.highlight.id } ?: t.highlight }

/** The text the menu acts on: the highlight's, or the selected text */
private fun markedText(target: MarkTarget, highlights: List<Highlight>?): String =
    currentHighlight(target, highlights)?.text ?: (target as? MarkTarget.Selection)?.locator?.text?.highlight.orEmpty()

private fun Locator.bookmarkKey() = bookmarkId(href.toString(), locations.position, locations.progression)
