package com.bookreader.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bookreader.app.R
import com.bookreader.app.data.DayStat
import com.bookreader.app.data.PeriodStats
import com.bookreader.app.data.ReadingStats
import com.bookreader.app.data.StatsBar
import com.bookreader.app.data.StatsPeriod
import com.bookreader.app.data.calendar
import com.bookreader.app.data.calendarLevel
import com.bookreader.app.ui.components.BookCover
import com.bookreader.app.ui.components.COVER_RATIO
import com.bookreader.app.ui.components.Gap
import com.bookreader.app.ui.components.PageHeader
import com.bookreader.app.ui.components.PagePadding
import com.bookreader.app.ui.components.ProgressLine
import com.bookreader.app.ui.components.Space
import com.bookreader.app.ui.components.SurfaceCard
import com.bookreader.app.ui.shelf.ShelfFormat
import com.bookreader.app.ui.theme.AppIcons
import java.time.LocalDate
import java.util.Locale

@Composable
fun StatsScreen(viewModel: StatsViewModel = viewModel(factory = StatsViewModel.Factory)) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val summary = state.summary

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader(context.getString(R.string.reading_stats))
        Column(
            Modifier.padding(horizontal = PagePadding, vertical = Space.L),
            verticalArrangement = Arrangement.spacedBy(Space.M),
        ) {
            TodayCard(summary.todaySeconds, state.today)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.M)) {
                Tile(context.getString(R.string.total_reading), duration(summary.totalSeconds))
                Tile(context.getString(R.string.books_read), context.getString(R.string.book_count, summary.bookCount))
                Tile(context.getString(R.string.reading_streak), context.getString(R.string.day_count, summary.streakDays))
            }
            PeriodCard(state.chart, state.today, state.canGoBack, state.canGoForward, viewModel::setPeriod, viewModel::shift)
            CalendarCard(state.stats, state.today)
            if (state.history.isNotEmpty()) TopBooksCard(state.history)
        }
    }
}

