package com.bookreader.app.reader

import android.content.Context
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookreader.app.ui.components.Space
import com.bookreader.app.ui.shelf.ShelfFormat
import com.bookreader.app.ui.theme.AppIcons
import kotlinx.coroutines.delay
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Height of the status line under the page; the page area is shortened by this much so text never sits under it */
internal val FOOTER_HEIGHT = 26.dp

/**
 * Thin status line under the page: battery, time and date on the left; chapter progress and book page on the right
 * Pages are Readium positions (fixed chunks of the book, the same numbers as in the contents), so they don't change with font size;
 * for a PDF or a comic they are its pages, and [chapterProgress] is off since there are no chapters to be part way through
 */
@Composable
internal fun ReaderFooter(publication: Publication, locator: Locator, background: Color, contentColor: Color, chapterProgress: Boolean) {
    val context = LocalContext.current
    val now = rememberMinuteClock()
    // Battery changes slowly; reading it with the clock once a minute is enough
    val battery = remember(now) { batteryPercent(context) }
    val (time, date) = remember(now) { clockLabels(context, now) }
    val total by produceState(0, publication) { value = publication.positions().size }
    val page = locator.locations.position
        ?: locator.locations.totalProgression?.takeIf { total > 0 }?.let { (it * total).roundToInt().coerceIn(1, total) }
    val chapter = locator.locations.progression?.takeIf { chapterProgress }

    Row(
        Modifier.fillMaxWidth().height(FOOTER_HEIGHT).background(background).padding(horizontal = Space.L),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (battery != null) {
            Icon(AppIcons.Battery, "电量", Modifier.size(16.dp), tint = contentColor)
            Spacer(Modifier.width(Space.XS))
        }
        FooterText(listOfNotNull(battery?.let { "$it%" }, time, date).joinToString("  "), contentColor)
        Spacer(Modifier.weight(1f))
        FooterText(
            listOfNotNull(
                chapter?.let { "本章 ${ShelfFormat.percent(it)}" },
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

/** Battery level 0..100, or null when the device doesn't report it */
private fun batteryPercent(context: Context): Int? =
    context.getSystemService(BatteryManager::class.java)
        ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        ?.takeIf { it in 0..100 }

/** "03:13" (or "3:13" on 12-hour devices) and "10月22日" */
private fun clockLabels(context: Context, millis: Long): Pair<String, String> {
    val time = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    return time.format(DateTimeFormatter.ofPattern(pattern)) to "${time.monthValue}月${time.dayOfMonth}日"
}
