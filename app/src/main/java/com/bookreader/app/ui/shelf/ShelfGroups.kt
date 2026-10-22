package com.bookreader.app.ui.shelf

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.bookreader.app.data.Book
import com.bookreader.app.ui.components.BookCover
import com.bookreader.app.ui.components.COVER_RATIO
import com.bookreader.app.ui.components.Gap
import com.bookreader.app.ui.components.ProgressLine
import com.bookreader.app.ui.components.Space
import com.bookreader.app.ui.theme.AppIcons
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
 * Open group: a floating card over the dimmed (and on Android 12+ blurred) shelf
 * Header: name + ✎ to rename in place + book count; drag the handle area down to close
 * Books show their progress; the dashed tile adds books from the shelf;
 * long-press a book or tap 批量管理 to select several and move them out or delete them
 */
@Composable
fun GroupDialog(
    group: ShelfEntry.Group,
    /** Book being opened, shown with a spinner over its cover */
    openingId: String?,
    candidates: List<ShelfItem>,
    onOpen: (Book) -> Unit,
    onAdd: (List<String>) -> Unit,
    onRemove: (List<Book>) -> Unit,
    onDelete: (List<Book>) -> Unit,
    onRename: (String) -> Unit,
    onUngroup: () -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf(TextFieldValue(group.name)) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var adding by remember { mutableStateOf(false) }
    val chosen = group.items.filter { it.book.id in selected }.map { it.book }
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant

    fun startEditing() {
        title = TextFieldValue(group.name, TextRange(0, group.name.length))
        editing = true
    }

    // Commit only a non-empty, changed name
    fun finishEditing() {
        editing = false
        title.text.trim().takeIf { it.isNotEmpty() && it != group.name }?.let(onRename)
    }

    fun endSelection() {
        selecting = false
        selected = emptySet()
    }

    // Back / tapping outside: finish renaming first, then leave selection, then close
    val back = {
        when {
            editing -> finishEditing()
            selecting -> endSelection()
            else -> onDismiss()
        }
    }

    Dialog(
        onDismissRequest = back,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        LaunchedEffect(window) {
            window ?: return@LaunchedEffect
            window.setDimAmount(0.5f)
            // Android 12+ blurs the shelf behind the card; devices with cross-window blur off just keep the dim
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.apply { blurBehindRadius = 64 }
            }
        }
        val scope = rememberCoroutineScope()
        val drag = remember { Animatable(0f) }
        val dismiss by rememberUpdatedState(onDismiss)
        val sheetShape = RoundedCornerShape(28.dp)

        Box(
            Modifier.fillMaxSize()
                .clickable(interactionSource = null, indication = null, onClick = back)
                .systemBarsPadding()
                .imePadding(),
        ) {
            Column(
                Modifier.align(Alignment.BottomCenter)
                    .padding(Space.L)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.78f)
                    .offset { IntOffset(0, drag.value.roundToInt()) }
                    .clip(sheetShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, sheetShape)
                    // The card takes its own taps so they don't close it; tapping it while renaming finishes the rename
                    .clickable(interactionSource = null, indication = null) { if (editing) finishEditing() },
            ) {
                // Handle + header: drag down to close, springs back if not dragged far enough
                Column(
                    Modifier.fillMaxWidth().pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    if (drag.value > 96.dp.toPx()) {
                                        drag.animateTo(drag.value + 600.dp.toPx(), tween(150))
                                        dismiss()
                                    } else {
                                        drag.animateTo(0f)
                                    }
                                }
                            },
                            onDragCancel = { scope.launch { drag.animateTo(0f) } },
                        ) { change, amount ->
                            change.consume()
                            scope.launch { drag.snapTo((drag.value + amount).coerceAtLeast(0f)) }
                        }
                    },
                ) {
                    Box(
                        Modifier.align(Alignment.CenterHorizontally).padding(top = Space.S)
                            .size(width = 36.dp, height = 4.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    )
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).padding(start = Space.XL, end = Space.M),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when {
                            editing -> {
                                val focus = remember { FocusRequester() }
                                LaunchedEffect(Unit) { focus.requestFocus() }
                                BasicTextField(
                                    value = title,
                                    onValueChange = { title = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { finishEditing() }),
                                    modifier = Modifier.weight(1f).focusRequester(focus),
                                )
                                IconButton(onClick = { title = TextFieldValue("") }) {
                                    Icon(AppIcons.Close, "清空", tint = secondary)
                                }
                            }
                            selecting -> {
                                Text(
                                    "已选 ${chosen.size} 本", fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                val all = chosen.size == group.items.size
                                TextButton(onClick = { selected = if (all) emptySet() else group.items.map { it.book.id }.toSet() }) {
                                    Text(if (all) "全不选" else "全选")
                                }
                            }
                            else -> {
                                Text(
                                    group.name, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                        .clickable(interactionSource = null, indication = null) { startEditing() },
                                )
                                IconButton(onClick = ::startEditing) {
                                    Icon(AppIcons.Edit, "重命名", Modifier.size(20.dp), tint = secondary)
                                }
                                CountChip("${group.items.size} 本")
                            }
                        }
                    }
                }
                LazyVerticalGrid(
                    // 84dp keeps 3 columns on a phone inside the card's margins
                    columns = GridCells.Adaptive(84.dp),
                    contentPadding = PaddingValues(start = Space.XL, end = Space.XL, top = Space.S, bottom = Space.S),
                    horizontalArrangement = Arrangement.spacedBy(Space.M),
                    modifier = Modifier.weight(1f),
                ) {
                    items(group.items, key = { it.book.id }) { item ->
                        val id = item.book.id
                        GroupBook(
                            item,
                            selecting = selecting,
                            selected = id in selected,
                            loading = id == openingId,
                            onClick = { if (selecting) selected = selected.toggle(id) else onOpen(item.book) },
                            onLongClick = {
                                if (!selecting) {
                                    selecting = true
                                    selected = setOf(id)
                                }
                            },
                        )
                    }
                    if (!selecting) item(key = "add") { AddBookTile(onClick = { adding = true }) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(Space.L), horizontalArrangement = Arrangement.spacedBy(Space.M)) {
                    if (selecting) {
                        SheetButton("移出分组", Modifier.weight(1f), enabled = chosen.isNotEmpty()) {
                            onRemove(chosen)
                            endSelection()
                        }
                        SheetButton("删除", Modifier.weight(1f), enabled = chosen.isNotEmpty(), danger = true) {
                            onDelete(chosen)
                            endSelection()
                        }
                    } else {
                        SheetButton("批量管理", Modifier.weight(1f), icon = AppIcons.Add) { selecting = true }
                        SheetButton("解散分组", Modifier.weight(1f), danger = true, onClick = onUngroup)
                    }
                }
            }
        }
    }

    if (adding) {
        AddBooksDialog(
            groupName = group.name,
            candidates = candidates,
            onConfirm = { ids ->
                onAdd(ids)
                adding = false
            },
            onDismiss = { adding = false },
        )
    }
}

