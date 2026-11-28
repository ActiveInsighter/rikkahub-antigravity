package me.rerere.rikkahub.ui.pages.agent

import android.animation.ValueAnimator
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val ClaudeTerracotta = Color(0xFFD97757)

/**
 * Claude 风格的 Agent 等待/思考指示器：
 * 1. 星芒脉冲变形图标（Starburst Morph + 呼吸缩放 + 柔和旋转）
 * 2. Shimmer 扫光状态文字（模仿 Claude shimmering text）
 * 3. 等宽数字（tnum）实时计时胶囊，点击可查看实时执行洞察
 */
@Composable
fun ClaudeAgentWaitingIndicator(
    elapsedText: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    statusLabel: String = "思考中",
) {
    val accentColor = lerp(ClaudeTerracotta, MaterialTheme.colorScheme.primary, 0.25f)
    val baseTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val shimmerHighlightColor = MaterialTheme.colorScheme.onSurface

    val isInspection = LocalInspectionMode.current
    val animatorsEnabled = remember(isInspection) {
        if (isInspection) true else ValueAnimator.areAnimatorsEnabled()
    }

    val shimmerProgress = if (animatorsEnabled) {
        val transition = rememberInfiniteTransition(label = "claude_shimmer_text")
        val progress by transition.animateFloat(
            initialValue = -0.6f,
            targetValue = 1.6f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1650, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "shimmer_offset",
        )
        progress
    } else {
        0.5f
    }

    val containerModifier = modifier
        .padding(horizontal = 8.dp, vertical = 6.dp)
        .let { base ->
            if (onClick != null) {
                base
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onClick)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            } else {
                base.padding(horizontal = 4.dp, vertical = 4.dp)
            }
        }

    Row(
        modifier = containerModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ClaudeStarburstIndicator(
            color = accentColor,
            size = 22.dp,
            animatorsEnabled = animatorsEnabled,
        )

        val shimmerBrush = remember(shimmerProgress, baseTextColor, shimmerHighlightColor, accentColor) {
            val center = shimmerProgress
            val span = 0.45f
            Brush.linearGradient(
                colorStops = arrayOf(
                    (center - span).coerceIn(0f, 1f) to baseTextColor.copy(alpha = 0.72f),
                    center.coerceIn(0f, 1f) to lerp(shimmerHighlightColor, accentColor, 0.35f),
                    (center + span).coerceIn(0f, 1f) to baseTextColor.copy(alpha = 0.72f),
                ),
                start = Offset(0f, 0f),
                end = Offset(220f, 40f),
            )
        }

        Text(
            text = statusLabel,
            style = MaterialTheme.typography.bodyMedium.merge(
                TextStyle(
                    brush = if (animatorsEnabled) shimmerBrush else null,
                    fontWeight = FontWeight.Medium,
                )
            ),
            color = if (animatorsEnabled) Color.Unspecified else baseTextColor,
        )

        val displayTime = elapsedText?.takeIf { it.isNotBlank() } ?: "0秒"
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = accentColor.copy(alpha = 0.10f),
            border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.25f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Canvas(modifier = Modifier.size(6.dp)) {
                    drawCircle(color = accentColor.copy(alpha = 0.85f))
                }
                Text(
                    text = displayTime,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFeatureSettings = "tnum",
                        fontWeight = FontWeight.Medium,
                    ),
                    color = accentColor,
                )
            }
        }
    }
}

/**
 * Claude 标志性星芒脉冲变形图标（8 瓣放射星芒 + 呼吸缩放 + 旋转）。
 */
@Composable
fun ClaudeStarburstIndicator(
    color: Color = lerp(ClaudeTerracotta, MaterialTheme.colorScheme.primary, 0.25f),
    size: Dp = 24.dp,
    animatorsEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val transition = if (animatorsEnabled) {
        rememberInfiniteTransition(label = "claude_starburst")
    } else {
        null
    }

    val rotation by (transition?.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "starburst_rotation",
    ) ?: remember { androidx.compose.runtime.mutableFloatStateOf(0f) })

    val pulse by (transition?.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "starburst_pulse",
    ) ?: remember { androidx.compose.runtime.mutableFloatStateOf(0.5f) })

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val maxRadius = this.size.minDimension / 2f
            val scaleFactor = 0.86f + 0.14f * pulse

            // 外圈柔和呼吸光晕
            drawCircle(
                color = color.copy(alpha = 0.08f + 0.08f * (1f - pulse)),
                radius = maxRadius * (0.85f + 0.15f * pulse),
                center = center,
            )

            val rays = 8
            val innerBase = maxRadius * (0.14f + 0.06f * pulse)
            val outerLong = maxRadius * 0.88f * scaleFactor
            val outerShort = maxRadius * (0.64f + 0.14f * (1f - pulse)) * scaleFactor
            val strokeWidth = (maxRadius * 0.20f).coerceAtLeast(1.8f)

            rotate(degrees = rotation, pivot = center) {
                for (i in 0 until rays) {
                    val angleRad = (i * (2.0 * PI / rays)).toFloat()
                    val outerR = if (i % 2 == 0) outerLong else outerShort
                    val start = Offset(
                        x = center.x + innerBase * cos(angleRad),
                        y = center.y + innerBase * sin(angleRad),
                    )
                    val end = Offset(
                        x = center.x + outerR * cos(angleRad),
                        y = center.y + outerR * sin(angleRad),
                    )
                    drawLine(
                        color = color,
                        start = start,
                        end = end,
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }

                // 中心柔和实心核，随脉冲微幅变化
                val corePath = Path().apply {
                    addOval(
                        androidx.compose.ui.geometry.Rect(
                            center = center,
                            radius = maxRadius * (0.16f + 0.04f * pulse),
                        )
                    )
                }
                drawPath(path = corePath, color = color)
            }
        }
    }
}
