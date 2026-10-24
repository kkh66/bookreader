package com.bookreader.app.reader

import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.RectF
import android.net.Uri
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookreader.app.R
import com.bookreader.app.data.Highlight
import com.bookreader.app.data.HighlightKind
import com.bookreader.app.ui.components.Space
import kotlin.math.roundToInt
import kotlinx.parcelize.Parcelize
import org.json.JSONObject
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.html.HtmlDecorationTemplate
import org.readium.r2.navigator.html.HtmlDecorationTemplates
import org.readium.r2.navigator.html.toCss
import org.readium.r2.shared.publication.Locator

/** Highlight colors, indexed by [Highlight.color] */
internal val HIGHLIGHT_COLORS = listOf(
    0xFFF6C344.toInt(), // yellow
    0xFF6CC56C.toInt(), // green
    0xFF4FA3F7.toInt(), // blue
    0xFFF06292.toInt(), // pink
    0xFFAB7FE0.toInt(), // purple
)

internal fun highlightColor(index: Int) = HIGHLIGHT_COLORS.getOrElse(index) { HIGHLIGHT_COLORS[0] }

/** Decoration group of the user's highlights on the page */
internal const val HIGHLIGHT_GROUP = "highlights"

/** Decoration style for all four highlight kinds; drawn by [highlightTemplates] */
@Parcelize
internal data class MarkStyle(
    val kind: HighlightKind,
    @ColorInt override val tint: Int,
    override val isActive: Boolean = false,
) : Decoration.Style, Decoration.Style.Tinted, Decoration.Style.Activable

internal fun Highlight.toDecoration(): Decoration? {
    val target = runCatching { Locator.fromJSON(JSONObject(locator)) }.getOrNull() ?: return null
    return Decoration(id = id, locator = target, style = MarkStyle(kind, highlightColor(color)))
}

/**
 * Readium's default templates plus one for [MarkStyle]: one element per line of text,
 * styled as a background, an underline, a line through the middle or a wavy underline
 * Colors are !important: in dark mode Readium CSS forces backgrounds and borders on every element
 */
internal fun highlightTemplates(): HtmlDecorationTemplates = HtmlDecorationTemplates.defaultTemplates().apply {
    set(
        MarkStyle::class,
        HtmlDecorationTemplate(
            layout = HtmlDecorationTemplate.Layout.BOXES,
            element = { decoration ->
                val style = decoration.style as? MarkStyle
                val tint = style?.tint ?: HIGHLIGHT_COLORS[0]
                when (style?.kind ?: HighlightKind.Highlight) {
                    HighlightKind.Highlight ->
                        """<div class="bookreader-mark" style="background-color: ${tint.toCss(alpha = 0.35)} !important"></div>"""
                    HighlightKind.Underline ->
                        """<div class="bookreader-mark" style="border-bottom: 2px solid ${tint.toCss()} !important"></div>"""
                    HighlightKind.Strikethrough ->
                        """<div class="bookreader-mark"><div class="bookreader-strike" style="background-color: ${tint.toCss()} !important"></div></div>"""
                    HighlightKind.Wavy ->
                        """<div class="bookreader-mark bookreader-wavy" style="background-image: url(&quot;${wavySvg(tint)}&quot;) !important"></div>"""
                }
            },
            stylesheet = """
                .bookreader-mark { box-sizing: border-box; border-radius: 3px; }
                .bookreader-strike { position: absolute; left: 0; right: 0; top: 50%; height: 2px; margin-top: -1px; }
                .bookreader-wavy { background-repeat: repeat-x; background-position: left bottom; background-size: 8px 4px; }
            """.trimIndent(),
        ),
    )
}

/**
 * Inline SVG of one wave period in [tint], tiled along the bottom of each line
 * The brackets are percent-encoded: most EPUB pages are XHTML, where a raw `<` in the style attribute makes the whole template invalid XML and Readium drops it
 */
internal fun wavySvg(@ColorInt tint: Int): String {
    val hex = "%06X".format(tint and 0xFFFFFF)
    return "data:image/svg+xml;utf8," +
        "%3Csvg xmlns='http://www.w3.org/2000/svg' width='8' height='4'%3E" +
        "%3Cpath d='M0 3 Q2 0 4 3 T8 3' fill='none' stroke='%23$hex' stroke-width='1.2'/%3E%3C/svg%3E"
}

/** What the popup menu acts on: a fresh text selection, or a highlight that was tapped */
internal sealed interface MarkTarget {
    /** Area on screen to place the menu next to, in px relative to the page */
    val rect: RectF

    data class Selection(val locator: Locator, override val rect: RectF) : MarkTarget

    /** [highlight] as it was when the menu opened; the menu reads newer copies by its id */
    data class Existing(val highlight: Highlight, override val rect: RectF) : MarkTarget
}

/**
 * Popup next to the selection or highlight: kinds, colors, then actions
 * [current] is the highlight being edited, or null for a fresh selection (then [lastKind] / [lastColor] are preselected)
 */