private fun Set<String>.toggle(id: String) = if (id in this) this - id else this + id

@Composable
private fun CountChip(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Space.XS).clip(CircleShape)
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
            .padding(horizontal = Space.M, vertical = Space.XS),
    )
}

/** Bottom bar button; [danger] tints it red for destructive actions */
@Composable
private fun SheetButton(
    text: String,
    modifier: Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val content = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (danger) content.copy(alpha = 0.14f) else MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
            contentColor = content,
            disabledContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.3f),
            disabledContentColor = content.copy(alpha = 0.4f),
        ),
        contentPadding = PaddingValues(vertical = 14.dp),
        modifier = modifier,
    ) {
        icon?.let {
            Icon(it, null, Modifier.size(18.dp))
            Spacer(Modifier.width(Space.S))
        }
        Text(text, fontSize = 15.sp)
    }
}

/** A book in the group sheet or the add picker: cover (with a check mark while selecting), two-line title, progress */
@Composable
private fun GroupBook(
    item: ShelfItem,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    loading: Boolean = false,
) {
    val book = item.book
    Column(Modifier.padding(bottom = Space.L).combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box {
            BookCover(book.title, book.author, item.coverPath, Modifier.fillMaxWidth().aspectRatio(COVER_RATIO), loading)
            if (selecting) SelectMark(selected, Modifier.align(Alignment.TopEnd).padding(Space.XS))
        }
        Gap(Space.S)
        // Always two lines so the progress bars line up across a row
        Text(
            book.title, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold,
            minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Gap(Space.XS)
        ProgressLine(book.progress.toFloat(), Modifier.fillMaxWidth())
        Gap(Space.XS)
        Text(readLabel(book), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SelectMark(selected: Boolean, modifier: Modifier) {
    Box(
        modifier.size(22.dp).clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.35f))
            .border(1.5.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(AppIcons.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
    }
}

/** Dashed tile at the end of the group's books: opens the picker */
@Composable
private fun AddBookTile(onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.padding(bottom = Space.L).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.fillMaxWidth().aspectRatio(COVER_RATIO).drawBehind {
                drawRoundRect(
                    color = color.copy(alpha = 0.6f),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                    ),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                )
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(AppIcons.Add, null, Modifier.size(28.dp), tint = color)
            Gap(Space.XS)
            Text("添加图书", fontSize = 12.sp, color = color)
        }
        Gap(Space.S)
        Text("添加", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Pick shelf books to add to the group; books in another group move over */
@Composable
private fun AddBooksDialog(
    groupName: String,
    candidates: List<ShelfItem>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(emptySet<String>()) }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.8f).dp
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.92f).widthIn(max = 560.dp).heightIn(max = maxHeight)
                .clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface)
                .padding(Space.XL),
        ) {
            Text(
                "添加到「$groupName」", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Gap(Space.L)
            if (candidates.isEmpty()) {
                Text("书架上的书都已经在这个分组里了", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(84.dp),
                    horizontalArrangement = Arrangement.spacedBy(Space.M),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    items(candidates, key = { it.book.id }) { item ->
                        val id = item.book.id
                        GroupBook(
                            item,
                            selecting = true,
                            selected = id in picked,
                            onClick = { picked = picked.toggle(id) },
                            onLongClick = { picked = picked.toggle(id) },
                        )
                    }
                }
            }
            Gap(Space.L)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S, Alignment.End)) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Button(onClick = { onConfirm(picked.toList()) }, enabled = picked.isNotEmpty(), shape = CircleShape) {
                    Text(if (picked.isEmpty()) "添加" else "添加 ${picked.size} 本")
                }
            }
        }
    }
}
