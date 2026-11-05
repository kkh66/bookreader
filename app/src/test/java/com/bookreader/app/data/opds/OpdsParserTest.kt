package com.bookreader.app.data.opds

import com.bookreader.app.data.BookFormat
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
          <link type="application/atom+xml" rel="search" title="Search" href="/opds/search/{searchTerms}" />
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
            <publisher><name>重庆出版社</name></publisher>
            <published>2008-01-01T00:00:00+00:00</published>
            <dcterms:language>zho</dcterms:language>
            <category scheme="http://www.bisg.org/standards/bisac_subject/index.html" term="科幻" label="科幻"/>
            <category scheme="http://www.bisg.org/standards/bisac_subject/index.html" term="小说" label="小说"/>
            <content type="xhtml"><div xmlns="http://www.w3.org/1999/xhtml">RATING: ★★★★<br/>TAGS: 科幻, 小说<br/><p>&lt;p&gt;文化大革命如火如荼。&lt;/p&gt;&lt;p&gt;第二段。&lt;/p&gt;</p></div></content>
            <link type="image/jpeg" href="/opds/cover/1" rel="http://opds-spec.org/image"/>
            <link type="image/jpeg" href="/opds/cover/1?size=small" rel="http://opds-spec.org/image/thumbnail"/>
            <link rel="http://opds-spec.org/acquisition" href="/opds/download/1/pdf/" length="2048" title="PDF" type="application/pdf"/>
            <link rel="http://opds-spec.org/acquisition" href="/opds/download/1/epub/" length="1024" title="EPUB" type="application/epub+zip"/>
          </entry>
          <entry>
            <title>Only PDF</title>
            <link rel="http://opds-spec.org/acquisition" href="/opds/download/2/pdf/" type="application/pdf"/>
            <link rel="alternate" href="/opds/book/2" type="application/atom+xml;profile=opds-catalog"/>
          </entry>
          <entry>
            <title>Comic</title>
            <link rel="http://opds-spec.org/acquisition" href="/opds/download/4/pdf/" type="application/pdf"/>
            <link rel="http://opds-spec.org/acquisition" href="/opds/download/4/cbz/" type="application/x-cbz"/>
          </entry>
          <entry>
            <title>Only MOBI</title>
            <link rel="http://opds-spec.org/acquisition" href="/opds/download/5/mobi/" type="application/x-mobipocket-ebook"/>
            <link rel="alternate" href="/opds/book/5" type="application/atom+xml;profile=opds-catalog"/>
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
                OpdsEntry.Folder(
                    "Alphabetical Books",
                    "http://192.168.1.100:8083/opds/books",
                    "Books sorted alphabetically",
                ),
                OpdsEntry.Folder("Recently added Books", "http://192.168.1.100:8083/opds/new"),
            ),
            page.entries,
        )
        assertNull(page.nextUrl)
        assertEquals("http://192.168.1.100:8083/opds/search/{searchTerms}", page.searchTemplate)
    }

    @Test
    fun feedWithoutAtomSearchHasNoTemplate() {
        assertNull(OpdsParser.parse(bookFeed, "$base/new").searchTemplate)
    }

    @Test
    fun epubAcquisitionBecomesBook() {
        val book = OpdsParser.parse(bookFeed, "$base/new").entries.first() as OpdsEntry.Book
        assertEquals("三体", book.title)
        assertEquals("刘慈欣", book.author)
        assertEquals("http://192.168.1.100:8083/opds/download/1/epub/", book.downloadUrl)
        assertEquals(BookFormat.EPUB, book.format)
        assertEquals(1024L, book.size)
    }

    @Test
    fun bookDetailsForModal() {
        val book = OpdsParser.parse(bookFeed, "$base/new").entries.first() as OpdsEntry.Book
        assertEquals("文化大革命如火如荼。\n\n第二段。", book.summary)
        assertEquals("http://192.168.1.100:8083/opds/cover/1", book.coverUrl)
        assertEquals("http://192.168.1.100:8083/opds/cover/1?size=small", book.thumbnailUrl)
        assertEquals(listOf("科幻", "小说"), book.tags)
        assertEquals("重庆出版社", book.publisher)
        assertEquals("2008", book.year)
        assertEquals(listOf("PDF", "EPUB"), book.formats)
    }

    @Test
    fun bookWithoutDetailsHasEmptyFields() {
        val book = OpdsParser.parse(bookFeed, "$base/new").entries.last() as OpdsEntry.Book
        assertEquals("", book.summary)
        assertNull(book.coverUrl)
        assertEquals(emptyList<String>(), book.tags)
        assertEquals(listOf("EPUB"), book.formats)
    }

    @Test
    fun bookWithoutEpubDownloadsAsPdf() {
        val book = OpdsParser.parse(bookFeed, "$base/new").entries.first { it.title == "Only PDF" } as OpdsEntry.Book
        assertEquals("http://192.168.1.100:8083/opds/download/2/pdf/", book.downloadUrl)
        assertEquals(BookFormat.PDF, book.format)
    }

    @Test
    fun comicPrefersCbzOverPdf() {
        val book = OpdsParser.parse(bookFeed, "$base/new").entries.first { it.title == "Comic" } as OpdsEntry.Book
        assertEquals("http://192.168.1.100:8083/opds/download/4/cbz/", book.downloadUrl)
        assertEquals(BookFormat.CBZ, book.format)
        assertEquals(listOf("PDF", "CBZ"), book.formats)
    }

    @Test
    fun bookWithoutSupportedFormatIsSkippedNotTreatedAsFolder() {
        val titles = OpdsParser.parse(bookFeed, "$base/new").entries.map { it.title }
        assertTrue("Only MOBI" !in titles)
    }

    @Test
    fun acquisitionSubRelAndMimeParamsAreAccepted() {
        val book = OpdsParser.parse(bookFeed, "$base/new").entries.last() as OpdsEntry.Book
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

    @Test
    fun pagingLinksAndPageNumberOfAMiddlePage() {
        val feed = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <link rel="first" href="/opds/new" type="application/atom+xml;profile=opds-catalog"/>
              <link rel="previous" href="/opds/new?offset=30" type="application/atom+xml;profile=opds-catalog"/>
              <link rel="next" href="/opds/new?offset=90" type="application/atom+xml;profile=opds-catalog"/>
              <link rel="last" href="/opds/new?offset=9990" type="application/atom+xml;profile=opds-catalog"/>
              <title>Recently added Books</title>
            </feed>
        """.trimIndent()
        val page = OpdsParser.parse(feed, "$base/new?offset=60")
        assertEquals("$base/new", page.firstUrl)
        assertEquals("$base/new?offset=30", page.previousUrl)
        assertEquals("$base/new?offset=90", page.nextUrl)
        assertEquals("$base/new?offset=9990", page.lastUrl)
        assertEquals(3, page.page)
    }

    @Test
    fun pageNumberFromOffsets() {
        // First page: no offset in its own URL
        assertEquals(1, OpdsParser.pageNumber("$base/new", "$base/new?offset=30", null))
        // Last page: only a previous page to measure against
        assertEquals(334, OpdsParser.pageNumber("$base/new?offset=9990", null, "$base/new?offset=9960"))
        // Paged some other way: no number
        assertNull(OpdsParser.pageNumber("$base/new?page=2", "$base/new?page=3", "$base/new?page=1"))
        // A single page
        assertNull(OpdsParser.pageNumber("$base/new", null, null))
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonFeedThrows() {
        OpdsParser.parse("<html><body>Unauthorized</body></html>", base)
    }
}
