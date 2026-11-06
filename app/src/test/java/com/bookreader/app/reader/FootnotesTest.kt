package com.bookreader.app.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FootnotesTest {

    private fun page(body: String, html: String = """<html xmlns="http://www.w3.org/1999/xhtml">""") =
        """<?xml version="1.0" encoding="utf-8"?>$html<head><title>t</title></head><body>$body</body></html>"""

    private val duokan = """<p>查理<a class="duokan-footnote" href="#note1" id="ref1"><img src="note.png"/></a>，告诉我</p>""" +
        """<aside epub:type="footnote" id="note1"><ol class="duokan-footnote-content">""" +
        """<li class="duokan-footnote-item" id="note1li"><a href="#ref1">◎</a>译：ChatGPT</li></ol></aside>"""

    @Test
    fun duokanLinkGetsNoterefAndNamespace() {
        val out = markFootnotes(page(duokan))
        assertTrue(out.contains("""<a epub:type="noteref" class="duokan-footnote" href="#note1""""))
        assertTrue(out.contains("""<html xmlns:epub="http://www.idpf.org/2007/ops" xmlns="http://www.w3.org/1999/xhtml">"""))
        // The back-link inside the note is left alone
        assertTrue(out.contains("""<a href="#ref1">◎</a>"""))
    }

    @Test
    fun existingNamespaceIsNotDeclaredTwice() {
        val html = """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">"""
        val out = markFootnotes(page(duokan, html))
        assertEquals(1, Regex("xmlns:epub").findAll(out).count())
    }

    @Test
    fun inlineFootnotesAreHiddenOnlyWhenANoterefOpensThem() {
        val out = markFootnotes(page(duokan))
        assertTrue(out.contains("""aside[epub|type~="footnote"]{display:none}"""))
        assertTrue(out.contains("</style></head>"))
        // An aside nothing links to as a noteref stays visible
        val orphan = page("""<aside epub:type="footnote" id="n"><p>text</p></aside>""")
        assertEquals(orphan, markFootnotes(orphan))
    }

    @Test
    fun standardNoterefsAreKeptAsTheyAre() {
        val body = """<p>x<a epub:type="noteref" class="footnote" href="#n1">1</a></p><aside epub:type="footnote" id="n1">note</aside>"""
        val html = """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">"""
        val out = markFootnotes(page(body, html))
        assertEquals(1, Regex("epub:type=\"noteref\"").findAll(out).count())
        assertTrue(out.contains("<style>"))
    }

    @Test
    fun otherLinksAreLeftAlone() {
        listOf(
            """<a class="footnote-backref" href="#ref1">↩</a>""",
            """<a class="toc" href="#ch1">第一章</a>""",
            """<a class="footnote" href="notes.xhtml">注释</a>""",
            """<a href="#n1">1</a>""",
        ).forEach { link ->
            val html = page("<p>$link</p>")
            assertEquals(link, html, markFootnotes(html))
        }
    }

    @Test
    fun pagesWithoutFootnotesKeepTheirBytes() {
        val plain = page("<p>正文</p>").encodeToByteArray()
        assertSame(plain, markFootnotes(plain))
        val notUtf8 = byteArrayOf(0x3c, 0xff.toByte(), 0xfe.toByte(), 0x3e)
        assertSame(notUtf8, markFootnotes(notUtf8))
        assertFalse(markFootnotes(page(duokan).encodeToByteArray()).contentEquals(page(duokan).encodeToByteArray()))
    }

    @Test
    fun noteDropsBackLinksAndListWrappers() {
        val note = """<ol class="duokan-footnote-content"><li class="duokan-footnote-item" id="n"><a href="#ref1">◎</a>译：<b>ChatGPT</b></li></ol>"""
        assertEquals("<p>译：<b>ChatGPT</b></p>", footnoteHtml(note))
    }

    @Test
    fun noteKeepsTheTextOfOtherLinks() {
        assertEquals("见 维基百科 条目", footnoteHtml("""见 <a href="https://zh.wikipedia.org/">维基百科</a> 条目"""))
        assertEquals("见第一章", footnoteHtml("""见<a href="#chapter-one">第一章</a>"""))
    }

    @Test
    fun emptyNoteIsNull() {
        assertNull(footnoteHtml("""<a href="#ref1">↩</a><img src="x.png"/>"""))
    }
}
