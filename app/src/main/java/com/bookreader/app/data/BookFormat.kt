package com.bookreader.app.data

/** Book file formats the reader opens; the shelf file's extension records which one a book is */
enum class BookFormat(val extension: String, val label: String) {
    EPUB("epub", "EPUB"),
    PDF("pdf", "PDF"),
    CBZ("cbz", "CBZ");

    companion object {
        /** Format of a shelf file; books imported before PDF / CBZ support are all .epub */
        fun of(fileName: String): BookFormat =
            entries.firstOrNull { fileName.endsWith(".${it.extension}", ignoreCase = true) } ?: EPUB
    }
}
