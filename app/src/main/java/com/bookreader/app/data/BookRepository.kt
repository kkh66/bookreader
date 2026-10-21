package com.bookreader.app.data

import com.bookreader.app.reader.Readium
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.util.UUID

@Serializable
data class Book(
    val id: String,
    val title: String,
    val author: String,
    val fileName: String,
    val addedAt: Long,
    /** OPDS download URL, used to tell whether the book is already on the shelf; null for local imports */
    val source: String? = null,
    /** Readium Locator JSON: reading progress */
    val locator: String? = null,
)

/**
 * Bookshelf: EPUB files live in [dir], metadata in dir/books.json
 */
class BookRepository(private val dir: File, private val readium: Readium) {

    private val json = Json { ignoreUnknownKeys = true }
    private val indexFile = File(dir, "books.json")
    private val mutex = Mutex()

    private val _books = MutableStateFlow(load())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    fun file(book: Book) = File(dir, book.fileName)

    fun find(id: String) = _books.value.firstOrNull { it.id == id }

    fun hasSource(source: String) = _books.value.any { it.source == source }

    /**
     * Saves an EPUB stream to the shelf; title/author come from the EPUB metadata, and books that fail to parse are not added
     */
    suspend fun import(input: InputStream, fallbackTitle: String, source: String? = null): Book {
        val id = UUID.randomUUID().toString()
        val file = File(dir, "$id.epub")
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            input.use { src -> file.outputStream().use { src.copyTo(it) } }
        }
        val publication = try {
            readium.open(file)
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        val book = try {
            Book(
                id = id,
                title = publication.metadata.title?.takeIf { it.isNotBlank() } ?: fallbackTitle,
                author = publication.metadata.authors.joinToString { it.name },
                fileName = file.name,
                addedAt = System.currentTimeMillis(),
                source = source,
            )
        } finally {
            publication.close()
        }
        update { it + book }
        return book
    }

    suspend fun remove(book: Book) {
        update { list -> list.filterNot { it.id == book.id } }
        withContext(Dispatchers.IO) { file(book).delete() }
    }

    suspend fun updateLocator(id: String, locator: String) {
        update { list -> list.map { if (it.id == id) it.copy(locator = locator) else it } }
    }

    private suspend fun update(transform: (List<Book>) -> List<Book>) = mutex.withLock {
        val list = transform(_books.value)
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            indexFile.writeText(json.encodeToString(list))
        }
        _books.value = list
    }

    private fun load(): List<Book> =
        runCatching { json.decodeFromString<List<Book>>(indexFile.readText()) }.getOrDefault(emptyList())
}
