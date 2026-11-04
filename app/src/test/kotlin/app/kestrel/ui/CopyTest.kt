package app.kestrel.ui

import app.kestrel.ui.components.SmallCapsRules
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.model.chipText
import app.kestrel.ui.model.detail
import app.kestrel.ui.model.label
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The copy rules every string obeys (DESIGN SC2, SC3, MF3 and MF4): curly quotes and apostrophes only, "eBay" never in
 * capitals, and small caps never hold a unit or the word eBay. Scans every string literal of the UI code and every
 * string resource, and the words of every fixture status.
 */
class CopyTest {
    private val uiSources = File("src/main/kotlin/app/kestrel/ui")
    private val resources = File("src/main/res")

    @Test
    fun uiStringLiteralsUseCurlyQuotes() {
        val offenders =
            uiSources
                .walkTopDown()
                .filter { it.extension == "kt" }
                .flatMap { file -> stringLiterals(file.readText()).map { "${file.name}: $it" } }
                .filter { it.contains('\'') || it.contains("\\\"") }
                .toList()
        assertTrue("Straight quotes in copy: $offenders", offenders.isEmpty())
    }

    @Test
    fun ebayIsNeverInCapitalsInTheCopy() {
        val pattern = Regex("""\b(EBAY|Ebay|EBay|ebay)\b""")
        val offenders =
            uiSources
                .walkTopDown()
                .filter { it.extension == "kt" }
                .flatMap { file -> stringLiterals(file.readText()).filter { pattern.containsMatchIn(it) } }
                .toList()
        assertTrue("Wrong eBay casing: $offenders", offenders.isEmpty())
    }

    @Test
    fun stringResourcesUseCurlyQuotes() {
        val values = resources.walkTopDown().filter { it.name == "strings.xml" }.toList()
        assertTrue(values.isNotEmpty())
        values.forEach { file ->
            Regex("""<string[^>]*>(.*?)</string>""").findAll(file.readText()).forEach { match ->
                val text = match.groupValues[1]
                assertFalse(
                    "${file.path}: $text",
                    text.contains('\'') || text.contains("\\\"") || text.contains("&quot;"),
                )
            }
        }
    }

    @Test
    fun everyStatusSpeaksWithCurlyQuotesAndProperEbay() {
        val fixture = CatalogueFixture
        val moments =
            listOf(
                fixture.polaroidWatching,
                fixture.canonArmed,
                fixture.omegaOverMax,
                fixture.canonSignInNeeded,
                fixture.canonCaptcha,
                fixture.canonPhoneCheck,
                fixture.canonDryRunFailed,
                fixture.canonOffline,
                fixture.canonUnreachable,
                fixture.omegaNeedsYou,
                fixture.canonFinalMinute,
                fixture.canonBidSent,
                fixture.canonInTheLead,
                fixture.canonOutbid,
                fixture.canonNoReply,
                fixture.walkmanExtended,
                fixture.canonReadingResult,
                fixture.canonWon,
                fixture.nintendo64Lost,
                fixture.gameBoyEndedEarly,
                fixture.gameBoyCancelled,
                fixture.walkmanCancelledByGroup,
                fixture.canonBidNotSent,
                fixture.canonBidFailed,
            )
        moments.forEach { moment ->
            val words =
                listOf(moment.status.label(moment.now).text, moment.status.detail(moment.now), moment.status.chipText())
            words.forEach { text ->
                assertFalse(text, text.contains('\'') || text.contains('"'))
                assertFalse(text, Regex("""\b(EBAY|Ebay|ebay)\b""").containsMatchIn(text))
            }
        }
    }

    @Test
    fun smallCapsRejectUnitsSentencesAndEbay() {
        assertTrue(SmallCapsRules.allows("Current · 14 bids"))
        assertTrue(SmallCapsRules.allows("Sunday 9 November · times in GMT"))
        assertTrue(SmallCapsRules.allows("Today · GMT"))
        assertFalse(SmallCapsRules.allows("Ends in 13 m"))
        assertFalse(SmallCapsRules.allows("Sent 6.1 s before the end"))
        assertFalse(SmallCapsRules.allows("Reply in 6s"))
        assertFalse(SmallCapsRules.allows("Checked on eBay"))
        assertFalse(SmallCapsRules.allows("A label that is far too long for small caps"))
        assertEquals(32, SmallCapsRules.MAXIMUM_LENGTH)
    }

    /** The contents of the ordinary (not raw) string literals of Kotlin [source], comments skipped. */
    private fun stringLiterals(source: String): List<String> {
        val literals = mutableListOf<String>()
        var index = 0
        while (index < source.length) {
            val character = source[index]
            when {
                source.startsWith("//", index) -> {
                    index = source.indexOf('\n', index).let { if (it < 0) source.length else it }
                }

                source.startsWith("/*", index) -> {
                    index = source.indexOf("*/", index + 2).let { if (it < 0) source.length else it + 2 }
                }

                source.startsWith("\"\"\"", index) -> {
                    index = source.indexOf("\"\"\"", index + 3).let { if (it < 0) source.length else it + 3 }
                }

                character == '\'' -> {
                    // A char literal: skip it whole, escapes included.
                    val end = source.indexOf('\'', index + if (source[index + 1] == '\\') 3 else 2)
                    index = if (end < 0) source.length else end + 1
                }

                character == '"' -> {
                    val builder = StringBuilder()
                    index++
                    while (index < source.length && source[index] != '"') {
                        if (source[index] == '\\' && index + 1 < source.length) {
                            builder.append(source, index, index + 2)
                            index += 2
                        } else {
                            builder.append(source[index])
                            index++
                        }
                    }
                    literals += builder.toString()
                    index++
                }

                else -> {
                    index++
                }
            }
        }
        return literals
    }
}
