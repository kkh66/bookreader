package com.bookreader.app.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bookreader.app.R
import com.bookreader.app.ui.components.PagePadding
import com.bookreader.app.ui.components.SearchHeader
import com.bookreader.app.ui.components.Space
import com.bookreader.app.ui.shelf.ShelfFormat
import org.readium.r2.shared.publication.Locator

/** Stop collecting after this many hits; a one-character query can match thousands of times */
internal const val MAX_RESULTS = 1000

/**
 * Full-text search in the open book, kept for the whole reading session
 * so reopening the panel shows the last query and its results
 */
internal data class ReaderSearchState(
    val query: String = "",
    /** Query of the last search that ran; null before the first one */
    val searched: String? = null,
    val results: List<Locator> = emptyList(),
    val running: Boolean = false,
    val truncated: Boolean = false,
    val error: String? = null,
)

/** Full-screen search panel: query field on top, then a list of hits with chapter, snippet and position */
@Composable
internal fun ReaderSearchDialog(
    state: ReaderSearchState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onOpen: (Locator) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.systemBarsPadding().imePadding()) {
                SearchHeader(
                    query = state.query,
                    onQueryChange = onQueryChange,
                    placeholder = context.getString(R.string.search_book_hint),
                    onClose = onDismiss,
                    horizontalPadding = Space.L,
                    onSearch = onSearch,
                )
                Column(Modifier.padding(horizontal = PagePadding, vertical = Space.M)) {
                    if (state.running) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = Space.S))
                    val status = when {
                        state.error != null -> state.error
                        state.searched == null -> context.getString(R.string.search_book_instruction)
                        state.running -> context.getString(R.string.search_found, state.results.size)
                        state.results.isEmpty() -> context.getString(R.string.search_no_match, state.searched)
                        state.truncated -> context.getString(R.string.search_truncated, MAX_RESULTS)
                        else -> context.getString(R.string.search_result_count, state.results.size)
                    }
                    Text(
                        status.orEmpty(), fontSize = 13.sp,
                        color = if (state.error != null) MaterialTheme.colorScheme.error else secondary,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LazyColumn(Modifier.weight(1f)) {
                    items(state.results) { locator ->
                        SearchResultRow(locator, onClick = { onOpen(locator) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(locator: Locator, onClick: () -> Unit) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val mark = MaterialTheme.colorScheme.primary
    val text = locator.text
    // Snippets come with up to 200 characters on each side; keep the end of the text before and the start after
    val snippet = buildAnnotatedString {
        append(text.before.orEmpty().oneLine().takeLast(30).trimStart())
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = mark, background = mark.copy(alpha = 0.12f))) {
            append(text.highlight.orEmpty().oneLine())
        }
        append(text.after.orEmpty().oneLine().take(80))
    }
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = PagePadding, vertical = Space.M)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                locator.title.orEmpty(), fontSize = 12.sp, color = secondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            locator.locations.totalProgression?.let {
                Text(ShelfFormat.percent(it), fontSize = 12.sp, color = secondary, modifier = Modifier.padding(start = Space.S))
            }
        }
        Text(snippet, fontSize = 15.sp, lineHeight = 22.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

/** Collapses line breaks and runs of spaces so a snippet reads as one line */
private fun String.oneLine() = replace(Regex("\\s+"), " ")

/** Search hit highlight on the page: amber, readable on both light and dark pages */
internal val SEARCH_HIGHLIGHT = Color(0xFFFFB300)
