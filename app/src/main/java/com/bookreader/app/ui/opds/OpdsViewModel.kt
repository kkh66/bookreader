package com.bookreader.app.ui.opds

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
    val nextUrl: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    /** Download URLs already on the shelf */
    val downloaded: Set<String> = emptySet(),
    val downloading: Set<String> = emptySet(),
    val message: String? = null,
    /** Book shown in the details dialog */
    val selected: OpdsEntry.Epub? = null,
)

/**
 * Calibre library browser: keeps a stack of visited folders and downloads epubs to the shelf
 */
class OpdsViewModel(
    private val libraries: LibraryRepository,
    private val opds: OpdsRepository,
    private val books: BookRepository,
    private val reader: ReaderRepository,
) : ViewModel() {

    private data class Level(val url: String, val title: String)

    /** Folders visited so far, the last one is the current page; going back pops it */
    private val stack = ArrayDeque<Level>()
    private var loadJob: Job? = null

    private val _state = MutableStateFlow(OpdsUiState())
    val state = combine(_state, books.books) { state, list ->
        state.copy(downloaded = list.mapNotNullTo(HashSet()) { it.source })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OpdsUiState())

    /** Library being browsed; its credentials are used for requests */
    private var library: OpdsLibrary? = null

    /** Called when the screen opens: restart from the entry URL when the library (including its URL/credentials) or the entry URL changed */
    fun start(libraryId: String, url: String, title: String) {
        val current = libraries.find(libraryId)
        if (current == library && stack.firstOrNull()?.url == url) return
        library = current
        stack.clear()
        _state.update { OpdsUiState() }
        open(url, title)
    }

    fun open(url: String, title: String) {
        stack.addLast(Level(url, title))
        load()
    }

    /** The next page keeps the current title */
    fun openNext(url: String) = open(url, stack.lastOrNull()?.title.orEmpty())

    fun refresh() = load()

    /** Go up one level; returns false at the entry page so the UI can exit */
    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeLast()
        load()
        return true
    }

    fun download(epub: OpdsEntry.Epub) {
        val url = epub.downloadUrl
        val library = library ?: return
        if (books.hasSource(url) || url in _state.value.downloading) return
        _state.update { it.copy(downloading = it.downloading + url) }
        viewModelScope.launch {
            try {
                val book = opds.download(library, url) { books.import(it, epub.title, source = url) }
                _state.update { it.copy(message = "已加入书架：${book.title}") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = "下载失败：${e.message ?: e}") }
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
    fun read(epub: OpdsEntry.Epub) {
        val book = books.books.value.firstOrNull { it.source == epub.downloadUrl } ?: return
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
                _state.update { it.copy(message = "打开失败：${e.message ?: e}") }
            } finally {
                opening = false
                lastOpenAt = SystemClock.elapsedRealtime()
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun select(epub: OpdsEntry.Epub) = _state.update { it.copy(selected = epub) }

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
            _state.update { it.copy(error = "书库不存在") }
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
                        nextUrl = page.nextUrl,
                        loading = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, error = e.message ?: e.toString(), entries = emptyList(), nextUrl = null)
                }
            }
        }
    }

    companion object {
        private const val OPEN_DEBOUNCE_MS = 1000L

        val Factory = viewModelFactory {
            initializer {
                val container = this[APPLICATION_KEY]!!.container
                OpdsViewModel(container.libraries, container.opds, container.books, container.reader)
            }
        }
    }
}
