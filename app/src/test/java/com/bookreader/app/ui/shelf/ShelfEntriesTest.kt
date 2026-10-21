package com.bookreader.app.ui.shelf

import com.bookreader.app.data.Book
import org.junit.Assert.assertEquals
import org.junit.Test

class ShelfEntriesTest {

    private fun item(id: String, group: String? = null) =
        ShelfItem(Book(id = id, title = id, author = "", fileName = "$id.epub", addedAt = 0, group = group), null)

    @Test
    fun ungroupedBooksStayInOrder() {
        val entries = shelfEntries(listOf(item("a"), item("b"), item("c")))
        assertEquals(listOf("a", "b", "c"), entries.map { it.key })
    }

    @Test
    fun groupTakesPlaceOfItsFirstBook() {
        val entries = shelfEntries(listOf(item("a"), item("b", "gg"), item("c"), item("d", "gg")))
        assertEquals(listOf("a", "group:gg", "c"), entries.map { it.key })
        val group = entries[1] as ShelfEntry.Group
        assertEquals("gg", group.name)
        assertEquals(listOf("b", "d"), group.items.map { it.book.id })
    }

    @Test
    fun separateGroupsKeepTheirOwnBooks() {
        val entries = shelfEntries(listOf(item("a", "x"), item("b", "y"), item("c", "x")))
        assertEquals(listOf("group:x", "group:y"), entries.map { it.key })
        assertEquals(listOf("a", "c"), (entries[0] as ShelfEntry.Group).items.map { it.book.id })
        assertEquals(listOf("b"), (entries[1] as ShelfEntry.Group).items.map { it.book.id })
    }
}
