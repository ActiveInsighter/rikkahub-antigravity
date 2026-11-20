package me.rerere.rikkahub.ui.pages.examdocs.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.Alert02
import me.rerere.hugeicons.stroke.AlertCircle
import me.rerere.hugeicons.stroke.Book02
import me.rerere.hugeicons.stroke.CheckmarkCircle02
import me.rerere.hugeicons.stroke.Idea
import me.rerere.rikkahub.data.examdocs.ExamDocsSegment

private data class CalloutStyle(
    val accent: Color,
    val icon: ImageVector,
)

@Composable
internal fun DocCallout(
    block: ExamDocsSegment.CustomBlock,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val style = when (block.type) {
        "tip", "idea" -> CalloutStyle(colors.primary, HugeIcons.Idea)
        "warn", "warning", "caution" -> CalloutStyle(colors.tertiary, HugeIcons.Alert02)
        "danger", "error" -> CalloutStyle(colors.error, HugeIcons.Alert01)
        "success" -> CalloutStyle(colors.secondary, HugeIcons.CheckmarkCircle02)
        "note" -> CalloutStyle(colors.onSurfaceVariant, HugeIcons.Book02)
        else -> CalloutStyle(colors.primary, HugeIcons.AlertCircle)
    }
    val title = block.title
    Surface(
        color = style.accent.copy(alpha = 0.10f),
        contentColor = colors.onSurface,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val railWidth = 3.dp.toPx()
                    val inset = 8.dp.toPx()
                    val railStart = if (layoutDirection == LayoutDirection.Ltr) inset else size.width - inset - railWidth
                    drawRoundRect(
                        color = style.accent.copy(alpha = 0.55f),
                        topLeft = Offset(railStart, 10.dp.toPx()),
                        size = Size(railWidth, (size.height - 20.dp.toPx()).coerceAtLeast(0f)),
                        cornerRadius = CornerRadius(railWidth / 2),
                    )
                }
                .padding(start = 19.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier.padding(top = 2.dp).size(24.dp)
                    .background(style.accent.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = style.icon,
                    contentDescription = null,
                    tint = style.accent,
                    modifier = Modifier.size(16.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (!title.isNullOrBlank()) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                content()
            }
        }
    }
}
