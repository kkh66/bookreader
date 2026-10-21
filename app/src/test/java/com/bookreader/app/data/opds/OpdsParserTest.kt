package com.bookreader.app.data.opds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Samples follow the structure of Calibre-Web's cps/templates/index.xml and feed.xml
 */
class OpdsParserTest {

    private val base = "http://192.168.1.100:8083/opds"

    private val rootFeed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <id>urn:uuid:2853dacf-ed79-42f5-8e8a-a7bb3d1ae6a2</id>
          <updated>2026-10-21T07:00:00+00:00</updated>
          <link rel="self" href="/opds" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
          <link rel="search" href="/opds/osd" type="application/opensearchdescription+xml"/>
          <title>Calibre-Web</title>
          <entry>
            <title>Alphabetical Books</title>
            <link href="/opds/books" type="application/atom+xml;profile=opds-catalog"/>
            <id>/opds/books</id>
            <content type="text">Books sorted alphabetically</content>
          </entry>
          <entry>
            <title>Recently added Books</title>
            <link href="/opds/new" type="application/atom+xml;profile=opds-catalog"/>
            <id>/opds/new</id>
          </entry>
        </feed>
    """.trimIndent()

    private val bookFeed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom" xmlns:dcterms="http://purl.org/dc/terms/">
          <link rel="up" href="/opds" type="application/atom+xml;profile=opds-catalog;type=feed;kind=navigation"/>
          <link rel="next" title="Next" href="/opds/new?offset=30" type="application/atom+xml;profile=opds-catalog;type=feed;kind=navigation"/>
          <title>Recently added Books</title>
          <entry>
            <title>三体</title>
            <id>urn:uuid:0001</id>
            <author><name>刘慈欣</name></author>
            <dcterms:language>zho</dcterms:language>
            <content type="xhtml"><div xmlns="http://www.w3.org/1999/xhtml"><p>简介</p></div></content>
            <link type="image/jpeg" href="/opds/cover/1" rel="http://opds-spec.org/image/thumbnail"/>
            <link rel="http://opds-spec.org/acquisition" href="/opds/download/1/pdf/" length="2048" title="PDF" type="application/pdf"/>
            <link rel="http://opds-spec.org/acquisition" href="/opds/download/1/epub/" length="1024" title="EPUB" type="application/epub+zip"/>
          </entry>
          <entry>
            <title>Only PDF</title>
            <link rel="http://opds-spec.org/acquisition" href="/opds/download/2/pdf/" type="application/pdf"/>
            <link rel="alternate" href="/opds/book/2" type="application/atom+xml;profile=opds-catalog"/>
          </entry>
          <entry>
            <title>Two Authors</title>
            <author><name>A</name></author>
            <author><name>B</name></author>
            <link rel="http://opds-spec.org/acquisition/open-access" href="http://other.example/3.epub" type="application/epub+zip; charset=binary"/>
          </entry>
        </feed>
    """.trimIndent()

    @Test
    fun navigationEntriesBecomeFolders() {
        val page = OpdsParser.parse(rootFeed, base)
        assertEquals("Calibre-Web", page.title)
        assertEquals(
            listOf(
                OpdsEntry.Folder("Alphabetical Books", "http://192.168.1.100:8083/opds/books"),
                OpdsEntry.Folder("Recently added Books", "http://192.168.1.100:8083/opds/new"),
            ),
            page.entries,
        )
        assertNull(page.nextUrl)
    }

    @Test
    fun epubAcquisitionBecomesBook() {
        val book = OpdsParser.parse(bookFeed, "$base/new").entries.first() as OpdsEntry.Epub
        assertEquals("三体", book.title)
        assertEquals("刘慈欣", book.author)
        assertEquals("http://192.168.1.100:8083/opds/download/1/epub/", book.downloadUrl)
        assertEquals(1024L, book.size)
    }

    @Test
    fun bookWithoutEpubIsSkippedNotTreatedAsFolder() {
        val titles = OpdsParser.parse(bookFeed, "$base/new").entries.map { it.title }
        assertTrue("Only PDF" !in titles)
    }

    @Test
    fun acquisitionSubRelAndMimeParamsAreAccepted() {
        val book = OpdsParser.parse(bookFeed, "$base/new").entries.last() as OpdsEntry.Epub
        assertEquals("A, B", book.author)
        assertEquals("http://other.example/3.epub", book.downloadUrl)
        assertEquals(0L, book.size)
    }

    @Test
    fun nextLinkIsResolved() {
        assertEquals(
            "http://192.168.1.100:8083/opds/new?offset=30",
            OpdsParser.parse(bookFeed, "$base/new").nextUrl,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonFeedThrows() {
        OpdsParser.parse("<html><body>Unauthorized</body></html>", base)
    }
}
