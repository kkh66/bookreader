package com.bookreader.app.ui.opds

import androidx.activity.compose.BackHandler
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bookreader.app.ui.components.Gap
import com.bookreader.app.ui.components.RemoteCover
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bookreader.app.data.opds.OpdsEntry
import com.bookreader.app.reader.ReaderActivity
import com.bookreader.app.ui.components.BackHeader
import com.bookreader.app.ui.components.COVER_RATIO
import com.bookreader.app.ui.components.CircleIconButton
import com.bookreader.app.ui.components.IconTileRow
import com.bookreader.app.ui.components.PagePadding
import com.bookreader.app.ui.components.Space
import com.bookreader.app.ui.theme.AppIcons

@Composable
fun OpdsScreen(
    libraryId: String,
    startUrl: String,
    startTitle: String,
    onExit: () -> Unit,
    viewModel: OpdsViewModel = viewModel(factory = OpdsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val goBack = { if (!viewModel.back()) onExit() }

    LaunchedEffect(libraryId, startUrl) { viewModel.start(libraryId, startUrl, startTitle) }
    LaunchedEffect(Unit) {
        viewModel.openReader.collect { context.startActivity(ReaderActivity.intent(context, it)) }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    BackHandler(onBack = goBack)

    Box(Modifier.fillMaxSize()) {
        Column {
            BackHeader(state.title.ifBlank { "Calibre 书库" }, onBack = goBack) {
                CircleIconButton(AppIcons.Refresh, "刷新", viewModel::refresh)
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(PagePadding))
            }
            LazyColumn {
                itemsIndexed(state.entries) { index, entry ->
                    val last = index == state.entries.lastIndex && state.nextUrl == null
                    when (entry) {
                        is OpdsEntry.Folder -> IconTileRow(
                            icon = AppIcons.Folder,
                            title = entry.title,
                            subtitle = entry.summary,
                            showDivider = !last,
                            onClick = { viewModel.open(entry.url, entry.title) },
                        )
                        is OpdsEntry.Epub -> {
                            val status = state.statusOf(entry)
                            EpubRow(
                                entry,
                                status = status,
                                showDivider = !last,
                                loadCover = { viewModel.cover(it, large = false) },
                                // Already on the shelf: go straight to reading instead of the details dialog
                                onClick = { if (status == DownloadStatus.Done) viewModel.read(entry) else viewModel.select(entry) },
                                onDownload = { viewModel.download(entry) },
                            )
                        }
                    }
                }
                state.nextUrl?.let { next ->
                    item {
                        IconTileRow(
                            icon = AppIcons.ArrowForward,
                            title = "下一页",
                            showDivider = false,
                            onClick = { viewModel.openNext(next) },
                        )
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    state.selected?.let { entry ->
        BookDetailDialog(
            entry,
            status = state.statusOf(entry),
            loadCover = { viewModel.cover(it, large = true) },
            onDownload = { viewModel.download(entry) },
            onRead = {
                viewModel.dismissDetail()
                viewModel.read(entry)
            },
            onDismiss = viewModel::dismissDetail,
        )
    }
}

private enum class DownloadStatus { None, Running, Done }

private fun OpdsUiState.statusOf(entry: OpdsEntry.Epub) = when (entry.downloadUrl) {
    in downloaded -> DownloadStatus.Done
    in downloading -> DownloadStatus.Running
    else -> DownloadStatus.None
}

/** Book row: tap for details (or to read once downloaded); the icon on the right downloads directly, or reads once downloaded */
@Composable
private fun EpubRow(
    entry: OpdsEntry.Epub,
    status: DownloadStatus,
    showDivider: Boolean,
    loadCover: suspend (String) -> Bitmap?,
    onClick: () -> Unit,
    onDownload: () -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = PagePadding, vertical = Space.M),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemoteCover(
                entry.title, entry.author, entry.thumbnailUrl ?: entry.coverUrl, loadCover,
                Modifier.width(48.dp).aspectRatio(COVER_RATIO),
            )
            Column(Modifier.weight(1f).padding(horizontal = Space.L)) {
                Text(entry.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    entry.author, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (status) {
                DownloadStatus.Running -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                DownloadStatus.Done -> Text(
                    "阅读",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onClick).padding(horizontal = Space.M, vertical = Space.XS),
                )
                DownloadStatus.None -> Icon(
                    AppIcons.Download, "下载",
                    Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onDownload).padding(Space.S),
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                Modifier.padding(start = PagePadding + 48.dp + Space.L, end = PagePadding),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

/** Book details dialog (like the modal on the Calibre-Web site): cover, info, tags, description, download */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookDetailDialog(
    entry: OpdsEntry.Epub,
    status: DownloadStatus,
    loadCover: suspend (String) -> Bitmap?,
    onDownload: () -> Unit,
    onRead: () -> Unit,
    onDismiss: () -> Unit,
) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.92f).widthIn(max = 560.dp).heightIn(max = maxHeight)
                .clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface)
                .padding(Space.XL),
        ) {
            Row {
                RemoteCover(
                    entry.title, entry.author, entry.coverUrl ?: entry.thumbnailUrl, loadCover,
                    Modifier.width(104.dp).aspectRatio(COVER_RATIO),
                )
                Column(Modifier.weight(1f).padding(start = Space.L)) {
                    Text(entry.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Gap(Space.XS)
                    Text(entry.author, fontSize = 14.sp, color = secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val meta = listOf(entry.publisher, entry.year).filter { it.isNotBlank() }.joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Gap(Space.S)
                        Text(meta, fontSize = 13.sp, color = secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (entry.formats.isNotEmpty()) {
                        Gap(Space.S)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Space.XS),
                            verticalArrangement = Arrangement.spacedBy(Space.XS),
                        ) {
                            entry.formats.forEach { Chip(it) }
                        }
                    }
                }
            }
            if (entry.tags.isNotEmpty()) {
                Gap(Space.L)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.S),
                    verticalArrangement = Arrangement.spacedBy(Space.S),
                ) {
                    entry.tags.forEach { Chip(it) }
                }
            }
            if (entry.summary.isNotBlank()) {
                Gap(Space.L)
                // The description can be long, so only this part scrolls and the buttons stay visible
                Text(
                    entry.summary, fontSize = 14.sp, lineHeight = 22.sp,
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                )
            }
            Gap(Space.XL)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.M)) {
                OutlinedButton(onClick = onDismiss, shape = CircleShape, modifier = Modifier.weight(1f)) { Text("关闭") }
                // Becomes 开始阅读 as soon as the download finishes, so the book can be opened right from here
                Button(
                    onClick = if (status == DownloadStatus.Done) onRead else onDownload,
                    enabled = status != DownloadStatus.Running,
                    shape = CircleShape,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        when (status) {
                            DownloadStatus.None -> "下载到书架"
                            DownloadStatus.Running -> "下载中…"
                            DownloadStatus.Done -> "开始阅读"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Space.M, vertical = Space.XS),
    )
}