/** Today on its own card: minutes read and the date */
@Composable
private fun TodayCard(seconds: Long, today: LocalDate) {
    val context = LocalContext.current
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    SurfaceCard {
        Column(Modifier.padding(Space.L)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(context.getString(R.string.reading_today), fontSize = 14.sp, color = secondary, modifier = Modifier.weight(1f))
                Text("${monthDay(today)} ${weekday(today)}", fontSize = 14.sp, color = secondary)
            }
            Gap(Space.XS)
            Text(todayText(seconds), fontSize = 32.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Tile(label: String, value: String) {
    SurfaceCard(Modifier.weight(1f)) {
        Column(Modifier.padding(Space.M)) {
            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Gap(Space.XS)
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Reading time of a week / month / year: total, average, and a bar per day (per month for a year); tapping a bar shows its numbers */
@Composable
private fun PeriodCard(
    chart: PeriodStats,
    today: LocalDate,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onPeriod: (StatsPeriod) -> Unit,
    onShift: (Int) -> Unit,
) {
    val context = LocalContext.current
    // The tapped bar; cleared when the chart moves to another period
    var selected by remember(chart.period, chart.start) { mutableStateOf<Int?>(null) }
    val bar = selected?.let { chart.bars.getOrNull(it) }
    SurfaceCard {
        Column(Modifier.padding(Space.L)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PeriodArrow(context.getString(R.string.earlier), back = true, enabled = canGoBack) { onShift(-1) }
                Text(periodTitle(chart, today), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                PeriodArrow(context.getString(R.string.later), back = false, enabled = canGoForward) { onShift(1) }
                Spacer(Modifier.weight(1f))
                PeriodSwitch(chart.period, onPeriod)
            }
            Gap(Space.S)
            Text(duration(chart.totalSeconds), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(
                if (bar != null) barSummary(bar, chart.period) else context.getString(
                    if (chart.period == StatsPeriod.Year) R.string.monthly_average else R.string.daily_average,
                    duration(chart.averageSeconds),
                ),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Gap(Space.L)
            BarChart(chart, today, selected) { selected = if (selected == it) null else it }
        }
    }
}

@Composable
private fun PeriodArrow(label: String, back: Boolean, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(32.dp)) {
        // One chevron, mirrored for "back"
        Icon(AppIcons.ChevronRight, label, Modifier.size(20.dp).scale(if (back) -1f else 1f, 1f))
    }
}

/** Week / month / year segmented switch */
@Composable
private fun PeriodSwitch(current: StatsPeriod, onSelect: (StatsPeriod) -> Unit) {
    val context = LocalContext.current
    Row(Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.background).padding(2.dp)) {
        StatsPeriod.entries.forEach { period ->
            val on = period == current
            Text(
                context.getString(period.label),
                fontSize = 13.sp,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .background(if (on) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                    .selectable(selected = on, interactionSource = null, indication = null, role = Role.Tab) { onSelect(period) }
                    .padding(horizontal = Space.M, vertical = Space.XS),
            )
        }
    }
}

private val StatsPeriod.label get() = when (this) {
    StatsPeriod.Week -> R.string.period_week
    StatsPeriod.Month -> R.string.period_month
    StatsPeriod.Year -> R.string.period_year
}

/** Bars scaled to the longest one, with the average as a dashed line; the selected bar (or the current day / month) stands out */
@Composable
private fun BarChart(chart: PeriodStats, today: LocalDate, selected: Int?, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val max = chart.bars.maxOf { it.seconds }.coerceAtLeast(1)
    // A month has 28-31 bars, so they sit closer together
    val gap = when (chart.period) {
        StatsPeriod.Week -> 12.dp
        StatsPeriod.Month -> 2.dp
        StatsPeriod.Year -> 6.dp
    }
    Box(Modifier.fillMaxWidth().height(110.dp)) {
        Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Bottom) {
            chart.bars.forEachIndexed { index, bar ->
                val highlight = if (selected != null) index == selected else chart.period.covers(bar, today)
                val color = when {
                    bar.seconds <= 0 -> colors.outlineVariant
                    highlight -> colors.primary
                    else -> colors.onSurfaceVariant.copy(alpha = 0.45f)
                }
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable(interactionSource = null, indication = null) { onSelect(index) },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    // Periods without reading keep a short placeholder bar
                    val fraction = if (bar.seconds > 0) (bar.seconds.toFloat() / max).coerceAtLeast(0.05f) else 0.03f
                    Box(Modifier.fillMaxWidth().fillMaxHeight(fraction).clip(RoundedCornerShape(3.dp)).background(color))
                }
            }
        }
        if (chart.averageSeconds > 0) {
            val line = colors.outline
            Canvas(Modifier.matchParentSize()) {
                val y = size.height * (1f - chart.averageSeconds.toFloat() / max)
                val dash = 4.dp.toPx()
                drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)))
            }
        }
    }
    Gap(Space.S)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
        chart.bars.forEachIndexed { index, bar ->
            val label = when (chart.period) {
                StatsPeriod.Week -> weekday(bar.start)
                // Too many days to label each one
                StatsPeriod.Month -> bar.start.dayOfMonth.takeIf { it == 1 || it % 5 == 0 }?.toString().orEmpty()
                StatsPeriod.Year -> bar.start.monthValue.toString()
            }
            val strong = if (selected != null) index == selected else chart.period.covers(bar, today)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                // Month labels are wider than their bar; let them overhang it, centered
                Text(
                    label,
                    fontSize = 11.sp,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
                    color = if (strong) colors.onSurface else colors.onSurfaceVariant,
                    modifier = Modifier.wrapContentWidth(unbounded = true),
                )
            }
        }
    }
}

/** Whether [bar] (a day, or a month in the year view) contains [day] */
private fun StatsPeriod.covers(bar: StatsBar, day: LocalDate) =
    if (this == StatsPeriod.Year) bar.start.year == day.year && bar.start.month == day.month else bar.start == day

@Composable
private fun periodTitle(chart: PeriodStats, today: LocalDate): String {
    val context = LocalContext.current
    return when (chart.period) {
        StatsPeriod.Week ->
            if (!today.isBefore(chart.start) && !today.isAfter(chart.end)) context.getString(R.string.this_week)
            else context.getString(R.string.date_range, monthDay(chart.start), monthDay(chart.end))
        StatsPeriod.Month -> context.getString(R.string.year_month, chart.start.year, chart.start.monthValue)
        StatsPeriod.Year -> context.getString(R.string.year_only, chart.start.year)
    }
}

