package me.rerere.rikkahub.data.workflow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URLEncoder

private const val USER_PREFS_COLLECTION = "aw_user_preferences"
private val START_URL_REGEX = Regex("^https://[A-Za-z0-9.-]+(?::\\d{1,5})?(?:[/?#][^\\s<>\"'`\\\\]*)?$")

class WorkflowSettingsRepository(
    private val workflowRepository: WorkflowRepository,
) {
    // -----------------------------------------------------------------------
    // 快捷链接 (aw_user_preferences)
    // -----------------------------------------------------------------------

    suspend fun getUserPreferences(): Result<UserPreferencesSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            val root = workflowRepository.executeJson(
                baseUrl = session.baseUrl,
                path = "/api/collections/$USER_PREFS_COLLECTION/records",
                method = "GET",
                query = mapOf(
                    "page" to "1",
                    "perPage" to "1",
                    "skipTotal" to "true",
                    "filter" to "owner=\"${workflowRepository.quoteFilter(session.userId)}\"",
                ),
                token = session.token,
            ).jsonObject
            val items = root["items"] as? JsonArray ?: JsonArray(emptyList())
            val first = items.firstOrNull() as? JsonObject
            if (first == null) {
                UserPreferencesSnapshot()
            } else {
                val parsed = parseStartLinks(first["startLinks"])
                UserPreferencesSnapshot(
                    recordId = first["id"]?.jsonPrimitive?.contentOrNull,
                    startLinks = parsed ?: DEFAULT_WORKFLOW_START_LINKS,
                    isCustom = parsed != null,
                )
            }
        }
    }

    suspend fun saveStartLinks(
        links: List<StartLink>,
        recordId: String?,
    ): Result<UserPreferencesSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            val clean = normalizeStartLinksInput(links)
            val data = buildJsonObject {
                put(
                    "startLinks",
                    buildJsonArray {
                        clean.forEach { link ->
                            add(
                                buildJsonObject {
                                    put("id", link.id)
                                    put("label", link.label)
                                    put("url", link.url)
                                }
                            )
                        }
                    },
                )
            }
            val savedObj = if (!recordId.isNullOrBlank()) {
                workflowRepository.executeJson(
                    baseUrl = session.baseUrl,
                    path = "/api/collections/$USER_PREFS_COLLECTION/records/${encodeSegment(recordId)}",
                    method = "PATCH",
                    body = data,
                    token = session.token,
                ).jsonObject
            } else {
                val createBody = buildJsonObject {
                    put("owner", session.userId)
                    put("startLinks", data["startLinks"]!!)
                }
                workflowRepository.executeJson(
                    baseUrl = session.baseUrl,
                    path = "/api/collections/$USER_PREFS_COLLECTION/records",
                    method = "POST",
                    body = createBody,
                    token = session.token,
                ).jsonObject
            }
            val parsed = parseStartLinks(savedObj["startLinks"]) ?: clean
            UserPreferencesSnapshot(
                recordId = savedObj["id"]?.jsonPrimitive?.contentOrNull ?: recordId,
                startLinks = parsed,
                isCustom = true,
            )
        }
    }

    // -----------------------------------------------------------------------
    // 模型库 (/api/anyworkflow/models/...)
    // -----------------------------------------------------------------------

    suspend fun getModelCatalog(): Result<ModelCatalogSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            val root = workflowRepository.executeJson(
                baseUrl = session.baseUrl,
                path = "/api/anyworkflow/models/catalog",
                method = "GET",
                token = session.token,
            ).jsonObject
            val arr = (root["models"] as? JsonArray)
                ?: (root["items"] as? JsonArray)
                ?: JsonArray(emptyList())
            val entries = arr.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val id = obj["id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (id.isEmpty()) return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifEmpty { id }
                val provider = obj["provider"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val description = obj["description"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                CatalogModelEntry(id = id, name = name, provider = provider, description = description)
            }
            ModelCatalogSnapshot(
                models = entries,
                updatedAt = root["updatedAt"]?.jsonPrimitive?.contentOrNull
                    ?: root["fetchedAt"]?.jsonPrimitive?.contentOrNull
                    ?: "",
            )
        }
    }

    suspend fun listSavedModels(): Result<List<SavedAiModel>> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            val arr = workflowRepository.executeJson(
                baseUrl = session.baseUrl,
                path = "/api/anyworkflow/models/saved",
                method = "GET",
                token = session.token,
            ).jsonArray
            arr.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val owner = obj["owner"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val modelId = obj["modelId"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (id.isEmpty() || modelId.isEmpty()) return@mapNotNull null
                if (owner.isNotEmpty() && owner != session.userId) return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { modelId }
                SavedAiModel(id = id, owner = owner, modelId = modelId, name = name)
            }
        }
    }

    suspend fun saveModel(modelId: String, name: String? = null): Result<SavedAiModel> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            val cleanModelId = modelId.trim()
            if (cleanModelId.isEmpty()) throw WorkflowApiException("请输入模型 ID", 400)
            val body = buildJsonObject {
                put("modelId", cleanModelId)
                if (!name.isNullOrBlank()) put("name", name.trim())
            }
            val obj = workflowRepository.executeJson(
                baseUrl = session.baseUrl,
                path = "/api/anyworkflow/models/saved",
                method = "POST",
                body = body,
                token = session.token,
            ).jsonObject
            SavedAiModel(
                id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                owner = obj["owner"]?.jsonPrimitive?.contentOrNull ?: session.userId,
                modelId = obj["modelId"]?.jsonPrimitive?.contentOrNull ?: cleanModelId,
                name = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: cleanModelId,
            )
        }
    }

    suspend fun deleteSavedModel(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            workflowRepository.executeJson(
                baseUrl = session.baseUrl,
                path = "/api/anyworkflow/models/saved/${encodeSegment(id)}",
                method = "DELETE",
                token = session.token,
            )
            Unit
        }
    }

    suspend fun refreshModelCatalog(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            workflowRepository.executeJson(
                baseUrl = DEFAULT_REMOTE_API_URL,
                path = "/api/models/catalog/refresh",
                method = "POST",
                token = session.token,
                extraHeaders = mapOf("X-PocketBase-Url" to session.baseUrl),
                authPrefix = "Bearer ",
            )
            Unit
        }
    }

    // -----------------------------------------------------------------------
    // 运行配置 (/api/anyworkflow/runtime-defaults)
    // -----------------------------------------------------------------------

    suspend fun getRuntimeDefaults(): Result<RuntimeDefaultsSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            val root = workflowRepository.executeJson(
                baseUrl = session.baseUrl,
                path = "/api/anyworkflow/runtime-defaults",
                method = "GET",
                token = session.token,
            ).jsonObject
            parseRuntimeDefaults(root, session.userId)
        }
    }

    suspend fun saveRuntimeDefaults(
        snapshot: RuntimeDefaultsSnapshot,
    ): Result<RuntimeDefaultsSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            val b = snapshot.browser
            if (b.replyTimeoutMinutes !in 1..240) {
                throw WorkflowApiException("回复超时需在 1–240 分钟之间", 400)
            }
            if (b.randomWaitEnabled && (b.randomWaitMinSeconds < 0 || b.randomWaitMaxSeconds < b.randomWaitMinSeconds)) {
                throw WorkflowApiException("随机等待时间范围无效", 400)
            }
            if (b.deferredReplyEnabled && b.deferredReplyDelayMinutes !in 1..240) {
                throw WorkflowApiException("延迟回复时间需在 1–240 分钟之间", 400)
            }
            val cleanStartUrl = snapshot.defaultStartUrl.trim()
            if (snapshot.schemaVersion >= 3 && !START_URL_REGEX.matches(cleanStartUrl)) {
                throw WorkflowApiException("默认开始链接必须以 https:// 开头", 400)
            }

            val configObj = buildJsonObject {
                put("schemaVersion", snapshot.schemaVersion)
                put(
                    "browser",
                    buildJsonObject {
                        put("replyTimeoutMinutes", b.replyTimeoutMinutes)
                        put(
                            "randomWait",
                            buildJsonObject {
                                put("enabled", b.randomWaitEnabled)
                                put("minSeconds", b.randomWaitMinSeconds)
                                put("maxSeconds", b.randomWaitMaxSeconds)
                            },
                        )
                        put(
                            "deferredReply",
                            buildJsonObject {
                                put("enabled", b.deferredReplyEnabled)
                                put("delayMinutes", b.deferredReplyDelayMinutes)
                            },
                        )
                    },
                )
                if (snapshot.schemaVersion >= 3) {
                    put("defaultStartUrl", cleanStartUrl)
                }
            }

            val body = buildJsonObject {
                put("config", configObj)
                put("expectedRevision", snapshot.revision)
            }
            val root = workflowRepository.executeJson(
                baseUrl = session.baseUrl,
                path = "/api/anyworkflow/runtime-defaults",
                method = "PUT",
                body = body,
                token = session.token,
            ).jsonObject
            parseRuntimeDefaults(root, session.userId)
        }
    }

    private fun parseRuntimeDefaults(obj: JsonObject, expectedOwner: String): RuntimeDefaultsSnapshot {
        val owner = obj["owner"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (owner.isNotEmpty() && owner != expectedOwner) {
            throw WorkflowApiException("运行配置归属无效", 502)
        }
        val schemaVersion = obj["schemaVersion"]?.jsonPrimitive?.intOrNull ?: 1
        val revision = obj["revision"]?.jsonPrimitive?.intOrNull ?: 0
        val cfg = obj["config"] as? JsonObject
        val browserObj = cfg?.get("browser") as? JsonObject
        val randomWaitObj = browserObj?.get("randomWait") as? JsonObject
        val deferredObj = browserObj?.get("deferredReply") as? JsonObject

        val browser = BrowserRuntimeConfig(
            replyTimeoutMinutes = browserObj?.get("replyTimeoutMinutes")?.jsonPrimitive?.intOrNull ?: 30,
            randomWaitEnabled = randomWaitObj?.get("enabled")?.jsonPrimitive?.booleanOrNull ?: false,
            randomWaitMinSeconds = randomWaitObj?.get("minSeconds")?.jsonPrimitive?.intOrNull ?: 0,
            randomWaitMaxSeconds = randomWaitObj?.get("maxSeconds")?.jsonPrimitive?.intOrNull ?: 0,
            deferredReplyEnabled = deferredObj?.get("enabled")?.jsonPrimitive?.booleanOrNull ?: false,
            deferredReplyDelayMinutes = deferredObj?.get("delayMinutes")?.jsonPrimitive?.intOrNull ?: 10,
        )
        val defaultStartUrl = cfg?.get("defaultStartUrl")?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() } ?: "https://chatgpt.com/"

        return RuntimeDefaultsSnapshot(
            owner = owner.ifEmpty { expectedOwner },
            schemaVersion = schemaVersion.coerceIn(1, 4),
            revision = revision.coerceAtLeast(0),
            browser = browser,
            defaultStartUrl = defaultStartUrl,
        )
    }

    private fun parseStartLinks(element: kotlinx.serialization.json.JsonElement?): List<StartLink>? {
        val arr = element as? JsonArray ?: return null
        val result = mutableListOf<StartLink>()
        val seenUrls = mutableSetOf<String>()
        for (item in arr) {
            if (result.size >= 30) break
            val obj = item as? JsonObject ?: continue
            val label = obj["label"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val url = obj["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (label.isEmpty() || !START_URL_REGEX.matches(url) || !seenUrls.add(url)) continue
            val id = obj["id"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                ?: StartLink.createId()
            result.add(StartLink(id = id, label = label, url = url))
        }
        return result
    }

    private fun normalizeStartLinksInput(links: List<StartLink>): List<StartLink> {
        val seenUrls = mutableSetOf<String>()
        return links.mapNotNull { link ->
            val label = link.label.replace(Regex("\\s+"), " ").trim()
            val url = link.url.trim()
            if (label.isEmpty()) throw WorkflowApiException("快捷链接名称不能为空", 400)
            if (label.length > 40) throw WorkflowApiException("快捷链接名称最多 40 个字", 400)
            if (!START_URL_REGEX.matches(url)) throw WorkflowApiException("链接必须以 https:// 开头", 400)
            if (!seenUrls.add(url)) throw WorkflowApiException("存在重复的链接：$url", 400)
            StartLink(id = link.id.ifBlank { StartLink.createId() }, label = label, url = url)
        }.take(30)
    }

    private fun encodeSegment(value: String): String =
        URLEncoder.encode(value.trim(), "UTF-8").replace("+", "%20")
}
