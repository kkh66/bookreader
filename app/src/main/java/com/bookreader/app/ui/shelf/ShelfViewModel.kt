package com.bookreader.app.ui.shelf

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bookreader.app.container
import com.bookreader.app.data.Book
import com.bookreader.app.data.BookRepository
import com.bookreader.app.data.StatsRepository
import com.bookreader.app.reader.ReaderRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

/** Shelf item: book + cover path + total seconds spent reading it */
data class ShelfItem(val book: Book, val coverPath: String?, val readSeconds: Long = 0)

data class ShelfUiState(
    val items: List<ShelfItem> = emptyList(),
    /** Continue reading: the most recently read book */
    val current: ShelfItem? = null,
    val total: Int = 0,
    val query: String = "",
    val busy: Boolean = false,
    val message: String? = null,
)

class ShelfViewModel(
    private val books: BookRepository,
    stats: StatsRepository,
    private val reader: ReaderRepository,
    private val resolver: ContentResolver,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    private val items = combine(books.books, stats.stats) { list, stats ->
        list.map { ShelfItem(it, books.cover(it)?.path, stats.books[it.id] ?: 0L) }
    }
        .flowOn(Dispatchers.IO)

    val state = combine(items, query, busy, message) { items, query, busy, message ->
        val sorted = items.sortedByDescending { maxOf(it.book.lastReadAt, it.book.addedAt) }
        ShelfUiState(
            items = if (query.isBlank()) sorted else sorted.filter {
                it.book.title.contains(query, ignoreCase = true) || it.book.author.contains(query, ignoreCase = true)
            },
            current = items.filter { it.book.lastReadAt > 0 }.maxByOrNull { it.book.lastReadAt },
            total = items.size,
            query = query,
            busy = busy,
            message = message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShelfUiState())

    /** One-shot event to open the reader, emitted only after the Publication opened successfully */
    private val _openReader = Channel<String>(Channel.BUFFERED)
    val openReader = _openReader.receiveAsFlow()

    init {
        // Extract covers and character counts for books imported earlier
        viewModelScope.launch { runCatching { books.ensureMetadata() } }
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun open(book: Book) = launchBusy {
        reader.open(book)
        _openReader.send(book.id)
    }

    fun importEpub(uri: Uri) = launchBusy {
        val input = resolver.openInputStream(uri) ?: throw IOException("无法读取文件")
        val book = books.import(input, fallbackTitle = uri.lastPathSegment ?: "未命名")
        message.value = "已导入《${book.title}》"
    }

    fun delete(book: Book) = launchBusy { books.remove(book) }

    fun clearMessage() {
        message.value = null
    }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy.value = true
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message.value = e.message ?: e.toString()
            } finally {
                busy.value = false
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                ShelfViewModel(app.container.books, app.container.stats, app.container.reader, app.contentResolver)
            }
        }
    }
}
