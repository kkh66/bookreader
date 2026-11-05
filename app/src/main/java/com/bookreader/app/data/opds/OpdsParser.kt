package com.bookreader.app.data.opds

import com.bookreader.app.data.BookFormat
import java.net.URL
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

sealed interface OpdsEntry {
    val title: String

    /** Navigation entry: a child catalog */
    data class Folder(override val title: String, val url: String, val summary: String = "") : OpdsEntry

    /** Book entry: acquisition link of a format the reader opens + info for the details dialog */
    data class Book(
        override val title: String,
        val author: String,
        val downloadUrl: String,
        /** Format of [downloadUrl]: EPUB when offered, otherwise CBZ, then PDF */
        val format: BookFormat,
        val size: Long,
        val summary: String = "",
        val coverUrl: String? = null,
        val thumbnailUrl: String? = null,
        val tags: List<String> = emptyList(),
        val publisher: String = "",
        /** Publication year, empty if unknown */
        val year: String = "",
        /** All formats the server offers, e.g. EPUB / PDF */
        val formats: List<String> = emptyList(),
    ) : OpdsEntry
}

data class OpdsPage(
    val title: String,
    val entries: List<OpdsEntry>,
    val nextUrl: String?,
    val previousUrl: String? = null,
    val firstUrl: String? = null,
    val lastUrl: String? = null,
    /** 1-based page number, when the server pages by offset (see [OpdsParser.pageNumber]) */
    val page: Int? = null,
    /** Atom search template with a {searchTerms} placeholder; null when the server doesn't support search */
    val searchTemplate: String? = null,
)

/**
 * OPDS 1.x (Atom) parser in plain Kotlin + Jsoup with no Android dependency, so it is easy to unit test
 * Checked against the output structure of Calibre-Web (cps/templates/index.xml, feed.xml)
 */
object OpdsParser {

    const val EPUB_TYPE = "application/epub+zip"
    const val PDF_TYPE = "application/pdf"
    private val CBZ_TYPES = listOf("application/vnd.comicbook+zip", "application/x-cbz")

    /** Download preference: EPUB reflows, a comic offered as both CBZ and PDF keeps its images as they are in CBZ */
    private val DOWNLOAD_FORMATS = listOf(EPUB_TYPE to BookFormat.EPUB) +
        CBZ_TYPES.map { it to BookFormat.CBZ } + (PDF_TYPE to BookFormat.PDF)

    /** MIME types the file picker offers for import; .cbz files often come as a plain zip or with no type at all */
    val IMPORT_TYPES = arrayOf(EPUB_TYPE, PDF_TYPE) + CBZ_TYPES + arrayOf("application/zip", "application/octet-stream")
    private const val ATOM_TYPE = "application/atom+xml"
    private const val ACQUISITION_REL = "http://opds-spec.org/acquisition"
    private const val CATALOG_PROFILE = "profile=opds-catalog"
    private val COVER_RELS = setOf(
        "http://opds-spec.org/image", "http://opds-spec.org/cover", "x-stanza-cover-image",
    )
    private val THUMBNAIL_RELS = setOf(
        "http://opds-spec.org/image/thumbnail", "http://opds-spec.org/thumbnail", "x-stanza-cover-image-thumbnail",
    )
    private val FORMAT_NAMES = mapOf(
        EPUB_TYPE to "EPUB",
        PDF_TYPE to "PDF",
        "application/x-mobipocket-ebook" to "MOBI",
        "application/vnd.amazon.ebook" to "AZW3",
        "text/plain" to "TXT",
        "application/x-cbz" to "CBZ",
        "application/vnd.comicbook+zip" to "CBZ",
    )

    fun parse(xml: String, feedUrl: String, invalidFeedMessage: String = "不是 OPDS feed"): OpdsPage {
        val feed = Jsoup.parse(xml, feedUrl, Parser.xmlParser()).selectFirst("feed")
            ?: throw IllegalArgumentException(invalidFeedMessage)
        fun link(vararg rels: String) =
            feed.childrenNamed("link").firstOrNull { it.attr("rel") in rels }?.let { resolve(feedUrl, it.attr("href")) }
        val next = link("next")
        val previous = link("previous", "prev")
        return OpdsPage(
            title = feed.childText("title"),
            entries = feed.childrenNamed("entry").mapNotNull { parseEntry(it, feedUrl) },
            nextUrl = next,
            previousUrl = previous,
            firstUrl = link("first"),
            lastUrl = link("last"),
            page = pageNumber(feedUrl, next, previous),
            searchTemplate = feed.childrenNamed("link")
                .firstOrNull {
                    it.attr("rel") == "search" && it.mimeType() == ATOM_TYPE && "{searchTerms}" in it.attr("href")
                }
                ?.let { resolve(feedUrl, it.attr("href")) },
        )
    }

