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

    @Test
    fun `uses only the PocketBase parent to continue a conversation`() {
        val payload = PocketBaseAgentCloudClient.buildSubmitJobPayload(
            jobId = "job126",
            config = RemoteAgentConfig(model = "Antigravity"),
            prompt = "follow up",
            sources = emptyList(),
            parentMessageId = "msg_prev",
        )

        assertEquals("msg_prev", payload["parentMessage"]?.jsonPrimitive?.content)
        assertNull(payload["environmentId"])
        assertNull(payload["previousInteractionId"])
    }

    @Test
    fun `forwards explicit Gemini model and thinkingLevel and omits agentModel`() {
        val payload = PocketBaseAgentCloudClient.buildSubmitJobPayload(
            jobId = "job127",
            config = RemoteAgentConfig(
                agentModel = "gemini-4-hydrogen",
                model = "Antigravity",
                thinkingLevel = "low",
            ),
            prompt = "summarize",
            sources = emptyList(),
            modelOverride = "gemini-2.5-pro",
            thinkingLevelOverride = "high",
        )

        assertEquals("gemini-2.5-pro", payload["model"]?.jsonPrimitive?.content)
        assertEquals("high", payload["thinkingLevel"]?.jsonPrimitive?.content)
        assertNull(payload["agentModel"])
    }

    @Test
    fun `forwards Codex model with agentModelOverride and thinkingLevel`() {
        val payload = PocketBaseAgentCloudClient.buildSubmitJobPayload(
            jobId = "job128",
            config = RemoteAgentConfig(
                agentModel = "gemini-4-hydrogen",
                model = "Antigravity",
                thinkingLevel = "low",
            ),
            prompt = "fix tests",
            sources = emptyList(),
            modelOverride = "Codex",
            thinkingLevelOverride = "high",
            agentModelOverride = "gpt-5.3-codex",
        )

        assertEquals("Codex", payload["model"]?.jsonPrimitive?.content)
        assertEquals("gpt-5.3-codex", payload["agentModel"]?.jsonPrimitive?.content)
        assertEquals("high", payload["thinkingLevel"]?.jsonPrimitive?.content)
    }

    @Test
    fun `overrides Antigravity agentModel when agentModelOverride is provided`() {
        val payload = PocketBaseAgentCloudClient.buildSubmitJobPayload(
            jobId = "job129",
            config = RemoteAgentConfig(
                agentModel = "gemini-4-hydrogen",
                model = "Antigravity",
            ),
            prompt = "analyze",
            sources = emptyList(),
            modelOverride = "Antigravity",
            agentModelOverride = "gemini-4-carbon",
        )

        assertEquals("Antigravity", payload["model"]?.jsonPrimitive?.content)
        assertEquals("gemini-4-carbon", payload["agentModel"]?.jsonPrimitive?.content)
    }

    @Test
    fun `omits agentModel for Codex when agentModelOverride is blank`() {
        val payload = PocketBaseAgentCloudClient.buildSubmitJobPayload(
            jobId = "job130",
            config = RemoteAgentConfig(
                agentModel = "gemini-4-hydrogen",
                model = "Antigravity",
            ),
            prompt = "inspect",
            sources = emptyList(),
            modelOverride = "Codex",
            agentModelOverride = "",
        )

        assertEquals("Codex", payload["model"]?.jsonPrimitive?.content)
        assertNull(payload["agentModel"])
    }
}

