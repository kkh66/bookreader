package com.bookreader.app.ui.shelf

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bookreader.app.data.Book
import com.bookreader.app.data.BookFormat
import com.bookreader.app.reader.ReaderActivity
import com.bookreader.app.ui.components.BookCover
import com.bookreader.app.ui.components.COVER_RATIO
import com.bookreader.app.ui.components.CircleIconButton
import com.bookreader.app.ui.components.Gap
import com.bookreader.app.ui.components.PageHeader
import com.bookreader.app.ui.components.PagePadding
import com.bookreader.app.ui.components.ProgressLine
import com.bookreader.app.ui.components.Space
import com.bookreader.app.ui.components.SurfaceCard
import com.bookreader.app.ui.components.SearchHeader
import com.bookreader.app.ui.theme.AppIcons

@Composable
fun ShelfScreen(
    onOpenDiscover: () -> Unit,
    onImportBook: () -> Unit,
    viewModel: ShelfViewModel = viewModel(factory = ShelfViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val grid by viewModel.grid.collectAsStateWithLifecycle()
    val openingId by viewModel.openingId.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(false) }
    /** Books waiting for delete confirmation: one from a long-press, several from group batch management */
    var pendingDelete by remember { mutableStateOf<List<Book>?>(null) }
    val drag = remember { ShelfDragState() }
    /** Two books dragged together, waiting for a group name */
    var pendingGroup by remember { mutableStateOf<List<String>?>(null) }
    var openGroup by rememberSaveable { mutableStateOf<String?>(null) }
    val listCoverWidth = with(LocalDensity.current) { 64.dp.toPx() }
    val closeSearch = {
        searching = false
        viewModel.setQuery("")
    }
    BackHandler(enabled = searching, onBack = closeSearch)

    /** Release: dropped on a book → name a new group; dropped on a group → join it directly */
    fun drop(book: Book, targetKey: String) {
        when (val target = state.entries.firstOrNull { it.key == targetKey }) {
            is ShelfEntry.Single -> pendingGroup = listOf(target.item.book.id, book.id)
            is ShelfEntry.Group -> viewModel.addToGroup(listOf(book.id), target.name)
            null -> Unit
        }
    }

    LaunchedEffect(Unit) {
        viewModel.openReader.collect { context.startActivity(ReaderActivity.intent(context, it)) }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { drag.origin = it.positionInRoot() }) {
        LazyVerticalGrid(
            // Adaptive column count: 3 columns on a phone in portrait, more in landscape/on tablets
            columns = GridCells.Adaptive(if (grid) 92.dp else 340.dp),
            contentPadding = PaddingValues(start = PagePadding, end = PagePadding, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(Space.L),
            // No uniform row spacing: the gap after the title block is fixed and grid row spacing lives on GridBook itself, so the title distance matches in grid and list modes
        ) {
            fullLine {
                if (searching) {
                    SearchHeader(
                        query = state.query,
                        onQueryChange = viewModel::setQuery,
                        placeholder = "搜索书名或作者",
                        onClose = closeSearch,
                        horizontalPadding = 0.dp,
                    )
                } else PageHeader("书架", horizontalPadding = 0.dp) {
                    CircleIconButton(AppIcons.Search, "搜索") { searching = true }
                }
            }
            if (state.busy) fullLine { LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = Space.S)) }

            val current = state.current
            if (current != null && !searching) {
                fullLine {
                    Column(Modifier.padding(top = Space.XL)) {
                        Text("继续阅读", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Gap(Space.M)
                        ContinueCard(current, loading = openingId == current.book.id) { viewModel.open(current.book) }
                    }
                }
            }

            if (state.total == 0) {
                fullLine { EmptyShelf(onOpenDiscover = onOpenDiscover, onImport = onImportBook) }
            } else {
                fullLine {
                    AllBooksHeader(state.count, grid, viewModel::setGrid)
                }
                items(state.entries, key = { it.key }) { entry ->
                    val key = entry.key
                    val highlighted = drag.target == key
                    // Report bounds for drop detection; items scrolled off screen drop out
                    val placed = Modifier.onGloballyPositioned { drag.place(key, it.boundsInRoot()) }
                    DisposableEffect(key) { onDispose { drag.forget(key) } }
                    when (entry) {
                        is ShelfEntry.Single -> {
                            val item = entry.item
                            val modifier = placed
                                .alpha(if (drag.dragging?.book?.id == key) 0.3f else 1f)
                                .shelfGestures(
                                    onTap = { viewModel.open(item.book) },
                                    onLongPress = { pendingDelete = listOf(item.book) },
                                    onDragStart = { drag.start(item, it, if (grid) null else listCoverWidth) },
                                    onDrag = drag::move,
                                    onDragEnd = { drag.drop()?.let { (dropped, target) -> drop(dropped.book, target) } },
                                    onDragCancel = drag::cancel,
                                )
                            if (grid) {
                                GridBook(item, highlighted, loading = openingId == item.book.id, modifier)
                            } else {
                                ListBook(item, highlighted, loading = openingId == item.book.id, modifier) { pendingDelete = listOf(item.book) }
                            }
                        }
                        is ShelfEntry.Group -> {
                            val modifier = placed.clickable { openGroup = entry.name }
                            if (grid) GridGroup(entry, highlighted, modifier) else ListGroup(entry, highlighted, modifier)
                        }
                    }
                }
            }
        }
        DragPreview(drag)
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    // Dialogs stack in the order they appear: the group dialog below, the delete confirmation it opens on top
    val group = state.entries.firstOrNull { it is ShelfEntry.Group && it.name == openGroup } as ShelfEntry.Group?
    if (group != null) {
        GroupDialog(
            group,
            openingId = openingId,
            // Everything on the shelf that isn't already in this group can be added
            candidates = state.entries.flatMap { entry ->
                when (entry) {
                    is ShelfEntry.Single -> listOf(entry.item)
                    is ShelfEntry.Group -> if (entry.name == group.name) emptyList() else entry.items
                }
            },
            onOpen = viewModel::open,
            onAdd = { ids -> viewModel.addToGroup(ids, group.name) },
            onRemove = { books ->
                // Moving every book out makes the group disappear
                if (books.size == group.items.size) openGroup = null
                viewModel.ungroup(books.map { it.id })
            },
            onDelete = { books -> pendingDelete = books },
            onRename = { name ->
                viewModel.renameGroup(group.name, name)
                openGroup = name
            },
            onUngroup = {
                openGroup = null
                viewModel.ungroup(group.items.map { it.book.id })
            },
            onDismiss = { openGroup = null },
        )
    }

    pendingGroup?.let { ids ->
        GroupNameDialog(
            title = "新建分组",
            initial = "",
            onConfirm = { name ->
                viewModel.createGroup(ids, name)
                pendingGroup = null
            },
            onDismiss = { pendingGroup = null },
        )
    }

    pendingDelete?.let { books ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(if (books.size == 1) "删除《${books[0].title}》？" else "删除 ${books.size} 本书？") },
            text = { Text("会同时删除本地文件和阅读进度") },
            confirmButton = {
                TextButton(onClick = {
                    // Deleting every book left in the open group removes the group, so its dialog closes too
                    val ids = books.map { it.id }.toSet()
                    if (group != null && group.items.all { it.book.id in ids }) openGroup = null
                    viewModel.delete(books)
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.fullLine(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

private fun percent(progress: Double) = ShelfFormat.percent(progress)

/** Continue reading: a compact one-row card, clickable as a whole */
@Composable
private fun ContinueCard(item: ShelfItem, loading: Boolean, onContinue: () -> Unit) {
    val book = item.book
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    SurfaceCard(onClick = onContinue) {
        Row(Modifier.padding(Space.L), verticalAlignment = Alignment.CenterVertically) {
            BookCover(book.title, book.author, item.coverPath, Modifier.width(52.dp).aspectRatio(COVER_RATIO), loading)
            Column(Modifier.weight(1f).padding(horizontal = Space.M)) {
                Text(book.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Gap(Space.XS)
                Text(
                    listOfNotNull(book.author.ifBlank { null }, book.chapter).joinToString(" · "),
                    fontSize = 13.sp, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Gap(Space.M)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressLine(book.progress.toFloat(), Modifier.weight(1f))
                    Text(percent(book.progress), fontSize = 12.sp, color = secondary, modifier = Modifier.padding(start = Space.S))
                }
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.ArrowForward, "继续阅读", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
private fun AllBooksHeader(count: Int, grid: Boolean, onGridChange: (Boolean) -> Unit) {
    Row(Modifier.padding(top = Space.XL, bottom = Space.M), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("全部书籍", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("${count}本", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ModeIcon(AppIcons.Grid, "网格", selected = grid) { onGridChange(true) }
        ModeIcon(AppIcons.List, "列表", selected = !grid) { onGridChange(false) }
    }
}

@Composable
private fun ModeIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Icon(
        icon, label,
        tint = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Space.S).size(40.dp).clip(CircleShape).clickable(onClick = onClick).padding(Space.S),
    )
}

@Composable
private fun GridBook(item: ShelfItem, highlighted: Boolean, loading: Boolean, modifier: Modifier) {
    val book = item.book
    Column(Modifier.padding(bottom = Space.XL).then(modifier)) {
        BookCover(
            book.title, book.author, item.coverPath,
            Modifier.dropHighlight(highlighted).fillMaxWidth().aspectRatio(COVER_RATIO),
            loading,
        )
        Gap(Space.M)
        Text(book.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            book.author, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Gap(Space.M)
        ProgressLine(book.progress.toFloat(), Modifier.fillMaxWidth())
        Gap(Space.S)
        Text(readLabel(book), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** List row: cover + title (two lines) + author + progress bar + reading time/speed + date range + overflow menu */
@Composable
private fun ListBook(item: ShelfItem, highlighted: Boolean, loading: Boolean, modifier: Modifier, onDelete: () -> Unit) {
    val book = item.book
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    var menu by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().padding(vertical = Space.M)) {
        BookCover(
            book.title, book.author, item.coverPath,
            Modifier.dropHighlight(highlighted).width(64.dp).aspectRatio(COVER_RATIO),
            loading,
        )
        Column(Modifier.weight(1f).padding(start = Space.L)) {
            Row {
                Text(
                    book.title, fontSize = 16.sp, lineHeight = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    Icon(
                        AppIcons.MoreVert, "更多", tint = secondary,
                        modifier = Modifier.size(32.dp).clip(CircleShape).clickable { menu = true }.padding(Space.XS),
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("删除") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            Text(book.author, fontSize = 13.sp, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Gap(Space.M)
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressLine(book.progress.toFloat(), Modifier.weight(1f))
                Text(
                    ShelfFormat.percent(book.progress), fontSize = 13.sp, color = secondary,
                    modifier = Modifier.padding(start = Space.S),
                )
            }
            Gap(Space.S)
            // Reading speed counts characters, so PDF / CBZ show their format instead
            val reading = if (book.format == BookFormat.EPUB) {
                ShelfFormat.readingLine(item.readSeconds, book.progress, book.totalChars)
            } else {
                "${ShelfFormat.readingHours(item.readSeconds)} · ${book.format.label}"
            }
            Text(reading, fontSize = 12.sp, color = secondary)
            ShelfFormat.dateRange(book.firstReadAt, book.lastReadAt)?.let {
                Text(it, fontSize = 12.sp, color = secondary)
            }
        }
    }
}

internal fun readLabel(book: Book) = if (book.lastReadAt == 0L) "未读" else "已读 ${percent(book.progress)}"

@Composable
private fun EmptyShelf(onOpenDiscover: () -> Unit, onImport: () -> Unit) {
    SurfaceCard(Modifier.padding(top = Space.XL)) {
        Column(Modifier.padding(Space.XL)) {
            Text("书架还是空的", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Gap(Space.S)
            Text("从 Calibre 书库下载，或导入本地 EPUB、PDF、CBZ", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Gap(Space.L)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.M)) {
                Button(onClick = onOpenDiscover, shape = CircleShape) { Text("去 Calibre 书库") }
                OutlinedButton(onClick = onImport, shape = CircleShape) { Text("导入书籍") }
            }
        }
    }
}
