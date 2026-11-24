package me.rerere.rikkahub.ui.pages.beszel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.beszel.BeszelChartRange
import me.rerere.rikkahub.data.beszel.BeszelFormatters
import me.rerere.rikkahub.ui.theme.CustomColors
import java.time.Instant
import kotlin.math.roundToInt

data class BeszelChartSeries(
    val label: String,
    val color: Color,
    val values: List<Double>,
)

@Composable
fun BeszelMetricChartCard(
    title: String,
    subtitle: String? = null,
    times: List<Instant>,
    series: List<BeszelChartSeries>,
    range: BeszelChartRange,
    valueFormatter: (Double) -> String,
    fixedMax: Double? = null,
    modifier: Modifier = Modifier,
) {
    val pointCount = times.size
    var selectedIndex by remember(times.size, range) { mutableIntStateOf(-1) }
    val activeIdx = if (selectedIndex in 0 until pointCount) selectedIndex else pointCount - 1

    val allValues = remember(series) { series.flatMap { it.values } }
    val maxVal = remember(allValues, fixedMax) {
        fixedMax ?: ((allValues.maxOrNull() ?: 1.0).coerceAtLeast(1.0) * 1.15)
    }

    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val crosshairColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val surfaceColor = CustomColors.listItemColors.containerColor

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = surfaceColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (pointCount > 0 && activeIdx >= 0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            text = BeszelFormatters.formatChartTime(times[activeIdx], range),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // Legend & active values row
            if (series.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    series.forEach { s ->
                        val currentVal = s.values.getOrNull(activeIdx) ?: 0.0
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(s.color),
                            )
                            Text(
                                text = "${s.label}: ${valueFormatter(currentVal)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            if (pointCount < 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (pointCount == 0) "暂无监控数据" else "正在等待更多采样点…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Y-axis min/mid/max labels
                    Column(
                        modifier = Modifier
                            .height(140.dp)
                            .padding(end = 8.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text(
                            text = valueFormatter(maxVal),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = valueFormatter(maxVal * 0.5),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = valueFormatter(0.0),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Canvas(
                        modifier = Modifier
                            .weight(1f)
                            .height(140.dp)
                            .pointerInput(pointCount) {
                                detectTapGestures { offset ->
                                    val ratio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    selectedIndex = (ratio * (pointCount - 1)).roundToInt()
                                }
                            }
                            .pointerInput(pointCount) {
                                detectHorizontalDragGestures(
                                    onHorizontalDrag = { change, _ ->
                                        change.consume()
                                        val ratio = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                        selectedIndex = (ratio * (pointCount - 1)).roundToInt()
                                    },
                                )
                            },
                    ) {
                        val w = size.width
                        val h = size.height
                        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

                        // Horizontal gridlines
                        listOf(0f, 0.5f, 1f).forEach { frac ->
                            val y = h * frac
                            drawLine(
                                color = gridColor,
                                start = Offset(0f, y),
                                end = Offset(w, y),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = dashEffect,
                            )
                        }

                        series.forEach { s ->
                            if (s.values.size < 2) return@forEach
                            val linePath = Path()
                            val fillPath = Path()
                            val points = s.values.mapIndexed { idx, v ->
                                val x = if (pointCount <= 1) 0f else idx.toFloat() / (pointCount - 1) * w
                                val norm = (v / maxVal).coerceIn(0.0, 1.0).toFloat()
                                val y = h - norm * h
                                Offset(x, y)
                            }

                            points.forEachIndexed { idx, pt ->
                                if (idx == 0) {
                                    linePath.moveTo(pt.x, pt.y)
                                    fillPath.moveTo(pt.x, h)
                                    fillPath.lineTo(pt.x, pt.y)
                                } else {
                                    val prev = points[idx - 1]
                                    val midX = (prev.x + pt.x) / 2f
                                    linePath.cubicTo(midX, prev.y, midX, pt.y, pt.x, pt.y)
                                    fillPath.cubicTo(midX, prev.y, midX, pt.y, pt.x, pt.y)
                                }
                            }
                            fillPath.lineTo(points.last().x, h)
                            fillPath.close()

                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        s.color.copy(alpha = 0.24f),
                                        s.color.copy(alpha = 0.01f),
                                    ),
                                ),
                            )
                            drawPath(
                                path = linePath,
                                color = s.color,
                                style = Stroke(
                                    width = 2.2.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round,
                                ),
                            )

                            // Highlight active point
                            if (activeIdx in points.indices) {
                                val pt = points[activeIdx]
                                drawCircle(
                                    color = surfaceColor,
                                    radius = 5.dp.toPx(),
                                    center = pt,
                                )
                                drawCircle(
                                    color = s.color,
                                    radius = 3.5.dp.toPx(),
                                    center = pt,
                                )
                            }
                        }

                        // Vertical crosshair at selected index
                        if (selectedIndex in 0 until pointCount) {
                            val x = selectedIndex.toFloat() / (pointCount - 1) * w
                            drawLine(
                                color = crosshairColor,
                                start = Offset(x, 0f),
                                end = Offset(x, h),
                                strokeWidth = 1.dp.toPx(),
                            )
                        }
                    }
                }

                // Bottom time axis
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 40.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = BeszelFormatters.formatChartTime(times.first(), range),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (times.size > 2) {
                        Text(
                            text = BeszelFormatters.formatChartTime(times[times.size / 2], range),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = BeszelFormatters.formatChartTime(times.last(), range),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
