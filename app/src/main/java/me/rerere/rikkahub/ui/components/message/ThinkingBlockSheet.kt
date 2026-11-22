package me.rerere.rikkahub.ui.components.message

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.Model
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ai.SheetHeader
import me.rerere.rikkahub.ui.components.message.tools.ServerToolPreview
import me.rerere.rikkahub.ui.components.message.tools.ToolUIContext
import me.rerere.rikkahub.ui.components.message.tools.ToolUIRegistry
import me.rerere.rikkahub.ui.components.message.tools.parseServerTool
import me.rerere.rikkahub.ui.components.ui.ChainOfThought

private sealed interface ThinkingSheetPage {
    data object Overview : ThinkingSheetPage
    data class ToolDetail(val stepIndex: Int) : ThinkingSheetPage
}

@Composable
fun ThinkingBlockSheet(
    steps: List<ThinkingStep>,
    model: Model?,
    assistant: Assistant?,
    loading: Boolean,
    onToolApproval: ((toolCallId: String, approved: Boolean, reason: String) -> Unit)?,
    onToolAnswer: ((toolCallId: String, answer: String) -> Unit)?,
    onDismissRequest: () -> Unit,
) {
    var currentPage by remember { mutableStateOf<ThinkingSheetPage>(ThinkingSheetPage.Overview) }

    ModalBottomSheet(
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
        onDismissRequest = onDismissRequest,
    ) {
        BackHandler(enabled = currentPage !is ThinkingSheetPage.Overview) {
            currentPage = ThinkingSheetPage.Overview
        }

        AnimatedContent(
            targetState = currentPage,
            transitionSpec = {
                val forward = targetState is ThinkingSheetPage.ToolDetail && initialState is ThinkingSheetPage.Overview
                if (forward) {
                    slideInHorizontally { it } + fadeIn() togetherWith
                        slideOutHorizontally { -it } + fadeOut()
                } else {
                    slideInHorizontally { -it } + fadeIn() togetherWith
                        slideOutHorizontally { it } + fadeOut()
                }
            },
            label = "ThinkingBlockSheetPage",
        ) { page ->
            when (page) {
                is ThinkingSheetPage.Overview -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 640.dp)
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp),
                    ) {
                        SheetHeader(title = "执行步骤 (${steps.size})")
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            ChainOfThought(
                                steps = steps,
                                collapsedVisibleCount = steps.size,
                                cardColors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                            ) { step ->
                                val stepIndex = steps.indexOf(step)
                                when (step) {
                                    is ThinkingStep.ReasoningStep -> {
                                        key(step.reasoning.createdAt) {
                                            ChatMessageReasoningStep(
                                                reasoning = step.reasoning,
                                                model = model,
                                                assistant = assistant,
                                            )
                                        }
                                    }

                                    is ThinkingStep.ToolStep -> {
                                        key(step.tool.toolCallId.ifBlank { step.hashCode().toString() }) {
                                            ChatMessageToolStep(
                                                tool = step.tool,
                                                loading = loading && !step.tool.isExecuted,
                                                assistant = assistant,
                                                onToolApproval = onToolApproval,
                                                onToolAnswer = onToolAnswer,
                                                onOpenDetail = if (stepIndex >= 0) {
                                                    { currentPage = ThinkingSheetPage.ToolDetail(stepIndex) }
                                                } else null,
                                            )
                                        }
                                    }

                                    is ThinkingStep.ServerToolStep -> {
                                        key(step.tool.toolCallId.ifBlank { step.hashCode().toString() }) {
                                            ChatMessageServerToolStep(
                                                tool = step.tool,
                                                onOpenDetail = if (stepIndex >= 0) {
                                                    { currentPage = ThinkingSheetPage.ToolDetail(stepIndex) }
                                                } else null,
                                            )
                                        }
                                    }

                                    is ThinkingStep.NoticeStep -> {
                                        ChatMessageNoticeStep(notice = step.notice)
                                    }
                                }
                            }
                        }
                    }
                }

                is ThinkingSheetPage.ToolDetail -> {
                    val step = steps.getOrNull(page.stepIndex)
                    val detailTitle = when (step) {
                        is ThinkingStep.ServerToolStep -> remember(step.tool) { parseServerTool(step.tool).title }
                        is ThinkingStep.ToolStep -> {
                            val renderer = remember(step.tool.toolName) { ToolUIRegistry.resolve(step.tool.toolName) }
                            val context = remember(step.tool, assistant) {
                                ToolUIContext(
                                    tool = step.tool,
                                    arguments = step.tool.inputAsJson(),
                                    content = null,
                                    loading = loading && !step.tool.isExecuted,
                                    assistant = assistant,
                                )
                            }
                            renderer.title(context)
                        }
                        else -> stringResource(R.string.chat_message_tool_call_title)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                    ) {
                        SheetHeader(
                            title = detailTitle,
                            navigationIcon = {
                                IconButton(onClick = { currentPage = ThinkingSheetPage.Overview }) {
                                    Icon(
                                        imageVector = HugeIcons.ArrowLeft01,
                                        contentDescription = stringResource(R.string.back),
                                    )
                                }
                            },
                            actions = {},
                        )
                        when (step) {
                            is ThinkingStep.ServerToolStep -> {
                                ServerToolPreview(
                                    tool = step.tool,
                                    showHeader = false,
                                )
                            }

                            is ThinkingStep.ToolStep -> {
                                ToolStepDetailPreview(
                                    tool = step.tool,
                                    loading = loading && !step.tool.isExecuted,
                                    assistant = assistant,
                                    onDismissRequest = { currentPage = ThinkingSheetPage.Overview },
                                )
                            }

                            else -> Unit
                        }
                    }
                }
            }
        }
    }
}
