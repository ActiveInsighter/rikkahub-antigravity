package me.rerere.rikkahub.data.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.ui.ServerToolStatus
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.utils.JsonInstant

const val METADATA_STEP_CLASSIFICATION = "stepClassification"

@Serializable
enum class AgentStepOperation {
    FILE_READ,
    FILE_CREATE,
    FILE_EDIT,
    FILE_DELETE,
    FILE_SEARCH,
    SHELL_EXEC,
    CODE_EXEC,
    WEB_SEARCH,
    WEB_FETCH,
    MCP_CALL,
    BUILTIN_TOOL,
    OTHER,
}

@Serializable
enum class AgentToolSource {
    BUILTIN,
    MCP,
    SHELL,
    UNKNOWN,
}

@Serializable
enum class AgentStepRenderMode {
    CODE,
    DIFF,
    SEARCH_LIST,
    WEB_RESULT,
}

@Serializable
data class AgentStepClassification(
    val operation: AgentStepOperation = AgentStepOperation.OTHER,
    val source: AgentToolSource = AgentToolSource.UNKNOWN,
    val label: String = "",
    val targetPath: String = "",
    val inputLanguage: String = "bash",
    val outputLanguage: String = "bash",
    val renderMode: AgentStepRenderMode = AgentStepRenderMode.CODE,
) {
    fun toJsonObject(): JsonObject =
        JsonInstant.parseToJsonElement(JsonInstant.encodeToString(this)).jsonObject
}

fun parseAgentStepClassification(element: JsonElement?): AgentStepClassification? {
    val obj = element as? JsonObject ?: return null
    val opRaw = obj["operation"]?.jsonPrimitive?.contentOrNull
    val op = AgentStepOperation.entries.firstOrNull { it.name.equals(opRaw, ignoreCase = true) }
        ?: AgentStepOperation.OTHER
    val srcRaw = obj["source"]?.jsonPrimitive?.contentOrNull
    val src = AgentToolSource.entries.firstOrNull { it.name.equals(srcRaw, ignoreCase = true) }
        ?: AgentToolSource.UNKNOWN
    val modeRaw = obj["renderMode"]?.jsonPrimitive?.contentOrNull
    val mode = AgentStepRenderMode.entries.firstOrNull { it.name.equals(modeRaw, ignoreCase = true) }
        ?: AgentStepRenderMode.CODE

    return AgentStepClassification(
        operation = op,
        source = src,
        label = obj["label"]?.jsonPrimitive?.contentOrNull.orEmpty().trim(),
        targetPath = obj["targetPath"]?.jsonPrimitive?.contentOrNull.orEmpty().trim(),
        inputLanguage = obj["inputLanguage"]?.jsonPrimitive?.contentOrNull.orEmpty().trim().ifBlank { "bash" },
        outputLanguage = obj["outputLanguage"]?.jsonPrimitive?.contentOrNull.orEmpty().trim().ifBlank { "bash" },
        renderMode = mode,
    )
}

fun stepClassificationCacheKey(tool: UIMessagePart.ServerTool): String {
    val id = tool.toolCallId.ifBlank { tool.toolName }
    val inHash = tool.input?.toString()?.hashCode() ?: 0
    val outHash = tool.output?.toString()?.hashCode() ?: 0
    return "$id:${tool.toolName}:$inHash:$outHash"
}

