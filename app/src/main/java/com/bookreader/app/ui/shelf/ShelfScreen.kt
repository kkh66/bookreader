package com.bookreader.app.ui.shelf

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bookreader.app.data.Book
import com.bookreader.app.data.opds.OpdsParser
import com.bookreader.app.reader.ReaderActivity
import com.bookreader.app.ui.components.BookCover
import com.bookreader.app.ui.components.COVER_RATIO
import com.bookreader.app.ui.components.CircleIconButton
import com.bookreader.app.ui.components.Gap
import com.bookreader.app.ui.components.PageHeader
import com.bookreader.app.ui.components.PagePadding
import com.bookreader.app.ui.components.ProgressLine
import com.bookreader.app.ui.components.SurfaceCard
import com.bookreader.app.ui.theme.AppIcons
import kotlin.math.roundToInt

@Composable
fun ShelfScreen(
    onOpenDiscover: () -> Unit,
    viewModel: ShelfViewModel = viewModel(factory = ShelfViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var grid by rememberSaveable { mutableStateOf(true) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Book?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importEpub)
    }
    val importEpub = { picker.launch(arrayOf(OpdsParser.EPUB_TYPE)) }

    LaunchedEffect(Unit) {
        viewModel.openReader.collect { context.startActivity(ReaderActivity.intent(context, it)) }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (grid) 3 else 1),
            contentPadding = PaddingValues(start = PagePadding, end = PagePadding, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(if (grid) 28.dp else 0.dp),
        ) {
            fullLine {
                PageHeader("书架", "阅读，让时间有了形状", horizontalPadding = 0.dp) {
                    CircleIconButton(AppIcons.Search, "搜索") {
                        searching = !searching
                        if (!searching) viewModel.setQuery("")
                    }
                    Box {
                        CircleIconButton(AppIcons.More, "更多") { menu = true }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("导入本地 EPUB") },
                                leadingIcon = { Icon(AppIcons.Import, null) },
                                onClick = { menu = false; importEpub() },
                            )
                            DropdownMenuItem(
                                text = { Text("Calibre 书库") },
                                leadingIcon = { Icon(AppIcons.Discover, null) },
                                onClick = { menu = false; onOpenDiscover() },
                            )
                        }
                    }
                }
            }
            if (searching) {
                fullLine {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        placeholder = { Text("搜索书名或作者") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
            if (state.busy) fullLine { LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp)) }

            val current = state.current
            if (current != null && state.query.isBlank()) {
                fullLine {
                    Column(Modifier.padding(top = 24.dp)) {
                        Text("继续阅读", fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Gap(14)
                        ContinueCard(current) { viewModel.open(current.book) }
                    }
                }
            }

            if (state.total == 0) {
                fullLine { EmptyShelf(onOpenDiscover = onOpenDiscover, onImport = importEpub) }
            } else {
                fullLine {
                    AllBooksHeader(state.items.size, grid) { grid = it }
                }
                items(state.items, key = { it.book.id }) { item ->
                    val modifier = Modifier.combinedClickable(
                        onClick = { viewModel.open(item.book) },
                        onLongClick = { pendingDelete = item.book },
                    )
                    if (grid) GridBook(item, modifier) else ListBook(item, modifier)
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除《${book.title}》？") },
            text = { Text("会同时删除本地文件和阅读进度") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(book)
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

private fun percent(progress: Double) = "${(progress * 100).roundToInt()}%"

@Composable
private fun ContinueCard(item: ShelfItem, onContinue: () -> Unit) {
    val book = item.book
    SurfaceCard {
        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(book.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Gap(4)
                Text(book.author, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Gap(18)
                Text(
                    book.chapter ?: "已读 ${percent(book.progress)}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Gap(10)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressLine(book.progress.toFloat(), Modifier.weight(1f))
                    Text(
                        percent(book.progress),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                Gap(20)
                Button(
                    onClick = onContinue,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Text("继续阅读", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Icon(AppIcons.ArrowForward, null, Modifier.padding(start = 10.dp).size(18.dp))
                }
            }
            BookCover(
                book.title, book.author, item.coverPath,
                Modifier.padding(start = 20.dp).width(104.dp).aspectRatio(COVER_RATIO),
            )
        }
    }
}

@Composable
private fun AllBooksHeader(count: Int, grid: Boolean, onGridChange: (Boolean) -> Unit) {
    Row(Modifier.padding(top = 32.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
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
        modifier = Modifier.padding(start = 8.dp).size(40.dp).clip(CircleShape).clickable(onClick = onClick).padding(8.dp),
    )
}

@Composable
private fun GridBook(item: ShelfItem, modifier: Modifier) {
    val book = item.book
    Column(modifier) {
        BookCover(book.title, book.author, item.coverPath, Modifier.fillMaxWidth().aspectRatio(COVER_RATIO))
        Gap(10)
        Text(book.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            book.author, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Gap(10)
        ProgressLine(book.progress.toFloat(), Modifier.fillMaxWidth())
        Gap(6)
        Text(readLabel(book), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ListBook(item: ShelfItem, modifier: Modifier) {
    val book = item.book
    Column(modifier) {
        Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(book.title, book.author, item.coverPath, Modifier.width(56.dp).aspectRatio(COVER_RATIO))
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(book.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    book.author, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Gap(10)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressLine(book.progress.toFloat(), Modifier.weight(1f))
                    Text(
                        readLabel(book), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

private fun readLabel(book: Book) = if (book.lastReadAt == 0L) "未读" else "已读 ${percent(book.progress)}"

@Composable
private fun EmptyShelf(onOpenDiscover: () -> Unit, onImport: () -> Unit) {
    SurfaceCard(Modifier.padding(top = 24.dp)) {
        Column(Modifier.padding(24.dp)) {
            Text("书架还是空的", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Gap(6)
            Text("从 Calibre 书库下载，或导入本地 EPUB", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Gap(20)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onOpenDiscover, shape = CircleShape) { Text("去 Calibre 书库") }
                OutlinedButton(onClick = onImport, shape = CircleShape) { Text("导入 EPUB") }
            }
        }
    }
}
