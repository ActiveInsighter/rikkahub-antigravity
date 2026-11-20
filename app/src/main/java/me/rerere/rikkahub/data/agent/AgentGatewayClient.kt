package me.rerere.rikkahub.data.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.providers.google.GoogleProvider
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.utils.JsonInstant
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class GatewayDecisionResponse(
    val model: String,
    val decisionJson: String,
    val summaryMarkdown: String,
    val usage: TokenUsage? = null,
)

data class GatewayAgentDispatchResponse(
    val requestId: String,
    val repo: String,
    val workflow: String,
    val ref: String,
)

class AgentGatewayClient(
    private val okHttpClient: OkHttpClient,
) {
    private val googleProvider = GoogleProvider(okHttpClient)

    suspend fun streamChat(
        modelEntity: AgentModelEntity,
        messages: List<UIMessage>,
        systemPrompt: String,
        reasoningLevel: ReasoningLevel,
        temperature: Float?,
        fallbackBaseUrl: String,
        fallbackApiKey: String,
    ): Flow<StreamChunk> {
        val baseUrl = resolveGeminiBaseUrl(modelEntity.baseUrl, fallbackBaseUrl)
        val apiKey = modelEntity.apiKey.ifBlank { fallbackApiKey }.trim()
        val cleanModelId = modelEntity.modelId.trim().removePrefix("models/")

        val model = Model(
            modelId = cleanModelId,
            displayName = modelEntity.displayName.ifBlank { cleanModelId },
            abilities = listOf(ModelAbility.REASONING),
        )
        val providerSetting = ProviderSetting.Google(
            name = "Gemini Gateway",
            apiKey = apiKey,
            baseUrl = baseUrl,
            models = listOf(model),
            vertexAI = false,
            useInteractionsApi = false,
        )

        val effectiveMessages = if (systemPrompt.isNotBlank() && messages.none { it.role == MessageRole.SYSTEM }) {
            listOf(
                UIMessage(
                    role = MessageRole.SYSTEM,
                    parts = listOf(UIMessagePart.Text(systemPrompt.trim())),
                )
            ) + messages
        } else {
            messages
        }

        val params = TextGenerationParams(
            model = model,
            temperature = temperature,
            reasoningLevel = reasoningLevel,
        )

        return googleProvider.streamText(providerSetting, effectiveMessages, params)
    }

    suspend fun callDecision(
        modelEntity: AgentModelEntity,
        prompt: String,
        messages: List<UIMessage> = emptyList(),
        systemPrompt: String,
        reasoningLevel: ReasoningLevel,
        temperature: Float? = null,
        schemaJson: String?,
        fallbackBaseUrl: String,
        fallbackApiKey: String,
    ): Result<GatewayDecisionResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val rootUrl = resolveGatewayRootUrl(modelEntity.baseUrl, fallbackBaseUrl)
            val apiKey = modelEntity.apiKey.ifBlank { fallbackApiKey }.trim()
            val cleanModelId = modelEntity.modelId.trim().removePrefix("models/")

            val payload = buildJsonObject {
                put("model", cleanModelId)
                put("prompt", prompt)
                val history = messages.mapNotNull { msg ->
                    if (msg.role != MessageRole.USER && msg.role != MessageRole.ASSISTANT) return@mapNotNull null
                    val text = msg.toText().trim()
                    if (text.isEmpty()) return@mapNotNull null
                    msg.role.name.lowercase() to text
                }
                if (history.isNotEmpty()) {
                    putJsonArray("messages") {
                        for ((role, text) in history) {
                            addJsonObject {
                                put("role", role)
                                put("text", text)
                            }
                        }
                    }
                }
                if (systemPrompt.isNotBlank()) {
                    put("systemPrompt", systemPrompt)
                }
                if (temperature != null) {
                    put("temperature", temperature)
                }
                val thinking = when (reasoningLevel) {
                    ReasoningLevel.OFF, ReasoningLevel.AUTO -> ""
                    ReasoningLevel.LOW -> "low"
                    ReasoningLevel.MEDIUM -> "medium"
                    ReasoningLevel.HIGH, ReasoningLevel.XHIGH, ReasoningLevel.MAX -> "high"
                }
                if (thinking.isNotEmpty()) {
                    put("thinkingLevel", thinking)
                }
                if (!schemaJson.isNullOrBlank()) {
                    val parsedSchema = runCatching {
                        JsonInstant.parseToJsonElement(schemaJson)
                    }.getOrElse { err ->
                        error("Decision Schema JSON 格式无效: ${err.message}")
                    }
                    put("schema", parsedSchema)
                }
            }

            val reqBuilder = Request.Builder()
                .url("$rootUrl/v1/decision")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            if (apiKey.isNotEmpty()) {
                reqBuilder.header("Authorization", "Bearer $apiKey")
            }

            okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                val raw = response.body.string()
                if (!response.isSuccessful) {
                    error("Decision 请求失败 (${response.code}): ${raw.take(400)}")
                }
                val rootObj = JsonInstant.parseToJsonElement(raw).jsonObject
                val decisionElem = rootObj["decision"] ?: JsonPrimitive("")
                val prettyDecision = formatJsonElement(decisionElem)
                val returnedModel = rootObj["model"]?.jsonPrimitive?.content ?: cleanModelId
                val usageObj = rootObj["usage"] as? JsonObject
                val usage = usageObj?.let {
                    TokenUsage(
                        promptTokens = it["promptTokenCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        completionTokens = it["candidatesTokenCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        totalTokens = it["totalTokenCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    )
                }

                val summary = buildDecisionSummaryMarkdown(returnedModel, decisionElem, prettyDecision)
                GatewayDecisionResponse(
                    model = returnedModel,
                    decisionJson = prettyDecision,
                    summaryMarkdown = summary,
                    usage = usage,
                )
            }
        }
    }

    suspend fun dispatchAgent(
        modelEntity: AgentModelEntity,
        prompt: String,
        reasoningLevel: ReasoningLevel,
        fallbackBaseUrl: String,
        fallbackApiKey: String,
    ): Result<GatewayAgentDispatchResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val rootUrl = resolveGatewayRootUrl(modelEntity.baseUrl, fallbackBaseUrl)
            val apiKey = modelEntity.apiKey.ifBlank { fallbackApiKey }.trim()
            val cleanModelId = modelEntity.modelId.trim().removePrefix("models/")

            val payload = buildJsonObject {
                put("model", cleanModelId)
                put("prompt", prompt)
                val thinking = when (reasoningLevel) {
                    ReasoningLevel.OFF, ReasoningLevel.AUTO -> ""
                    ReasoningLevel.LOW -> "low"
                    ReasoningLevel.MEDIUM -> "medium"
                    ReasoningLevel.HIGH, ReasoningLevel.XHIGH, ReasoningLevel.MAX -> "high"
                }
                if (thinking.isNotEmpty()) {
                    put("thinkingLevel", thinking)
                }
            }

            val reqBuilder = Request.Builder()
                .url("$rootUrl/v1/agent/dispatch")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            if (apiKey.isNotEmpty()) {
                reqBuilder.header("Authorization", "Bearer $apiKey")
            }

            okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                val raw = response.body.string()
                if (!response.isSuccessful) {
                    error("Agent 派发失败 (${response.code}): ${raw.take(400)}")
                }
                val obj = JsonInstant.parseToJsonElement(raw).jsonObject
                GatewayAgentDispatchResponse(
                    requestId = obj["requestId"]?.jsonPrimitive?.content.orEmpty(),
                    repo = obj["repo"]?.jsonPrimitive?.content.orEmpty(),
                    workflow = obj["workflow"]?.jsonPrimitive?.content.orEmpty(),
                    ref = obj["ref"]?.jsonPrimitive?.content.orEmpty(),
                )
            }
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun resolveGeminiBaseUrl(modelBaseUrl: String, fallbackBaseUrl: String): String {
            val raw = modelBaseUrl.ifBlank { fallbackBaseUrl }.ifBlank { DEFAULT_GEMINI_GATEWAY_URL }
            val trimmed = raw.trim().trimEnd('/')
            return if (trimmed.endsWith("/v1beta", ignoreCase = true)) trimmed else "$trimmed/v1beta"
        }

        fun resolveGatewayRootUrl(modelBaseUrl: String, fallbackBaseUrl: String): String {
            val raw = modelBaseUrl.ifBlank { fallbackBaseUrl }.ifBlank { DEFAULT_GEMINI_GATEWAY_URL }
            val trimmed = raw.trim().trimEnd('/')
            return trimmed
                .removeSuffix("/v1beta")
                .removeSuffix("/v1")
                .trimEnd('/')
        }

        private fun formatJsonElement(element: JsonElement): String = when (element) {
            is JsonPrimitive -> if (element.isString) element.content else element.toString()
            else -> kotlinx.serialization.json.Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), element)
        }

        private fun buildDecisionSummaryMarkdown(
            model: String,
            decisionElem: JsonElement,
            prettyJson: String,
        ): String = buildString {
            appendLine("### 结构化决策结果 (`$model`)")
            appendLine()
            if (decisionElem is JsonObject && decisionElem.isNotEmpty()) {
                for ((k, v) in decisionElem) {
                    val valueText = when (v) {
                        is JsonPrimitive -> v.contentOrNull ?: v.toString()
                        is JsonArray -> v.joinToString(", ") { it.toString() }
                        is JsonObject -> v.toString()
                    }
                    appendLine("- **$k**: $valueText")
                }
                appendLine()
            }
            appendLine("```json")
            appendLine(prettyJson)
            append("```")
        }
    }
}
