package me.rerere.rikkahub.data.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AgentTimelineTest {
    @Test
    fun readsToolDetailsAndCitationsFromAnOrderedSnapshot() {
        val json = Json.parseToJsonElement(
            """{
                "version":1,"omittedParts":0,
                "annotations":[{"type":"url_citation","title":"Reference","url":"https://example.com"}],
                "parts":[
                    {"sequence":2,"type":"text","status":"completed","text":"Done"},
                    {"sequence":1,"type":"server_tool","status":"completed","toolName":"commandExecution",
                        "input":{"command":"printf test"},"output":{"aggregatedOutput":"test","exitCode":0}}
                ]
            }""",
        ).jsonObject
        val timeline = requireNotNull(decodeAgentTimeline(json))
        assertEquals(2, timeline.parts.size)
        assertEquals("\"server_tool\"", timeline.parts.first()["type"].toString())
        assertEquals("\"test\"", timeline.parts.first()["output"]?.jsonObject?.get("aggregatedOutput").toString())
        assertNotNull(timeline.annotations.first().jsonObject["url"])
    }

    @Test
    fun skipsUnknownSnapshotVersions() {
        val json = Json.parseToJsonElement("""{"version":2,"parts":[]}""").jsonObject
        assertNull(decodeAgentTimeline(json))
    }
}
