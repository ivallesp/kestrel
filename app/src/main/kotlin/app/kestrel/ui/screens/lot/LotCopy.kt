package app.kestrel.ui.screens.lot

import app.kestrel.ebay.MoneyText
import app.kestrel.model.Money
import app.kestrel.ui.format.AuctionZone
import app.kestrel.ui.format.NARROW_NO_BREAK_SPACE
import app.kestrel.ui.format.clockFor
import app.kestrel.ui.format.dayDate
import app.kestrel.ui.format.isSameDay
import java.time.Instant
import java.time.ZoneId

// Small copy helpers the lot and result screens share. State words never live here: they come from LotStatusWords.

/**
 * The short name of a lot for snackbars: its first two words, keeping a symbol between them ("Canon AE-1",
 * "Omega Speedmaster", "Game & Watch"). Quotes around a word are dropped.
 */
fun shortTitle(title: String): String {
    val words = title.split(' ').filter { it.isNotBlank() }
    val kept = mutableListOf<String>()
    var realWords = 0
    for (word in words) {
        val cleaned = word.trim('“', '”', '"', ',', '.')
        if (cleaned.isEmpty()) continue
        kept += cleaned
        if (cleaned.any { it.isLetterOrDigit() }) realWords++
        if (realWords == 2) break
    }
    return kept.joinToString(" ").ifEmpty { title }
}

/** The currency's symbol as eBay's money format writes it: "£", "US$", "€". */
fun currencySymbol(currency: String): String =
    MoneyText
        .format(Money.zero(currency))
        .filterNot { it.isDigit() || it == '.' || it == ',' || it.isWhitespace() }
        .ifEmpty { currency }

/** "18:21:34" on the same day (seconds only within the hour), "Sun 16 Nov at 21:43" on another. */
fun momentText(
    now: Instant,
    instant: Instant,
    zone: ZoneId = AuctionZone,
): String =
    if (isSameDay(now, instant, zone)) {
        clockFor(now, instant, zone)
    } else {
        "${instant.dayDate(zone)} at ${clockFor(now, instant, zone)}"
    }

/** "10 min": minutes in prose, with the narrow no-break space of rule T5. */
fun minutesText(count: Long): String = "$count${NARROW_NO_BREAK_SPACE}min"

private val NumberAndUnit = Regex("""(\d+(?:\.\d+)?)[\u202F\u00A0 ](min|s|m|h|d)\b""")

/**
 * The same text for TalkBack, units in words: "6.1 s before the end" becomes "6.1 seconds before the end", so a
 * countdown or a receipt row is spoken as one plain sentence (rule MF47).
 */
fun speakUnits(text: String): String =
    NumberAndUnit.replace(text) { match ->
        val number = match.groupValues[1]
        val one = number == "1"
        val word =
            when (match.groupValues[2]) {
                "s" -> if (one) "second" else "seconds"
                "m", "min" -> if (one) "minute" else "minutes"
                "h" -> if (one) "hour" else "hours"
                else -> if (one) "day" else "days"
            }
        "$number $word"
    }

/** The first letter in capitals: a detail line that starts a sentence. */
fun String.sentenceCase(): String = replaceFirstChar { it.uppercase() }

/** The first letter in lower case, for a state word inside a sentence ("eBay replied: in the lead"). */
fun String.inSentence(): String = replaceFirstChar { it.lowercase() }
