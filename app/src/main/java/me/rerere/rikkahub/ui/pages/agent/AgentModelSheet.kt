package me.rerere.rikkahub.ui.pages.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.rikkahub.ui.components.ai.AutoAIIcon

@Composable
fun AgentModelSheet(
    selectedModel: String,
    savedModels: List<String>,
    onSelectModel: (String) -> Unit,
    onAddModel: (String) -> Boolean,
    onRemoveModel: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var customInput by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    fun submitCustom() {
        val trimmed = customInput.trim().removePrefix("models/")
        if (trimmed.isEmpty()) {
            inputError = "请输入模型名，例如 gemini-2.5-pro"
            return
        }
        if (!Regex("^[A-Za-z0-9][A-Za-z0-9._:/-]{0,109}$").matches(trimmed)) {
            inputError = "模型名格式无效"
            return
        }
        if (onAddModel(trimmed)) {
            customInput = ""
            inputError = null
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "选择 Agent 模型",
                style = MaterialTheme.typography.titleLarge,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = customInput,
                    onValueChange = {
                        customInput = it
                        inputError = null
                    },
                    label = { Text("手动添加 Gemini 模型名") },
                    placeholder = { Text("如 gemini-2.5-pro / gemini-3-flash-preview") },
                    isError = inputError != null,
                    supportingText = inputError?.let { msg -> { Text(msg) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitCustom() }),
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(
                    onClick = { submitCustom() },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Icon(
                        imageVector = HugeIcons.Add01,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(4.dp))
                    Text("添加")
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(savedModels, key = { it }) { modelName ->
                    val isSelected = modelName.equals(selectedModel, ignoreCase = true)
                    val isAntigravity = modelName.equals("antigravity", ignoreCase = true) ||
                        modelName.startsWith("antigravity-", ignoreCase = true)
                    val isProtected = modelName.equals("Antigravity", ignoreCase = true)
                    Surface(
                        onClick = {
                            onSelectModel(modelName)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AutoAIIcon(
                                name = modelName,
                                modifier = Modifier.size(28.dp),
                                color = Color.Transparent,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = modelName,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = if (isAntigravity) "Antigravity · Interactions API" else "Gemini · generateContent",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = HugeIcons.Tick01,
                                    contentDescription = "已选择",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            if (!isProtected) {
                                IconButton(
                                    onClick = { onRemoveModel(modelName) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = HugeIcons.Delete01,
                                        contentDescription = "移除模型",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
