package me.rerere.rikkahub.ui.components.message

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.Model
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ai.SheetHeader
import me.rerere.rikkahub.ui.components.ui.ChainOfThought

internal sealed interface ThinkingBlockSheetRoute {
    data object Overview : ThinkingBlockSheetRoute
    data class ToolDetail(val stepIndex: Int) : ThinkingBlockSheetRoute
}

@Composable
internal fun ThinkingBlockSheet(
    steps: List<ThinkingStep>,
    route: ThinkingBlockSheetRoute,
    openedFromOverview: Boolean,
    onRouteChange: (ThinkingBlockSheetRoute) -> Unit,
    onDismiss: () -> Unit,
    loading: Boolean,
    model: Model?,
    assistant: Assistant?,
    onToolApproval: ((toolCallId: String, approved: Boolean, reason: String) -> Unit)?,
    onToolAnswer: ((toolCallId: String, answer: String) -> Unit)?,
) {
    ModalBottomSheet(
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
        onDismissRequest = onDismiss,
    ) {
        BackHandler(enabled = route is ThinkingBlockSheetRoute.ToolDetail && openedFromOverview) {
            onRouteChange(ThinkingBlockSheetRoute.Overview)
        }

        AnimatedContent(
            targetState = route,
            transitionSpec = {
                if (targetState is ThinkingBlockSheetRoute.ToolDetail &&
                    initialState is ThinkingBlockSheetRoute.Overview
                ) {
                    slideInHorizontally { it } + fadeIn() togetherWith
                        slideOutHorizontally { -it } + fadeOut()
                } else {
                    slideInHorizontally { -it } + fadeIn() togetherWith
                        slideOutHorizontally { it } + fadeOut()
                }
            },
            label = "ThinkingBlockSheet",
        ) { currentRoute ->
            when (currentRoute) {
                ThinkingBlockSheetRoute.Overview -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.85f)
                            .padding(horizontal = 16.dp),
                    ) {
                        SheetHeader(
                            title = "执行步骤 (${steps.size})",
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = 16.dp),
                        ) {
                            ChainOfThought(
                                modifier = Modifier.fillMaxWidth(),
                                steps = steps,
                                collapsedVisibleCount = steps.size,
                                cardColors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                                                onShowDetails = {
                                                    if (stepIndex >= 0) {
                                                        onRouteChange(ThinkingBlockSheetRoute.ToolDetail(stepIndex))
                                                    }
                                                },
                                            )
                                        }
                                    }

                                    is ThinkingStep.ServerToolStep -> {
                                        key(step.tool.toolCallId.ifBlank { step.hashCode().toString() }) {
                                            ChatMessageServerToolStep(
                                                tool = step.tool,
                                                onShowDetails = {
                                                    if (stepIndex >= 0) {
                                                        onRouteChange(ThinkingBlockSheetRoute.ToolDetail(stepIndex))
                                                    }
                                                },
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

                is ThinkingBlockSheetRoute.ToolDetail -> {
                    val step = steps.getOrNull(currentRoute.stepIndex)
                    val title = when (step) {
                        is ThinkingStep.ToolStep -> step.tool.toolName
                        is ThinkingStep.ServerToolStep -> step.tool.toolName
                        else -> stringResource(R.string.chat_message_tool_call_title)
                    }
                    val navigateBack = {
                        if (openedFromOverview) {
                            onRouteChange(ThinkingBlockSheetRoute.Overview)
                        } else {
                            onDismiss()
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.85f)
                            .padding(horizontal = 16.dp),
                    ) {
                        SheetHeader(
                            title = title,
                            navigationIcon = {
                                IconButton(onClick = navigateBack) {
                                    Icon(
                                        imageVector = HugeIcons.ArrowLeft01,
                                        contentDescription = stringResource(R.string.back),
                                    )
                                }
                            },
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            when (step) {
                                is ThinkingStep.ToolStep -> {
                                    ToolStepDetailContent(
                                        tool = step.tool,
                                        loading = loading && !step.tool.isExecuted,
                                        assistant = assistant,
                                        onDismissRequest = navigateBack,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(bottom = 16.dp),
                                        showDefaultTitle = false,
                                    )
                                }

                                is ThinkingStep.ServerToolStep -> {
                                    ServerToolDetailContent(
                                        tool = step.tool,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(bottom = 16.dp),
                                        showTitle = false,
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
}
