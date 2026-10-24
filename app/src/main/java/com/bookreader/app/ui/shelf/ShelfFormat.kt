package com.bookreader.app.ui.shelf

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Display text for the shelf list; pure functions so they are easy to unit test */
object ShelfFormat {

    fun percent(progress: Double) = "${(progress * 100).roundToInt()}%"

    /** "0.97小时" (0.97 h): reading time in hours, up to two decimals */
    fun readingHours(seconds: Long): String =
        String.format(Locale.US, "%.2f", seconds / 3600.0).trimEnd('0').trimEnd('.') + "小时"

    /**
     * "0.97小时, 1420字/分钟" (0.97 h, 1420 chars/min): reading time + reading speed
     * Speed = characters read (progress × total characters) / minutes read, 0 when there is no data
     */
    fun readingLine(seconds: Long, progress: Double, totalChars: Long?): String {
        val minutes = seconds / 60.0
        val speed = if (minutes >= 1 && totalChars != null && totalChars > 0) {
            (progress * totalChars / minutes).roundToLong()
        } else {
            0L
        }
        return "${readingHours(seconds)}, ${speed}字/分钟"
    }

    /**
     * "2026年10月14日 - 2026年10月21日" (2026-10-14 - 2026-10-21); null if never read
     * Early versions didn't record the first read time; fall back to the last read time when it's missing
     */
    fun dateRange(firstReadAt: Long, lastReadAt: Long, zone: ZoneId = ZoneId.systemDefault()): String? {
        if (lastReadAt <= 0) return null
        fun date(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().label()
        return "${date(firstReadAt.takeIf { it > 0 } ?: lastReadAt)} - ${date(lastReadAt)}"
    }

    private fun LocalDate.label() = "${year}年${monthValue}月${dayOfMonth}日"
}
