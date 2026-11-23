package me.rerere.rikkahub.ui.components.message.tools

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.ui.ServerToolStatus
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerToolFormatterTest {

    @Test
    fun `formats codex commandExecution with bash command and exit code`() {
        val tool = UIMessagePart.ServerTool(
            toolCallId = "call-1",
            toolName = "commandExecution",
            input = buildJsonObject {
                put("command", "./gradlew test")
                put("cwd", "/workspace/app")
            },
            output = buildJsonObject {
                put("exitCode", 0)
                put("durationMs", 1250)
                put("aggregatedOutput", "BUILD SUCCESSFUL")
            },
            status = ServerToolStatus.COMPLETED,
            metadata = buildJsonObject {
                put("providerType", "commandExecution")
            },
        )

        val formatted = formatServerTool(tool)
        assertEquals(ServerToolCategory.COMMAND_EXECUTION, formatted.category)
        assertFalse(formatted.isError)
        assertEquals("退出码 0 · 1250 ms", formatted.statusText)
        assertTrue(formatted.inputSections.any { it is ToolDetailSection.Code && it.language == "bash" && it.code == "./gradlew test" })
        assertTrue(formatted.outputSections.any { it is ToolDetailSection.Code && it.code == "BUILD SUCCESSFUL" })
    }

    @Test
    fun `formats antigravity code_execution with java and kotlin detection`() {
        val kotlinTool = UIMessagePart.ServerTool(
            toolCallId = "exec-kt",
            toolName = "code_execution",
            input = buildJsonObject {
                put("code", "data class User(val name: String)\nfun main() {\n    println(User(\"Alice\"))\n}")
            },
            output = buildJsonObject {
                put("outcome", "OUTCOME_OK")
                put("output", "User(name=Alice)")
            },
            status = ServerToolStatus.COMPLETED,
        )

        val formattedKt = formatServerTool(kotlinTool)
        assertEquals(ServerToolCategory.CODE_EXECUTION, formattedKt.category)
        assertEquals("成功", formattedKt.statusText)
        val ktCode = formattedKt.inputSections.filterIsInstance<ToolDetailSection.Code>().single()
        assertEquals("kotlin", ktCode.language)

        val javaTool = UIMessagePart.ServerTool(
            toolCallId = "exec-java",
            toolName = "code_execution",
            input = buildJsonObject {
                put("code", "public class Main {\n    public static void main(String[] args) {\n        System.out.println(\"Hi\");\n    }\n}")
            },
            output = buildJsonObject {
                put("outcome", "OUTCOME_FAILED")
                put("stderr", "Compilation error")
            },
            status = ServerToolStatus.FAILED,
        )

        val formattedJava = formatServerTool(javaTool)
        assertTrue(formattedJava.isError)
        val javaCode = formattedJava.inputSections.filterIsInstance<ToolDetailSection.Code>().single()
        assertEquals("java", javaCode.language)
        assertTrue(formattedJava.outputSections.any { it is ToolDetailSection.Text && it.isError && it.text == "Compilation error" })
    }

    @Test
    fun `formats antigravity read_file and edit_file with kotlin and java extensions`() {
        val readKt = UIMessagePart.ServerTool(
            toolCallId = "read-1",
            toolName = "read_file",
            input = buildJsonObject {
                put("file_path", "app/src/MainActivity.kt")
                put("start_line", 1)
                put("end_line", 20)
            },
            output = JsonPrimitive("{\"content\":\"class MainActivity\"}"),
            status = ServerToolStatus.COMPLETED,
        )

        val formattedRead = formatServerTool(readKt)
        assertEquals(ServerToolCategory.FILE_READ, formattedRead.category)
        assertEquals("读取 MainActivity.kt", formattedRead.title)
        val codeSection = formattedRead.outputSections.filterIsInstance<ToolDetailSection.Code>().single()
        assertEquals("kotlin", codeSection.language)
        assertEquals("class MainActivity", codeSection.code)

        val editJava = UIMessagePart.ServerTool(
            toolCallId = "edit-1",
            toolName = "edit_file",
            input = buildJsonObject {
                put("path", "src/Helper.java")
                put("old_str", "int x = 1;")
                put("new_str", "int x = 2;")
            },
            output = buildJsonObject {
                put("message", "Updated successfully")
            },
            status = ServerToolStatus.COMPLETED,
        )

        val formattedEdit = formatServerTool(editJava)
        assertEquals(ServerToolCategory.FILE_EDIT, formattedEdit.category)
        assertEquals("编辑 Helper.java", formattedEdit.title)
        assertTrue(formattedEdit.inputSections.any { it is ToolDetailSection.Diff && it.diff.contains("-int x = 1;") && it.diff.contains("+int x = 2;") })
    }

    @Test
    fun `formats google_search without showing raw json`() {
        val search = UIMessagePart.ServerTool(
            toolCallId = "gs-1",
            toolName = "google_search",
            input = buildJsonObject {
                put("queries", buildJsonArray { add(JsonPrimitive("kotlin coroutines")) })
            },
            output = buildJsonArray {
                add(buildJsonObject {
                    put("title", "Kotlin Coroutines Guide")
                    put("url", "https://kotlinlang.org/docs/coroutines-guide.html")
                })
            },
            status = ServerToolStatus.COMPLETED,
        )

        val formatted = formatServerTool(search)
        assertEquals(ServerToolCategory.WEB_SEARCH, formatted.category)
        assertTrue(formatted.inputSections.any { it is ToolDetailSection.Chips && it.items == listOf("kotlin coroutines") })
        val textOut = formatted.outputSections.filterIsInstance<ToolDetailSection.Text>().single()
        assertTrue(textOut.text.contains("Kotlin Coroutines Guide"))
    }

    @Test
    fun `exposes stepDurationText without exit code and defaults command output to bash`() {
        val tool = UIMessagePart.ServerTool(
            toolCallId = "cmd-1",
            toolName = "commandExecution",
            input = buildJsonObject { put("command", "ls -la") },
            output = buildJsonObject {
                put("exitCode", 0)
                put("durationMs", 1250)
                put("aggregatedOutput", "total 12\ndrwxr-xr-x 2 root root 40")
            },
            status = ServerToolStatus.COMPLETED,
        )

        val formatted = formatServerTool(tool)
        assertEquals("1.3 s", formatted.stepDurationText)
        assertEquals("退出码 0 · 1250 ms", formatted.statusText)
        val outCode = formatted.outputSections.filterIsInstance<ToolDetailSection.Code>().single()
        assertEquals("bash", outCode.language)
    }

    @Test
    fun `applies decision classification to bash read of typescript file`() {
        val classification = me.rerere.rikkahub.data.agent.AgentStepClassification(
            operation = me.rerere.rikkahub.data.agent.AgentStepOperation.FILE_READ,
            source = me.rerere.rikkahub.data.agent.AgentToolSource.SHELL,
            label = "读取 index.ts",
            targetPath = "src/index.ts",
            inputLanguage = "bash",
            outputLanguage = "typescript",
            renderMode = me.rerere.rikkahub.data.agent.AgentStepRenderMode.CODE,
        )
        val tool = UIMessagePart.ServerTool(
            toolCallId = "cmd-read-ts",
            toolName = "commandExecution",
            input = buildJsonObject { put("command", "sed -n '1,20p' src/index.ts") },
            output = buildJsonObject {
                put("exitCode", 0)
                put("aggregatedOutput", "export const hello = 'world';")
            },
            status = ServerToolStatus.COMPLETED,
            metadata = buildJsonObject {
                put(me.rerere.rikkahub.data.agent.METADATA_STEP_CLASSIFICATION, classification.toJsonObject())
            },
        )

        val formatted = formatServerTool(tool)
        assertEquals(ServerToolCategory.FILE_READ, formatted.category)
        assertEquals("读取 index.ts", formatted.title)
        assertEquals(null, formatted.stepDurationText)
        val outCode = formatted.outputSections.filterIsInstance<ToolDetailSection.Code>().single()
        assertEquals("typescript", outCode.language)
    }
}
