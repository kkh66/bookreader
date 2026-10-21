package com.bookreader.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bookreader.app.data.DayStat
import com.bookreader.app.ui.components.Gap
import com.bookreader.app.ui.components.PageHeader
import com.bookreader.app.ui.components.PagePadding
import com.bookreader.app.ui.components.SurfaceCard
import java.time.LocalDate

@Composable
fun StatsScreen(viewModel: StatsViewModel = viewModel(factory = StatsViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val summary = state.summary

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("阅读统计", "每一页，都算数")
        Column(
            Modifier.padding(horizontal = PagePadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TodayCard(summary.todaySeconds, state.today)
            SurfaceCard {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Row {
                        Metric("累计阅读", duration(summary.totalSeconds))
                        Metric("阅读书籍", "${summary.bookCount} 本")
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row {
                        Metric("近 7 天", duration(summary.last7Seconds))
                        Metric("连续阅读", "${summary.streakDays} 天")
                    }
                }
            }
            SurfaceCard {
                Column(Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("最近 7 天", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("每日阅读时长", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Gap(20)
                    WeekChart(summary.last7, state.today)
                }
            }
            if (state.history.isNotEmpty()) {
                SurfaceCard {
                    Column(Modifier.padding(24.dp)) {
                        Text("阅读记录", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Gap(8)
                        state.history.forEach { item ->
                            Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        item.author, fontSize = 12.sp, maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(duration(item.seconds), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayCard(seconds: Long, today: LocalDate) {
    val ink = MaterialTheme.colorScheme.inverseOnSurface
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.inverseSurface).padding(28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("今日阅读", fontSize = 16.sp, color = ink.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
            Text("${today.monthValue}月${today.dayOfMonth}日", fontSize = 14.sp, color = ink.copy(alpha = 0.7f))
        }
        Gap(16)
        Text(todayText(seconds), fontSize = 46.sp, fontWeight = FontWeight.Bold, color = ink)
        Gap(12)
        Text(
            if (seconds > 0) "已记录今天的阅读时长" else "今天还没开始读",
            fontSize = 14.sp,
            color = ink.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Metric(label: String, value: String) {
    Column(Modifier.weight(1f).padding(vertical = 18.dp)) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Gap(8)
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WeekChart(days: List<DayStat>, today: LocalDate) {
    val max = days.maxOf { it.seconds }.coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(170.dp), verticalAlignment = Alignment.Bottom) {
        days.forEach { day ->
            val isToday = day.date == today
            val minutes = day.seconds / 60
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (minutes > 0) "${minutes}分" else "0",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Gap(6)
                Bar(fraction = day.seconds.toFloat() / max, highlight = isToday)
                Gap(8)
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

private fun weekday(date: LocalDate) = "周" + "一二三四五六日"[date.dayOfWeek.value - 1]

/** Today's big number: under an hour it shows "21分钟" (21 minutes) */
private fun todayText(seconds: Long): String {
    val minutes = seconds / 60
    return if (minutes < 60) "${minutes}分钟" else "${minutes / 60}时${minutes % 60}分"
}

private fun duration(seconds: Long): String {
    val minutes = seconds / 60
    return if (minutes < 60) "${minutes}分" else "${minutes / 60}时${minutes % 60}分"
}
