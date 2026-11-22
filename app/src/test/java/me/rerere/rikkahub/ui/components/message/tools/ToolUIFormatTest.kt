package me.rerere.rikkahub.ui.components.message.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolUIFormatTest {
    @Test
    fun unwrapJsonElementParsesEmbeddedJsonObjectStrings() {
        val raw = JsonPrimitive("""{"exitCode":0,"stdout":"hello"}""")
        val unwrapped = unwrapJsonElement(raw)
        assertTrue(unwrapped is JsonObject)
        assertEquals("hello", (unwrapped as JsonObject)["stdout"]?.let { (it as JsonPrimitive).content })
    }

    @Test
    fun unwrapJsonElementParsesEmbeddedJsonArrayStrings() {
        val raw = JsonPrimitive("""["a","b"]""")
        val unwrapped = unwrapJsonElement(raw)
        assertTrue(unwrapped is JsonArray)
        assertEquals(2, (unwrapped as JsonArray).size)
    }

    @Test
    fun unwrapJsonElementKeepsPlainStringsUntouched() {
        val raw = JsonPrimitive("plain text output")
        val unwrapped = unwrapJsonElement(raw)
        assertEquals(raw, unwrapped)
    }

    @Test
    fun detectLanguageFromPathMapsCommonExtensions() {
        assertEquals("kotlin", detectLanguageFromPath("/src/Main.kt"))
        assertEquals("typescript", detectLanguageFromPath("app/index.ts"))
        assertEquals("python", detectLanguageFromPath("script.py"))
        assertEquals("diff", detectLanguageFromPath("changes.diff"))
        assertEquals("text", detectLanguageFromPath(null))
    }
}
