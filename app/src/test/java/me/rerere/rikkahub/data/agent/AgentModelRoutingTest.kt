package me.rerere.rikkahub.data.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentModelRoutingTest {
    @Test
    fun `inferFromModelId classifies agent chat and decision models`() {
        assertEquals(AgentModelCategory.AGENT, AgentModelCategory.inferFromModelId("Antigravity"))
        assertEquals(AgentModelCategory.AGENT, AgentModelCategory.inferFromModelId("antigravity-preview-09-2026"))
        assertEquals(AgentModelCategory.DECISION, AgentModelCategory.inferFromModelId("gemini-decision"))
        assertEquals(AgentModelCategory.DECISION, AgentModelCategory.inferFromModelId("models/gemini-2.5-flash-decision"))
        assertEquals(AgentModelCategory.CHAT, AgentModelCategory.inferFromModelId("gemini-2.5-flash"))
        assertEquals(AgentModelCategory.CHAT, AgentModelCategory.inferFromModelId("gemini-2.5-pro"))
    }

    @Test
    fun `gateway url helpers normalize v1beta and root`() {
        assertEquals(
            "https://gemini.any1.tech/v1beta",
            AgentGatewayClient.resolveGeminiBaseUrl("", DEFAULT_GEMINI_GATEWAY_URL),
        )
        assertEquals(
            "https://gemini.any1.tech/v1beta",
            AgentGatewayClient.resolveGeminiBaseUrl("https://gemini.any1.tech", DEFAULT_GEMINI_GATEWAY_URL),
        )
        assertEquals(
            "https://gemini.any1.tech",
            AgentGatewayClient.resolveGatewayRootUrl("https://gemini.any1.tech/v1beta/", DEFAULT_GEMINI_GATEWAY_URL),
        )
    }

    @Test
    fun `anthropic base url always resolves to a v1 root`() {
        assertEquals(
            "https://modelflare.dev/v1",
            AgentGatewayClient.resolveAnthropicBaseUrl("https://modelflare.dev/v1", ""),
        )
        assertEquals(
            "https://modelflare.dev/v1",
            AgentGatewayClient.resolveAnthropicBaseUrl("https://modelflare.dev", ""),
        )
        assertEquals(
            "https://modelflare.dev/v1",
            AgentGatewayClient.resolveAnthropicBaseUrl("https://modelflare.dev/v1/", ""),
        )
        assertEquals(
            "https://gateway.example.test/v1",
            AgentGatewayClient.resolveAnthropicBaseUrl("https://gateway.example.test/v1beta", ""),
        )
    }
}
