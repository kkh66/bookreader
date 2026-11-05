package com.bookreader.app.ui.shelf

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bookreader.app.R
import com.bookreader.app.container
import com.bookreader.app.data.Book
import com.bookreader.app.data.BookRepository
import com.bookreader.app.data.LibraryRepository
import com.bookreader.app.data.SettingsRepository
import com.bookreader.app.data.StatsRepository
import com.bookreader.app.data.displayName
import com.bookreader.app.data.opds.OpdsRepository
import com.bookreader.app.reader.ReaderRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shelf item: book + cover path + total seconds spent reading it; [missing] = its file isn't on this device (restored from a backup) */
data class ShelfItem(val book: Book, val coverPath: String?, val readSeconds: Long = 0, val missing: Boolean = false)

/** One shelf cell: a single book, or a group the user created by dragging */
sealed interface ShelfEntry {
    val key: String

    data class Single(val item: ShelfItem) : ShelfEntry {
        override val key get() = item.book.id
    }

    /** Group members keep the shelf order; the cover collage uses the first 4 */
    data class Group(val name: String, val items: List<ShelfItem>) : ShelfEntry {
        override val key get() = "group:$name"
    }
}

/** Sorted books → shelf items: a group takes the position of its first book */
internal fun shelfEntries(sorted: List<ShelfItem>): List<ShelfEntry> {
    val groups = sorted.filter { it.book.group != null }.groupBy { it.book.group!! }
    val placed = HashSet<String>()
    return sorted.mapNotNull { item ->
        val name = item.book.group ?: return@mapNotNull ShelfEntry.Single(item)
        if (placed.add(name)) ShelfEntry.Group(name, groups.getValue(name)) else null
    }
}

