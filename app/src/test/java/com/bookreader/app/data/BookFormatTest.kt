package com.bookreader.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BookFormatTest {

    @Test
    fun formatComesFromTheShelfFileExtension() {
        assertEquals(BookFormat.EPUB, BookFormat.of("1a2b.epub"))
        assertEquals(BookFormat.PDF, BookFormat.of("1a2b.pdf"))
        assertEquals(BookFormat.CBZ, BookFormat.of("1a2b.CBZ"))
    }

    @Test
    fun unknownExtensionIsTreatedAsEpub() {
        assertEquals(BookFormat.EPUB, BookFormat.of("1a2b"))
    }
}
