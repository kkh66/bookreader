package com.bookreader.app.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bookreader.app.data.OpdsLibrary
import com.bookreader.app.ui.components.CircleIconButton
import com.bookreader.app.ui.components.Gap
import com.bookreader.app.ui.components.PageHeader
import com.bookreader.app.ui.components.PagePadding
import com.bookreader.app.ui.components.Space
import com.bookreader.app.ui.components.SurfaceCard
import com.bookreader.app.ui.theme.AppIcons
import kotlin.math.absoluteValue

/**
 * Discover: all OPDS libraries; categories and lists are one tap in
 */
@Composable
fun DiscoverScreen(
    onOpenLibrary: (id: String) -> Unit,
    onAddLibrary: () -> Unit,
    onEditLibrary: (id: String) -> Unit,
    onImportEpub: () -> Unit,
    viewModel: DiscoverViewModel = viewModel(factory = DiscoverViewModel.Factory),
) {
    val libraries by viewModel.items.collectAsStateWithLifecycle()
    val grid by viewModel.grid.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<OpdsLibrary?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (grid) 140.dp else 340.dp),
        contentPadding = PaddingValues(start = PagePadding, end = PagePadding, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.L),
        verticalArrangement = Arrangement.spacedBy(Space.L),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            PageHeader("发现", horizontalPadding = 0.dp) {
                Box {
                    CircleIconButton(AppIcons.More, "更多") { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("导入本地 EPUB") },
                            leadingIcon = { Icon(AppIcons.Import, null) },
                            onClick = { menu = false; onImportEpub() },
                        )
                        DropdownMenuItem(
                            text = { Text("添加书库") },
                            leadingIcon = { Icon(AppIcons.Library, null) },
                            onClick = { menu = false; onAddLibrary() },
                        )
                        DropdownMenuItem(
                            text = { Text("使用格子布局") },
                            leadingIcon = { Icon(AppIcons.Grid, null) },
                            trailingIcon = { if (grid) Icon(AppIcons.Check, "已启用") },
                            onClick = { menu = false; viewModel.setGrid(!grid) },
                        )
                    }
                }
            }
        }
        if (libraries.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { EmptyLibraries(onAddLibrary) }
        }
        items(libraries, key = { it.id }) { library ->
            LibraryCard(
                library,
                grid = grid,
                onClick = { onOpenLibrary(library.id) },
                onEdit = { onEditLibrary(library.id) },
                onDelete = { pendingDelete = library },
            )
        }
    }

    pendingDelete?.let { library ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除书库「${library.name}」？") },
            text = { Text("只删除书库配置，已下载到书架的书不受影响") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(library.id)
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}

private val libraryColors = listOf(
    Color(0xFFC9666E),
    Color(0xFF3B5A73),
    Color(0xFF5E7462),
    Color(0xFFB5725A),
    Color(0xFF5B4A63),
)

/** Library icon: a circle colored by name + a book */
@Composable
fun LibraryBadge(library: OpdsLibrary, size: Int) {
    Box(
        Modifier.size(size.dp).clip(CircleShape)
            .background(libraryColors[library.name.hashCode().absoluteValue % libraryColors.size]),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.Shelf, null, Modifier.size((size * 0.5f).dp), tint = Color.White)
    }
}

@Composable
private fun LibraryCard(
    library: OpdsLibrary,
    grid: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val more = @Composable {
        Box {
            Icon(
                AppIcons.MoreVert, "更多",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp).clip(CircleShape).clickable { menu = true }.padding(Space.XS),
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("编辑") }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(text = { Text("删除") }, onClick = { menu = false; onDelete() })
            }
        }
    }
    SurfaceCard(onClick = onClick) {
        if (grid) {
            // Top: icon, overflow menu in the top-right corner; bottom: name and address spanning the full width
            Box(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().padding(vertical = Space.XL), contentAlignment = Alignment.Center) {
                    LibraryBadge(library, 72)
                }
                Box(Modifier.align(Alignment.TopEnd).padding(Space.S)) { more() }
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer).padding(Space.M),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    library.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    library.host, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Row(Modifier.padding(Space.L), verticalAlignment = Alignment.CenterVertically) {
                LibraryBadge(library, 52)
                Column(Modifier.weight(1f).padding(horizontal = Space.L)) {
                    Text(library.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        library.host, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                more()
            }
        }
    }
}

@Composable
private fun EmptyLibraries(onAdd: () -> Unit) {
    SurfaceCard {
        Column(Modifier.padding(Space.XL)) {
            Text("还没有书库", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Gap(Space.S)
            Text(
                "添加 Calibre / Calibre-Web 的 OPDS 地址，就能在这里浏览和下载",
                fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Gap(Space.L)
            Button(onClick = onAdd, shape = CircleShape) { Text("添加书库") }
        }
    }
}
