package com.bookreader.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookreader.app.data.ReadingStyle
import com.bookreader.app.data.UserFont
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Font choice + size / weight / line / paragraph / margin sliders, shared by the reader's settings and the Me tab
 * Ranges stay inside what Readium accepts for each preference
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReadingStyleControls(style: ReadingStyle, fonts: List<UserFont>, onChange: (ReadingStyle) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.S)) {
        Text("字体", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S), verticalArrangement = Arrangement.spacedBy(Space.S)) {
            val choices = listOf(null to "书籍默认", ReadingStyle.SERIF to "宋体", ReadingStyle.SANS_SERIF to "黑体") +
                fonts.map { it.family to it.name }
            choices.forEach { (family, label) ->
                ChoiceChip(label, selected = style.fontFamily == family) { onChange(style.copy(fontFamily = family)) }
            }
        }
        Gap(Space.XS)
        StyleSlider(
            "字号", style.fontSize, 0.7..2.0, 0.05,
            label = { "${(it * 100).roundToInt()}%" },
        ) { onChange(style.copy(fontSize = it)) }
        StyleSlider(
            "字重", style.fontWeight, 0.5..2.0, 0.25, default = 1.0,
            label = { "${(it * 100).roundToInt()}%" },
        ) { onChange(style.copy(fontWeight = it)) }
        StyleSlider(
            "行距", style.lineHeight, 1.0..2.0, 0.1, default = 1.5,
            label = { decimal(it) },
        ) { onChange(style.copy(lineHeight = it)) }
        StyleSlider(
            "段距", style.paragraphSpacing, 0.0..2.0, 0.1, default = 0.5,
            label = { decimal(it) },
        ) { onChange(style.copy(paragraphSpacing = it)) }
        StyleSlider(
            "页边距", style.pageMargins, 0.0..3.0, 0.25,
            label = { decimal(it) },
        ) { onChange(style.copy(pageMargins = it)) }
        TextButton(onClick = { onChange(ReadingStyle()) }, modifier = Modifier.align(Alignment.End)) {
            Text("恢复书籍默认")
        }
    }
}

private fun decimal(value: Double) = String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

/** Pill that fills in when selected; unselected uses the page background so it shows on cards and panels */
@Composable
private fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        text,
        fontSize = 14.sp,
        color = if (selected) colors.onPrimary else colors.onSurface,
        maxLines = 1,
        modifier = Modifier.clip(CircleShape)
            .background(if (selected) colors.primary else colors.background)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.M, vertical = Space.S),
    )
}

/**
 * Label + slider + value; a null [value] shows 默认 with the knob at [default]
 * The page only re-lays out when the knob is released, not on every step of the drag
 */
@Composable
private fun StyleSlider(
    title: String,
    value: Double?,
    range: ClosedFloatingPointRange<Double>,
    step: Double,
    default: Double = range.start,
    label: (Double) -> String,
    onChange: (Double) -> Unit,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging?.toDouble() ?: value
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 15.sp, modifier = Modifier.width(52.dp))
        Slider(
            value = dragging ?: (value ?: default).toFloat(),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onChange(snap(it.toDouble(), range, step)) }
                dragging = null
            },
            valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
            steps = ((range.endInclusive - range.start) / step).roundToInt() - 1,
            // Snap to steps without drawing a dot for each one
            colors = SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
            modifier = Modifier.weight(1f),
        )
        Text(
            shown?.let { label(snap(it, range, step)) } ?: "默认",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp),
        )
    }
}

/** Rounds to the slider's step so stored values don't carry float noise like 1.4999999 */
private fun snap(value: Double, range: ClosedFloatingPointRange<Double>, step: Double) =
    (range.start + ((value - range.start) / step).roundToInt() * step).coerceIn(range)
