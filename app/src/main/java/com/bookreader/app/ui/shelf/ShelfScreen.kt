package com.bookreader.app.ui.shelf

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bookreader.app.data.Book
import com.bookreader.app.data.opds.OpdsParser
import com.bookreader.app.reader.ReaderActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfScreen(
    onOpenOpds: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ShelfViewModel = viewModel(factory = ShelfViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<Book?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importEpub)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("书架") },
                actions = {
                    TextButton(onClick = onOpenOpds) { Text("Calibre") }
                    TextButton(onClick = { picker.launch(arrayOf(OpdsParser.EPUB_TYPE)) }) { Text("导入") }
                    TextButton(onClick = onOpenSettings) { Text("设置") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.books.isEmpty()) {
                Text("书架是空的：从 Calibre 下载，或导入本地 EPUB", Modifier.padding(16.dp))
            }
            LazyColumn {
                items(state.books, key = { it.id }) { book ->
                    ListItem(
                        headlineContent = { Text(book.title) },
                        supportingContent = { Text(book.author) },
                        modifier = Modifier.combinedClickable(
                            onClick = { viewModel.open(book) },
                            onLongClick = { pendingDelete = book },
                        ),
                    )
                    HorizontalDivider()
                }
            }
        }
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
