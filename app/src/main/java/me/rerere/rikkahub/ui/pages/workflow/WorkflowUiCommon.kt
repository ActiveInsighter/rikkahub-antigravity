package me.rerere.rikkahub.ui.pages.workflow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.rikkahub.data.workflow.StatusTone
import me.rerere.rikkahub.ui.theme.CustomColors
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun WorkflowErrorBanner(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = !message.isNullOrBlank()) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.Alert01,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = message.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = HugeIcons.Cancel01,
                        contentDescription = "关闭",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun WorkflowNoticeBanner(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = !message.isNullOrBlank()) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.Tick01,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = message.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = HugeIcons.Cancel01,
                        contentDescription = "关闭",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun WorkflowEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = CustomColors.listItemColors.containerColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(26.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(4.dp))
                FilledTonalButton(
                    onClick = onAction,
                    shape = CircleShape,
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
internal fun WorkflowPagerBar(
    page: Int,
    totalPages: Int,
    totalItems: Int,
    enabled: Boolean,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (totalPages <= 1 && totalItems <= 0) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = CustomColors.listItemColors.containerColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "第 $page / $totalPages 页 · 共 $totalItems 项",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = { onPageChange(page - 1) },
                    enabled = enabled && page > 1,
                ) {
                    Icon(HugeIcons.ArrowLeft01, contentDescription = "上一页")
                }
                IconButton(
                    onClick = { onPageChange(page + 1) },
                    enabled = enabled && page < totalPages,
                ) {
                    Icon(HugeIcons.ArrowRight01, contentDescription = "下一页")
                }
            }
        }
    }
}

@Composable
internal fun WorkflowTextInputDialog(
    title: String,
    label: String,
    initialValue: String = "",
    placeholder: String = "",
    confirmLabel: String = "确定",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                placeholder = if (placeholder.isNotEmpty()) ({ Text(placeholder) }) else null,
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(value.trim()) },
                enabled = value.trim().isNotEmpty(),
                shape = CircleShape,
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

internal fun formatWorkflowTime(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return "—"
    return runCatching {
        val instant = Instant.parse(trimmed)
        val local = instant.atZone(ZoneId.systemDefault())
        val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.getDefault())
        local.format(formatter)
    }.getOrElse { trimmed.take(16).replace('T', ' ') }
}

internal fun formatByteSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.2f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}

@Composable
internal fun StatusBadgePill(
    label: String,
    colors: ToneColors,
) {
    Surface(
        shape = CircleShape,
        color = colors.container,
        contentColor = colors.content,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

internal data class ToneColors(
    val container: Color,
    val content: Color,
)

@Composable
internal fun statusToneColors(tone: StatusTone): ToneColors {
    val scheme = MaterialTheme.colorScheme
    return when (tone) {
        StatusTone.NEUTRAL -> ToneColors(
            container = scheme.surfaceContainerHighest,
            content = scheme.onSurfaceVariant,
        )
        StatusTone.INFO -> ToneColors(
            container = scheme.secondaryContainer,
            content = scheme.onSecondaryContainer,
        )
        StatusTone.SUCCESS -> ToneColors(
            container = scheme.primaryContainer,
            content = scheme.onPrimaryContainer,
        )
        StatusTone.WARNING -> ToneColors(
            container = scheme.tertiaryContainer,
            content = scheme.onTertiaryContainer,
        )
        StatusTone.DANGER -> ToneColors(
            container = scheme.errorContainer,
            content = scheme.onErrorContainer,
        )
    }
}

private fun parseInstantSafe(raw: String): Instant? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val normalized = if (trimmed.contains(' ') && !trimmed.contains('T')) {
        trimmed.replace(' ', 'T')
    } else {
        trimmed
    }
    return runCatching { Instant.parse(normalized) }.getOrNull()
}

internal fun formatRelativeTime(raw: String): String {
    val instant = parseInstantSafe(raw) ?: return raw.ifEmpty { "—" }
    val seconds = Duration.between(instant, Instant.now()).seconds
    return when {
        seconds < 60 -> "刚刚"
        seconds < 3600 -> "${seconds / 60} 分钟前"
        seconds < 86_400 -> "${seconds / 3600} 小时前"
        seconds < 86_400 * 7 -> "${seconds / 86_400} 天前"
        else -> formatDateTime(raw)
    }
}

internal fun formatDateTime(raw: String): String {
    val instant = parseInstantSafe(raw) ?: return raw.ifEmpty { "—" }
    val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}

internal enum class WorkflowButtonTone {
    PRIMARY,
    TONAL,
    OUTLINED,
    DANGER,
}

@Composable
internal fun WorkflowActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: WorkflowButtonTone = WorkflowButtonTone.TONAL,
    enabled: Boolean = true,
) {
    val contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
    val buttonModifier = modifier.defaultMinSize(minHeight = 40.dp)
    val content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }

    when (tone) {
        WorkflowButtonTone.PRIMARY -> Button(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            contentPadding = contentPadding,
            modifier = buttonModifier,
            content = content,
        )
        WorkflowButtonTone.TONAL -> FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            contentPadding = contentPadding,
            modifier = buttonModifier,
            content = content,
        )
        WorkflowButtonTone.OUTLINED -> OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            contentPadding = contentPadding,
            modifier = buttonModifier,
            content = content,
        )
        WorkflowButtonTone.DANGER -> OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            contentPadding = contentPadding,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
            modifier = buttonModifier,
            content = content,
        )
    }
}

