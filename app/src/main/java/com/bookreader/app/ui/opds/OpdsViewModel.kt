package com.bookreader.app.ui.opds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bookreader.app.container
import com.bookreader.app.data.BookRepository
import com.bookreader.app.data.opds.OpdsEntry
import com.bookreader.app.data.opds.OpdsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
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
)

/**
 * Calibre library browser: keeps a stack of visited folders and downloads epubs to the shelf
 */
class OpdsViewModel(
    private val opds: OpdsRepository,
    private val books: BookRepository,
) : ViewModel() {

    private data class Level(val url: String, val title: String)

    /** Folders visited so far, the last one is the current page; going back pops it */
    private val stack = ArrayDeque<Level>()
    private var loadJob: Job? = null

    private val _state = MutableStateFlow(OpdsUiState())
    val state = combine(_state, books.books) { state, list ->
        state.copy(downloaded = list.mapNotNullTo(HashSet()) { it.source })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OpdsUiState())

    /** Called when the screen opens: restart from the entry URL when it changed */
    fun start(url: String, title: String) {
        if (stack.firstOrNull()?.url == url) return
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
        if (books.hasSource(url) || url in _state.value.downloading) return
        _state.update { it.copy(downloading = it.downloading + url) }
        viewModelScope.launch {
            try {
                val book = opds.download(url) { books.import(it, epub.title, source = url) }
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

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun load() {
        val level = stack.lastOrNull() ?: return
        loadJob?.cancel()
        _state.update { it.copy(title = level.title, loading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                val page = opds.load(level.url)
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
        val Factory = viewModelFactory {
            initializer {
                val container = this[APPLICATION_KEY]!!.container
                OpdsViewModel(container.opds, container.books)
            }
        }
    }
}
