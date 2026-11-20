package me.rerere.rikkahub.ui.pages.examdocs.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.Alert02
import me.rerere.hugeicons.stroke.AlertCircle
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Book02
import me.rerere.hugeicons.stroke.CheckmarkCircle02
import me.rerere.hugeicons.stroke.Idea
import me.rerere.hugeicons.stroke.Link01
import me.rerere.rikkahub.data.examdocs.ExamDocsSegment
import me.rerere.rikkahub.ui.theme.extendColors

@Composable
internal fun DocCards(
    block: ExamDocsSegment.CustomBlock,
    renderChild: @Composable (ExamDocsSegment) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        block.blocks.forEach { child ->
            renderChild(child)
        }
    }
}

@Composable
internal fun DocCard(
    block: ExamDocsSegment.CustomBlock,
    content: @Composable () -> Unit,
    onLinkClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val href = block.attrs["href"]?.takeIf { it.isNotBlank() }
    val toneColor = resolveToneColor(block.attrs["tone"])
    val icon = resolveToneIcon(block.attrs["tone"], block.attrs["icon"])

    val cardContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (icon != null || !block.attrs["icon"].isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = toneColor.copy(alpha = 0.14f),
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (icon != null) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = toneColor,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Text(
                                text = block.attrs["icon"].orEmpty().take(2),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    block.title?.let { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (href != null) {
                        Icon(
                            imageVector = HugeIcons.ArrowRight01,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                if (block.blocks.isNotEmpty()) {
                    content()
                }
            }
        }
    }

    val shape = RoundedCornerShape(18.dp)
    val border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    if (href != null && onLinkClick != null) {
        Surface(
            onClick = { onLinkClick(href) },
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = border,
            modifier = modifier.fillMaxWidth(),
            content = cardContent,
        )
    } else {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = border,
            modifier = modifier.fillMaxWidth(),
            content = cardContent,
        )
    }
}

@Composable
internal fun resolveToneColor(tone: String?): Color {
    val scheme = MaterialTheme.colorScheme
    val extendColors = MaterialTheme.extendColors
    return when (tone?.lowercase()) {
        "success" -> extendColors.green6
        "warning", "warn", "caution" -> extendColors.orange6
        "error", "danger" -> extendColors.red6
        "idea" -> extendColors.orange5
        "info" -> extendColors.blue6
        "primary" -> scheme.primary
        else -> scheme.primary
    }
}

internal fun resolveToneIcon(tone: String?, iconName: String?): ImageVector? {
    val lowerIcon = iconName?.lowercase().orEmpty()
    if (lowerIcon.isNotEmpty() && lowerIcon.any { it.code > 127 }) {
        // Emoji icon — render as text instead of vector
        return null
    }
    return when (tone?.lowercase()) {
        "success" -> HugeIcons.CheckmarkCircle02
        "warning" -> HugeIcons.Alert02
        "error", "danger" -> HugeIcons.Alert01
        "idea" -> HugeIcons.Idea
        "info" -> HugeIcons.AlertCircle
        else -> when {
            "book" in lowerIcon -> HugeIcons.Book02
            "link" in lowerIcon -> HugeIcons.Link01
            "alert" in lowerIcon || "warn" in lowerIcon -> HugeIcons.Alert02
            "check" in lowerIcon -> HugeIcons.CheckmarkCircle02
            "idea" in lowerIcon || "light" in lowerIcon -> HugeIcons.Idea
            iconName.isNullOrBlank() -> null
            else -> HugeIcons.SparklesFallback
        }
    }
}

private val HugeIcons.SparklesFallback: ImageVector
    get() = HugeIcons.Book02
