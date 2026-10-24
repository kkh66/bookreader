package com.bookreader.app.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bookreader.app.R
import com.bookreader.app.ui.components.BackHeader
import com.bookreader.app.ui.components.CircleIconButton
import com.bookreader.app.ui.components.Gap
import com.bookreader.app.ui.components.IconTileRow
import com.bookreader.app.ui.components.PagePadding
import com.bookreader.app.ui.components.SearchHeader
import com.bookreader.app.ui.components.SectionTitle
import com.bookreader.app.ui.components.Space
import com.bookreader.app.ui.components.SurfaceCard
import com.bookreader.app.ui.theme.AppIcons

/**
 * Library home: categories and lists; the header has search and edit
 */
@Composable
fun LibraryScreen(
    libraryId: String,
    onBrowse: (url: String, title: String) -> Unit,
    onEdit: () -> Unit,
    onExit: () -> Unit,
    revision: Int = 0,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val closeSearch = {
        searching = false
        query = ""
    }
    val submit = {
        viewModel.searchUrl(query)?.takeIf { query.isNotBlank() }?.let { onBrowse(it, context.getString(R.string.search_title, query.trim())) }
    }

    LaunchedEffect(libraryId, revision) { viewModel.start(libraryId) }
    BackHandler(onBack = if (searching) closeSearch else onExit)

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            if (searching) {
                SearchHeader(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = context.getString(R.string.library_search_hint),
                    onClose = closeSearch,
                    onSearch = { submit() },
                )
            } else {
                BackHeader(state.library?.name ?: context.getString(R.string.library), onBack = onExit) {
                    if (state.searchTemplate != null) CircleIconButton(AppIcons.Search, context.getString(R.string.search)) { searching = true }
                    CircleIconButton(AppIcons.Tune, context.getString(R.string.edit_library), onEdit)
                }
            }
        }
        item {
            SectionTitle(
                context.getString(R.string.categories_lists),
                Modifier.padding(start = PagePadding, end = PagePadding, top = Space.M, bottom = Space.M),
                trailing = state.library?.host,
                large = true,
            )
        }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = PagePadding)) }
        state.error?.let { error ->
            item { ErrorCard(error, onRetry = viewModel::refresh, onEdit = onEdit) }
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
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    SurfaceCard(Modifier.padding(horizontal = PagePadding, vertical = Space.S)) {
        Column(Modifier.padding(Space.XL)) {
            Text(context.getString(R.string.library_connection_failed), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Gap(Space.XS)
            Text(message, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
            Gap(Space.L)
            Row {
                Button(onClick = onRetry, shape = CircleShape) { Text(context.getString(R.string.retry)) }
                OutlinedButton(onClick = onEdit, shape = CircleShape, modifier = Modifier.padding(start = Space.M)) {
                    Text(context.getString(R.string.edit_library))
                }
            }
        }
    }
}
