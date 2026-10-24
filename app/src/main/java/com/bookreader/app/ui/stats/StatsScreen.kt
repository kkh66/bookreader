package com.bookreader.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bookreader.app.R
import com.bookreader.app.data.DayStat
import com.bookreader.app.ui.components.BookCover
import com.bookreader.app.ui.components.COVER_RATIO
import com.bookreader.app.ui.components.Gap
import com.bookreader.app.ui.components.PageHeader
import com.bookreader.app.ui.components.PagePadding
import com.bookreader.app.ui.components.ProgressLine
import com.bookreader.app.ui.components.Space
import com.bookreader.app.ui.components.SurfaceCard
import com.bookreader.app.ui.shelf.ShelfFormat
import java.time.LocalDate

@Composable
fun StatsScreen(viewModel: StatsViewModel = viewModel(factory = StatsViewModel.Factory)) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val summary = state.summary

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader(context.getString(R.string.reading_stats))
        Column(
            Modifier.padding(horizontal = PagePadding, vertical = Space.L),
            verticalArrangement = Arrangement.spacedBy(Space.L),
        ) {
            TodayCard(summary.todaySeconds, state.today)
            SurfaceCard {
                Column(Modifier.padding(horizontal = Space.XL, vertical = Space.S)) {
                    Row {
                        Metric(context.getString(R.string.total_reading), duration(summary.totalSeconds))
                        Metric(context.getString(R.string.books_read), context.getString(R.string.book_count, summary.bookCount))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row {
                        Metric(context.getString(R.string.last_seven_days), duration(summary.last7Seconds))
                        Metric(context.getString(R.string.reading_streak), context.getString(R.string.day_count, summary.streakDays))
                    }
                }
            }
            SurfaceCard {
                Column(Modifier.padding(Space.XL)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(context.getString(R.string.recent_seven_days), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(context.getString(R.string.daily_reading_time), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Gap(Space.L)
                    WeekChart(summary.last7, state.today)
                }
            }
            if (state.history.isNotEmpty()) {
                SurfaceCard {
                    Column(Modifier.padding(Space.XL)) {
                        Text(context.getString(R.string.reading_history), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Gap(Space.S)
                        state.history.forEach { RecordRow(it) }
                    }
                }
            }
        }
    }
}

/** Reading history: with cover and progress, like the shelf list */
@Composable
private fun RecordRow(item: BookTime) {
    val book = item.book
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.padding(vertical = Space.M), verticalAlignment = Alignment.CenterVertically) {
        BookCover(book.title, book.author, item.coverPath, Modifier.width(48.dp).aspectRatio(COVER_RATIO))
        Column(Modifier.weight(1f).padding(horizontal = Space.M)) {
            Text(book.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(book.author, fontSize = 12.sp, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Gap(Space.S)
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressLine(book.progress.toFloat(), Modifier.weight(1f))
                Text(ShelfFormat.percent(book.progress), fontSize = 12.sp, color = secondary, modifier = Modifier.padding(start = Space.S))
            }
        }
        Text(duration(item.seconds), fontSize = 14.sp, color = secondary)
    }
}

@Composable
private fun TodayCard(seconds: Long, today: LocalDate) {
    val context = LocalContext.current
    val ink = MaterialTheme.colorScheme.inverseOnSurface
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.inverseSurface).padding(Space.XL),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(context.getString(R.string.reading_today), fontSize = 16.sp, color = ink.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
            Text(context.getString(R.string.month_day, today.monthValue, today.dayOfMonth), fontSize = 14.sp, color = ink.copy(alpha = 0.7f))
        }
        Gap(Space.L)
        Text(todayText(seconds), fontSize = 46.sp, fontWeight = FontWeight.Bold, color = ink)
        Gap(Space.M)
        Text(
            if (seconds > 0) context.getString(R.string.today_recorded) else context.getString(R.string.today_unread),
            fontSize = 14.sp,
            color = ink.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Metric(label: String, value: String) {
    Column(Modifier.weight(1f).padding(vertical = Space.L)) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Gap(Space.S)
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WeekChart(days: List<DayStat>, today: LocalDate) {
    val context = LocalContext.current
    val max = days.maxOf { it.seconds }.coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(170.dp), verticalAlignment = Alignment.Bottom) {
        days.forEach { day ->
            val isToday = day.date == today
            val minutes = displayMinutes(day.seconds)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (minutes > 0) context.getString(R.string.minutes_short, minutes) else "0",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Gap(Space.S)
                Bar(fraction = day.seconds.toFloat() / max, highlight = isToday)
                Gap(Space.S)
                Text(
                    weekday(day.date),
                    fontSize = 13.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.Bar(fraction: Float, highlight: Boolean) {
    val color = when {
        highlight && fraction > 0 -> MaterialTheme.colorScheme.primary
        fraction > 0 -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    // Days without reading get a short placeholder bar
    val height = if (fraction > 0) (100 * fraction).coerceAtLeast(6f) else 3f
    Box(Modifier.width(26.dp).height(height.dp).clip(RoundedCornerShape(6.dp)).background(color))
}

private fun weekday(date: LocalDate) = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())

/** Today's big number: under an hour it shows "21分钟" (21 minutes) */
@Composable
private fun todayText(seconds: Long): String {
    val context = LocalContext.current
    val minutes = displayMinutes(seconds)
    return if (minutes < 60) context.getString(R.string.minutes_long, minutes) else context.getString(R.string.hours_minutes, minutes / 60, minutes % 60)
}

@Composable
private fun duration(seconds: Long): String {
    val context = LocalContext.current
    val minutes = displayMinutes(seconds)
    return if (minutes < 60) context.getString(R.string.minutes_short, minutes) else context.getString(R.string.hours_minutes, minutes / 60, minutes % 60)
}

/** Show at least 1 minute even when under a minute, so the UI never says "0分钟" (0 minutes) next to "已记录" (recorded) */
private fun displayMinutes(seconds: Long) = if (seconds in 1..59) 1L else seconds / 60