/** The tapped bar: "11月3日 · 42分", or for a month "10月 · 14时10分 · 读了 22 天" */
@Composable
private fun barSummary(bar: StatsBar, period: StatsPeriod): String {
    val context = LocalContext.current
    return if (period == StatsPeriod.Year) {
        val month = bar.start.month.getDisplayName(java.time.format.TextStyle.SHORT, appLocale())
        "$month · ${duration(bar.seconds)} · ${context.getString(R.string.days_read, bar.daysRead)}"
    } else {
        "${monthDay(bar.start)} · ${duration(bar.seconds)}"
    }
}

/** GitHub-style calendar of the last weeks, as many as fit the width; darker = longer, tap a day for its time */
@Composable
private fun CalendarCard(stats: ReadingStats, today: LocalDate) {
    val context = LocalContext.current
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    var selected by remember { mutableStateOf<DayStat?>(null) }
    SurfaceCard {
        Column(Modifier.padding(Space.L)) {
            Text(context.getString(R.string.reading_calendar), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Gap(Space.M)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val weeks = ((maxWidth + CELL_GAP) / (CELL + CELL_GAP)).toInt().coerceIn(1, 53)
                val columns = remember(stats, today, weeks) { stats.calendar(today, weeks) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    columns.forEach { week ->
                        Column(verticalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                            week.forEach { day ->
                                val cell = Modifier.size(CELL).clip(RoundedCornerShape(2.dp))
                                if (day == null) {
                                    Box(cell)
                                } else {
                                    Box(
                                        cell.background(levelColor(calendarLevel(day.seconds)))
                                            .clickable(interactionSource = null, indication = null) { selected = day },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Gap(Space.S)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    selected?.let { "${monthDay(it.date)} · ${duration(it.seconds)}" }.orEmpty(),
                    fontSize = 12.sp, color = secondary, modifier = Modifier.weight(1f),
                )
                Text(context.getString(R.string.legend_less), fontSize = 11.sp, color = secondary)
                (0..4).forEach { level ->
                    Box(Modifier.padding(horizontal = 2.dp).size(10.dp).clip(RoundedCornerShape(2.dp)).background(levelColor(level)))
                }
                Text(context.getString(R.string.legend_more), fontSize = 11.sp, color = secondary)
            }
        }
    }
}

private val CELL = 12.dp
private val CELL_GAP = 3.dp

@Composable
private fun levelColor(level: Int): Color =
    if (level == 0) MaterialTheme.colorScheme.outlineVariant
    else MaterialTheme.colorScheme.primary.copy(alpha = listOf(0.3f, 0.5f, 0.75f, 1f)[level - 1])

/** Most read books: the top few, the rest behind "show all" */
@Composable
private fun TopBooksCard(history: List<BookTime>) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    SurfaceCard {
        Column(Modifier.padding(Space.L)) {
            Text(context.getString(R.string.most_read), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            (if (expanded) history else history.take(TOP_BOOKS)).forEachIndexed { index, item -> RecordRow(index + 1, item) }
            if (history.size > TOP_BOOKS) {
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(context.getString(if (expanded) R.string.show_less else R.string.show_all))
                }
            }
        }
    }
}

private const val TOP_BOOKS = 5

/** A ranked book: cover, progress and time read */
@Composable
private fun RecordRow(rank: Int, item: BookTime) {
    val book = item.book
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.padding(vertical = Space.S), verticalAlignment = Alignment.CenterVertically) {
        Text(rank.toString(), fontSize = 13.sp, color = secondary, textAlign = TextAlign.Center, modifier = Modifier.width(20.dp))
        BookCover(book.title, book.author, item.coverPath, Modifier.padding(start = Space.XS).width(40.dp).aspectRatio(COVER_RATIO))
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

/** Short weekday in the app's language (周五 / Fri); the system locale may differ, e.g. 週五 on a Traditional Chinese system */
@Composable
private fun weekday(date: LocalDate) = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, appLocale())

@Composable
private fun appLocale(): Locale = Locale.forLanguageTag(LocalContext.current.getString(R.string.language_code))

@Composable
private fun monthDay(date: LocalDate) = LocalContext.current.getString(R.string.month_day, date.monthValue, date.dayOfMonth)

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

/** Show at least 1 minute even when under a minute, so a day with some reading never reads "0分" */
private fun displayMinutes(seconds: Long) = if (seconds in 1..59) 1L else seconds / 60
