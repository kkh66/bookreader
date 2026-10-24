package com.bookreader.app.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import androidx.core.content.ContextCompat
import com.bookreader.app.R
import com.bookreader.app.reader.Readium
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.coverFitting
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.getOrElse

@Serializable
data class Bookmark(
    val id: String,
    val locator: String,
    val title: String,
    val progress: Double,
)

// Prefer the stable position so re-layout doesn't add the same bookmark twice.
// A position may span a whole chapter, so in-chapter progress is kept too to tell pages apart.
fun bookmarkId(href: String, position: Int?, progression: Double?): String =
    "$href#position=${position ?: 0}#progression=${((progression ?: 0.0) * 1_000_000).roundToLong()}"

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
    /** Whole-book progress, 0..1 */
    val progress: Double = 0.0,
    /** Current chapter title */
    val chapter: String? = null,
    /** Last time the book was read, 0 if never */
    val lastReadAt: Long = 0,
    /** Version of the cover extraction rules last applied; books without a cover are retried when the rules are upgraded */
    val coverVersion: Int = 0,
    /** First time the book was read, 0 if never */
    val firstReadAt: Long = 0,
    /** Character count of the whole book (excluding whitespace), null if not counted yet */
    val totalChars: Long? = null,
    val bookmarks: List<Bookmark> = emptyList(),
    val highlights: List<Highlight> = emptyList(),
    /** Name of the user-created shelf group, null if not in a group */
    val group: String? = null,
    /** Page turns of a PDF / CBZ go right to left, as in Japanese manga */
    val rightToLeft: Boolean = false,
) {
    val format: BookFormat get() = BookFormat.of(fileName)
}

/**
 * Bookshelf: book files (EPUB / PDF / CBZ) live in [dir], covers in dir/covers, metadata in dir/books.json
 */
class BookRepository(private val dir: File, private val readium: Readium, private val context: Context) {
    private val strings get() = ContextCompat.getContextForLanguage(context).resources


    private val json = Json { ignoreUnknownKeys = true }
    private val indexFile = File(dir, "books.json")
    private val coverDir = File(dir, "covers")
    private val mutex = Mutex()

    private val _books = MutableStateFlow(load())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    fun file(book: Book) = File(dir, book.fileName)

    /** Cover file, or null when there is none (the UI uses a generated cover) */
    fun cover(book: Book): File? = File(coverDir, "${book.id}.jpg").takeIf { it.isFile }

    fun find(id: String) = _books.value.firstOrNull { it.id == id }

    fun hasSource(source: String) = _books.value.any { it.source == source }

    /**
     * Saves an EPUB / PDF / CBZ stream to the shelf; title/author come from the book's metadata (comics have none, so [fallbackTitle]),
     * and files that aren't one of these formats or fail to parse are not added
     */
    suspend fun import(input: InputStream, fallbackTitle: String, source: String? = null): Book {
        val id = UUID.randomUUID().toString()
        val temp = File(dir, "$id.tmp")
        var file = temp
        // Never leave files behind when a step fails: copying (network loss, disk full, cancellation), an unsupported format,
        // parsing, or saving the index
        try {
            withContext(Dispatchers.IO) {
                dir.mkdirs()
                input.use { src -> temp.outputStream().use { src.copyTo(it) } }
            }
            // The content decides the format, which the file extension then records
            val format = readium.sniff(temp) ?: throw IOException(strings.getString(R.string.unsupported_book))
            file = File(dir, "$id.${format.extension}")
            if (!temp.renameTo(file)) throw IOException(strings.getString(R.string.file_save_failed))
            val publication = readium.open(file)
            val book = try {
                saveCover(id, publication, format)
                Book(
                    id = id,
                    title = publication.metadata.title?.takeIf { it.isNotBlank() } ?: fallbackTitle,
                    author = publication.metadata.authors.joinToString { it.name },
                    fileName = file.name,
                    addedAt = System.currentTimeMillis(),
                    source = source,
                    coverVersion = COVER_VERSION,
                    // Character counts (for reading speed) only make sense for text; 0 keeps PDF / CBZ from being counted again
                    totalChars = if (format == BookFormat.EPUB) countChars(publication) else 0L,
                )
            } finally {
                publication.close()
            }
            update { it + book }
            return book
        } catch (e: Exception) {
            temp.delete()
            file.delete()
            File(coverDir, "$id.jpg").delete()
            throw e
        }
    }

    /** Adds covers and character counts to books imported earlier */
    suspend fun ensureMetadata() {
        val pending = _books.value.filter { book ->
            book.totalChars == null || (book.coverVersion < COVER_VERSION && cover(book) == null)
        }
        for (book in pending) {
            val chars = runCatching {
                val publication = readium.open(file(book))
                try {
                    if (cover(book) == null) saveCover(book.id, publication, book.format)
                    book.totalChars ?: if (book.format == BookFormat.EPUB) countChars(publication) else 0L
                } finally {
                    publication.close()
                }
            }.getOrDefault(book.totalChars ?: 0L)
            update { list ->
                list.map { if (it.id == book.id) it.copy(coverVersion = COVER_VERSION, totalChars = chars) else it }
            }
        }
    }

    suspend fun remove(book: Book) {
        update { list -> list.filterNot { it.id == book.id } }
        withContext(Dispatchers.IO) {
            file(book).delete()
            File(coverDir, "${book.id}.jpg").delete()
        }
    }

