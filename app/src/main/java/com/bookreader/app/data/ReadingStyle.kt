package com.bookreader.app.data

import kotlinx.serialization.Serializable

/**
 * Text layout for EPUB pages, mapped to Readium preferences in the reader
 * Nullable values mean "as the book's own stylesheet has it"
 */
@Serializable
data class ReadingStyle(
    /** CSS font family: null = the book's fonts, "serif" / "sans-serif" = system fonts, or a [UserFont.family] */
    val fontFamily: String? = null,
    /** Scale applied to the book's font sizes, 1.0 = as published */
    val fontSize: Double = 1.0,
    /** Multiplier of the book's font weights */
    val fontWeight: Double? = null,
    val lineHeight: Double? = null,
    /** Space between paragraphs, in rem */
    val paragraphSpacing: Double? = null,
    /** Multiplier of the default page margins */
    val pageMargins: Double = 1.0,
) {
    /** Line and paragraph spacing only take effect with the book's own styles switched off */
    val overridesPublisherStyles: Boolean get() = lineHeight != null || paragraphSpacing != null

    companion object {
        const val SERIF = "serif"
        const val SANS_SERIF = "sans-serif"
    }
}
