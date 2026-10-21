package com.bookreader.app.ui.shelf

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import com.bookreader.app.ui.components.BookCover
import com.bookreader.app.ui.components.COVER_RATIO
import kotlinx.coroutines.CancellationException

/**
 * Shelf drag and drop: long-press a book and drop it on another book to create a group, or on a group to join it
 * Item bounds and the finger position are all converted to root coordinates to find the cell under the finger
 */
@Stable
class ShelfDragState {
    /** Bounds of each item in root coordinates, reported by the items themselves */
    private val bounds = HashMap<String, Rect>()

    /** Position of the shelf container in root coordinates; the drag preview is placed relative to it */
    var origin = Offset.Zero

    var dragging by mutableStateOf<ShelfItem?>(null)
        private set

    /** Key of the item under the finger; releasing drops into it */
    var target by mutableStateOf<String?>(null)
        private set

    /** Finger position in root coordinates */
    var pointer by mutableStateOf(Offset.Zero)
        private set

    /** Press point relative to the cover's top-left corner, so the cover doesn't jump when dragging starts */
    var grab = Offset.Zero
        private set

    /** Preview cover width (px) */
    var width = 0f
        private set

    fun place(key: String, rect: Rect) {
        bounds[key] = rect
    }

    fun forget(key: String) {
        bounds.remove(key)
    }

    /** [local] is the press point within the item; a null [coverWidth] means the cover is as wide as the item (grid mode) */
    fun start(item: ShelfItem, local: Offset, coverWidth: Float?) {
        val origin = bounds[item.book.id] ?: return
        width = coverWidth ?: origin.width
        grab = Offset(local.x.coerceIn(0f, width), local.y.coerceIn(0f, width / COVER_RATIO))
        pointer = origin.topLeft + local
        target = null
        dragging = item
    }

    fun move(delta: Offset) {
        val id = dragging?.book?.id ?: return
        pointer += delta
        target = bounds.entries.firstOrNull { it.key != id && it.value.contains(pointer) }?.key
    }

    /** Release: returns the dragged book and the key of the item it was dropped on; null if not dropped on another item */
    fun drop(): Pair<ShelfItem, String>? {
        val result = dragging?.let { item -> target?.let { item to it } }
        cancel()
        return result
    }

    fun cancel() {
        dragging = null
        target = null
    }
}

/** Cover that follows the finger, drawn on top of the shelf */
@Composable
fun DragPreview(drag: ShelfDragState) {
    val item = drag.dragging ?: return
    val width = with(LocalDensity.current) { drag.width.toDp() }
    BookCover(
        item.book.title, item.book.author, item.coverPath,
        Modifier
            .offset { (drag.pointer - drag.grab - drag.origin).round() }
            .width(width)
            .aspectRatio(COVER_RATIO)
            .graphicsLayer {
                scaleX = 1.06f
                scaleY = 1.06f
                alpha = 0.92f
                shadowElevation = 12.dp.toPx()
                shape = RoundedCornerShape(6.dp)
            },
    )
}

private enum class Press { Tap, Hold, Cancel }

/**
 * Shelf item gestures: tap / long-press and release / long-press then drag
 * All three are detected here so combinedClickable's long-press and drag detection don't fight over the same events;
 * moving past the touch slop before the long press gives up and lets the grid scroll
 */
@Composable
fun Modifier.shelfGestures(
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
): Modifier {
    val haptic = LocalHapticFeedback.current
    // The gesture coroutine starts once and reads the latest callbacks, so opening a book after its progress updates doesn't use stale data
    val tap by rememberUpdatedState(onTap)
    val longPress by rememberUpdatedState(onLongPress)
    val dragStart by rememberUpdatedState(onDragStart)
    val drag by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    val dragCancel by rememberUpdatedState(onDragCancel)
    return this
        .semantics {
            onClick { tap(); true }
            onLongClick { longPress(); true }
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                when (awaitPress(down)) {
                    Press.Tap -> tap()
                    Press.Cancel -> Unit
                    Press.Hold -> {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (awaitDrag(down, dragStart, drag, dragCancel)) dragEnd() else longPress()
                    }
                }
            }
        }
}

/** Wait for a long press: releasing before the timeout is a tap; moving past the slop or an already consumed event (grid scrolling) cancels */
private suspend fun AwaitPointerEventScope.awaitPress(down: PointerInputChange): Press =
    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitTapOrCancel(down) } ?: Press.Hold

private suspend fun AwaitPointerEventScope.awaitTapOrCancel(down: PointerInputChange): Press {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return Press.Cancel
        if (change.changedToUp()) {
            if (change.isConsumed) return Press.Cancel
            change.consume()
            return Press.Tap
        }
        if (change.isConsumed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
            return Press.Cancel
        }
    }
}

/**
 * After the long press: moving past the slop starts a drag; returns whether a drag happened (if so, releasing drops; otherwise it counts as a long press)
 * All events are consumed meanwhile so the grid doesn't scroll along
 */
private suspend fun AwaitPointerEventScope.awaitDrag(
    down: PointerInputChange,
    onStart: (Offset) -> Unit,
    onMove: (Offset) -> Unit,
    onCancel: () -> Unit,
): Boolean {
    var dragging = false
    try {
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            if (change.changedToUp()) {
                change.consume()
                break
            }
            if (dragging) {
                onMove(change.positionChange())
            } else if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                dragging = true
                onStart(down.position)
                onMove(change.position - down.position)
            }
            change.consume()
        }
    } catch (e: CancellationException) {
        if (dragging) onCancel()
        throw e
    }
    return dragging
}