class AgentStepClassifier(
    private val gatewayClient: AgentGatewayClient,
    private val modelDao: AgentModelDao,
    private val settingsStore: AgentSettingsStore? = null,
) {
    suspend fun classify(tool: UIMessagePart.ServerTool): AgentStepClassification? {
        if (tool.status == ServerToolStatus.IN_PROGRESS) return null

        val now = System.currentTimeMillis()
        val modelEntity = modelDao.getById("builtin-gemini-decision")
            ?: AgentModelEntity(
                id = "builtin-gemini-decision",
                modelId = "gemini-decision",
                displayName = "Gemini Decision",
                category = AgentModelCategory.DECISION.name,
                baseUrl = DEFAULT_GEMINI_GATEWAY_URL,
                isBuiltin = true,
                createdAt = now,
                updatedAt = now,
            )

        val providerType = tool.metadata?.get("providerType")?.jsonPrimitive?.contentOrNull.orEmpty()
        val inputStr = tool.input?.toString().orEmpty().take(1500)
        val outputStr = tool.output?.toString().orEmpty().take(1200)

        val prompt = buildString {
            appendLine("请分析以下 Agent 工具执行步骤并返回结构化分类结果：")
            appendLine("- toolName: ${tool.toolName}")
            if (providerType.isNotBlank()) appendLine("- providerType: $providerType")
            appendLine("- status: ${tool.status.name}")
            if (inputStr.isNotBlank()) {
                appendLine("- input:")
                appendLine(inputStr)
            }
            if (outputStr.isNotBlank()) {
                appendLine("- output:")
                appendLine(outputStr)
            }
        }

        val fallbackUrl = settingsStore?.current?.gatewayBaseUrl ?: DEFAULT_GEMINI_GATEWAY_URL
        val fallbackKey = settingsStore?.current?.gatewayApiKey ?: ""

        val result = gatewayClient.callDecision(
            modelEntity = modelEntity,
            prompt = prompt,
            systemPrompt = SYSTEM_PROMPT,
            reasoningLevel = ReasoningLevel.OFF,
            temperature = 0f,
            schemaJson = STEP_CLASSIFICATION_SCHEMA_JSON,
            fallbackBaseUrl = fallbackUrl,
            fallbackApiKey = fallbackKey,
        )

        val response = result.getOrNull() ?: return null
        val elem = runCatching { JsonInstant.parseToJsonElement(response.decisionJson) }.getOrNull()
        return parseAgentStepClassification(elem)
    }

    companion object {
        private val SYSTEM_PROMPT = """
            你是一个 Agent 执行步骤语义分类器。根据工具名称(toolName)、providerType、输入(input)与输出(output)，判断该步骤的实际操作意图与渲染语言。
            规则：
            1. operation 必须从枚举中选择最准确的一项：
               - FILE_READ：读取文件内容（包括 read_file、view_file，以及通过 bash 执行 cat/head/tail/sed -n/nl/bat 等读文件命令）
               - FILE_CREATE：新建/写入文件（包括 write_file、create_file、touch、cat <<EOF > ... 等）
               - FILE_EDIT：修改/编辑文件（包括 edit_file、apply_patch、fileChange、sed -i 等）
               - FILE_DELETE：删除/移除文件（包括 delete_file、rm、git rm 等）
               - FILE_SEARCH：搜索文件/内容或列出目录（包括 grep、rg、glob、find、ls、list_dir）
               - SHELL_EXEC：通用终端命令执行（如构建、测试、git 状态/提交、安装依赖等）
               - CODE_EXEC：沙箱代码执行（如 code_execution、python/node 代码片段）
               - WEB_SEARCH：网页搜索（google_search、web_search 等）
               - WEB_FETCH：网页内容抓取（url_context、fetch_url、browse 等）
               - MCP_CALL：外部 MCP 工具调用（如 mcp__* 或第三方服务调用且不属于上述具体文件/网页操作）
               - BUILTIN_TOOL：官方内置辅助工具（如记忆读写、询问用户、任务规划等）
               - OTHER：其他操作
            2. source：
               - MCP：工具名含 mcp 或明显为 MCP 服务
               - SHELL：通过 bash/shell/commandExecution 执行
               - BUILTIN：Agent/模型内置工具
               - UNKNOWN：无法判断
            3. label：用简洁中文概括操作（不超过 18 个字），例如“读取 index.ts”、“修改 MainActivity.kt”、“运行单元测试”、“搜索代码”。
            4. targetPath：如果涉及读取/新增/修改/删除/搜索具体文件或目录，提取其路径（如 src/index.ts）；否则留空字符串。
            5. inputLanguage：输入代码块的 Markdown 语言标识（如 bash、python、sql、json），命令行类默认 bash。
            6. outputLanguage：输出代码块的 Markdown 语言标识。若为读文件/写文件，根据 targetPath 文件扩展名或内容推断精确语言（如 .ts/.tsx->typescript, .js->javascript, .kt/.kts->kotlin, .py->python, .json->json, .md->markdown, .yml->yaml, .xml->xml, .html->html, .css->css, .sh->bash）；若为差异对比填 diff；否则默认填 bash。
            7. renderMode：FILE_EDIT 有 diff/补丁时填 DIFF；FILE_SEARCH/WEB_SEARCH 填 SEARCH_LIST；WEB_FETCH 填 WEB_RESULT；其余（命令、读文件、写文件、代码执行等）填 CODE。
        """.trimIndent()

        private val STEP_CLASSIFICATION_SCHEMA_JSON = """
            {
              "type": "object",
              "properties": {
                "operation": {
                  "type": "string",
                  "enum": [
                    "FILE_READ", "FILE_CREATE", "FILE_EDIT", "FILE_DELETE",
                    "FILE_SEARCH", "SHELL_EXEC", "CODE_EXEC",
                    "WEB_SEARCH", "WEB_FETCH", "MCP_CALL", "BUILTIN_TOOL", "OTHER"
                  ]
                },
                "source": {
                  "type": "string",
                  "enum": ["BUILTIN", "MCP", "SHELL", "UNKNOWN"]
                },
                "label": { "type": "string" },
                "targetPath": { "type": "string" },
                "inputLanguage": { "type": "string" },
                "outputLanguage": { "type": "string" },
                "renderMode": {
                  "type": "string",
                  "enum": ["CODE", "DIFF", "SEARCH_LIST", "WEB_RESULT"]
                }
              },
              "required": ["operation", "source", "label", "targetPath", "inputLanguage", "outputLanguage", "renderMode"]
            }
        """.trimIndent()
    }
}
