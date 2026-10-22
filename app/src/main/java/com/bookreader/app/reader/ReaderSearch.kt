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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bookreader.app.ui.components.PagePadding
import com.bookreader.app.ui.components.SearchHeader
import com.bookreader.app.ui.components.Space
import com.bookreader.app.ui.shelf.ShelfFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.util.getOrElse

/** Stop collecting after this many hits; a one-character query can match thousands of times */
private const val MAX_RESULTS = 1000

/**
 * Full-text search in the open book, kept for the whole reading session
 * so reopening the panel shows the last query and its results
 */
@Stable
internal class ReaderSearchState {
    var query by mutableStateOf("")
    /** Query of the last search that ran; null before the first one */
    var searched by mutableStateOf<String?>(null)
        private set
    var results by mutableStateOf(emptyList<Locator>())
        private set
    var running by mutableStateOf(false)
        private set
    var truncated by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var job: Job? = null

    /** Searches resource by resource, showing hits as they come in; a new search cancels the previous one */
    @OptIn(ExperimentalReadiumApi::class)
    fun run(scope: CoroutineScope, publication: Publication) {
        val text = query.trim().ifEmpty { return }
        job?.cancel()
        searched = text
        results = emptyList()
        truncated = false
        error = null
        running = true
        job = scope.launch {
            val iterator = publication.search(text)
            if (iterator == null) {
                error = "这本书不支持搜索"
                running = false
                return@launch
            }
            try {
                while (true) {
                    val page = iterator.next().getOrElse {
                        error = "搜索失败：${it.message}"
                        null
                    } ?: break
                    results = results + page.locators
                    if (results.size >= MAX_RESULTS) {
                        results = results.take(MAX_RESULTS)
                        truncated = true
                        break
                    }
                }
            } finally {
                iterator.close()
                running = false
            }
        }
    }
}

/** Full-screen search panel: query field on top, then a list of hits with chapter, snippet and position */
@Composable
internal fun ReaderSearchDialog(
    state: ReaderSearchState,
    onSearch: () -> Unit,
    onOpen: (Locator) -> Unit,
    onDismiss: () -> Unit,
) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.systemBarsPadding().imePadding()) {
                SearchHeader(
                    query = state.query,
                    onQueryChange = { state.query = it },
                    placeholder = "搜索书中内容",
                    onClose = onDismiss,
                    horizontalPadding = Space.L,
                    onSearch = onSearch,
                )
                Column(Modifier.padding(horizontal = PagePadding, vertical = Space.M)) {
                    if (state.running) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = Space.S))
                    val status = when {
                        state.error != null -> state.error
                        state.searched == null -> "输入关键字后按键盘上的搜索"
                        state.running -> "已找到 ${state.results.size} 条…"
                        state.results.isEmpty() -> "没有找到「${state.searched}」"
                        state.truncated -> "结果太多，只显示前 $MAX_RESULTS 条"
                        else -> "共 ${state.results.size} 条"
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
