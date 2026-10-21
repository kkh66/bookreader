package com.bookreader.app.reader

import com.bookreader.app.data.Book
import com.bookreader.app.data.BookRepository
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

/**
 * The currently open book. The Publication must survive Activity recreation, so it lives at app level (modeled on the Readium test-app's ReaderRepository)
 */
class ReaderRepository(private val readium: Readium, private val books: BookRepository) {

    class Session(
        val book: Book,
        val publication: Publication,
        val navigatorFactory: EpubNavigatorFactory,
        val initialLocator: Locator?,
    )

    private val sessions = mutableMapOf<String, Session>()

    operator fun get(bookId: String): Session? = sessions[bookId]

    suspend fun open(book: Book): Session {
        close(book.id)
        val publication = readium.open(books.file(book))
        val locator = book.locator?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }
        return Session(book, publication, EpubNavigatorFactory(publication), locator)
            .also { sessions[book.id] = it }
    }

    fun close(bookId: String) {
        sessions.remove(bookId)?.publication?.close()
    }
}
