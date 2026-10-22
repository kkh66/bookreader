package com.bookreader.app.data

import kotlinx.serialization.Serializable

/** How a highlight is drawn over the text */
@Serializable
enum class HighlightKind { Highlight, Underline, Strikethrough, Wavy }

/**
 * A marked passage, optionally with a note
 * [locator] is the Readium Locator JSON of the selection, whose quoted text lets the reader find the range again
 * after the layout changes (font, size, spacing)
 */
@Serializable
data class Highlight(
    val id: String,
    val locator: String,
    val text: String,
    val kind: HighlightKind = HighlightKind.Highlight,
    /** Index into the reader's color palette */
    val color: Int = 0,
    val note: String = "",
    /** Chapter title and whole-book progress, kept for the notes list */
    val chapter: String? = null,
    val progress: Double = 0.0,
    val createdAt: Long = 0,
)