@Composable
internal fun MarkMenu(
    target: MarkTarget,
    current: Highlight?,
    lastKind: HighlightKind,
    lastColor: Int,
    onStyle: (HighlightKind, Int) -> Unit,
    onHighlight: () -> Unit,
    onCopy: () -> Unit,
    onNote: () -> Unit,
    onDelete: () -> Unit,
    onDictionary: () -> Unit,
    onSearch: () -> Unit,
    onShare: () -> Unit,
    onWebSearch: () -> Unit,
) {
    val context = LocalContext.current
    val kind = current?.kind ?: lastKind
    val color = current?.color ?: lastColor
    val gap = with(LocalDensity.current) { 12.dp.roundToPx() }
    // Above the selection when there's room, otherwise below; kept inside the screen horizontally
    Layout(content = {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 8.dp,
            modifier = Modifier.widthIn(max = 360.dp),
        ) {
            Column(Modifier.padding(vertical = Space.S)) {
                Row(Modifier.padding(horizontal = Space.S), horizontalArrangement = Arrangement.spacedBy(Space.XS)) {
                    HighlightKind.entries.forEach { option ->
                        KindButton(option, selected = option == kind, tint = highlightColor(color)) { onStyle(option, color) }
                    }
                }
                Row(
                    Modifier.padding(horizontal = Space.M, vertical = Space.S),
                    horizontalArrangement = Arrangement.spacedBy(Space.M),
                ) {
                    HIGHLIGHT_COLORS.forEachIndexed { index, value ->
                        ColorDot(value, selected = index == color) { onStyle(kind, index) }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.padding(horizontal = Space.XS)) {
                    MenuAction(context.getString(R.string.copy), onCopy)
                    if (current == null) {
                        MenuAction(context.getString(R.string.highlight), onHighlight)
                        MenuAction(context.getString(R.string.annotate), onNote)
                    } else {
                        MenuAction(context.getString(R.string.notes), onNote)
                        MenuAction(context.getString(R.string.delete), onDelete)
                    }
                    MenuAction(context.getString(R.string.dictionary), onDictionary)
                    Box {
                        var more by remember { mutableStateOf(false) }
                        MenuAction(context.getString(R.string.more)) { more = true }
                        DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                            DropdownMenuItem(text = { Text(context.getString(R.string.search_in_book)) }, onClick = { more = false; onSearch() })
                            DropdownMenuItem(text = { Text(context.getString(R.string.share)) }, onClick = { more = false; onShare() })
                            DropdownMenuItem(text = { Text(context.getString(R.string.web_search)) }, onClick = { more = false; onWebSearch() })
                        }
                    }
                }
            }
        }
    }) { measurables, constraints ->
        val card = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val rect = target.rect
            val x = (rect.centerX().roundToInt() - card.width / 2)
                .coerceIn(gap, (constraints.maxWidth - card.width - gap).coerceAtLeast(gap))
            val above = rect.top.roundToInt() - card.height - gap
            val y = if (above >= gap) above else {
                (rect.bottom.roundToInt() + gap).coerceAtMost((constraints.maxHeight - card.height - gap).coerceAtLeast(gap))
            }
            card.place(x, y)
        }
    }
}

/** A "T" drawn with the kind's decoration, like the selection menus of other readers */
@Composable
private fun KindButton(kind: HighlightKind, selected: Boolean, tint: Int, onClick: () -> Unit) {
    val color = Color(tint)
    val ink = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier.size(width = 52.dp, height = 40.dp).clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.background else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(26.dp)) {
            Canvas(Modifier.matchParentSize()) {
                val stroke = 2.dp.toPx()
                when (kind) {
                    HighlightKind.Highlight -> drawRect(color.copy(alpha = 0.45f))
                    HighlightKind.Underline -> drawLine(color, Offset(0f, size.height - stroke), Offset(size.width, size.height - stroke), stroke)
                    HighlightKind.Strikethrough -> drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), stroke)
                    HighlightKind.Wavy -> {
                        val wave = Path()
                        val y = size.height - stroke * 1.5f
                        val step = size.width / 4
                        wave.moveTo(0f, y)
                        repeat(4) { i ->
                            val start = i * step
                            wave.quadraticTo(start + step / 2, y + (if (i % 2 == 0) -stroke * 2 else stroke * 2), start + step, y)
                        }
                        drawPath(wave, color, style = Stroke(width = stroke * 0.75f))
                    }
                }
            }
            Text("T", fontSize = 20.sp, fontFamily = FontFamily.Serif, color = ink)
        }
    }
}

@Composable
private fun ColorDot(@ColorInt value: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(width = 44.dp, height = 22.dp).clip(CircleShape)
            .background(Color(value))
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun MenuAction(text: String, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 15.sp,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
            .padding(horizontal = Space.M, vertical = Space.S),
    )
}

/** Edits a highlight's note, showing the marked text above the field; what's typed survives the activity being recreated */
@Composable
internal fun NoteDialog(highlight: Highlight, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var note by rememberSaveable(highlight.id) { mutableStateOf(highlight.note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.notes)) },
        text = {
            Column {
                // Quoted text with a bar in the highlight's color
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(Color(highlightColor(highlight.color))))
                    Text(
                        highlight.text, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Space.S),
                    )
                }
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text(context.getString(R.string.note_hint)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().padding(top = Space.M).focusRequester(focus),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(note.trim()) }) { Text(context.getString(R.string.save), fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(context.getString(R.string.cancel)) } },
    )
}

/** Selection actions that leave the reader: copy, dictionary apps, share, web search */
internal object TextActions {

    fun copy(context: Context, text: String) {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("BookReader", text))
        Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
    }

    /** Hands the text to dictionary / translate apps through Android's "process text" action */
    fun lookUp(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_PROCESS_TEXT)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_PROCESS_TEXT, text)
            .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        if (context.packageManager.queryIntentActivities(intent, 0).isEmpty()) {
            Toast.makeText(context, context.getString(R.string.no_dictionary), Toast.LENGTH_SHORT).show()
            return
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.lookup)))
    }

    fun share(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share)))
    }

    fun webSearch(context: Context, text: String) {
        val search = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, text)
        val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(text)}"))
        runCatching { context.startActivity(search) }.recoverCatching { context.startActivity(fallback) }
            .onFailure { Toast.makeText(context, context.getString(R.string.no_browser), Toast.LENGTH_SHORT).show() }
    }
}
