package com.bookreader.app.ui.opds

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bookreader.app.data.opds.OpdsEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpdsScreen(
    onExit: () -> Unit,
    viewModel: OpdsViewModel = viewModel(factory = OpdsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val goBack = { if (!viewModel.back()) onExit() }

    LaunchedEffect(Unit) { viewModel.start() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    BackHandler(onBack = goBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title.ifBlank { "Calibre 书库" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { TextButton(onClick = goBack) { Text("返回") } },
                actions = { TextButton(onClick = viewModel::refresh) { Text("刷新") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
            LazyColumn {
                items(state.entries) { entry ->
                    when (entry) {
                        is OpdsEntry.Folder -> ListItem(
                            headlineContent = { Text(entry.title) },
                            trailingContent = { Text("›") },
                            modifier = Modifier.clickable { viewModel.open(entry.url) },
                        )
                        is OpdsEntry.Epub -> ListItem(
                            headlineContent = { Text(entry.title) },
                            supportingContent = { Text(entry.author) },
                            trailingContent = {
                                Text(
                                    when (entry.downloadUrl) {
                                        in state.downloaded -> "已在书架"
                                        in state.downloading -> "下载中…"
                                        else -> "下载"
                                    }
                                )
                            },
                            modifier = Modifier.clickable { viewModel.download(entry) },
                        )
                    }
                    HorizontalDivider()
                }
                state.nextUrl?.let { next ->
                    item {
                        ListItem(
                            headlineContent = { Text("下一页") },
                            modifier = Modifier.clickable { viewModel.open(next) },
                        )
                    }
                }
            }
        }
    }
}
