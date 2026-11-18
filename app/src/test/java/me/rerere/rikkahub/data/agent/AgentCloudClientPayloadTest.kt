package me.rerere.rikkahub.data.agent

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentCloudClientPayloadTest {
    @Test
    fun `keeps Antigravity as model and forwards agentModel`() {
        val payload = PocketBaseAgentCloudClient.buildSubmitJobPayload(
            jobId = "job123",
            config = RemoteAgentConfig(
                agentModel = "gemini-4-hydrogen",
                model = "Antigravity",
                systemPrompt = "sys",
            ),
            prompt = "check repo",
            sources = emptyList(),
        )

        assertEquals("Antigravity", payload["model"]?.jsonPrimitive?.content)
        assertEquals("gemini-4-hydrogen", payload["agentModel"]?.jsonPrimitive?.content)
    }

    @Test
    fun `falls back to Antigravity when remote default model is non-agent`() {
        val payload = PocketBaseAgentCloudClient.buildSubmitJobPayload(
            jobId = "job124",
            config = RemoteAgentConfig(
                agentModel = "gemini-4-carbon",
                model = "gemini-3.5-flash-lite",
            ),
            prompt = "run tools",
            sources = emptyList(),
        )

        assertEquals("Antigravity", payload["model"]?.jsonPrimitive?.content)
        assertEquals("gemini-4-carbon", payload["agentModel"]?.jsonPrimitive?.content)
    }

    @Test
    fun `preserves explicit antigravity preview model and omits blank agentModel`() {
        val payload = PocketBaseAgentCloudClient.buildSubmitJobPayload(
            jobId = "job125",
            config = RemoteAgentConfig(
                agentModel = "",
                model = "antigravity-preview-09-2026",
            ),
            prompt = "hello",
            sources = emptyList(),
        )

        assertEquals("antigravity-preview-09-2026", payload["model"]?.jsonPrimitive?.content)
        assertNull(payload["agentModel"])
    }
}
