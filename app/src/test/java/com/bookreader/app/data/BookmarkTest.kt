package com.bookreader.app.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkTest {
    @Test
    fun oldBooksWithoutBookmarksStillLoad() {
        val book = Json.decodeFromString<Book>(
            """{"id":"book-1","title":"测试","author":"作者","fileName":"book.epub","addedAt":0}"""
        )
        assertTrue(book.bookmarks.isEmpty())
    }

    @Test
    fun bookmarkLocatorSurvivesStorageRoundTrip() {
        val bookmark = Bookmark(
            id = bookmarkId("chapter.xhtml", 7, 0.25),
            locator = """{"href":"chapter.xhtml","type":"application/xhtml+xml","locations":{"position":7,"totalProgression":0.25}}""",
            title = "第一章",
            progress = 0.25,
        )
        val book = Book("book-1", "测试", "作者", "book.epub", 0, bookmarks = listOf(bookmark))
        assertEquals(book, Json.decodeFromString<Book>(Json.encodeToString(book)))
    }

    @Test
    fun bookmarkIdentityDistinguishesPagesInTheSamePosition() {
        assertNotEquals(bookmarkId("chapter.xhtml", 7, 0.25), bookmarkId("chapter.xhtml", 7, 0.3))
        assertNotEquals(bookmarkId("chapter.xhtml", 7, 0.25), bookmarkId("chapter.xhtml", 8, 0.25))
        assertNotEquals(bookmarkId("chapter.xhtml", 7, 0.25), bookmarkId("other.xhtml", 7, 0.25))
    }

    @Test
    fun tinyProgressionRoundingDifferencesDoNotDuplicateBookmarks() {
        assertEquals(bookmarkId("chapter.xhtml", 7, 0.25), bookmarkId("chapter.xhtml", 7, 0.25000000001))
    }

    @Test
    fun bookmarkIdentityFallsBackToResourceProgression() {
        assertNotEquals(bookmarkId("chapter.xhtml", null, 0.25), bookmarkId("chapter.xhtml", null, 0.5))
        assertEquals(bookmarkId("chapter.xhtml", null, null), bookmarkId("chapter.xhtml", null, 0.0))
    }
}
