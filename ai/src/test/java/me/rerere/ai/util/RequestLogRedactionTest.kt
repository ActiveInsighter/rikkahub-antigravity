package me.rerere.ai.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestLogRedactionTest {
    @Test
    fun `remote Git REST and MCP credentials never appear in request logs`() {
        val raw = """{"agent":"antigravity-preview-09-2026","environment":{"type":"remote","env":{"GH_TOKEN":"env-secret"},"network":{"allowlist":[{"domain":"github.com","transform":{"Authorization":"Basic git-secret"}},{"domain":"api.github.com","transform":{"Authorization":"Bearer rest-secret"}}]}},"tools":[{"type":"mcp_server","name":"github","headers":{"Authorization":"Bearer mcp-secret","X-Private":"custom-secret"}}]}"""
        val logged = redactInteractionRequestForLogging(raw)

        for (secret in listOf("env-secret", "git-secret", "rest-secret", "mcp-secret", "custom-secret")) {
            assertFalse("Log contains $secret", logged.contains(secret))
        }
        assertTrue(logged.contains("github.com"))
        assertTrue(logged.contains("antigravity-preview-09-2026"))
        assertTrue(logged.contains("[REDACTED]"))
        assertTrue(raw.contains("rest-secret"))
    }

    @Test
    fun `ordinary request fields remain inspectable`() {
        val raw = """{"model":"gemini-3.8-flash","stream":true,"input":"hello","generation_config":{"temperature":0.5}}"""
        assertEquals(json.parseToJsonElement(raw), json.parseToJsonElement(redactInteractionRequestForLogging(raw)))
    }

    @Test
    fun `malformed JSON is omitted instead of exposing unparsed secrets`() {
        assertEquals("[Interactions request body omitted]", redactInteractionRequestForLogging("{secret"))
    }
}
