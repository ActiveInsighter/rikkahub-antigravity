package me.rerere.ai.util

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Keep remote environment and MCP credentials out of diagnostic logs. */
fun redactInteractionRequestForLogging(raw: String): String = runCatching {
    redactInteractionCredentials(json.parseToJsonElement(raw)).toString()
}.getOrDefault("[Interactions request body omitted]")

private fun redactInteractionCredentials(element: JsonElement): JsonElement = when (element) {
    is JsonObject -> JsonObject(element.mapValues { (key, value) ->
        // Header transforms can use arbitrary secret header names; redact the whole map.
        if (key.lowercase() in setOf(
                "headers", "transform", "env", "authorization", "proxy-authorization",
                "x-goog-api-key", "x-api-key", "api_key", "apikey", "token",
                "access_token", "refresh_token", "github_token", "gh_token",
            )
        ) JsonPrimitive("[REDACTED]") else redactInteractionCredentials(value)
    })
    is JsonArray -> JsonArray(element.map(::redactInteractionCredentials))
    else -> element
}
