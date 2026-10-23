package com.bookreader.app.reader

import com.bookreader.app.data.Book
import com.bookreader.app.data.BookRepository
import com.bookreader.app.data.FontRepository
import com.bookreader.app.data.UserFont
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.use

/**
 * Books currently open. The Publication must survive Activity recreation, so it lives at app level (as in the Readium test-app's ReaderRepository)
 */
class ReaderRepository(
    private val readium: Readium,
    private val books: BookRepository,
    private val fonts: FontRepository,
) {

    /** An imported font and the URL the reader's pages load it from */
    class ServedFont(val font: UserFont, val url: AbsoluteUrl)

    class Session(
        val book: Book,
        val publication: Publication,
        val navigatorFactory: EpubNavigatorFactory,
        val initialLocator: Locator?,
        /** Imported fonts as of opening; fonts imported later show up the next time the book opens */
        val fonts: List<ServedFont>,
        /** Where the reader's pages load the publication's resources from */
        private val baseUrl: AbsoluteUrl,
    ) {
        /** Bytes of the book resource a page loads from [url] (an image, say); null for anything outside the book */
        suspend fun read(url: AbsoluteUrl): ByteArray? {
            val href = baseUrl.relativize(url)
            return publication.get(publication.linkWithHref(href)?.url() ?: href)?.use { it.read().getOrNull() }
        }
    }

    private val sessions = mutableMapOf<String, Session>()

    operator fun get(bookId: String): Session? = sessions[bookId]

    suspend fun open(book: Book): Session {
        close(book.id)
        val userFonts = fonts.fonts.value
        val files = userFonts.map { FontFile(Url.fromDecodedPath("$FONTS_DIR/${it.fileName}")!!, fonts.file(it)) }
        val publication = readium.open(books.file(book), files)
        // Same rule the navigator uses to serve publication resources: relative to the publication's base URL
        val base = publication.baseUrl ?: PACKAGE_BASE_URL
        val served = userFonts.zip(files) { font, file -> ServedFont(font, base.resolve(file.href)) }
        val locator = book.locator?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }
        return Session(book, publication, EpubNavigatorFactory(publication), locator, served, base)
            .also { sessions[book.id] = it }
    }

    fun close(bookId: String) {
        sessions.remove(bookId)?.publication?.close()
    }

    private companion object {
        /** Folder the fonts appear under inside the publication; unlikely to clash with a book's own paths */
        const val FONTS_DIR = "__bookreader_fonts"

        /** Where the EPUB navigator serves a local publication's resources (Readium's WebViewServer) */
        val PACKAGE_BASE_URL = AbsoluteUrl("https://readium_package/")!!
    }
}
