package com.bookreader.app.data.opds

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.net.URL

sealed interface OpdsEntry {
    val title: String

    /** Navigation entry: a child catalog */
    data class Folder(override val title: String, val url: String, val summary: String = "") : OpdsEntry

    /** Book entry: epub acquisition link + info for the details dialog */
    data class Epub(
        override val title: String,
        val author: String,
        val downloadUrl: String,
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
    /** Atom search template with a {searchTerms} placeholder; null when the server doesn't support search */
    val searchTemplate: String? = null,
)

/**
 * OPDS 1.x (Atom) parser in plain Kotlin + Jsoup with no Android dependency, so it is easy to unit test
 * Checked against the output structure of Calibre-Web (cps/templates/index.xml, feed.xml)
 */
object OpdsParser {

    const val EPUB_TYPE = "application/epub+zip"
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
        "application/pdf" to "PDF",
        "application/x-mobipocket-ebook" to "MOBI",
        "application/vnd.amazon.ebook" to "AZW3",
        "text/plain" to "TXT",
        "application/x-cbz" to "CBZ",
    )

    fun parse(xml: String, feedUrl: String): OpdsPage {
        val feed = Jsoup.parse(xml, feedUrl, Parser.xmlParser()).selectFirst("feed")
            ?: throw IllegalArgumentException("不是 OPDS feed")
        val next = feed.childrenNamed("link").firstOrNull { it.attr("rel") == "next" }
        return OpdsPage(
            title = feed.childText("title"),
            entries = feed.childrenNamed("entry").mapNotNull { parseEntry(it, feedUrl) },
            nextUrl = next?.let { resolve(feedUrl, it.attr("href")) },
            searchTemplate = feed.childrenNamed("link")
                .firstOrNull {
                    it.attr("rel") == "search" && it.mimeType() == ATOM_TYPE && "{searchTerms}" in it.attr("href")
                }
                ?.let { resolve(feedUrl, it.attr("href")) },
        )
    }

    private fun parseEntry(entry: Element, feedUrl: String): OpdsEntry? {
        val title = entry.childText("title").ifBlank { return null }
        val links = entry.childrenNamed("link")
        val acquisitions = links.filter { it.attr("rel").startsWith(ACQUISITION_REL) }
        acquisitions.firstOrNull { it.mimeType() == EPUB_TYPE }
            ?.let {
                fun linkWith(rels: Set<String>) =
                    links.firstOrNull { link -> link.attr("rel") in rels }?.let { link -> resolve(feedUrl, link.attr("href")) }
                return OpdsEntry.Epub(
                    title = title,
                    author = entry.childrenNamed("author").joinToString { a -> a.childText("name") },
                    downloadUrl = resolve(feedUrl, it.attr("href")),
                    size = it.attr("length").toLongOrNull() ?: 0,
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
        // Books with acquisition links but no epub format are skipped, not treated as folders
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
