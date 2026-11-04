package app.kestrel.ui.screens.lot

import app.kestrel.model.Money
import app.kestrel.model.Snipe
import app.kestrel.model.TimelineEvent
import app.kestrel.model.TimelineKind
import app.kestrel.platform.SnipeJobAftermath
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.time.Instant

/**
 * What a snipe's timeline proves, read once per moment: when the bid left and with what amount, eBay's reply and its
 * delay, the dry run, the clock check and the extensions. Times are UTC epoch milliseconds, preferring the true-time
 * instants the engine stores in each event's detail. Events after [nowMillis] are left out, so no screen states a step
 * before it happens (rule MF31).
 */
class LotEvidence(
    val snipe: Snipe,
    events: List<TimelineEvent>,
    val nowMillis: Long = Long.MAX_VALUE,
) {
    val history: List<TimelineEvent> = events.filter { it.atUtcMillis <= nowMillis }.sortedBy { it.atUtcMillis }

    private val bidSentEvent: TimelineEvent? = history.lastOrNull { it.kind == TimelineKind.BID_SENT }

    /** When the bid left the phone, or null when no bid was sent. */
    val sentAtMillis: Long? =
        bidSentEvent?.let { it.detailLong("sentAtUtcMillis") ?: it.atUtcMillis } ?: snipe.result?.bidSentAtUtcMillis

    val sentAt: Instant? = sentAtMillis?.let(Instant::ofEpochMilli)

    /** The amount the bid carried: the max at the moment it left. */
    val sentAmount: Money? = bidSentEvent?.detailMoney("amount") ?: snipe.maxBid.takeIf { sentAtMillis != null }

    /** How long before the end the bid left. */
    val leadMillis: Long? = bidSentEvent?.detailLong("leadMillis") ?: snipe.result?.bidLeadMillis

    private val replyEvent: TimelineEvent? =
        history.lastOrNull { it.kind == TimelineKind.EBAY_REPLY && it.detailText("reply") != null }

    /** eBay's reply type: "Leading", "Outbid", "Rejected" or "Unconfirmed". */
    val replyType: String? = replyEvent?.detailText("reply")

    /** How long eBay took to reply. */
    val replyMillis: Long? = replyEvent?.detailLong("latencyMillis") ?: snipe.result?.replyLatencyMillis

    /**
     * True while eBay's latest word on the bid is "in the lead": the latest EBAY_REPLY line of any kind (the live
     * price passing the max writes one too) says Leading, and the last price seen is not above the max.
     */
    val stillLeading: Boolean =
        history.lastOrNull { it.kind == TimelineKind.EBAY_REPLY }?.detailText("reply") == "Leading" &&
            !priceAboveMax(snipe)

    /** eBay's own words in its reply, quoted exactly. */
    val replyText: String? = replyEvent?.let { quotedText(it.message) } ?: snipe.result?.ebayReplyText

    /** The latest bid window: its opening proves Kestrel followed the final minute. */
    val windowOpened: TimelineEvent? = history.lastOrNull { it.kind == TimelineKind.WINDOW_OPENED }

    /** The end the window opened against, before any extension. */
    val endAtWindowMillis: Long? =
        windowOpened?.let {
            it.detailLong("endUtcMillis") ?: history.firstExtensionPreviousEnd()
                ?: snipe.endTimeUtcMillis
        }

    val extensions: List<TimelineEvent> = history.filter { it.kind == TimelineKind.EXTENDED }

    /** The latest clock check against Google time, as an offset in milliseconds. */
    val clockOffsetMillis: Long? =
        history
            .lastOrNull { event ->
                event.kind == TimelineKind.CLOCK_SYNCED &&
                    event.detailText("source")?.lowercase()?.startsWith("sntp") == true
            }?.detailLong("offsetMillis")

    /** The latest moment eBay showed the session signed in for this lot. */
    val signedInSeenAtMillis: Long? =
        history
            .lastOrNull { event ->
                when (event.kind) {
                    TimelineKind.PAGE_READY -> event.detailText("signedIn") != "false"

                    // The bid layer, a sent bid and eBay's answer to it all need a signed-in session.
                    TimelineKind.LAYER_READY, TimelineKind.BID_SENT -> true

                    TimelineKind.EBAY_REPLY -> event.detailText("reply") in setOf("Leading", "Outbid")

                    TimelineKind.DRY_RUN -> event.detailText("reason") == null

                    else -> false
                }
            }?.atUtcMillis

    val result: TimelineEvent? = history.lastOrNull { it.kind == TimelineKind.RESULT }

    /** When the snipe was cancelled or skipped by its group, as the CANCELLED event says. */
    val cancelledAtMillis: Long? = history.lastOrNull { it.kind == TimelineKind.CANCELLED }?.atUtcMillis

    /** The dry run, once it has run: the snipe's own record, never one stamped later than now. */
    val dryRunPassedAtMillis: Long? =
        snipe.dryRunAtUtcMillis?.takeIf { snipe.dryRunPassed == true && it <= nowMillis }

    val dryRunDone: Boolean = snipe.dryRunAtUtcMillis?.let { it <= nowMillis } == true

    val resultOutcome: String? = result?.detailText("outcome")

    /** Kestrel stopped reading the result, three days after the end (SnipeJobAftermath): only the user can say now. */
    val resultGivenUp: Boolean =
        history.any { it.kind == TimelineKind.PROBLEM && it.message == SnipeJobAftermath.RESULT_UNREAD }

    private fun List<TimelineEvent>.firstExtensionPreviousEnd(): Long? =
        firstOrNull { it.kind == TimelineKind.EXTENDED }?.detailLong("previousEndUtcMillis")

    companion object {
        private fun priceAboveMax(snipe: Snipe): Boolean {
            val price = snipe.lastPrice ?: return false
            val max = snipe.maxBid ?: return false
            return price.currency == max.currency && price > max
        }

        /** The text between the first and the last double quote: eBay's words inside an engine message. */
        fun quotedText(message: String): String? {
            val first = message.indexOf('"')
            val last = message.lastIndexOf('"')
            return if (first in 0 until last) message.substring(first + 1, last).takeIf { it.isNotBlank() } else null
        }
    }
}

private val DetailJson = Json { ignoreUnknownKeys = true }

private fun TimelineEvent.detailObject(): JsonObject? {
    val text = detail ?: return null
    return try {
        DetailJson.parseToJsonElement(text).jsonObject
    } catch (error: SerializationException) {
        null
    } catch (error: IllegalArgumentException) {
        null
    }
}

/** A primitive of the event's detail JSON, as text; null when absent or JSON null. */
fun TimelineEvent.detailText(key: String): String? = (detailObject()?.get(key) as? JsonPrimitive)?.contentOrNull

fun TimelineEvent.detailLong(key: String): Long? = (detailObject()?.get(key) as? JsonPrimitive)?.longOrNull

/** Amounts in the engine's detail JSON read "GBP 120.00". */
fun TimelineEvent.detailMoney(key: String): Money? {
    val parts = detailText(key)?.split(' ') ?: return null
    if (parts.size != 2) return null
    return try {
        Money.of(parts[1], parts[0])
    } catch (error: NumberFormatException) {
        null
    }
}

// eBay's money format: "£1,234.56", "US$69.99", "€7.50", "CA$12.00", "CHF 12.00".
private val AmountPattern = Regex("""(?:[A-Z]{2}\$|[£€]|[A-Z]{3} )\d[\d,]*\.\d{2}""")

/** Amounts written out in a message, in order: "£120.00", "US$69.99". */
fun amountsIn(message: String): List<String> = AmountPattern.findAll(message).map { it.value }.toList()
