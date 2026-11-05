package com.bookreader.app.ui.opds

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.LruCache
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bookreader.app.R
import com.bookreader.app.container
import com.bookreader.app.data.BookRepository
import com.bookreader.app.data.LibraryRepository
import com.bookreader.app.data.OpdsLibrary
import com.bookreader.app.data.opds.OpdsEntry
import com.bookreader.app.data.opds.OpdsRepository
import com.bookreader.app.reader.ReaderRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OpdsUiState(
    val title: String = "",
    val entries: List<OpdsEntry> = emptyList(),
    /** Page shown, set once it has loaded */
    val url: String? = null,
    val nextUrl: String? = null,
    val previousUrl: String? = null,
    /** The server's first page link, or else the URL the list was opened with */
    val firstUrl: String? = null,
    val lastUrl: String? = null,
    val page: Int? = null,
    val loading: Boolean = false,
    val error: String? = null,
    /** Download URLs already on the shelf */
    val downloaded: Set<String> = emptySet(),
    val downloading: Set<String> = emptySet(),
    val message: String? = null,
    /** Book shown in the details dialog */
    val selected: OpdsEntry.Book? = null,
)

/**
 * Calibre library browser: keeps a stack of visited folders and downloads books (EPUB / PDF / CBZ) to the shelf
 */
class OpdsViewModel(
    private val libraries: LibraryRepository,
    private val opds: OpdsRepository,
    private val books: BookRepository,
    private val reader: ReaderRepository,
    private val context: Context,
) : ViewModel() {
    private val strings get() = ContextCompat.getContextForLanguage(context).resources


    /** A visited list: the page shown ([url]) and the one it was opened at ([firstUrl]) */
    private data class Level(val url: String, val title: String, val firstUrl: String = url)

    /** Folders visited so far, the last one is the current page; going back pops it */
    private val stack = ArrayDeque<Level>()
    private var loadJob: Job? = null

    private val _state = MutableStateFlow(OpdsUiState())
    // A book restored from a backup without its file counts as not downloaded
    val state = combine(_state, books.books, books.missing) { state, list, missing ->
        state.copy(downloaded = list.filter { it.id !in missing }.mapNotNullTo(HashSet()) { it.source })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OpdsUiState())

    /** Library being browsed; its credentials are used for requests */
    private var library: OpdsLibrary? = null

    /** URL the screen was started with; the first level's page changes as it's paged through */
    private var entryUrl: String? = null

    /** Called when the screen opens: restart from the entry URL when the library (including its URL/credentials) or the entry URL changed */
    fun start(libraryId: String, url: String, title: String) {
        val current = libraries.find(libraryId)
        if (current == library && entryUrl == url) return
        library = current
        entryUrl = url
        stack.clear()
        _state.update { OpdsUiState() }
        open(url, title)
    }

    fun open(url: String, title: String) {
        stack.addLast(Level(url, title))
        load()
    }

    /** Another page of the same list replaces the current one, so going back leaves the list in one step */
    fun turnPage(url: String) {
        val level = stack.removeLastOrNull() ?: return
        stack.addLast(level.copy(url = url))
        load()
    }

    fun refresh() = load()

    /** Go up one level; returns false at the entry page so the UI can exit */
    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeLast()
        load()
        return true
    }

    fun download(entry: OpdsEntry.Book) {
        val url = entry.downloadUrl
        val library = library ?: return
        // Restored from a backup without its file: download it into that book, keeping its progress
        val restored = books.books.value.firstOrNull { it.source == url && it.id in books.missing.value }
        if ((books.hasSource(url) && restored == null) || url in _state.value.downloading) return
        _state.update { it.copy(downloading = it.downloading + url) }
        viewModelScope.launch {
            try {
                val book = opds.download(library, url) { input ->
                    restored?.also { books.restoreFile(it, input) } ?: books.import(input, entry.title, source = url)
                }
                _state.update { it.copy(message = strings.getString(R.string.book_downloaded, book.title)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = strings.getString(R.string.download_failed, e.message ?: e)) }
            } finally {
                _state.update { it.copy(downloading = it.downloading - url) }
            }
        }
    }

    /** One-off event to open the reader, sent only after the Publication opened */
    private val _openReader = Channel<String>(Channel.BUFFERED)
    val openReader = _openReader.receiveAsFlow()

    // Same guard as the shelf: a double tap would open two readers, and the second closes the first one's Publication
    private var opening = false
    private var lastOpenAt = 0L

    /** A downloaded book: open the shelf copy in the reader */
    fun read(entry: OpdsEntry.Book) {
        val book = books.books.value.firstOrNull { it.source == entry.downloadUrl } ?: return
        val now = SystemClock.elapsedRealtime()
        if (opening || now - lastOpenAt < OPEN_DEBOUNCE_MS) return
        opening = true
        lastOpenAt = now
        viewModelScope.launch {
            try {
                reader.open(book)
                _openReader.send(book.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = strings.getString(R.string.book_open_failed, e.message ?: e)) }
            } finally {
                opening = false
                lastOpenAt = SystemClock.elapsedRealtime()
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun select(entry: OpdsEntry.Book) = _state.update { it.copy(selected = entry) }

    fun dismissDetail() = _state.update { it.copy(selected = null) }

    /** Loaded covers/thumbnails, so going back a page doesn't download them again */
    private val covers = LruCache<String, Bitmap>(80)

    /** Thumbnail size for the list, full size for the dialog; null on failure (the UI shows a generated cover) */
    suspend fun cover(url: String, large: Boolean): Bitmap? {
        val key = "$url#$large"
        covers.get(key)?.let { return it }
        val library = library ?: return null
        val (width, height) = if (large) 480 to 720 else 160 to 240
        return opds.image(library, url, width, height)?.also { covers.put(key, it) }
    }

    private fun load() {
        val level = stack.lastOrNull() ?: return
        val library = library ?: run {
            _state.update { it.copy(error = strings.getString(R.string.library_not_found)) }
            return
        }
        loadJob?.cancel()
        _state.update { it.copy(title = level.title, loading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                val page = opds.load(library, level.url)
                _state.update {
                    it.copy(
                        title = level.title.ifBlank { page.title },
                        entries = page.entries,
                        url = level.url,
                        nextUrl = page.nextUrl,
                        previousUrl = page.previousUrl,
                        firstUrl = page.firstUrl ?: level.firstUrl,
                        lastUrl = page.lastUrl,
                        page = page.page,
                        loading = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loading = false, error = e.message ?: e.toString(), entries = emptyList(),
                        nextUrl = null, previousUrl = null, firstUrl = null, lastUrl = null, page = null,
                    )
                }
            }
        }
    }

    companion object {
        private const val OPEN_DEBOUNCE_MS = 1000L

        val Factory = viewModelFactory {
            initializer {
                val container = this[APPLICATION_KEY]!!.container
                OpdsViewModel(container.libraries, container.opds, container.books, container.reader, this[APPLICATION_KEY]!!)
            }
        }
    }
}
