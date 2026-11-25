package me.rerere.rikkahub.ui.pages.examdocs.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import me.rerere.bettertext.markdown.MarkdownBlock
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Code
import me.rerere.rikkahub.data.examdocs.ExamDocsSegment
import me.rerere.rikkahub.ui.pages.examdocs.LocalDocsQuestionJump
import me.rerere.rikkahub.ui.pages.examdocs.containsDockQuestion
import kotlin.math.roundToInt

private data class TabMeasurement(
    val leftPx: Float,
    val widthPx: Int,
)

@Composable
internal fun DocTabs(
    block: ExamDocsSegment.CustomBlock,
    renderChildren: @Composable (List<ExamDocsSegment>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = block.blocks.filterIsInstance<ExamDocsSegment.CustomBlock>().filter { it.type == "tab" }
    if (tabs.isEmpty()) {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            renderChildren(block.blocks)
        }
        return
    }

    val isCodeTabs = block.type == "codetabs"
    var selectedIndex by rememberSaveable(tabs.size, block.type) { mutableIntStateOf(0) }
    val jump = LocalDocsQuestionJump.current?.value
    LaunchedEffect(jump) {
        if (jump != null) {
            val index = tabs.indexOfFirst { it.containsDockQuestion(jump.block) }
            if (index >= 0) selectedIndex = index
        }
    }
    val safeIndex = selectedIndex.coerceIn(0, tabs.lastIndex)

    val useCodeLayout = isCodeTabs || remember(tabs) { tabs.all { it.isSingleCodeBlock() } }
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val measurements = remember(tabs.size) { mutableStateMapOf<Int, TabMeasurement>() }
    val activeMeasurement = measurements[safeIndex]
    val indicatorInsetPx = with(density) { (if (useCodeLayout) 8.dp else 0.dp).toPx() }

    val targetOffsetDp = with(density) {
        ((activeMeasurement?.leftPx ?: 0f) + indicatorInsetPx).coerceAtLeast(0f).toDp()
    }
    val targetWidthDp = with(density) {
        ((activeMeasurement?.widthPx ?: 0) - indicatorInsetPx * 2).coerceAtLeast(0f).toDp()
    }
    val indicatorOffset by animateDpAsState(
        targetValue = targetOffsetDp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "DocTabsIndicatorOffset",
    )
    val indicatorWidth by animateDpAsState(
        targetValue = targetWidthDp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "DocTabsIndicatorWidth",
    )

    LaunchedEffect(safeIndex, activeMeasurement) {
        val m = activeMeasurement ?: return@LaunchedEffect
        val targetScroll = (m.leftPx - 48f).coerceAtLeast(0f).roundToInt()
        if (m.leftPx < scrollState.value || m.leftPx + m.widthPx > scrollState.value + scrollState.viewportSize) {
            scrollState.animateScrollTo(targetScroll)
        }
    }

    Surface(
        shape = if (useCodeLayout) RectangleShape else RoundedCornerShape(16.dp),
        color = if (useCodeLayout) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (useCodeLayout) null else BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
            ),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(horizontal = if (useCodeLayout) 8.dp else 14.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(if (useCodeLayout) 0.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isCodeTabs) {
                        Icon(
                            imageVector = HugeIcons.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier
                                .padding(start = 6.dp, end = 4.dp)
                                .size(14.dp),
                        )
                    }
                    tabs.forEachIndexed { index, tab ->
                        val selected = index == safeIndex
                        val textColor by animateColorAsState(
                            targetValue = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            animationSpec = tween(durationMillis = 180),
                            label = "DocTabsTextColor",
                        )
                        Box(
                            modifier = Modifier
                                .onGloballyPositioned { coords ->
                                    measurements[index] = TabMeasurement(
                                        leftPx = coords.positionInParent().x,
                                        widthPx = coords.size.width,
                                    )
                                }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { selectedIndex = index }
                                .padding(
                                    horizontal = if (useCodeLayout) 10.dp else 2.dp,
                                    vertical = if (useCodeLayout) 9.dp else 10.dp,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = tab.title ?: "Tab ${index + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = textColor,
                                maxLines = 1,
                            )
                        }
                    }
                }
                if (activeMeasurement != null && indicatorWidth > 0.dp) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset { IntOffset(indicatorOffset.roundToPx(), 0) }
                            .width(indicatorWidth)
                            .height(2.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp),
                            ),
                    )
                }
            }

            if (!useCodeLayout) {
                Box(Modifier.fillMaxWidth().height(0.8.dp).background(borderColor))
            }

            AnimatedContent(
                targetState = safeIndex,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { width ->
                        (width * 0.04f * direction).roundToInt()
                    }) togetherWith fadeOut(tween(80))
                },
                label = "DocTabsPanel",
            ) { index ->
                val tab = tabs[index]
                val padding = if (useCodeLayout && tab.isSingleCodeBlock()) 0.dp else 14.dp
                Column(
                    modifier = Modifier.fillMaxWidth().padding(padding),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    renderChildren(tab.blocks)
                }
            }
        }
    }
}

private fun ExamDocsSegment.CustomBlock.isSingleCodeBlock(): Boolean {
    val segment = blocks.singleOrNull() as? ExamDocsSegment.Markdown ?: return false
    return segment.document.blocks.singleOrNull() is MarkdownBlock.CodeBlock
}
