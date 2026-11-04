package app.kestrel.web

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PageScriptTest {
    // Unit tests run in the module directory.
    private val assetSource = File("src/main/assets/${PageScript.ASSET_PATH}").readText()

    @Test
    fun `the asset declares its version`() {
        assertEquals("1", PageScript(assetSource).version)
    }

    @Test
    fun `every function Kotlin calls exists in the helper's API`() {
        val api = Regex("""var api = \{(.*?)\};""", RegexOption.DOT_MATCHES_ALL).find(assetSource)!!.groupValues[1]
        val names = Regex("""(\w+): safe\('(\w+)'""").findAll(api).map { it.groupValues[1] }.toSet()
        PageFunction.entries.forEach { function -> assertTrue(function.jsName, function.jsName in names) }
    }

    @Test
    fun `a call checks the installed version and passes JSON arguments`() {
        val script = PageScript(assetSource)
        val expression =
            script.callExpression(
                PageFunction.SUBMIT,
                listOf(buildJsonObject { put("expectValue", "12.50") }),
            )
        assertEquals(
            "(function(){var k=window.__kestrel;if(!k||k.version!=='1')" +
                "{return '{\"ok\":false,\"helperMissing\":true}';}" +
                "return k.submit({\"expectValue\":\"12.50\"});})()",
            expression,
        )
    }

    @Test
    fun `string arguments are quoted, so page text cannot break out of the call`() {
        val expression =
            PageScript(
                assetSource,
            ).callExpression(PageFunction.TYPE_MAX, listOf(JsonPrimitive("1\"); alert(1); (\"")))
        assertTrue(expression, expression.contains("k.typeMax(\"1\\\"); alert(1); (\\\"\")"))
    }

    @Test
    fun `the install expression is the whole helper`() {
        val script = PageScript(assetSource)
        assertTrue(script.installExpression.startsWith(assetSource))
        assertTrue(script.installExpression.endsWith("\n;true"))
    }

    @Test
    fun `a script without a version is refused`() {
        assertThrows(IllegalArgumentException::class.java) { PageScript("(function () {})();") }
    }

    @Test
    fun `a missing helper is installed once and the call is retried`() =
        runTest {
            val driver = FakePageDriver()
            driver.on("probe") { """{"ok":true,"url":"https://www.ebay.co.uk/itm/1"}""" }
            val helper = PageHelper(driver, PageScript("var VERSION = '1';"))
            val probe = helper.call(PageFunction.PROBE, ProbeSnapshot.serializer())
            assertEquals("https://www.ebay.co.uk/itm/1", probe.url)
            assertEquals(1, driver.installs)
            helper.call(PageFunction.PROBE, ProbeSnapshot.serializer())
            assertEquals(1, driver.installs)
        }

    @Test
    fun `unreadable answers become a script exception`() =
        runTest {
            val driver = FakePageDriver()
            driver.on("probe") { "not json" }
            driver.on("layerState") { "[1,2]" }
            driver.on("report") { """{"ok":true,"signedIn":"maybe","bidButton":7}""" }
            val helper = PageHelper(driver, PageScript("var VERSION = '1';"))
            assertTrue(runCatching { helper.call(PageFunction.PROBE) }.exceptionOrNull() is PageScriptException)
            assertTrue(runCatching { helper.call(PageFunction.LAYER_STATE) }.exceptionOrNull() is PageScriptException)
            assertTrue(
                runCatching {
                    helper.call(PageFunction.REPORT, ProbeSnapshot.serializer())
                }.exceptionOrNull() is PageScriptException,
            )
        }

    @Test
    fun `a helper that cannot be installed is a script exception`() =
        runTest {
            val driver =
                object : PageDriver by FakePageDriver() {
                    override suspend fun evaluate(
                        script: String,
                        timeoutMillis: Long,
                    ): String = if (script.endsWith("\n;true")) "true" else PageScript.HELPER_MISSING
                }
            val helper = PageHelper(driver, PageScript("var VERSION = '1';"))
            assertTrue(runCatching { helper.call(PageFunction.PROBE) }.exceptionOrNull() is PageScriptException)
        }

    @Test
    fun `a lost answer is never retried, since the function may have run`() =
        runTest {
            val driver = FakePageDriver()
            var runs = 0
            driver.on("submit") {
                runs += 1
                "null"
            }
            val helper = PageHelper(driver, PageScript("var VERSION = '1';"))
            val lost = runCatching { helper.call(PageFunction.SUBMIT) }.exceptionOrNull()
            assertTrue(lost is ScriptAnswerLostException)
            assertEquals(1, runs)
        }

    @Test
    fun `missing fields in an answer take their defaults`() {
        val layer =
            pageJson.decodeFromString(
                LayerSnapshot.serializer(),
                """{"ok":true,"open":true,"input":null,"unknownField":3}""",
            )
        assertTrue(layer.ok && layer.open)
        assertEquals(emptyList<String>(), layer.notices)
        assertEquals(null, layer.input)
    }
}
