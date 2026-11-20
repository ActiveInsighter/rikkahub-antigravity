package me.rerere.rikkahub.ui.pages.examdocs.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import me.rerere.rikkahub.ui.components.richtext.HighlightCodeBlock
import me.rerere.rikkahub.ui.components.richtext.codeBlockBackground
import me.rerere.rikkahub.ui.components.richtext.codeBlockBorder
import kotlin.math.roundToInt

private const val TABS_ANIM_MS = 260

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
    val safeIndex = selectedIndex.coerceIn(0, tabs.lastIndex)
    val activeTab = tabs[safeIndex]
    val activeSingleCode = extractSingleCodeBlock(activeTab)
    val useCodeSurface = isCodeTabs || activeSingleCode != null

    val shape = RoundedCornerShape(14.dp)
    val borderColor = codeBlockBorder()
    val containerColor = if (useCodeSurface) {
        codeBlockBackground()
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Surface(
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(TABS_ANIM_MS, easing = FastOutSlowInEasing)),
    ) {
        Column(Modifier.fillMaxWidth()) {
            DocTabsHeader(
                tabs = tabs,
                selectedIndex = safeIndex,
                isCodeTabs = isCodeTabs,
                onSelect = { selectedIndex = it },
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.8.dp)
                    .background(borderColor.copy(alpha = 0.7f)),
            )
            AnimatedContent(
                targetState = safeIndex,
                transitionSpec = {
                    val forward = targetState > initialState
                    val enter = slideInHorizontally(
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        initialOffsetX = { fullWidth ->
                            val shift = (fullWidth / 16).coerceAtLeast(12)
                            if (forward) shift else -shift
                        },
                    ) + fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing))
                    val exit = fadeOut(animationSpec = tween(90))
                    enter togetherWith exit
                },
                label = "DocTabsPanel",
            ) { index ->
                val tab = tabs[index]
                val singleCodeBlock = extractSingleCodeBlock(tab)
                if (singleCodeBlock != null) {
                    // Edge-to-edge embedded code block without nested borders, matching web's doc-tabs.css.
                    HighlightCodeBlock(
                        code = singleCodeBlock.code,
                        language = singleCodeBlock.language ?: "plaintext",
                        modifier = Modifier.fillMaxWidth(),
                        completeCodeBlock = singleCodeBlock.closed,
                        embedded = true,
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        renderChildren(tab.blocks)
                    }
                }
            }
        }
    }
}

@Composable
private fun DocTabsHeader(
    tabs: List<ExamDocsSegment.CustomBlock>,
    selectedIndex: Int,
    isCodeTabs: Boolean,
    onSelect: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    // Maps tab index -> (xPx, widthPx) inside the Row for the gliding underline indicator.
    val tabBounds = remember(tabs.size) { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val activeBounds = tabBounds[selectedIndex]
    val underlineInsetPx = with(density) { if (isCodeTabs) 8.dp.toPx() else 2.dp.toPx() }
    val targetLeftPx = ((activeBounds?.first ?: 0f) + underlineInsetPx)
    val targetWidthDp = with(density) {
        ((activeBounds?.second ?: 0f) - 2 * underlineInsetPx).coerceAtLeast(0f).toDp()
    }
    val indicatorLeftPx by animateDpAsState(
        targetValue = with(density) { targetLeftPx.toDp() },
        animationSpec = tween(TABS_ANIM_MS, easing = FastOutSlowInEasing),
        label = "DocTabsIndicatorLeft",
    )
    val indicatorWidth by animateDpAsState(
        targetValue = targetWidthDp,
        animationSpec = tween(TABS_ANIM_MS, easing = FastOutSlowInEasing),
        label = "DocTabsIndicatorWidth",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (isCodeTabs) 8.dp else 14.dp),
            horizontalArrangement = Arrangement.spacedBy(if (isCodeTabs) 2.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isCodeTabs) {
                Icon(
                    imageVector = HugeIcons.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .padding(start = 6.dp, end = 4.dp)
                        .size(15.dp),
                )
            }
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedIndex
                val textColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(180),
                    label = "DocTabTextColor",
                )
                Box(
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            tabBounds[index] = coords.positionInParent().x to coords.size.width.toFloat()
                        }
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(index) }
                        .padding(
                            horizontal = if (isCodeTabs) 8.dp else 2.dp,
                            vertical = if (isCodeTabs) 10.dp else 11.dp,
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
        if (activeBounds != null && indicatorWidth > 0.dp) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset { IntOffset(with(density) { indicatorLeftPx.toPx() }.roundToInt(), 0) }
                    .width(indicatorWidth)
                    .height(2.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp),
                    ),
            )
        }
    }
}

private fun extractSingleCodeBlock(tab: ExamDocsSegment.CustomBlock): MarkdownBlock.CodeBlock? {
    val md = tab.blocks.singleOrNull() as? ExamDocsSegment.Markdown ?: return null
    return md.document.blocks.singleOrNull() as? MarkdownBlock.CodeBlock
}
