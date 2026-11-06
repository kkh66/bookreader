package com.bookreader.app.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookreader.app.ui.components.Space
import kotlin.math.roundToInt
import org.jsoup.Jsoup

/*
 * Footnotes pop up over the page instead of jumping to the note.
 * Readium recognizes only links marked epub:type="noteref"; books made for Duokan and similar readers mark theirs with a class,
 * so chapters are rewritten as they're read to add the mark, and to hide the footnotes those readers keep inline
 */

/** A link class meaning "footnote reference": duokan-footnote, zhangyue-footnote, footnote, noteref, … but not a note's back-link or body */
private fun isNoterefClass(name: String): Boolean {
    val token = name.lowercase()
    return token == "noteref" || token == "note-ref" ||
        ("footnote" in token && "back" !in token && "content" !in token && "item" !in token)
}

private val ANCHOR_TAG = Regex("""<a\s[^>]*>""", RegexOption.IGNORE_CASE)
private val CLASS_ATTR = Regex("""\bclass\s*=\s*(["'])(.*?)\1""", RegexOption.IGNORE_CASE)
private val FRAGMENT_HREF = Regex("""\bhref\s*=\s*(["'])[^"']*#[^"']+\1""", RegexOption.IGNORE_CASE)
private val EPUB_TYPE_ATTR = Regex("""\bepub:type\s*=""", RegexOption.IGNORE_CASE)
private val NOTEREF = Regex("""\bepub:type\s*=\s*(["'])[^"']*\bnoteref\b""", RegexOption.IGNORE_CASE)
private val HTML_TAG = Regex("""<html\b""", RegexOption.IGNORE_CASE)
private val HEAD_END = Regex("""</head\s*>""", RegexOption.IGNORE_CASE)
private const val OPS_NAMESPACE = "http://www.idpf.org/2007/ops"

/**
 * Footnotes reachable from a noteref pop up, so their inline copies are hidden as Duokan and Apple Books do.
 * One rule for XHTML (namespaced attribute), one for HTML (the attribute is literally named "epub:type")
 */
private const val HIDE_FOOTNOTES =
    "<style>@namespace epub \"$OPS_NAMESPACE\";" +
        "aside[epub|type~=\"footnote\"]{display:none}" +
        "aside[epub\\:type~=\"footnote\"]{display:none}</style>"

/**
 * A chapter with class-only footnote links (Duokan style) gets epub:type="noteref" on them (declaring the epub prefix if needed,
 * so the XHTML stays well-formed), and footnote asides are hidden wherever a noteref can open them
 */
internal fun markFootnotes(html: String): String {
    var tagged = false
    var out = ANCHOR_TAG.replace(html) { match ->
        val tag = match.value
        val classes = CLASS_ATTR.find(tag)?.groupValues?.get(2)?.split(Regex("\\s+")).orEmpty()
        if (EPUB_TYPE_ATTR.containsMatchIn(tag) || !FRAGMENT_HREF.containsMatchIn(tag) || classes.none(::isNoterefClass)) {
            tag
        } else {
            tagged = true
            "<a epub:type=\"noteref\"" + tag.substring(2)
        }
    }
    if (tagged && "xmlns:epub" !in out) {
        // Without the declaration an XHTML page wouldn't parse; leave such a page as it was
        val root = HTML_TAG.find(out) ?: return html
        out = out.replaceRange(root.range, "${root.value} xmlns:epub=\"$OPS_NAMESPACE\"")
    }
    if (NOTEREF.containsMatchIn(out)) {
        HEAD_END.find(out)?.let { out = out.replaceRange(it.range, HIDE_FOOTNOTES + it.value) }
    }
    return out
}

/** [markFootnotes] on a resource's bytes; anything that isn't UTF-8 or has no footnotes comes back untouched */
internal fun markFootnotes(bytes: ByteArray): ByteArray {
    val html = runCatching { bytes.decodeToString(throwOnInvalidSequence = true) }.getOrNull() ?: return bytes
    // Most chapters have no footnotes
    if (!html.contains("footnote", ignoreCase = true) && !html.contains("noteref", ignoreCase = true)) return bytes
    val marked = markFootnotes(html)
    return if (marked == html) bytes else marked.encodeToByteArray()
}

