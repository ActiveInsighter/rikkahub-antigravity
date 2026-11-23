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
        assertEquals("1.3s", formatted.stepDurationText)
        assertTrue(formatted.inputSections.any { it is ToolDetailSection.Code && it.language == "bash" && it.code == "./gradlew test" })
        assertTrue(formatted.outputSections.any { it is ToolDetailSection.Code && it.language == "bash" && it.code == "BUILD SUCCESSFUL" })
    }

    @Test
    fun `detects codex commandActions read and claude bash cat ts file as typescript`() {
        val codexRead = UIMessagePart.ServerTool(
            toolCallId = "call-codex-read",
            toolName = "commandExecution",
            input = buildJsonObject {
                put("command", "nl -ba src/index.ts")
                put("commandActions", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "read")
                        put("path", "src/index.ts")
                    })
                })
            },
            output = buildJsonObject {
                put("exitCode", 0)
                put("aggregatedOutput", "1\tconst x: number = 1;")
            },
            status = ServerToolStatus.COMPLETED,
        )

        val formattedCodex = formatServerTool(codexRead)
        assertEquals(ServerToolCategory.FILE_READ, formattedCodex.category)
        assertEquals("读取 index.ts", formattedCodex.title)
        assertNull(formattedCodex.stepDurationText)
        val codexOut = formattedCodex.outputSections.filterIsInstance<ToolDetailSection.Code>().single()
        assertEquals("typescript", codexOut.language)

        val claudeBashRead = UIMessagePart.ServerTool(
            toolCallId = "call-claude-read",
            toolName = "Bash",
            input = buildJsonObject {
                put("command", "cat -n src/app.ts | head -n 20")
            },
            output = buildJsonObject {
                put("exitCode", 0)
                put("duration_ms", 420)
                put("stdout", "     1\texport function hello(): string { return 'hi'; }")
            },
            status = ServerToolStatus.COMPLETED,
        )

        val formattedClaude = formatServerTool(claudeBashRead)
        assertEquals(ServerToolCategory.FILE_READ, formattedClaude.category)
        assertEquals("读取 app.ts", formattedClaude.title)
        assertEquals("420ms", formattedClaude.stepDurationText)
        val claudeOut = formattedClaude.outputSections.filterIsInstance<ToolDetailSection.Code>().single()
        assertEquals("typescript", claudeOut.language)
    }

    @Test
    fun `formats mcp calls, file add, file delete, antigravity replace, and builtin tools`() {
        val claudeMcp = UIMessagePart.ServerTool(
            toolCallId = "mcp-1",
            toolName = "mcp__github__get_issue",
            input = buildJsonObject {
                put("owner", "ActiveInsighter")
                put("repo", "rikkahub-antigravity")
                put("issue_number", 15)
            },
            output = buildJsonObject {
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", "{\"title\":\"Bug report\"}")
                    })
                })
            },
            status = ServerToolStatus.COMPLETED,
        )
        val formattedMcp = formatServerTool(claudeMcp)
        assertEquals(ServerToolCategory.MCP_CALL, formattedMcp.category)
        assertEquals("MCP: github / get_issue", formattedMcp.title)
        val mcpOut = formattedMcp.outputSections.filterIsInstance<ToolDetailSection.Code>().single()
        assertEquals("json", mcpOut.language)

        val codexDelete = UIMessagePart.ServerTool(
            toolCallId = "del-1",
            toolName = "fileChange",
            input = buildJsonObject {
                put("changes", buildJsonArray {
                    add(buildJsonObject {
                        put("path", "src/old.ts")
                        put("kind", "delete")
                    })
                })
            },
            output = buildJsonObject {
                put("status", "completed")
            },
            status = ServerToolStatus.COMPLETED,
        )
        val formattedDelete = formatServerTool(codexDelete)
        assertEquals(ServerToolCategory.FILE_DELETE, formattedDelete.category)
        assertEquals("删除 old.ts", formattedDelete.title)

        val codexAdd = UIMessagePart.ServerTool(
            toolCallId = "add-1",
            toolName = "fileChange",
            input = buildJsonObject {
                put("changes", buildJsonArray {
                    add(buildJsonObject {
                        put("path", "src/new.ts")
                        put("kind", "add")
                    })
                })
            },
            status = ServerToolStatus.COMPLETED,
        )
        val formattedAdd = formatServerTool(codexAdd)
        assertEquals(ServerToolCategory.FILE_ADD, formattedAdd.category)
        assertEquals("新建 new.ts", formattedAdd.title)

        val antigravityReplace = UIMessagePart.ServerTool(
            toolCallId = "ag-edit-1",
            toolName = "replace_file_content",
            input = buildJsonObject {
                put("TargetFile", "src/Main.kt")
                put("TargetContent", "val a = 1")
                put("ReplacementContent", "val a = 2")
            },
            status = ServerToolStatus.COMPLETED,
        )
        val formattedReplace = formatServerTool(antigravityReplace)
        assertEquals(ServerToolCategory.FILE_EDIT, formattedReplace.category)
        assertEquals("编辑 Main.kt", formattedReplace.title)
        assertTrue(formattedReplace.inputSections.any { it is ToolDetailSection.Diff && it.diff.contains("+val a = 2") })

        val todoTool = UIMessagePart.ServerTool(
            toolCallId = "todo-1",
            toolName = "TodoWrite",
            input = buildJsonObject {
                put("todos", buildJsonArray { add(JsonPrimitive("step 1")) })
            },
            status = ServerToolStatus.COMPLETED,
        )
        val formattedTodo = formatServerTool(todoTool)
        assertEquals(ServerToolCategory.BUILTIN_TOOL, formattedTodo.category)
        assertEquals("更新任务计划", formattedTodo.title)
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
}