    suspend fun updateProgress(id: String, locator: String, progress: Double, chapter: String?) {
        val now = System.currentTimeMillis()
        update { list ->
            list.map {
                if (it.id == id) {
                    it.copy(
                        locator = locator,
                        progress = progress,
                        chapter = chapter,
                        lastReadAt = now,
                        firstReadAt = it.firstReadAt.takeIf { first -> first > 0 } ?: now,
                    )
                } else {
                    it
                }
            }
        }
    }

    /** Page turn direction of a PDF / CBZ */
    suspend fun setRightToLeft(id: String, rightToLeft: Boolean) {
        update { list -> list.map { if (it.id == id) it.copy(rightToLeft = rightToLeft) else it } }
    }

    /** Puts books into a group (groups with the same name merge); a null [group] removes them from their group */
    suspend fun setGroup(ids: Collection<String>, group: String?) {
        update { list -> list.map { if (it.id in ids) it.copy(group = group) else it } }
    }

    /** Renames a group; renaming to an existing name merges into that group */
    suspend fun renameGroup(from: String, to: String) {
        update { list -> list.map { if (it.group == from) it.copy(group = to) else it } }
    }

    suspend fun toggleBookmark(bookId: String, bookmark: Bookmark): Boolean {
        var added = false
        update { list ->
            require(list.any { it.id == bookId }) { strings.getString(R.string.book_deleted) }
            list.map { book ->
                if (book.id != bookId) book else {
                    added = book.bookmarks.none { it.id == bookmark.id }
                    book.copy(bookmarks = if (added) book.bookmarks + bookmark else book.bookmarks.filterNot { it.id == bookmark.id })
                }
            }
        }
        return added
    }

    /** Adds a highlight, or replaces the one with the same id (style, color or note changed) */
    suspend fun saveHighlight(bookId: String, highlight: Highlight) {
        update { list ->
            require(list.any { it.id == bookId }) { strings.getString(R.string.book_deleted) }
            list.map { book ->
                if (book.id != bookId) book else {
                    val others = book.highlights.filterNot { it.id == highlight.id }
                    book.copy(highlights = (others + highlight).sortedBy { it.progress })
                }
            }
        }
    }

    suspend fun removeHighlight(bookId: String, highlightId: String) {
        update { list ->
            list.map { book ->
                if (book.id == bookId) book.copy(highlights = book.highlights.filterNot { it.id == highlightId }) else book
            }
        }
    }

    suspend fun removeBookmark(bookId: String, bookmarkId: String) {
        update { list ->
            list.map { book ->
                if (book.id == bookId) book.copy(bookmarks = book.bookmarks.filterNot { it.id == bookmarkId }) else book
            }
        }
    }

    /**
     * For EPUBs whose metadata has no cover: first take the first image in the first chapter (usually the cover page),
     * then try images with "cover" in the file name
     */
    private suspend fun fallbackCover(publication: Publication): Bitmap? {
        val candidates = buildList {
            publication.readingOrder.firstOrNull()?.let { first ->
                readBytes(publication, first.url())?.let { html ->
                    val page = Jsoup.parse(String(html, Charsets.UTF_8))
                    val src = page.selectFirst("img[src]")?.attr("src")
                        ?: page.selectFirst("image")?.let { it.attr("xlink:href").ifBlank { it.attr("href") } }
                    src?.takeIf { it.isNotBlank() }?.let { Url(it) }?.let { add(first.url().resolve(it)) }
                }
            }
            publication.resources
                .filter { it.mediaType?.isBitmap == true && "cover" in it.url().toString().lowercase() }
                .forEach { add(it.url()) }
        }
        for (url in candidates) {
            val bytes = readBytes(publication, url) ?: continue
            decodeScaledBitmap(bytes, COVER_WIDTH, COVER_HEIGHT)?.let { return it }
        }
        return null
    }

    private suspend fun readBytes(publication: Publication, url: Url): ByteArray? {
        val resource = publication.get(url) ?: return null
        return try {
            resource.read().getOrElse { null }
        } finally {
            resource.close()
        }
    }

    /** Counts the characters of the text (tags and whitespace removed), used to compute reading speed */
    private suspend fun countChars(publication: Publication): Long = withContext(Dispatchers.IO) {
        publication.readingOrder.sumOf { link ->
            val resource = publication.get(link) ?: return@sumOf 0L
            try {
                val bytes = resource.read().getOrElse { return@sumOf 0L }
                Jsoup.parse(String(bytes, Charsets.UTF_8)).text().count { !it.isWhitespace() }.toLong()
            } finally {
                resource.close()
            }
        }
    }

    /** Readium finds EPUB covers in the metadata, renders a PDF's first page and takes a comic's first image */
    private suspend fun saveCover(id: String, publication: Publication, format: BookFormat) {
        val bitmap = publication.coverFitting(Size(COVER_WIDTH, COVER_HEIGHT))
            ?: (if (format == BookFormat.EPUB) fallbackCover(publication) else null)
            ?: return
        withContext(Dispatchers.IO) {
            coverDir.mkdirs()
            File(coverDir, "$id.jpg").outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        }
    }

    private suspend fun update(transform: (List<Book>) -> List<Book>) = mutex.withLock {
        val list = transform(_books.value)
        withContext(Dispatchers.IO) { indexFile.writeAtomically(json.encodeToString(list)) }
        _books.value = list
    }

    private fun load(): List<Book> =
        indexFile.readAtomically { json.decodeFromString<List<Book>>(it) }.orEmpty()

    private companion object {
        /** Cover extraction rules version: 2 = added cover page / file name fallbacks */
        const val COVER_VERSION = 2
        const val COVER_WIDTH = 480
        const val COVER_HEIGHT = 720
    }
}
