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
import com.bookreader.app.reader.ReaderRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

data class ShelfUiState(
    val books: List<Book> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
)

class ShelfViewModel(
    private val books: BookRepository,
    private val reader: ReaderRepository,
    private val resolver: ContentResolver,
) : ViewModel() {

    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val state = combine(books.books, busy, message) { list, busy, message ->
        ShelfUiState(list.sortedByDescending { it.addedAt }, busy, message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShelfUiState())

    /** One-shot event to open the reader, emitted only after the Publication opened successfully */
    private val _openReader = Channel<String>(Channel.BUFFERED)
    val openReader = _openReader.receiveAsFlow()

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
                ShelfViewModel(app.container.books, app.container.reader, app.contentResolver)
            }
        }
    }
}
