package com.bookreader.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {

    @Test
    fun acceptsOnlyTheEntriesABackupHolds() {
        listOf(
            "manifest.json", "stats.json", "libraries.json", "settings.json",
            "books/books.json", "books/1234.epub", "books/covers/1234.jpg", "fonts/fonts.json", "fonts/abc.ttf",
        ).forEach { assertTrue(it, isBackupEntry(it)) }
        listOf(
            "../evil", "books/../../evil", "/etc/passwd", "books/covers/../x", "books/./x", "other.json",
            "fonts/sub/x.ttf", "books/a/b", "books\\..\\x", "books/", "",
        ).forEach { assertFalse(it, isBackupEntry(it)) }
    }

    @Test
    fun settingsKeepTheirTypes() {
        val values = mapOf(
            "on" to true, "count" to 3, "time" to 5_000_000_000L, "level" to 0.6f,
            "style" to """{"fontFamily":"serif:with:colons"}""",
        )
        assertEquals(values, decodePrefs(encodePrefs(values)))
    }

    @Test
    fun unknownSettingTypesAreDropped() {
        assertEquals(emptyMap<String, Any>(), encodePrefs(mapOf("set" to setOf("a"))))
        assertEquals(mapOf("ok" to 1), decodePrefs(mapOf("ok" to "i:1", "bad" to "x:1", "broken" to "i:one", "none" to "plain")))
    }

    @Test
    fun downloadUrlFindsItsLibrary() {
        val calibre = OpdsLibrary(id = "a", name = "Calibre", url = "https://books.example.com/opds")
        val web = OpdsLibrary(id = "b", name = "Web", url = "https://books.example.com/web/opds")
        val other = OpdsLibrary(id = "c", name = "Other", url = "http://192.168.1.2:8083/opds")
        val all = listOf(calibre, web, other)
        assertEquals(web, libraryFor(all, "https://books.example.com/web/opds/download/1/epub"))
        assertEquals(calibre, libraryFor(all, "https://books.example.com/get/epub/1"))
        assertEquals(other, libraryFor(all, "http://192.168.1.2:8083/opds/download/9/epub/"))
        assertNull(libraryFor(all, "https://elsewhere.org/book.epub"))
        assertNull(libraryFor(all, "not a url"))
    }
}