/**
 * The note as the bubble shows it: back-links (internal links without words, like "◎", "↩", "[1]") removed, other links kept
 * as their text, list wrappers and images dropped, attributes cleared; null when no text is left
 */
internal fun footnoteHtml(noteContent: String): String? {
    val document = Jsoup.parseBodyFragment(noteContent)
    document.outputSettings().prettyPrint(false)
    val body = document.body()
    body.select("a").forEach { link ->
        if (link.attr("href").startsWith("#") && link.text().none { it.isLetter() }) link.remove() else link.unwrap()
    }
    body.select("img").remove()
    body.select("ol, ul").unwrap()
    body.select("li").tagName("p")
    body.select("*").forEach { it.clearAttributes() }
    return body.takeIf { it.text().isNotBlank() }?.html()
}

/** A footnote over the page: its HTML (from [footnoteHtml]) and where the tap that opened it went down, in screen pixels */
internal class Footnote(val html: String, val tap: Offset)

/**
 * Bubble with the note just above the tapped marker, its pointer aimed at the marker; below the marker when there's no room above.
 * Long notes scroll inside it (up to 40% of the page height)
 */
@Composable
internal fun FootnoteBubble(footnote: Footnote) {
    val density = LocalDensity.current
    val gap = with(density) { 12.dp.roundToPx() }
    // Keeps the bubble off the marker's line of text
    val clearance = with(density) { 18.dp.roundToPx() }
    val corner = with(density) { 12.dp.roundToPx() }
    val color = MaterialTheme.colorScheme.surfaceVariant
    val text = remember(footnote.html) { AnnotatedString.fromHtml(footnote.html).trimmed() }
    // Where this layer sits on screen, to place the tap in its own coordinates
    var origin by remember { mutableStateOf<Offset?>(null) }
    Layout(
        content = {
            // Pointer: a small square turned 45°, half hidden under the bubble (placed first, so the bubble covers it)
            Box(Modifier.size(12.dp).rotate(45f).background(color))
            Surface(shape = RoundedCornerShape(12.dp), color = color, shadowElevation = 8.dp) {
                Text(
                    text,
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.L, vertical = Space.M),
                )
            }
        },
        modifier = Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionOnScreen() },
    ) { measurables, constraints ->
        // Nothing to place until this layer's position on screen is known
        val at = origin?.let { footnote.tap - it } ?: return@Layout layout(constraints.maxWidth, constraints.maxHeight) {}
        val pointer = measurables[0].measure(Constraints())
        val card = measurables[1].measure(
            Constraints(
                maxWidth = minOf(constraints.maxWidth - 2 * gap, 400.dp.roundToPx()).coerceAtLeast(0),
                maxHeight = (constraints.maxHeight * 0.4f).roundToInt(),
            ),
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            val tapX = at.x.roundToInt()
            val tapY = at.y.roundToInt()
            val x = (tapX - card.width / 2).coerceIn(gap, (constraints.maxWidth - card.width - gap).coerceAtLeast(gap))
            val above = tapY - clearance - card.height >= gap
            val y = if (above) tapY - clearance - card.height else {
                (tapY + clearance).coerceAtMost((constraints.maxHeight - card.height - gap).coerceAtLeast(gap))
            }
            // The pointer stays clear of the bubble's rounded corners
            val pointerX = (tapX - pointer.width / 2)
                .coerceIn(x + corner, (x + card.width - corner - pointer.width).coerceAtLeast(x + corner))
            val pointerY = if (above) y + card.height - pointer.height / 2 else y - pointer.height / 2
            pointer.place(pointerX, pointerY)
            card.place(x, y)
        }
    }
}

/** Without the blank lines HTML paragraphs leave at the start and end */
private fun AnnotatedString.trimmed(): AnnotatedString {
    val start = text.indexOfFirst { !it.isWhitespace() }
    if (start < 0) return AnnotatedString("")
    return subSequence(start, text.indexOfLast { !it.isWhitespace() } + 1)
}
