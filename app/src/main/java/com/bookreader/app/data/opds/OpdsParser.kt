package com.bookreader.app.data.opds

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.net.URL

sealed interface OpdsEntry {
    val title: String

    /** Navigation entry: a child catalog */
    data class Folder(override val title: String, val url: String) : OpdsEntry

    /** Book entry: only the epub acquisition link is kept */
    data class Epub(
        override val title: String,
        val author: String,
        val downloadUrl: String,
        val size: Long,
    ) : OpdsEntry
}

data class OpdsPage(val title: String, val entries: List<OpdsEntry>, val nextUrl: String?)

/**
 * OPDS 1.x (Atom) parser in plain Kotlin + Jsoup with no Android dependency, so it is easy to unit test
 * Checked against the output structure of Calibre-Web (cps/templates/index.xml, feed.xml)
 */
object OpdsParser {

    const val EPUB_TYPE = "application/epub+zip"
    private const val ACQUISITION_REL = "http://opds-spec.org/acquisition"
    private const val CATALOG_PROFILE = "profile=opds-catalog"

    fun parse(xml: String, feedUrl: String): OpdsPage {
        val feed = Jsoup.parse(xml, feedUrl, Parser.xmlParser()).selectFirst("feed")
            ?: throw IllegalArgumentException("不是 OPDS feed")
        val next = feed.childrenNamed("link").firstOrNull { it.attr("rel") == "next" }
        return OpdsPage(
            title = feed.childText("title"),
            entries = feed.childrenNamed("entry").mapNotNull { parseEntry(it, feedUrl) },
            nextUrl = next?.let { resolve(feedUrl, it.attr("href")) },
        )
    }

    private fun parseEntry(entry: Element, feedUrl: String): OpdsEntry? {
        val title = entry.childText("title").ifBlank { return null }
        val links = entry.childrenNamed("link")
        links.firstOrNull { it.attr("rel").startsWith(ACQUISITION_REL) && it.mimeType() == EPUB_TYPE }
            ?.let {
                return OpdsEntry.Epub(
                    title = title,
                    author = entry.childrenNamed("author").joinToString { a -> a.childText("name") },
                    downloadUrl = resolve(feedUrl, it.attr("href")),
                    size = it.attr("length").toLongOrNull() ?: 0,
                )
            }
        // Books with acquisition links but no epub format are skipped, not treated as folders
        if (links.any { it.attr("rel").startsWith(ACQUISITION_REL) }) return null
        return links.firstOrNull { it.attr("type").contains(CATALOG_PROFILE) }
            ?.let { OpdsEntry.Folder(title, resolve(feedUrl, it.attr("href"))) }
    }

    private fun Element.childrenNamed(name: String) = children().filter { it.tagName() == name }

    private fun Element.childText(name: String) =
        childrenNamed(name).firstOrNull()?.text()?.trim().orEmpty()

    private fun Element.mimeType() = attr("type").substringBefore(";").trim()

    private fun resolve(base: String, href: String): String =
        runCatching { URL(URL(base), href.trim()).toString() }.getOrDefault(href.trim())
}
