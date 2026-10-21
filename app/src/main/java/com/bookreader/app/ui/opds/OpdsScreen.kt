package com.bookreader.app.ui.opds

import androidx.activity.compose.BackHandler
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
import com.bookreader.app.ui.components.BackHeader
import com.bookreader.app.ui.components.BookCover
import com.bookreader.app.ui.components.COVER_RATIO
import com.bookreader.app.ui.components.CircleIconButton
import com.bookreader.app.ui.components.IconTileRow
import com.bookreader.app.ui.components.PagePadding
import com.bookreader.app.ui.theme.AppIcons

@Composable
fun OpdsScreen(
    startUrl: String,
    startTitle: String,
    onExit: () -> Unit,
    viewModel: OpdsViewModel = viewModel(factory = OpdsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val goBack = { if (!viewModel.back()) onExit() }

    LaunchedEffect(startUrl) { viewModel.start(startUrl, startTitle) }
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
                        is OpdsEntry.Epub -> EpubRow(
                            entry,
                            status = when (entry.downloadUrl) {
                                in state.downloaded -> DownloadStatus.Done
                                in state.downloading -> DownloadStatus.Running
                                else -> DownloadStatus.None
                            },
                            showDivider = !last,
                            onClick = { viewModel.download(entry) },
                        )
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
}

private enum class DownloadStatus { None, Running, Done }

@Composable
private fun EpubRow(entry: OpdsEntry.Epub, status: DownloadStatus, showDivider: Boolean, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = PagePadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(entry.title, entry.author, null, Modifier.width(48.dp).aspectRatio(COVER_RATIO))
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(entry.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    entry.author, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (status) {
                DownloadStatus.Running -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                DownloadStatus.Done -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("已在书架", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DownloadStatus.None -> Icon(AppIcons.Download, "下载", Modifier.size(24.dp))
            }
        }
        if (showDivider) {
            HorizontalDivider(
                Modifier.padding(start = PagePadding + 64.dp, end = PagePadding),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}
