package me.rerere.rikkahub.data.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.ui.ServerToolStatus
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.ui.components.message.tools.ServerToolBlock
import me.rerere.rikkahub.ui.components.message.tools.ServerToolKind
import me.rerere.rikkahub.ui.components.message.tools.inferCodeLanguage
import me.rerere.rikkahub.ui.components.message.tools.parseServerTool
import me.rerere.rikkahub.ui.components.message.tools.unwrapJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerToolFormatTest {
    @Test
    fun `parses antigravity code_execution into code block and output block`() {
        val tool = UIMessagePart.ServerTool(
            toolCallId = "exec_1",
            toolName = "code_execution",
            input = Json.parseToJsonElement("""{"language":"PYTHON","code":"print(42)"}"""),
            output = Json.parseToJsonElement("""{"outcome":"OUTCOME_OK","output":"42\n"}"""),
            status = ServerToolStatus.COMPLETED,
        )

        val parsed = parseServerTool(tool)
        assertEquals(ServerToolKind.CODE_EXECUTION, parsed.kind)
        assertEquals("执行代码 (python)", parsed.title)
        assertFalse(parsed.isError)
        assertTrue(parsed.badges.contains("PYTHON"))
        assertTrue(parsed.inputBlocks.any { it is ServerToolBlock.Code && it.language == "python" && it.code == "print(42)" })
        assertTrue(parsed.outputBlocks.any { it is ServerToolBlock.Code && it.code == "42\n" })
    }

    @Test
    fun `unwraps double-encoded json string in antigravity tool output`() {
        val wrapped = JsonPrimitive("""{"stdout":"ok","exit_code":0,"duration_ms":250}""")
        val tool = UIMessagePart.ServerTool(
            toolCallId = "exec_2",
            toolName = "code_execution",
            input = JsonPrimitive("""{"code":"echo ok","language":"bash"}"""),
            output = wrapped,
            status = ServerToolStatus.COMPLETED,
        )

        val parsed = parseServerTool(tool)
        assertEquals("250ms", parsed.durationLabel)
        assertEquals(0, parsed.exitCode)
        assertTrue(parsed.inputBlocks.any { it is ServerToolBlock.Code && it.language == "bash" && it.code == "echo ok" })
        assertTrue(parsed.outputBlocks.any { it is ServerToolBlock.Code && it.code == "ok" })
    }

    @Test
    fun `parses codex commandExecution with exitCode and duration`() {
        val tool = UIMessagePart.ServerTool(
            toolCallId = "cmd_1",
            toolName = "commandExecution",
            input = Json.parseToJsonElement("""{"command":"ls -la","cwd":"/workspace"}"""),
            output = Json.parseToJsonElement("""{"exitCode":1,"durationMs":1500,"aggregatedOutput":"not found"}"""),
            status = ServerToolStatus.COMPLETED,
            metadata = buildJsonObject { put("providerType", "commandExecution") },
        )

        val parsed = parseServerTool(tool)
        assertEquals(ServerToolKind.COMMAND_EXECUTION, parsed.kind)
        assertEquals("执行命令: ls -la", parsed.title)
        assertEquals("1.5s", parsed.durationLabel)
        assertEquals(1, parsed.exitCode)
        assertTrue(parsed.isError)
        assertTrue(parsed.badges.contains("退出码 1"))
        assertTrue(parsed.badges.contains("目录: /workspace"))
    }

    @Test
    fun `parses google_search queries and results`() {
        val tool = UIMessagePart.ServerTool(
            toolCallId = "search_1",
            toolName = "google_search",
            input = Json.parseToJsonElement("""{"queries":["kotlin compose","material 3"]}"""),
            output = Json.parseToJsonElement("""{"results":[{"title":"Kotlin","url":"https://kotlinlang.org","snippet":"Docs"}]}"""),
            status = ServerToolStatus.COMPLETED,
        )

        val parsed = parseServerTool(tool)
        assertEquals(ServerToolKind.WEB_SEARCH, parsed.kind)
        assertEquals("搜索: kotlin compose", parsed.title)
        assertTrue(parsed.inputBlocks.any { it is ServerToolBlock.Chips && it.items == listOf("kotlin compose", "material 3") })
        assertTrue(parsed.outputBlocks.any { it is ServerToolBlock.SearchResults && it.results.size == 1 })
    }

    @Test
    fun `infers code language from path and hint`() {
        assertEquals("kotlin", inferCodeLanguage(pathHint = "Main.kt"))
        assertEquals("python", inferCodeLanguage(languageHint = "PY"))
        assertEquals("bash", inferCodeLanguage(languageHint = "SHELL"))
        assertEquals("diff", inferCodeLanguage(pathHint = "changes.patch"))
        assertEquals("json", unwrapJsonElement(JsonPrimitive("""{"a":1}"""))?.let { "json" })
    }
}
