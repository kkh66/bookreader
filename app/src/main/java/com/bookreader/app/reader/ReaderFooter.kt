package com.bookreader.app.reader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.bookreader.app.R
import com.bookreader.app.ui.components.Space
import com.bookreader.app.ui.shelf.ShelfFormat
import com.bookreader.app.ui.theme.AppIcons
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.readium.r2.shared.publication.Locator

/** Height of the status line under the page; the page area is shortened by this much so text never sits under it */
internal val FOOTER_HEIGHT = 26.dp

/**
 * Thin status line under the page: battery, time and date on the left; chapter progress and book page on the right
 * The battery shows its level in bars and color (see [batteryColor]) and a bolt while plugged in
 * Pages are Readium positions (fixed chunks of the book, the same numbers as in the contents), so they don't change with font size;
 * for a PDF or a comic they are its pages, and [chapterProgress] is off since there are no chapters to be part way through
 * [total] is 0 until the book's pages are counted
 */
@Composable
internal fun ReaderFooter(locator: Locator, total: Int, chapterProgress: Boolean, background: Color, contentColor: Color) {
    val context = LocalContext.current
    val now = rememberMinuteClock()
    val battery = rememberBattery()
    val (time, date) = remember(now) { clockLabels(context, now) }
    val page = locator.locations.position
        ?: locator.locations.totalProgression?.takeIf { total > 0 }?.let { (it * total).roundToInt().coerceIn(1, total) }
    val chapter = locator.locations.progression?.takeIf { chapterProgress }

    Row(
        Modifier.fillMaxWidth().height(FOOTER_HEIGHT).background(background).padding(horizontal = Space.L),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (battery != null) {
            Icon(batteryIcon(battery), context.getString(R.string.battery), Modifier.size(18.dp), tint = batteryColor(battery.level))
            Spacer(Modifier.width(Space.XS))
        }
        FooterText(listOfNotNull(battery?.let { "${it.level}%" }, time, date).joinToString("  "), contentColor)
        Spacer(Modifier.weight(1f))
        FooterText(
            listOfNotNull(
                chapter?.let { context.getString(R.string.chapter_progress, ShelfFormat.percent(it)) },
                page?.takeIf { total > 0 }?.let { "$it / $total" },
            ).joinToString("  "),
            contentColor,
        )
    }
}

@Composable
private fun FooterText(text: String, color: Color) {
    Text(text, fontSize = 11.sp, color = color, maxLines = 1)
}

/** Current time in millis, refreshed at the start of every minute */
@Composable
private fun rememberMinuteClock(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** Battery level 0..100 and whether it's plugged in */
internal data class Battery(val level: Int, val charging: Boolean)

/** The battery as the system last announced it, updated whenever it changes; null when the device doesn't report a level */
@Composable
private fun rememberBattery(): Battery? {
    val context = LocalContext.current
    var battery by remember { mutableStateOf<Battery?>(null) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                battery = batteryOf(intent)
            }
        }
        // The announcement is sticky: registering hands back the current state right away
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )?.let { battery = batteryOf(it) }
        onDispose { context.unregisterReceiver(receiver) }
    }
    return battery
}

private fun batteryOf(intent: Intent): Battery? {
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return null
    return Battery((level * 100 / scale).coerceIn(0, 100), intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0)
}

private fun batteryIcon(battery: Battery): ImageVector = when {
    battery.charging -> AppIcons.BatteryCharging
    else -> when (batteryBars(battery.level)) {
        0 -> AppIcons.BatteryAlert
        1 -> AppIcons.Battery1
        2 -> AppIcons.Battery2
        3 -> AppIcons.Battery3
        4 -> AppIcons.Battery4
        5 -> AppIcons.Battery5
        6 -> AppIcons.Battery6
        else -> AppIcons.BatteryFull
    }
}

/**
 * Bars the battery icon shows for [level]: the icons fill 3, 5, 7 … 13 of the battery's 15.5 units for 1–6 bars,
 * so the closest one is picked; 7 is full, and 0 (under 10%) shows the alert icon instead of an empty battery
 */
internal fun batteryBars(level: Int): Int = when {
    level >= 95 -> 7
    level < 10 -> 0
    else -> ((level / 100f * 15.5f - 1f) / 2f).roundToInt().coerceIn(1, 6)
}

/** Cyan from 80%, yellow from 20%, red below */
internal fun batteryColor(level: Int): Color = when {
    level >= 80 -> BATTERY_HIGH
    level >= 20 -> BATTERY_MEDIUM
    else -> BATTERY_LOW
}

internal val BATTERY_HIGH = Color(0xFF00ACC1)
internal val BATTERY_MEDIUM = Color(0xFFF2B800)
internal val BATTERY_LOW = Color(0xFFE53935)

/** "03:13" (or "3:13" on 12-hour devices) and "10月22日" */
private fun clockLabels(context: Context, millis: Long): Pair<String, String> {
    val time = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    return time.format(DateTimeFormatter.ofPattern(pattern)) to context.getString(R.string.month_day, time.monthValue, time.dayOfMonth)
}