data class ShelfUiState(
    /** No grouping while searching: matching books are listed directly, including books inside groups */
    val entries: List<ShelfEntry> = emptyList(),
    /** Number of books listed (groups count their books) */
    val count: Int = 0,
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
    private val settings: SettingsRepository,
    private val libraries: LibraryRepository,
    private val opds: OpdsRepository,
    private val resolver: ContentResolver,
    private val context: Context,
) : ViewModel() {
    private val strings get() = ContextCompat.getContextForLanguage(context).resources


    private val query = MutableStateFlow("")
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    private val items = combine(books.books, stats.stats, books.missing) { list, stats, missing ->
        list.map { ShelfItem(it, books.cover(it)?.path, stats.books[it.id] ?: 0L, it.id in missing) }
    }
        .flowOn(Dispatchers.IO)

    val state = combine(items, query, busy, message) { items, query, busy, message ->
        val sorted = items.sortedByDescending { maxOf(it.book.lastReadAt, it.book.addedAt) }
        val matched = if (query.isBlank()) sorted else sorted.filter {
            it.book.title.contains(query, ignoreCase = true) || it.book.author.contains(query, ignoreCase = true)
        }
        ShelfUiState(
            entries = if (query.isBlank()) shelfEntries(sorted) else matched.map { ShelfEntry.Single(it) },
            count = matched.size,
            current = items.filter { it.book.lastReadAt > 0 }.maxByOrNull { it.book.lastReadAt },
            total = items.size,
            query = query,
            busy = busy,
            message = message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShelfUiState())

    /** After restoring a backup: library passwords weren't in it, and a library that needs one has none yet */
    val passwordNotice = combine(settings.passwordNotice.state, libraries.libraries) { on, list ->
        on && list.any { it.username.isNotBlank() && it.password.isBlank() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun dismissPasswordNotice() = settings.passwordNotice.set(false)

    /** A local import restored without its file was opened: the screen asks for the file, then calls [restoreLocal] */
    private val _pickFile = Channel<Book>(Channel.BUFFERED)
    val pickFile = _pickFile.receiveAsFlow()

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

    val grid = settings.shelfGrid

    fun setGrid(grid: Boolean) = settings.setShelfGrid(grid)

    /** Book being opened, shown with a spinner over its cover */
    private val _openingId = MutableStateFlow<String?>(null)
    val openingId: StateFlow<String?> = _openingId.asStateFlow()

    /** True while the last book opens on launch; the splash screen waits for it */
    private val _launching = MutableStateFlow(false)
    val launching: StateFlow<Boolean> = _launching.asStateFlow()

    /** Called on cold start: if "open last book on launch" is on, open the most recently read book */
    fun openLastOnLaunch() {
        if (!settings.openLastOnLaunch.state.value) return
        val book = books.books.value.filter { it.lastReadAt > 0 }.maxByOrNull { it.lastReadAt } ?: return
        // Not on this device yet: no download or file picker behind the splash screen
        if (book.id in books.missing.value) return
        _launching.value = true
        open(book)
    }

    // A double tap would open two readers, and the second open would close the Publication the first one is using
    private var opening = false
    private var lastOpenAt = 0L

    fun open(book: Book) {
        val now = SystemClock.elapsedRealtime()
        if (opening || now - lastOpenAt < OPEN_DEBOUNCE_MS) {
            _launching.value = false
            return
        }
        opening = true
        lastOpenAt = now
        _openingId.value = book.id
        launchCatching {
            try {
                if (book.id in books.missing.value) {
                    val source = book.source
                    if (source == null) {
                        _pickFile.send(book)
                        return@launchCatching
                    }
                    // From an OPDS library: download it again into the same book
                    val library = libraries.forUrl(source) ?: throw IOException(strings.getString(R.string.book_library_missing))
                    opds.download(library, source) { books.restoreFile(book, it) }
                }
                reader.open(book)
                _openReader.send(book.id)
            } finally {
                opening = false
                lastOpenAt = SystemClock.elapsedRealtime()
                _openingId.value = null
                _launching.value = false
            }
        }
    }

    fun importBook(uri: Uri) = launchBusy {
        // Opening a document can block while a cloud provider downloads it
        val (input, name) = withContext(Dispatchers.IO) {
            (resolver.openInputStream(uri) ?: throw IOException(strings.getString(R.string.file_read_failed))) to resolver.displayName(uri)
        }
        // Comics have no title of their own, so the file name stands in
        val book = books.import(input, fallbackTitle = name?.substringBeforeLast('.')?.ifBlank { null } ?: strings.getString(R.string.untitled))
        message.value = strings.getString(R.string.book_imported, book.title)
    }

    /** The file picked for a local import restored without it; opens the book once it's back */
    fun restoreLocal(bookId: String, uri: Uri) = launchCatching {
        val book = books.find(bookId) ?: return@launchCatching
        _openingId.value = book.id
        try {
            val input = withContext(Dispatchers.IO) {
                resolver.openInputStream(uri) ?: throw IOException(strings.getString(R.string.file_read_failed))
            }
            books.restoreFile(book, input)
        } finally {
            _openingId.value = null
        }
        open(book)
    }

    fun delete(list: List<Book>) = launchBusy { list.forEach { books.remove(it) } }

    /** After dragging one book onto another and naming the group: put both into the new group */
    fun createGroup(ids: List<String>, name: String) = launchCatching { books.setGroup(ids, name.trim()) }

    /** Dropped onto an existing group, or picked with the group's add tile; books from another group move here */
    fun addToGroup(ids: List<String>, name: String) = launchCatching {
        books.setGroup(ids, name)
        message.value = if (ids.size == 1) strings.getString(R.string.moved_to_group, name) else strings.getString(R.string.books_moved_to_group, ids.size, name)
    }

    /** Removes books from their group; passing all of a group's books ungroups it */
    fun ungroup(ids: List<String>) = launchCatching { books.setGroup(ids, null) }

    fun renameGroup(from: String, to: String) = launchCatching { books.renameGroup(from, to.trim()) }

    fun clearMessage() {
        message.value = null
    }

    private fun launchBusy(block: suspend () -> Unit) = launchCatching {
        busy.value = true
        try {
            block()
        } finally {
            busy.value = false
        }
    }

    /** Shown in a snackbar on error */
    private fun launchCatching(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message.value = e.message ?: e.toString()
            }
        }
    }

    companion object {
        private const val OPEN_DEBOUNCE_MS = 1000L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                ShelfViewModel(
                    app.container.books, app.container.stats, app.container.reader, app.container.settings,
                    app.container.libraries, app.container.opds, app.contentResolver, app,
                )
            }
        }
    }
}
