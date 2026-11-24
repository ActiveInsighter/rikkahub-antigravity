package me.rerere.rikkahub.data.beszel

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

object BeszelFormatters {
    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
    private val SHORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault())

    fun formatBytes(bytes: Double): String {
        if (bytes <= 0.0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
        var value = bytes
        var idx = 0
        while (value >= 1024.0 && idx < units.lastIndex) {
            value /= 1024.0
            idx++
        }
        return if (idx == 0) {
            "${value.toInt()} B"
        } else {
            String.format(Locale.US, "%.1f %s", value, units[idx])
        }
    }

    fun formatBytesPerSec(bytesPerSec: Double): String = "${formatBytes(bytesPerSec)}/s"

    fun formatGb(gb: Double): String {
        if (gb <= 0.0) return "0 GB"
        return if (gb >= 1024.0) {
            String.format(Locale.US, "%.2f TB", gb / 1024.0)
        } else if (gb < 1.0) {
            String.format(Locale.US, "%.0f MB", gb * 1024.0)
        } else {
            String.format(Locale.US, "%.1f GB", gb)
        }
    }

    fun formatPercent(percent: Double): String {
        val clamped = percent.coerceIn(0.0, 999.9)
        return if (clamped >= 10.0) {
            String.format(Locale.US, "%.1f%%", clamped)
        } else {
            String.format(Locale.US, "%.2f%%", clamped)
        }
    }

    fun formatUptime(seconds: Long): String {
        if (seconds <= 0L) return "未知"
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val mins = (seconds % 3600) / 60
        return when {
            days > 0 -> "${days}天 ${hours}小时"
            hours > 0 -> "${hours}小时 ${mins}分"
            else -> "${mins.coerceAtLeast(1)}分钟"
        }
    }

    fun formatTemp(celsius: Double): String =
        String.format(Locale.US, "%.1f°C", celsius)

    fun formatChartTime(instant: Instant, range: BeszelChartRange): String {
        return when (range) {
            BeszelChartRange.LIVE, BeszelChartRange.HOUR, BeszelChartRange.HALF_DAY, BeszelChartRange.DAY ->
                TIME_FORMATTER.format(instant)
            BeszelChartRange.WEEK, BeszelChartRange.MONTH ->
                SHORT_TIME_FORMATTER.format(instant)
        }
    }

    fun formatRelativeTime(instant: Instant?): String {
        if (instant == null) return ""
        val diff = abs(Duration.between(instant, Instant.now()).seconds)
        return when {
            diff < 60 -> "刚刚"
            diff < 3600 -> "${diff / 60} 分钟前"
            diff < 86400 -> "${diff / 3600} 小时前"
            else -> "${diff / 86400} 天前"
        }
    }

    fun osDisplayName(os: BeszelSystemOs): String = when (os) {
        BeszelSystemOs.LINUX -> "Linux"
        BeszelSystemOs.DARWIN -> "macOS"
        BeszelSystemOs.WINDOWS -> "Windows"
        BeszelSystemOs.FREEBSD -> "FreeBSD"
        BeszelSystemOs.UNKNOWN -> "Unknown"
    }

    fun statusDisplayName(status: String): String = when (status) {
        "up" -> "在线"
        "down" -> "离线"
        "paused" -> "已暂停"
        "pending" -> "等待中"
        else -> status
    }
}
