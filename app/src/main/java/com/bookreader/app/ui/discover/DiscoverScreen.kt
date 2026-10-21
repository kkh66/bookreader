package com.bookreader.app.ui.discover

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bookreader.app.ui.components.CircleIconButton
import com.bookreader.app.ui.components.Gap
import com.bookreader.app.ui.components.IconTileRow
import com.bookreader.app.ui.components.PageHeader
import com.bookreader.app.ui.components.PagePadding
import com.bookreader.app.ui.components.SearchHeader
import com.bookreader.app.ui.components.SectionTitle
import com.bookreader.app.ui.components.SurfaceCard
import com.bookreader.app.ui.theme.AppIcons

@Composable
fun DiscoverScreen(
    onBrowse: (url: String, title: String) -> Unit,
    onOpenServer: () -> Unit,
    viewModel: DiscoverViewModel = viewModel(factory = DiscoverViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val submit = {
        viewModel.searchUrl(query)?.takeIf { query.isNotBlank() }?.let { onBrowse(it, "搜索：${query.trim()}") }
    }

    val closeSearch = {
        searching = false
        query = ""
    }
    BackHandler(enabled = searching, onBack = closeSearch)

    LaunchedEffect(Unit) { viewModel.start() }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            if (searching) {
                SearchHeader(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "搜索 Calibre 书名、作者",
                    onClose = closeSearch,
                    onSearch = { submit() },
                )
            } else PageHeader("发现", "下一本好书，在这里遇见") {
                if (state.searchTemplate != null) CircleIconButton(AppIcons.Search, "搜索") { searching = true }
            }
        }
        item {
            CardRow(
                icon = AppIcons.Discover,
                title = "从 Calibre 书库开始",
                subtitle = "浏览分类与榜单，发现更多故事",
                modifier = Modifier.padding(horizontal = PagePadding, vertical = 16.dp),
            ) {
                if (state.rootUrl.isNotBlank()) onBrowse(state.rootUrl, state.libraryName.ifBlank { "Calibre 书库" })
            }
        }
        item {
            SectionTitle(
                "分类与榜单",
                Modifier.padding(start = PagePadding, end = PagePadding, top = 16.dp, bottom = 12.dp),
                trailing = state.libraryName.takeIf { it.isNotBlank() },
                large = true,
            )
        }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = PagePadding)) }
        state.error?.let { error ->
            item { ErrorCard(error, onRetry = viewModel::refresh, onOpenServer = onOpenServer) }
        }
        items(state.folders) { folder ->
            IconTileRow(
                icon = AppIcons.Library,
                title = folder.title,
                subtitle = folder.summary,
                showDivider = folder != state.folders.last(),
                onClick = { onBrowse(folder.url, folder.title) },
            )
        }
        item {
            CardRow(
                icon = AppIcons.Tune,
                title = "Calibre 服务器",
                subtitle = "地址与账号",
                trailing = AppIcons.NorthEast,
                modifier = Modifier.padding(horizontal = PagePadding, vertical = 24.dp),
                onClick = onOpenServer,
            )
        }
    }
}

@Composable
private fun CardRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: ImageVector? = null,
    onClick: () -> Unit,
) {
    SurfaceCard(modifier, onClick = onClick) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(26.dp))
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Gap(4)
                Text(subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing?.let { Icon(it, null, Modifier.size(24.dp)) }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit, onOpenServer: () -> Unit) {
    SurfaceCard(Modifier.padding(horizontal = PagePadding, vertical = 8.dp)) {
        Column(Modifier.padding(24.dp)) {
            Text("连接 Calibre 失败", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Gap(4)
            Text(message, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
            Gap(16)
            Row {
                Button(onClick = onRetry, shape = CircleShape) { Text("重试") }
                OutlinedButton(onClick = onOpenServer, shape = CircleShape, modifier = Modifier.padding(start = 12.dp)) {
                    Text("服务器设置")
                }
            }
        }
    }
}
