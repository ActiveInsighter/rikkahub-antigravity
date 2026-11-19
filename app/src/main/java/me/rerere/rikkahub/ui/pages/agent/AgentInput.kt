package me.rerere.rikkahub.ui.pages.agent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.Camera01
import me.rerere.hugeicons.stroke.Files02
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.StopCircle
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.ui.components.ai.MediaFileInputRow
import me.rerere.rikkahub.ui.components.ai.chatInputHaze
import me.rerere.rikkahub.ui.components.ai.rememberChatAttachmentPickerActions
import me.rerere.rikkahub.ui.hooks.ChatInputState

@Composable
fun AgentInput(
    state: ChatInputState,
    settings: Settings,
    loading: Boolean,
    hazeState: HazeState,
    onSend: (List<UIMessagePart>) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAttachMenu by remember { mutableStateOf(false) }
    val pickerActions = rememberChatAttachmentPickerActions(
        inputState = state,
        setting = settings,
        onAttachmentAdded = {},
    )

    fun submit() {
        if (state.hasUploadingImages) return
        val parts = state.getContents()
        if (parts.isNotEmpty() && !loading) {
            onSend(parts)
            state.clearInput()
        }
    }

    val shape = RoundedCornerShape(28.dp)
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.messageContent.isNotEmpty()) {
            MediaFileInputRow(state = state)
        }

        Surface(
            shape = shape,
            color = if (settings.displaySetting.enableBlurEffect) Color.Transparent else containerColor,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .chatInputHaze(
                    hazeState = hazeState,
                    displaySetting = settings.displaySetting,
                    containerColor = containerColor,
                    shape = shape,
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                TextField(
                    state = state.textContent,
                    placeholder = {
                        Text(
                            text = "输入文件转换指令，或附加 PDF / 图片…",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 1, maxHeightInLines = 6),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = if (settings.displaySetting.sendOnEnter) ImeAction.Send else ImeAction.Default,
                    ),
                    onKeyboardAction = {
                        if (settings.displaySetting.sendOnEnter) {
                            submit()
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            if (
                                settings.displaySetting.sendOnEnter &&
                                event.type == KeyEventType.KeyDown &&
                                event.key == Key.Enter &&
                                !event.isShiftPressed
                            ) {
                                submit()
                                true
                            } else {
                                false
                            }
                        },
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showAttachMenu = true }) {
                            Icon(
                                imageVector = HugeIcons.Add01,
                                contentDescription = "添加附件",
                            )
                        }
                        DropdownMenu(
                            expanded = showAttachMenu,
                            onDismissRequest = { showAttachMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("选择文件 (PDF / 文档)") },
                                leadingIcon = { Icon(HugeIcons.Files02, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    showAttachMenu = false
                                    pickerActions.onPickFile()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("选择图片") },
                                leadingIcon = { Icon(HugeIcons.Image02, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    showAttachMenu = false
                                    pickerActions.onPickImage()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("拍照") },
                                leadingIcon = { Icon(HugeIcons.Camera01, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    showAttachMenu = false
                                    pickerActions.onTakePicture()
                                },
                            )
                        }
                    }

                    if (loading) {
                        FilledIconButton(
                            onClick = onStop,
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        ) {
                            Icon(
                                imageVector = HugeIcons.StopCircle,
                                contentDescription = "停止生成",
                            )
                        }
                    } else {
                        val canSend = !state.isEmpty() && !state.hasUploadingImages
                        FilledIconButton(
                            onClick = { submit() },
                            enabled = canSend,
                            shape = CircleShape,
                        ) {
                            Icon(
                                imageVector = HugeIcons.ArrowUp02,
                                contentDescription = "发送",
                            )
                        }
                    }
                }
            }
        }
    }
}
