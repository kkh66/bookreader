package com.bookreader.app.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.bookreader.app.data.Bookmark
import com.bookreader.app.data.Highlight
import com.bookreader.app.ui.components.Space
import com.bookreader.app.ui.shelf.ShelfFormat
import com.bookreader.app.ui.theme.AppIcons
import kotlinx.coroutines.flow.first

/** A contents entry as the drawer lists it */
internal data class ChapterItem(val title: String, val depth: Int)

/** A bookmark and its page; the page is null in an EPUB, where the chapter progress tells where it is */
internal data class BookmarkItem(val bookmark: Bookmark, val page: Int?)

/**
 * Contents and bookmarks: a drawer sliding in from the left, about 60% of the screen wide, with the dimmed text visible on the right; tapping the text closes it
 * Hosted in a separate dialog because the reading area is Readium's View, outside the Compose layout
 * [highlights] is null for a PDF or comic, which have no notes since their text can't be selected
 * The open callbacks return whether the jump worked; the drawer closes when it did
 */
@Composable
internal fun ReaderNavigation(
    chapters: List<ChapterItem>,
    chapterPages: List<Int?>,
    currentChapter: Int,
    onOpenChapter: (Int) -> Boolean,
    bookmarks: List<BookmarkItem>,
    onOpenBookmark: (Bookmark) -> Boolean,
    onRemoveBookmark: (Bookmark) -> Unit,
    highlights: List<Highlight>?,
    onOpenHighlight: (Highlight) -> Boolean,
    onRemoveHighlight: (Highlight) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val chapterList = rememberLazyListState()
    val bookmarkList = rememberLazyListState()
    val highlightList = rememberLazyListState()
    LaunchedEffect(currentChapter) {
        if (currentChapter >= 0) chapterList.scrollToItem(currentChapter)
    }

    // Slides in on open; on close it slides out first and the dialog is dismissed when the animation ends
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    val close = { shown.targetState = false }
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(shown) {
        snapshotFlow { !shown.targetState && !shown.currentState && shown.isIdle }.first { it }
        dismiss()
    }

    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // The dialog's built-in full-screen dimming doesn't follow the drawer animation, so it's turned off in favor of our own scrim
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }
        val secondary = MaterialTheme.colorScheme.onSurfaceVariant
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // About 60% of the screen, leaving the text visible on the right; at most 420dp on tablets
            val panelWidth = minOf(maxWidth * 0.6f, 420.dp)
            AnimatedVisibility(visibleState = shown, enter = EnterTransition.None, exit = ExitTransition.None) {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.fillMaxSize()
                            .animateEnterExit(enter = fadeIn(), exit = fadeOut())
                            .background(Color.Black.copy(alpha = 0.4f))
                            .clickable(interactionSource = null, indication = null, onClick = close),
                    )
                    Surface(
                        Modifier.fillMaxHeight().width(panelWidth)
                            .animateEnterExit(enter = slideInHorizontally { -it }, exit = slideOutHorizontally { -it }),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        Column(Modifier.systemBarsPadding()) {
                            PrimaryTabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.background) {
                                listOfNotNull(
                                    AppIcons.List to "目录",
                                    AppIcons.Bookmark to "书签（${bookmarks.size}）",
                                    highlights?.let { AppIcons.Note to "笔记（${it.size}）" },
                                ).forEachIndexed { index, (icon, label) ->
                                    Tab(
                                        selected = selectedTab == index,
                                        onClick = { selectedTab = index },
                                        icon = { Icon(icon, label) },
                                        selectedContentColor = MaterialTheme.colorScheme.primary,
                                        unselectedContentColor = secondary,
                                    )
                                }
                            }
                            if (selectedTab == 0 && chapters.isEmpty()) {
                                Text("这本书没有目录", Modifier.padding(Space.L), color = secondary)
                            } else if (selectedTab == 0) {
                                LazyColumn(Modifier.weight(1f), state = chapterList) {
                                    itemsIndexed(chapters) { index, chapter ->
                                        val selected = index == currentChapter
                                        val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        Row(
                                            Modifier.fillMaxWidth()
                                                .selectable(selected = selected, onClick = { if (onOpenChapter(index)) close() })
                                                .padding(start = Space.L + Space.L * chapter.depth, end = Space.L, top = Space.L, bottom = Space.L),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                chapter.title,
                                                color = color,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f),
                                            )
                                            chapterPages.getOrNull(index)?.let {
                                                Text(
                                                    "$it", fontSize = 13.sp, color = if (selected) color else secondary,
                                                    modifier = Modifier.padding(start = Space.S),
                                                )
                                            }
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            } else if (selectedTab == 2 && highlights != null) {
                                if (highlights.isEmpty()) {
                                    Text("还没有笔记，长按正文选中文字后可以高亮或写笔记", Modifier.padding(Space.L), color = secondary)
                                } else {
                                    LazyColumn(Modifier.weight(1f), state = highlightList) {
                                        items(highlights, key = { it.id }) { highlight ->
                                            HighlightRow(
                                                highlight,
                                                onClick = { if (onOpenHighlight(highlight)) close() },
                                                onRemove = { onRemoveHighlight(highlight) },
                                            )
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        }
                                    }
                                }
                            } else if (bookmarks.isEmpty()) {
                                Text("还没有书签，点击底部的书签按钮添加", Modifier.padding(Space.L), color = secondary)
                            } else {
                                LazyColumn(Modifier.weight(1f), state = bookmarkList) {
                                    items(bookmarks, key = { it.bookmark.id }) { (bookmark, page) ->
                                        Row(
                                            Modifier.fillMaxWidth().clickable {
                                                if (onOpenBookmark(bookmark)) close()
                                            }.padding(start = Space.L, end = Space.XS, top = Space.S, bottom = Space.S),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(bookmark.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                Text(
                                                    if (page == null) "本章 ${ShelfFormat.percent(bookmark.progress)}" else "第 $page 页",
                                                    fontSize = 12.sp,
                                                    color = secondary,
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
        }
    }
}

/** A highlight in the notes tab: color bar, marked text, note, chapter and position */
@Composable
private fun HighlightRow(highlight: Highlight, onClick: () -> Unit, onRemove: () -> Unit) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable(onClick = onClick)
            .padding(start = Space.L, end = Space.XS, top = Space.M, bottom = Space.M),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(Color(highlightColor(highlight.color))))
        Column(Modifier.weight(1f).padding(start = Space.S)) {
            Text(highlight.text, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (highlight.note.isNotBlank()) {
                Text(
                    highlight.note, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
                    maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = Space.XS),
                )
            }
            Text(
                listOfNotNull(highlight.chapter?.takeIf { it.isNotBlank() }, ShelfFormat.percent(highlight.progress)).joinToString(" · "),
                fontSize = 12.sp, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Space.XS),
            )
        }
        TextButton(onClick = onRemove, modifier = Modifier.align(Alignment.CenterVertically)) { Text("删除") }
    }
}
