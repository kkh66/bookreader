package com.bookreader.app.reader

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookreader.app.data.Bookmark
import com.bookreader.app.ui.components.BackHeader
import com.bookreader.app.ui.components.Space
import com.bookreader.app.ui.shelf.ShelfFormat
import com.bookreader.app.ui.theme.AppIcons
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

@OptIn(ExperimentalReadiumApi::class)
@Composable
internal fun ReaderNavigation(
    publication: Publication,
    navigator: EpubNavigatorFragment,
    bookmarks: List<Bookmark>,
    onOpenBookmark: (Bookmark) -> Boolean,
    onRemoveBookmark: (Bookmark) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val chapters = remember(publication) {
        flattenContents(publication.tableOfContents.ifEmpty { publication.readingOrder })
    }
    val locator by navigator.currentLocator.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var currentChapter by remember { mutableIntStateOf(-1) }
    val chapterList = rememberLazyListState()
    val bookmarkList = rememberLazyListState()
    LaunchedEffect(locator) {
        currentChapter = currentChapterIndex(publication, navigator, chapters, locator)
    }
    LaunchedEffect(currentChapter) {
        if (currentChapter >= 0) chapterList.scrollToItem(currentChapter)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.systemBarsPadding()) {
                BackHeader("目录与书签", onBack = onDismiss)
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("目录") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("书签（${bookmarks.size}）") })
                }
                if (selectedTab == 0) {
                    LazyColumn(Modifier.weight(1f), state = chapterList) {
                        itemsIndexed(chapters) { index, (link, depth) ->
                            val selected = index == currentChapter
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                                    .selectable(selected = selected, onClick = {
                                        if (navigator.go(link)) onDismiss()
                                        else Toast.makeText(context, "无法跳转到此章节", Toast.LENGTH_SHORT).show()
                                    })
                                    .padding(start = Space.XL + Space.L * depth, end = Space.XL, top = Space.L, bottom = Space.L),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    link.title?.takeIf { it.isNotBlank() } ?: link.url().toString().substringAfterLast('/'),
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f),
                                )
                                if (selected) Icon(AppIcons.Check, "当前章节", Modifier.padding(start = Space.S))
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                } else if (bookmarks.isEmpty()) {
                    Text("还没有书签，点击底部的书签按钮添加", Modifier.padding(Space.XL))
                } else {
                    LazyColumn(Modifier.weight(1f), state = bookmarkList) {
                        items(bookmarks, key = { it.id }) { bookmark ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    if (onOpenBookmark(bookmark)) onDismiss()
                                }.padding(horizontal = Space.XL, vertical = Space.S),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(bookmark.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "本章 ${ShelfFormat.percent(bookmark.progress)}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { onRemoveBookmark(bookmark) }) { Text("删除") }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

private fun flattenContents(links: List<Link>, depth: Int = 0): List<Pair<Link, Int>> =
    links.flatMap { listOf(it to depth) + flattenContents(it.children, depth + 1) }

@OptIn(ExperimentalReadiumApi::class)
private suspend fun currentChapterIndex(
    publication: Publication,
    navigator: EpubNavigatorFragment,
    chapters: List<Pair<Link, Int>>,
    locator: Locator,
): Int {
    val href = locator.href.removeFragment()
    val matches = chapters.indices.filter { chapters[it].first.url().removeFragment().isEquivalent(href) }
    val resourceIndex = publication.readingOrder.indexOfFirst { it.url().isEquivalent(href) }
    val fallback = matches.firstOrNull() ?: chapters.indices.lastOrNull { index ->
        publication.readingOrder.indexOfFirst { it.url().isEquivalent(chapters[index].first.url().removeFragment()) } in 0..resourceIndex
    } ?: -1
    if (matches.size < 2) return fallback

    // One XHTML file can contain several chapters, so compare anchor order with the first visible element.
    return try {
        val selector = navigator.firstVisibleElementLocator()?.locations?.get("cssSelector") as? String
            ?: return fallback
        val anchors = JSONArray(matches.map { index ->
            JSONObject().put("index", index).put("id", Uri.decode(chapters[index].first.url().toString().substringAfter('#', "")))
        })
        // When a page also holds the end of the previous chapter, prefer the chapter heading that is visible.
        val result = navigator.evaluateJavascript("""
            (() => {
                const first = document.querySelector(${JSONObject.quote(selector)});
                if (!first) return -1;
                let current = -1;
                let visible = -1;
                let visibleHeading = null;
                for (const entry of $anchors) {
                    const anchor = entry.id ? document.getElementById(entry.id) || document.getElementsByName(entry.id)[0] : document.body;
                    if (!anchor) continue;
                    if (anchor === first || (anchor.compareDocumentPosition(first) & Node.DOCUMENT_POSITION_FOLLOWING)) current = entry.index;
                    const heading = anchor.matches('h1,h2,h3,h4,h5,h6') ? anchor : anchor.querySelector('h1,h2,h3,h4,h5,h6') || anchor;
                    const rect = heading.getBoundingClientRect();
                    if (rect.right > 0 && rect.left < innerWidth && rect.bottom > 0 && rect.top < innerHeight && (visible < 0 || heading === visibleHeading)) {
                        visible = entry.index;
                        visibleHeading = heading;
                    }
                }
                return visible >= 0 ? visible : current;
            })()
        """.trimIndent())?.toIntOrNull()
        result?.takeIf { it in matches } ?: fallback
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        fallback
    }
}
