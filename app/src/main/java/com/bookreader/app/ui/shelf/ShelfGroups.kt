package com.bookreader.app.ui.shelf

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.bookreader.app.data.Book
import com.bookreader.app.ui.components.BookCover
import com.bookreader.app.ui.components.COVER_RATIO
import com.bookreader.app.ui.components.Gap
import com.bookreader.app.ui.components.Space
import com.bookreader.app.ui.theme.AppIcons

/** Group cover: the first 4 covers of the group in a 2×2 grid on a light background, with empty slots if there are fewer than 4 */
@Composable
fun GroupCover(items: List<ShelfItem>, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(Space.XS),
        verticalArrangement = Arrangement.spacedBy(Space.XS),
    ) {
        repeat(2) { row ->
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Space.XS)) {
                repeat(2) { column ->
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        items.getOrNull(row * 2 + column)?.let {
                            BookCover(it.book.title, it.book.author, it.coverPath, Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

/** Enlarges and outlines this cell while a dragged book hovers over it, hinting that releasing drops it here */
@Composable
fun Modifier.dropHighlight(active: Boolean): Modifier {
    val scale by animateFloatAsState(if (active) 1.08f else 1f, label = "dropHighlight")
    val outline = MaterialTheme.colorScheme.primary
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(if (active) Modifier.border(2.dp, outline, RoundedCornerShape(8.dp)) else Modifier)
}

@Composable
fun GridGroup(group: ShelfEntry.Group, highlighted: Boolean, modifier: Modifier) {
    Column(Modifier.padding(bottom = Space.XL).then(modifier)) {
        GroupCover(group.items, Modifier.dropHighlight(highlighted).fillMaxWidth().aspectRatio(COVER_RATIO))
        Gap(Space.M)
        Text(group.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("共 ${group.items.size} 本", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ListGroup(group: ShelfEntry.Group, highlighted: Boolean, modifier: Modifier) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier.fillMaxWidth().padding(vertical = Space.M), verticalAlignment = Alignment.CenterVertically) {
        GroupCover(group.items, Modifier.dropHighlight(highlighted).width(64.dp).aspectRatio(COVER_RATIO))
        Column(Modifier.weight(1f).padding(horizontal = Space.L)) {
            Text(group.name, fontSize = 16.sp, lineHeight = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("共 ${group.items.size} 本", fontSize = 13.sp, color = secondary)
        }
        Icon(AppIcons.ChevronRight, null, Modifier.size(22.dp), tint = secondary)
    }
}

/** Name input for a new group, focused when opened with the cursor at the end */
@Composable
fun GroupNameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val valid = name.text.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            val focus = remember { FocusRequester() }
            // Request focus from inside the dialog content so the text field is already attached
            LaunchedEffect(Unit) { focus.requestFocus() }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("分组名称") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (valid) onConfirm(name.text.trim()) }),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.text.trim()) }, enabled = valid) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * Open group (like a home screen folder): group name above a translucent scrim, with a full-width panel of the group's books in the middle
 * Tap the name to rename in place (all selected, × on the right clears it); tapping the scrim or going back saves a pending rename first, otherwise closes
 * Tap a book to read it, long-press to remove it from the group or delete it; the bottom-right corner of the panel ungroups
 */
@Composable
fun GroupDialog(
    group: ShelfEntry.Group,
    onOpen: (Book) -> Unit,
    onRemove: (Book) -> Unit,
    onDelete: (Book) -> Unit,
    onRename: (String) -> Unit,
    onUngroup: () -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf(TextFieldValue(group.name)) }
    // Commit only a non-empty, changed name
    fun finishEditing() {
        editing = false
        title.text.trim().takeIf { it.isNotEmpty() && it != group.name }?.let(onRename)
    }
    val tapOutside = { if (editing) finishEditing() else onDismiss() }
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val titleStyle = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
    )

    Dialog(
        onDismissRequest = tapOutside,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // Replace the dialog's built-in dimming with a scrim drawn in theme colors, so the group name is readable in light and dark
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }
        Column(
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.88f))
                .clickable(interactionSource = null, indication = null, onClick = tapOutside)
                .systemBarsPadding()
                .imePadding(),
        ) {
            Spacer(Modifier.weight(0.2f))
            // Fixed row height: the text field is a bit taller than the text, so without it the panel below jumps when the name is tapped
            Box(
                Modifier.fillMaxWidth().height(48.dp).padding(horizontal = Space.XL),
                contentAlignment = Alignment.Center,
            ) {
                if (editing) {
                    val focus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { focus.requestFocus() }
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it },
                        singleLine = true,
                        textStyle = titleStyle,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { finishEditing() }),
                        // Leave space as wide as the × on both sides so the text stays centered
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp).focusRequester(focus),
                    )
                    Icon(
                        AppIcons.Close, "清空", tint = secondary,
                        modifier = Modifier.align(Alignment.CenterEnd).size(40.dp).clip(CircleShape)
                            .clickable { title = TextFieldValue("") }.padding(Space.S),
                    )
                } else {
                    Text(
                        group.name, style = titleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(interactionSource = null, indication = null) {
                            title = TextFieldValue(group.name, TextRange(0, group.name.length))
                            editing = true
                        },
                    )
                }
            }
            Gap(Space.L)
            // The panel handles its own taps so they don't count as scrim taps; tapping it while renaming finishes the rename
            Column(
                Modifier.fillMaxWidth().weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(interactionSource = null, indication = null) { if (editing) finishEditing() },
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(92.dp),
                    contentPadding = PaddingValues(start = Space.XL, end = Space.XL, top = Space.XL),
                    horizontalArrangement = Arrangement.spacedBy(Space.L),
                    modifier = Modifier.weight(1f),
                ) {
                    items(group.items, key = { it.book.id }) { item ->
                        GroupBook(
                            item,
                            onOpen = { onOpen(item.book) },
                            onRemove = { onRemove(item.book) },
                            onDelete = { onDelete(item.book) },
                        )
                    }
                }
                TextButton(onClick = onUngroup, modifier = Modifier.align(Alignment.End).padding(end = Space.M)) {
                    Text("解散分组", color = secondary)
                }
            }
            Spacer(Modifier.weight(0.2f))
        }
    }
}

@Composable
private fun GroupBook(item: ShelfItem, onOpen: () -> Unit, onRemove: () -> Unit, onDelete: () -> Unit) {
    val book = item.book
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(Modifier.padding(bottom = Space.L).combinedClickable(onClick = onOpen, onLongClick = { menu = true })) {
            BookCover(book.title, book.author, item.coverPath, Modifier.fillMaxWidth().aspectRatio(COVER_RATIO))
            Gap(Space.S)
            Text(book.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(readLabel(book), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("移出分组") }, onClick = { menu = false; onRemove() })
            DropdownMenuItem(text = { Text("删除") }, onClick = { menu = false; onDelete() })
        }
    }
}
