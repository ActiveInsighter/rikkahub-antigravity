package me.rerere.rikkahub.ui.pages.beszel

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
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
import me.rerere.rikkahub.ui.theme.CustomColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class ChartSeries(
    val label: String,
    val color: Color,
    val values: List<Double>,
)

@Composable
fun BeszelExpressiveBar(
    progress: Float,
    color: Color,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 450),
        label = "beszel_bar",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(CircleShape)
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated.coerceAtLeast(0.02f))
                .height(7.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}

@Composable
fun usageSemanticColor(percent: Double): Color = when {
    percent >= 90.0 -> MaterialTheme.colorScheme.error
    percent >= 75.0 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary
}

@Composable
fun BeszelMetricChartCard(
    title: String,
    subtitle: String? = null,
    timestamps: List<Long>,
    series: List<ChartSeries>,
    valueFormatter: (Double) -> String,
    fixedMaxValue: Double? = null,
    modifier: Modifier = Modifier,
) {
    val pointCount = timestamps.size
    var selectedIndex by remember(timestamps, series) { mutableStateOf<Int?>(null) }

    val timeFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    val shortTimeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val maxVal = remember(series, fixedMaxValue) {
        if (fixedMaxValue != null) {
            fixedMaxValue
        } else {
            val rawMax = series.flatMap { it.values }.maxOrNull() ?: 0.0
            if (rawMax <= 0.0) 1.0 else rawMax * 1.15
        }
    }

    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val crosshairColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = CustomColors.listItemColors.containerColor,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
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
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val activeTime = selectedIndex?.let { idx ->
                        timestamps.getOrNull(idx)?.let { timeFormat.format(Date(it)) }
                    } ?: subtitle
                    if (!activeTime.isNullOrBlank()) {
                        Text(
                            text = activeTime,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selectedIndex != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Current / Selected values pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    series.forEach { s ->
                        val displayVal = selectedIndex?.let { s.values.getOrNull(it) }
                            ?: s.values.lastOrNull()
                        if (displayVal != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(s.color),
                                )
                                Text(
                                    text = if (series.size > 1) {
                                        "${s.label} ${valueFormatter(displayVal)}"
                                    } else {
                                        valueFormatter(displayVal)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }

            // Chart Canvas
            if (pointCount < 2 || series.all { it.values.size < 2 }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "暂无足够的历史采样数据",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                        .pointerInput(pointCount) {
                            detectTapGestures(
                                onPress = { offset ->
                                    val idx = ((offset.x / size.width.coerceAtLeast(1)) * (pointCount - 1))
                                        .roundToInt()
                                        .coerceIn(0, pointCount - 1)
                                    selectedIndex = idx
                                    tryAwaitRelease()
                                    selectedIndex = null
                                },
                            )
                        }
                        .pointerInput(pointCount) {
                            detectHorizontalDragGestures(
                                onDragEnd = { selectedIndex = null },
                                onDragCancel = { selectedIndex = null },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    val idx = ((change.position.x / size.width.coerceAtLeast(1)) * (pointCount - 1))
                                        .roundToInt()
                                        .coerceIn(0, pointCount - 1)
                                    selectedIndex = idx
                                },
                            )
                        },
                ) {
                    val w = size.width
                    val h = size.height
                    val topPad = 8.dp.toPx()
                    val bottomPad = 6.dp.toPx()
                    val chartH = (h - topPad - bottomPad).coerceAtLeast(1f)

                    // Horizontal dashed gridlines (3 lines)
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    listOf(0f, 0.5f, 1f).forEach { fraction ->
                        val y = topPad + chartH * fraction
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashEffect,
                        )
                    }

                    // Draw each series
                    series.forEach { s ->
                        if (s.values.size < 2) return@forEach
                        val count = minOf(s.values.size, pointCount)
                        val points = (0 until count).map { i ->
                            val x = if (count == 1) 0f else (i.toFloat() / (count - 1)) * w
                            val normY = (s.values[i] / maxVal).coerceIn(0.0, 1.0).toFloat()
                            val y = topPad + chartH * (1f - normY)
                            Offset(x, y)
                        }

                        val linePath = Path().apply {
                            moveTo(points.first().x, points.first().y)
                            for (i in 1 until points.size) {
                                val prev = points[i - 1]
                                val curr = points[i]
                                val midX = (prev.x + curr.x) / 2f
                                cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                            }
                        }

                        val fillPath = Path().apply {
                            addPath(linePath)
                            lineTo(points.last().x, topPad + chartH)
                            lineTo(points.first().x, topPad + chartH)
                            close()
                        }

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    s.color.copy(alpha = 0.26f),
                                    s.color.copy(alpha = 0.02f),
                                ),
                                startY = topPad,
                                endY = topPad + chartH,
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
                    }

                    // Draw crosshair & dot if scrubbing
                    selectedIndex?.let { idx ->
                        val x = if (pointCount <= 1) 0f else (idx.toFloat() / (pointCount - 1)) * w
                        drawLine(
                            color = crosshairColor,
                            start = Offset(x, topPad),
                            end = Offset(x, topPad + chartH),
                            strokeWidth = 1.2.dp.toPx(),
                        )
                        series.forEach { s ->
                            val v = s.values.getOrNull(idx) ?: return@forEach
                            val normY = (v / maxVal).coerceIn(0.0, 1.0).toFloat()
                            val y = topPad + chartH * (1f - normY)
                            drawCircle(
                                color = s.color,
                                radius = 4.5.dp.toPx(),
                                center = Offset(x, y),
                            )
                        }
                    }
                }

                // Bottom X-axis time labels & max label
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = shortTimeFormat.format(Date(timestamps.first())),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "峰值 ${valueFormatter(series.flatMap { it.values }.maxOrNull() ?: 0.0)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = shortTimeFormat.format(Date(timestamps.last())),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
