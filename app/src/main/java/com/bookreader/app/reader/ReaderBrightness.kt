package com.bookreader.app.reader

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import com.bookreader.app.R
import com.bookreader.app.ui.components.Gap
import com.bookreader.app.ui.components.Space

/** Left end of the brightness slider: the backlight stays at its lowest and a black veil dims the page further */
internal const val DIM_RANGE = 0.2f

/** Veil opacity at the slider's far left; any darker and text gets hard to read */
private const val MAX_DIM = 0.6f

/** Lowest backlight asked for; 0 turns the screen fully dark on some older devices */
private const val MIN_BACKLIGHT = 0.01f

/**
 * Window brightness (WindowManager.LayoutParams.screenBrightness) for a slider position [level], 0..1.
 * The window value is on the backlight's linear scale while the system slider is perceptual,
 * so the position is squared to make the dim end as fine-grained as the system's
 */
internal fun backlightFor(level: Float): Float {
    val t = ((level - DIM_RANGE) / (1f - DIM_RANGE)).coerceIn(0f, 1f)
    return (t * t).coerceAtLeast(MIN_BACKLIGHT)
}

/** Opacity of the black veil over the reader for a slider position: 0 above [DIM_RANGE], up to [MAX_DIM] at the far left */
internal fun dimFor(level: Float): Float = ((DIM_RANGE - level) / DIM_RANGE).coerceIn(0f, 1f) * MAX_DIM

/**
 * [☐ 自动] then the brightness slider: ticked follows the system brightness, the knob keeping the last chosen position.
 * Any touch on the slider unticks it, even a tap on the knob that leaves the value as it was
 */
@Composable
private fun BrightnessRow(level: Float, system: Boolean, onLevel: (Float) -> Unit, onSystem: (Boolean) -> Unit) {
    val context = LocalContext.current
    val label = context.getString(R.string.brightness)
    Row(
        Modifier.fillMaxWidth().padding(start = Space.L, end = Space.XL, top = Space.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Box and label toggle together
        Row(
            Modifier.toggleable(value = system, role = Role.Checkbox, onValueChange = onSystem)
                .padding(horizontal = Space.S, vertical = Space.S),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = system, onCheckedChange = null)
            Text(context.getString(R.string.brightness_auto), fontSize = 15.sp, modifier = Modifier.padding(start = Space.S))
        }
        Slider(
            value = level,
            onValueChange = onLevel,
            // Not onLevel(level): this runs right after onValueChange in the same gesture, before [level] has caught up
            onValueChangeFinished = { onSystem(false) },
            modifier = Modifier.weight(1f).padding(start = Space.M).semantics { contentDescription = label },
        )
    }
}

/** Brightness panel ([ReaderSheet]) opened from the menu's sun button; it sits outside the veil, so only the page dims behind it */
@Composable
internal fun ReaderBrightnessDialog(level: Float, system: Boolean, onLevel: (Float) -> Unit, onSystem: (Boolean) -> Unit, onDismiss: () -> Unit) {
    ReaderSheet(LocalContext.current.getString(R.string.brightness), onDismiss) {
        BrightnessRow(level, system, onLevel, onSystem)
        Gap(Space.L)
    }
}
