package me.rerere.rikkahub.ui.pages.examdocs.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.Edit03
import me.rerere.hugeicons.stroke.Idea
import me.rerere.hugeicons.stroke.Notification01
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.rikkahub.data.examdocs.ExamDocsSegment

@Composable
internal fun ExamDocsCalloutCard(
    block: ExamDocsSegment.CustomBlock,
    content: @Composable () -> Unit,
) {
    val palette = resolveTonePalette(block.type)
    val (defaultLabel, icon) = when (block.type.lowercase()) {
        "tip", "idea" -> "提示" to HugeIcons.Idea
        "success" -> "要点" to HugeIcons.Tick01
        "warning", "warn" -> "注意" to HugeIcons.Alert01
        "danger", "error" -> "警告" to HugeIcons.Alert01
        "note" -> "笔记" to HugeIcons.Edit03
        else -> "说明" to HugeIcons.Notification01
    }

    Surface(
        color = palette.container,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(palette.accent)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = block.title ?: defaultLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.onContainer,
                    )
                }
                content()
            }
        }
    }
}
