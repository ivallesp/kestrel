package app.kestrel.web

import org.junit.Assert.assertEquals
import org.junit.Test

class ScriptResultTest {
    @Test
    fun `a string result is unquoted and unescaped`() {
        assertEquals(
            """{"ok":true,"url":"https://www.ebay.co.uk/itm/1"}""",
            ScriptResult.decode("\"{\\\"ok\\\":true,\\\"url\\\":\\\"https://www.ebay.co.uk/itm/1\\\"}\""),
        )
    }

    @Test
    fun `escapes for quotes, backslashes, newlines and unicode are decoded`() {
        assertEquals("a\"b\\c\nd£e", ScriptResult.decode("\"a\\\"b\\\\c\\nd\\u00a3e\""))
        assertEquals("", ScriptResult.decode("\"\""))
    }

    @Test
    fun `null, undefined and a missing value read as null`() {
        assertEquals("null", ScriptResult.decode(null))
        assertEquals("null", ScriptResult.decode("null"))
        assertEquals("null", ScriptResult.decode(""))
        assertEquals("null", ScriptResult.decode("  "))
    }

    @Test
    fun `other values come back as their JSON text`() {
        assertEquals("42", ScriptResult.decode("42"))
        assertEquals("true", ScriptResult.decode("true"))
        assertEquals("{}", ScriptResult.decode("{}"))
        assertEquals("""{"a":1}""", ScriptResult.decode("""{"a":1}"""))
    }

    @Test
    fun `a malformed string literal is returned as it came`() {
        assertEquals("\"unterminated", ScriptResult.decode("\"unterminated"))
    }

    @Test
    fun `a long string survives`() {
        val text = "x".repeat(300_000)
        assertEquals(text, ScriptResult.decode("\"$text\""))
    }
}
