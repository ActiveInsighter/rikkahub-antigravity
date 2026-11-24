package me.rerere.rikkahub.ui.pages.pocketbase

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.pocketbase.PbLogStat
import java.util.Locale
import kotlin.math.max

@Composable
fun PbLogStatsChart(
    stats: List<PbLogStat>,
    errorStats: List<PbLogStat>,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val displayStats = stats.takeLast(24)
    val errorByDate = errorStats.associateBy({ it.date }, { it.total })
    val maxTotal = max(1, displayStats.maxOfOrNull { it.total } ?: 1)

    Column(modifier = modifier.fillMaxWidth()) {
        if (displayStats.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(trackColor.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "暂无日志统计数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(vertical = 4.dp),
            ) {
                val count = displayStats.size
                val slotWidth = size.width / count
                val barWidth = (slotWidth * 0.62f).coerceIn(4.dp.toPx(), 18.dp.toPx())
                val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())

                displayStats.forEachIndexed { index, item ->
                    val x = index * slotWidth + (slotWidth - barWidth) / 2f
                    // Background track
                    drawRoundRect(
                        color = trackColor.copy(alpha = 0.45f),
                        topLeft = Offset(x, 0f),
                        size = Size(barWidth, size.height),
                        cornerRadius = radius,
                    )
                    val ratio = (item.total.toFloat() / maxTotal.toFloat()).coerceIn(0f, 1f)
                    val barHeight = (size.height * ratio).coerceAtLeast(if (item.total > 0) 4.dp.toPx() else 0f)
                    if (barHeight > 0f) {
                        drawRoundRect(
                            color = primaryColor.copy(alpha = 0.85f),
                            topLeft = Offset(x, size.height - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = radius,
                        )
                    }
                    val errCount = errorByDate[item.date] ?: 0
                    if (errCount > 0) {
                        val errRatio = (errCount.toFloat() / maxTotal.toFloat()).coerceIn(0f, 1f)
                        val errHeight = (size.height * errRatio).coerceAtLeast(4.dp.toPx())
                        drawRoundRect(
                            color = errorColor,
                            topLeft = Offset(x, size.height - errHeight),
                            size = Size(barWidth, errHeight),
                            cornerRadius = radius,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatStatTime(displayStats.first().date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LegendDot(color = primaryColor, label = "总请求")
                    if (errorStats.isNotEmpty()) {
                        LegendDot(color = errorColor, label = "错误 (>=400)")
                    }
                }
                Text(
                    text = formatStatTime(displayStats.last().date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun PbBadgePill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}

fun formatPbTimestamp(raw: String): String {
    if (raw.isBlank()) return "-"
    return raw.replace("T", " ").substringBefore(".").removeSuffix("Z")
}

private fun formatStatTime(raw: String): String {
    if (raw.isBlank()) return ""
    // e.g. "2026-04-12 14:00:00.000Z" -> "14:00"
    val timePart = raw.substringAfter(" ", raw.substringAfter("T", raw))
    return timePart.take(5)
}
