package com.bookreader.app.reader

import android.net.Uri
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positionsByReadingOrder

/*
 * The book's contents for the reader: the flattened entries, the page each one starts on, and which one is being read.
 * Plain functions, kept out of the UI: the ViewModel holds the contents, and the activity finds the current chapter
 * since in an EPUB that takes a look at the rendered page
 */

/**
 * Table of contents flattened into a list (chapter + depth); an EPUB without one falls back to its reading order,
 * but a comic's reading order is just its images, and a PDF's is the one file
 */
internal fun Publication.chapters(): List<Pair<Link, Int>> =
    flattenContents(tableOfContents.ifEmpty { if (conformsTo(Publication.Profile.EPUB)) readingOrder else emptyList() })

/** Page of a PDF contents entry, whose link points at file.pdf#page=N */
private fun pdfPage(link: Link): Int? = link.url().fragment?.removePrefix("page=")?.toIntOrNull()

private fun flattenContents(links: List<Link>, depth: Int = 0): List<Pair<Link, Int>> =
    links.flatMap { listOf(it to depth) + flattenContents(it.children, depth + 1) }

/**
 * Previous/next chapter: from [current], search in direction [step] for the first chapter with a different href
 * (a parent chapter and its first child often point to the same place, so skip it); before the first chapter, the next chapter is the first one
 */
internal fun adjacentChapter(chapters: List<Pair<Link, Int>>, current: Int, step: Int): Link? {
    val here = chapters.getOrNull(current)?.first?.url()
    var index = current + step
    while (index in chapters.indices) {
        val link = chapters[index].first
        if (here == null || !link.url().isEquivalent(here)) return link
        index += step
    }
    return null
}

/** Start page of each chapter: the first position of the chapter's resource; chapters in the same resource share a page number */
internal suspend fun chapterPages(publication: Publication, chapters: List<Pair<Link, Int>>): List<Int?> {
    if (publication.conformsTo(Publication.Profile.PDF)) return chapters.map { (link, _) -> pdfPage(link) }
    val positions = publication.positionsByReadingOrder()
    return chapters.map { (link, _) ->
        val href = link.url().removeFragment()
        val resource = publication.readingOrder.indexOfFirst { it.url().isEquivalent(href) }
        positions.getOrNull(resource)?.firstOrNull()?.locations?.position
    }
}

@OptIn(ExperimentalReadiumApi::class)
internal suspend fun currentChapterIndex(
    publication: Publication,
    navigator: VisualNavigator,
    chapters: List<Pair<Link, Int>>,
    locator: Locator,
): Int {
    // A PDF's contents are pages of the one file: the last entry starting at or before the current page
    if (publication.conformsTo(Publication.Profile.PDF)) {
        val page = locator.locations.position ?: return -1
        return chapters.indices.lastOrNull { (pdfPage(chapters[it].first) ?: Int.MAX_VALUE) <= page } ?: -1
    }
    val href = locator.href.removeFragment()
    val matches = chapters.indices.filter { chapters[it].first.url().removeFragment().isEquivalent(href) }
    val resourceIndex = publication.readingOrder.indexOfFirst { it.url().isEquivalent(href) }
    val fallback = matches.firstOrNull() ?: chapters.indices.lastOrNull { index ->
        publication.readingOrder.indexOfFirst { it.url().isEquivalent(chapters[index].first.url().removeFragment()) } in 0..resourceIndex
    } ?: -1
    if (matches.size < 2) return fallback

    // One XHTML file can contain several chapters, so compare anchor order with the first visible element.
    val epub = navigator as? EpubNavigatorFragment ?: return fallback
    return try {
        val selector = epub.firstVisibleElementLocator()?.locations?.get("cssSelector") as? String
            ?: return fallback
        val anchors = JSONArray(matches.map { index ->
            JSONObject().put("index", index).put("id", Uri.decode(chapters[index].first.url().toString().substringAfter('#', "")))
        })
        // When a page also holds the end of the previous chapter, prefer the chapter heading that is visible.
        val result = epub.evaluateJavascript("""
            (() => {
                const first = document.querySelector(${JSONObject.quote(selector)});
                if (!first) return -1;
                let current = -1;
                let visible = -1;
                let visibleHeading = null;
                for (const entry of $anchors) {
                    const anchor = entry.id ? document.getElementById(entry.id) || document.getElementsByName(entry.id)[0] : document.body;
                    if (!anchor) continue;
                    if (anchor === first || (anchor.compareDocumentPosition(first) & Node.DOCUMENT_POSITION_FOLLOWING)) current = entry.index;
                    const heading = anchor.matches('h1,h2,h3,h4,h5,h6') ? anchor : anchor.querySelector('h1,h2,h3,h4,h5,h6') || anchor;
                    const rect = heading.getBoundingClientRect();
                    if (rect.right > 0 && rect.left < innerWidth && rect.bottom > 0 && rect.top < innerHeight && (visible < 0 || heading === visibleHeading)) {
                        visible = entry.index;
                        visibleHeading = heading;
                    }
                }
                return visible >= 0 ? visible : current;
            })()
        """.trimIndent())?.toIntOrNull()
        result?.takeIf { it in matches } ?: fallback
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        fallback
    }
}