    /**
     * Page number for servers paging by offset (Calibre, Calibre-Web: ?offset=30): this page's offset over the step to
     * the next or previous page; a URL without offset is offset 0. Null when the server pages some other way
     */
    fun pageNumber(url: String, next: String?, previous: String?): Int? {
        val self = offset(url)
        val step = next?.let { offset(it) - self } ?: previous?.let { self - offset(it) } ?: return null
        return if (step > 0 && self % step == 0) self / step + 1 else null
    }

    private val OFFSET = Regex("""[?&]offset=(\d+)""")

    private fun offset(url: String) = OFFSET.find(url)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    private fun parseEntry(entry: Element, feedUrl: String): OpdsEntry? {
        val title = entry.childText("title").ifBlank { return null }
        val links = entry.childrenNamed("link")
        val acquisitions = links.filter { it.attr("rel").startsWith(ACQUISITION_REL) }
        DOWNLOAD_FORMATS.firstNotNullOfOrNull { (type, format) -> acquisitions.firstOrNull { it.mimeType() == type }?.to(format) }
            ?.let { (acquisition, format) ->
                fun linkWith(rels: Set<String>) =
                    links.firstOrNull { link -> link.attr("rel") in rels }?.let { link -> resolve(feedUrl, link.attr("href")) }
                return OpdsEntry.Book(
                    title = title,
                    author = entry.childrenNamed("author").joinToString { a -> a.childText("name") },
                    downloadUrl = resolve(feedUrl, acquisition.attr("href")),
                    format = format,
                    size = acquisition.attr("length").toLongOrNull() ?: 0,
                    summary = description(entry),
                    coverUrl = linkWith(COVER_RELS),
                    thumbnailUrl = linkWith(THUMBNAIL_RELS),
                    tags = entry.childrenNamed("category")
                        .map { c -> c.attr("label").ifBlank { c.attr("term") }.trim() }
                        .filter { t -> t.isNotBlank() }
                        .distinct(),
                    publisher = entry.childrenNamed("publisher").firstOrNull()?.childText("name")
                        ?.ifBlank { null } ?: entry.childText("dcterms:publisher"),
                    year = Regex("""\d{4}""").find(entry.childText("published").ifBlank { entry.childText("dcterms:issued") })
                        ?.value.orEmpty(),
                    formats = acquisitions.map { a -> formatName(a) }.distinct(),
                )
            }
        // Books with acquisition links but none in a format the reader opens are skipped, not treated as folders
        if (links.any { it.attr("rel").startsWith(ACQUISITION_REL) }) return null
        return links.firstOrNull { it.attr("type").contains(CATALOG_PROFILE) }
            ?.let { OpdsEntry.Folder(title, resolve(feedUrl, it.attr("href")), entry.childText("content")) }
    }

    /**
     * Description: prefer <summary>; otherwise take the paragraphs in <content>
     * (Calibre-Web also stuffs rating/tags/series into content; the description is in <p> and may be escaped HTML)
     */
    private fun description(entry: Element): String {
        entry.childText("summary").takeIf { it.isNotBlank() }?.let { return htmlToText(it) }
        val content = entry.childrenNamed("content").firstOrNull() ?: return ""
        val paragraphs = content.select("p").map { it.text().trim() }.filter { it.isNotBlank() }
        val text = if (paragraphs.isNotEmpty()) paragraphs.joinToString("\n\n") else content.text().trim()
        return htmlToText(text)
    }

    /** If the text still contains HTML tags (it was escaped once), parse it again into plain text, keeping paragraphs */
    private fun htmlToText(text: String): String {
        if ('<' !in text) return text
        val body = Jsoup.parse(text).body()
        val paragraphs = body.select("p").map { it.text().trim() }.filter { it.isNotBlank() }
        return if (paragraphs.isNotEmpty()) paragraphs.joinToString("\n\n") else body.text().trim()
    }

    private fun formatName(link: Element): String =
        link.attr("title").trim().uppercase().ifBlank { null }
            ?: FORMAT_NAMES[link.mimeType()]
            ?: link.mimeType().substringAfterLast('/').uppercase()

    private fun Element.childrenNamed(name: String) = children().filter { it.tagName() == name }

    private fun Element.childText(name: String) =
        childrenNamed(name).firstOrNull()?.text()?.trim().orEmpty()

    private fun Element.mimeType() = attr("type").substringBefore(";").trim()

    private fun resolve(base: String, href: String): String =
        runCatching { URL(URL(base), href.trim()).toString() }.getOrDefault(href.trim())
}
