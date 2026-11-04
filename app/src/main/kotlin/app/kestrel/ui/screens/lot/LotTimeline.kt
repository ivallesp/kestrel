package app.kestrel.ui.screens.lot

import app.kestrel.data.PricePoint
import app.kestrel.model.Money
import app.kestrel.model.SnipeStatus
import app.kestrel.model.TimelineEvent
import app.kestrel.model.TimelineKind
import app.kestrel.ui.components.TimelineEntry
import app.kestrel.ui.components.TimelineMoment
import app.kestrel.ui.format.AuctionZone
import app.kestrel.ui.format.NO_BREAK_SPACE
import app.kestrel.ui.format.clockFor
import app.kestrel.ui.format.clockWithSeconds
import app.kestrel.ui.format.clockWithTenths
import app.kestrel.ui.format.dayDate
import app.kestrel.ui.format.formatted
import app.kestrel.ui.format.isSameDay
import app.kestrel.ui.format.plural
import app.kestrel.ui.format.receiptTime
import app.kestrel.ui.format.secondsText
import app.kestrel.ui.format.secondsWithTenths
import app.kestrel.ui.format.withCurlyQuotes
import app.kestrel.ui.model.LotStatus
import app.kestrel.ui.model.LotTimes
import app.kestrel.ui.model.chipText
import app.kestrel.ui.model.label
import app.kestrel.ui.screens.result.ResultStateBuilder
import app.kestrel.ui.theme.StatusTone
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/**
 * The S3 timeline and the S4 receipt (DESIGN 3.10), newest first, read from what the engine and the data layer wrote.
 * Technical steps (page loads, the clock check, the bid layer) stay in the database; the rows are what a person would
 * want on a receipt: arming and edits, the dry run, price rises, the bid, eBay's reply, extensions, the end and the
 * result. State words come from the status model.
 */
object LotTimeline {
    /** Engine checks in the final minute run at most this often (eBay's own page uses the same pace). */
    private const val FINAL_MINUTE_POLL_SECONDS = 2L

    private const val FINAL_MINUTE_MILLIS = 60_000L
    private const val DRY_RUN_BEFORE_END_MILLIS = 10 * 60_000L

    /** An extension this close to Kestrel's bid plus 60 s was caused by Kestrel's bid. */
    private const val OWN_EXTENSION_TOLERANCE_MILLIS = 1_500L

    /** S3: what will happen above "Now", what happened below it, times with seconds (tenths for the bid). */
    fun live(
        evidence: LotEvidence,
        prices: List<PricePoint>,
        status: LotStatus,
        now: Instant,
        zone: ZoneId = AuctionZone,
    ): List<TimelineEntry> {
        val nowMillis = now.toEpochMilli()
        val past =
            (eventRows(evidence, Style.Live) + priceRows(prices) + listOfNotNull(finalMinuteRow(evidence, nowMillis)))
                .filter { it.atMillis <= nowMillis }
                .sortedByDescending { it.atMillis }
        val future = futureRows(evidence, status, nowMillis).sortedByDescending { it.atMillis }
        // "Now 18:21:04" alone: the chip and the card above already say the status (DESIGN 3.10).
        val nowRow = Row(nowMillis, "", moment = TimelineMoment.Now)
        return (future + nowRow + past).map { liveEntry(it, now, zone) }
    }

    /** S4: every step from arming to the result, in tenths of a second on the day of the end. */
    fun receipt(
        evidence: LotEvidence,
        now: Instant,
        zone: ZoneId = AuctionZone,
    ): List<TimelineEntry> {
        val nowMillis = now.toEpochMilli()
        val rows = eventRows(evidence, Style.Receipt) + listOfNotNull(finalMinuteRow(evidence, nowMillis))
        val ended = rows.any { it.kind == TimelineKind.ENDED }
        val endMillis = evidence.snipe.endTimeUtcMillis
        val endedEarly = evidence.resultOutcome == "ENDED_EARLY" || evidence.snipe.status == SnipeStatus.ENDED_EARLY
        val endedRow =
            when {
                ended || nowMillis < endMillis -> {
                    null
                }

                // The seller's early end is the receipt's last fact, at the moment the listing ended.
                endedEarly -> {
                    Row(endMillis, "Seller ended the listing", "Before its end time", kind = TimelineKind.ENDED)
                }

                evidence.result != null && evidence.windowOpened != null -> {
                    Row(endMillis, "Auction ended", endedWithoutReport(evidence), kind = TimelineKind.ENDED)
                }

                else -> {
                    null
                }
            }
        // Tenths on the day of the result: the end, or the cancel for a snipe that was cancelled days before.
        val resultDay = Instant.ofEpochMilli(evidence.cancelledAtMillis ?: endMillis)
        return (rows + listOfNotNull(endedRow))
            .filter { it.atMillis <= nowMillis }
            .sortedByDescending { it.atMillis }
            .map { row ->
                val time = receiptTime(Instant.ofEpochMilli(row.atMillis), resultDay, zone)
                TimelineEntry(time, row.title, row.detail, tone = row.tone, spoken = spoken(time, row))
            }
    }

    private enum class Style { Live, Receipt }

    private class Row(
        val atMillis: Long,
        val title: String,
        val detail: String? = null,
        val tone: StatusTone? = null,
        val tenths: Boolean = false,
        val moment: TimelineMoment = TimelineMoment.Past,
        val kind: TimelineKind? = null,
    )

    private fun liveEntry(
        row: Row,
        now: Instant,
        zone: ZoneId,
    ): TimelineEntry {
        val at = Instant.ofEpochMilli(row.atMillis)
        val clock =
            when {
                row.moment == TimelineMoment.Future -> clockFor(now, at, zone)
                row.tenths -> at.clockWithTenths(zone)
                else -> at.clockWithSeconds(zone)
            }
        val time = if (isSameDay(now, at, zone)) clock else "${at.dayDate(zone)} $clock"
        val spokenTime = if (row.moment == TimelineMoment.Now) "Now, $time" else time
        return TimelineEntry(time, row.title, row.detail, row.moment, row.tone, spoken(spokenTime, row))
    }

    private fun spoken(
        time: String,
        row: Row,
    ): String = speakUnits(listOfNotNull(time, row.title.ifEmpty { null }, row.detail).joinToString(", "))

    // What will happen: the dry run, the bid and the end.
    private fun futureRows(
        evidence: LotEvidence,
        status: LotStatus,
        nowMillis: Long,
    ): List<Row> {
        val snipe = evidence.snipe
        val endMillis = snipe.endTimeUtcMillis
        if (nowMillis >= endMillis || status.kind.isFinal) return emptyList()
        val rows = mutableListOf<Row>()
        val max = snipe.maxBid
        val bidPlanned = max != null && evidence.sentAtMillis == null && status.plansABid()
        val follows = bidPlanned || evidence.sentAtMillis != null
        rows +=
            Row(
                endMillis,
                "Auction ends",
                if (follows) "Kestrel keeps following if it extends" else null,
                moment = TimelineMoment.Future,
            )
        if (bidPlanned) {
            val bidAtMillis = LotTimes.bidAtUtcMillis(snipe)
            if (bidAtMillis > nowMillis) {
                rows +=
                    Row(
                        bidAtMillis,
                        "Kestrel bids ${max.formatted()}",
                        "Your max, ${secondsText(snipe.leadSeconds.toLong())} before the end",
                        moment = TimelineMoment.Future,
                    )
            }
            val dryRunMillis = endMillis - DRY_RUN_BEFORE_END_MILLIS
            if (!evidence.dryRunDone && dryRunMillis > nowMillis) {
                val detail = "Kestrel types your max on eBay’s bid panel and sends nothing"
                rows += Row(dryRunMillis, "Dry run", detail, moment = TimelineMoment.Future)
            }
        }
        return rows
    }

    /** "Final minute · checking every 2 s", once the window opened and the minute began. */
    private fun finalMinuteRow(
        evidence: LotEvidence,
        nowMillis: Long,
    ): Row? {
        val endAtWindow = evidence.endAtWindowMillis ?: return null
        val startMillis = endAtWindow - FINAL_MINUTE_MILLIS
        if (startMillis > nowMillis) return null
        return Row(
            startMillis,
            StateNames.finalMinute,
            "Checking every ${secondsText(FINAL_MINUTE_POLL_SECONDS)}",
        )
    }

    /** "Price rose to £96.00 · 16 bids" for each rise Kestrel saw. */
    private fun priceRows(prices: List<PricePoint>): List<Row> =
        prices
            .sortedBy { it.atUtcMillis }
            .zipWithNext()
            .filter { (before, after) -> after.price.currency == before.price.currency && after.price > before.price }
            .map { (_, after) ->
                val bids = plural(after.bidCount, "bid", "bids")
                Row(after.atUtcMillis, "Price rose to ${after.price.formatted()}", bids)
            }

    private fun eventRows(
        evidence: LotEvidence,
        style: Style,
    ): List<Row> = evidence.history.mapNotNull { rowOf(it, evidence, style) }

    private fun rowOf(
        event: TimelineEvent,
        evidence: LotEvidence,
        style: Style,
    ): Row? {
        val at = event.atUtcMillis
        val message = event.message
        return when (event.kind) {
            TimelineKind.ADDED -> addedRow(at, message, evidence, style)

            TimelineKind.ARMED -> armedRow(at, amountsIn(message).firstOrNull(), null, style)

            TimelineKind.EDITED -> editedRow(event)

            TimelineKind.PRICE_OVER_MAX -> priceOverMaxRow(event)

            TimelineKind.DRY_RUN -> dryRunRow(event, style)

            TimelineKind.BID_SENT -> bidSentRow(event, evidence)

            TimelineKind.EBAY_REPLY -> replyRow(event, style)

            TimelineKind.EXTENDED -> extendedRow(event, evidence)

            TimelineKind.ENDED -> endedRow(event, evidence)

            TimelineKind.RESULT -> resultRow(event, evidence)

            TimelineKind.NEEDS_USER -> needsUserRow(event)

            TimelineKind.PROBLEM -> sentenceRow(at, message)

            TimelineKind.CANCELLED -> cancelledRow(at, message)

            // Page loads, the clock check, the bid layer, the window opening and routine checks are steps, not news.
            TimelineKind.CHECKED,
            TimelineKind.WINDOW_OPENED,
            TimelineKind.CLOCK_SYNCED,
            TimelineKind.PAGE_READY,
            TimelineKind.LAYER_READY,
            -> null
        }
    }

    private val LeadSeconds = Regex("""(\d+) s before the end""")

    private fun addedRow(
        at: Long,
        message: String,
        evidence: LotEvidence,
        style: Style,
    ): Row {
        val amount = amountsIn(message).firstOrNull()
        if (amount == null) {
            val detail = if (style == Style.Live) "Added without a max" else null
            val title = if (style == Style.Live) StateNames.watching else "Added to watch, without a max"
            return Row(at, title, detail)
        }
        val lead =
            LeadSeconds
                .find(message)
                ?.groupValues
                ?.get(1)
                ?.toLongOrNull() ?: evidence.snipe.leadSeconds.toLong()
        return armedRow(at, amount, lead, style)
    }

    /** S3 "Armed · £120.00 max, 6 s lead"; the receipt "Armed with a £120.00 max, 6 s lead". */
    private fun armedRow(
        at: Long,
        amount: String?,
        leadSeconds: Long?,
        style: Style,
    ): Row {
        // "6 s lead" stays on one line.
        val lead = leadSeconds?.let { "${secondsText(it)}${NO_BREAK_SPACE}lead" }
        return when {
            amount == null -> Row(at, StateNames.armed)
            style == Style.Live -> Row(at, StateNames.armed, listOfNotNull("$amount max", lead).joined())
            else -> Row(at, listOfNotNull("${StateNames.armed} with a $amount max", lead).joined())
        }
    }

    private fun List<String>.joined(): String = joinToString(", ")

    private fun editedRow(event: TimelineEvent): Row {
        val at = event.atUtcMillis
        val message = event.message
        val previousMax = event.detailMoney("previousMax")?.formatted()
        val newMax = event.detailMoney("max")?.formatted()
        val amounts = amountsIn(message)
        val leadMillis = event.detailLong("leadMillis")
        return when {
            newMax != null || (amounts.size >= 2 && message.contains("max", ignoreCase = true)) -> {
                val before = previousMax ?: amounts.first()
                val after = newMax ?: amounts.last()
                Row(at, "Max changed to $after", "Was $before")
            }

            message.startsWith("Max removed") -> {
                Row(at, "Stopped sniping", "${StateNames.watching}, no max")
            }

            leadMillis != null || (message.contains("bids") && LeadSeconds.containsMatchIn(message)) -> {
                val seconds =
                    leadMillis?.div(1_000) ?: LeadSeconds
                        .find(message)
                        ?.groupValues
                        ?.get(1)
                        ?.toLongOrNull()
                Row(at, "Bid timing changed", seconds?.let { "Bids ${secondsText(it)} before the end" })
            }

            message.startsWith("Added to the group") -> {
                val name = LotEvidence.quotedText(message)
                Row(at, if (name != null) "Added to $name" else "Added to a group")
            }

            message.startsWith("Removed from") -> {
                Row(at, "Removed from its group")
            }

            else -> {
                sentenceRow(at, message)
            }
        }
    }

    private fun priceOverMaxRow(event: TimelineEvent): Row {
        val minimum = event.detailMoney("minimum")
        val price = event.detailMoney("price")
        val detail =
            when {
                minimum != null -> "Next bid at least ${minimum.formatted()}"
                price != null -> "Now ${price.formatted()}"
                else -> null
            }
        return Row(event.atUtcMillis, "Price passed your max", detail)
    }

    private fun dryRunRow(
        event: TimelineEvent,
        style: Style,
    ): Row {
        val at = event.atUtcMillis
        val failed = event.detailText("reason") != null || event.message.startsWith("Dry run failed")
        if (failed) {
            val why =
                event.message
                    .removePrefix("Dry run failed:")
                    .trim()
                    .withCurlyQuotes()
            return Row(at, "Dry run failed", why.takeIf { it.isNotEmpty() })
        }
        val detail =
            if (style == Style.Live) {
                val typed = amountsIn(event.message).firstOrNull()
                if (typed != null) "Bid panel opened, $typed typed, nothing sent" else "Bid panel opened, nothing sent"
            } else {
                "Nothing was sent"
            }
        return Row(at, "Dry run passed", detail)
    }

    private fun bidSentRow(
        event: TimelineEvent,
        evidence: LotEvidence,
    ): Row {
        val at = event.detailLong("sentAtUtcMillis") ?: event.atUtcMillis
        val amount = event.detailMoney("amount")?.formatted() ?: amountsIn(event.message).firstOrNull()
        val again = (event.detailLong("attempt") ?: 1L) > 1L
        val title =
            buildString {
                append(if (amount != null) "Bid of $amount sent" else "Bid sent")
                if (again) append(" again")
            }
        val leadMillis = event.detailLong("leadMillis") ?: evidence.leadMillis
        val lead = leadMillis?.let { "${secondsWithTenths(it)} before the end" }
        return Row(at, title, lead, tone = StatusTone.Bidding, tenths = true)
    }

    private fun replyRow(
        event: TimelineEvent,
        style: Style,
    ): Row {
        val at = event.atUtcMillis
        val latency = event.detailLong("latencyMillis")?.let(::secondsWithTenths)
        val repliedIn = if (latency != null) "eBay replied in $latency" else "eBay replied"
        return when (event.detailText("reply")) {
            "Leading" -> {
                replyWithState(at, StateNames.inTheLead, repliedIn, style)
            }

            "Outbid" -> {
                replyWithState(at, StateNames.outbid, repliedIn, style)
            }

            // A refusal is a failure: its dot takes the Failed tone (DESIGN 3.10).
            "Rejected" -> {
                val words = LotEvidence.quotedText(event.message)
                Row(at, "eBay refused the bid", words?.let { "“$it”" }, tone = StatusTone.Failed, tenths = true)
            }

            "Unconfirmed" -> {
                Row(at, "No reply from eBay", "Kestrel couldn’t read one in time", tenths = true)
            }

            // The live price passed the max after the bid: no reply, the page itself showed it.
            else -> {
                Row(at, "Another bidder is ahead", "The price passed your max", tenths = true)
            }
        }
    }

    /** S3 "eBay replied: in the lead"; the receipt "eBay replied in 0.8 s · In the lead". */
    private fun replyWithState(
        at: Long,
        state: String,
        repliedIn: String,
        style: Style,
    ): Row =
        if (style == Style.Live) {
            Row(at, "eBay replied: ${state.inSentence()}", tenths = true)
        } else {
            Row(at, repliedIn, state, tenths = true)
        }

    /** "Kestrel’s bid reset the clock to 60 s · Now ends 19:56:42", at the moment of the bid that reset it. */
    private fun extendedRow(
        event: TimelineEvent,
        evidence: LotEvidence,
    ): Row {
        val newEnd = event.detailLong("endUtcMillis")
        val resetAt = newEnd?.minus(FINAL_MINUTE_MILLIS) ?: event.atUtcMillis
        val ownBid = evidence.sentAtMillis?.let { abs(it - resetAt) <= OWN_EXTENSION_TOLERANCE_MILLIS } == true
        val who = if (ownBid) "Kestrel’s bid" else "A late bid"
        val detail = newEnd?.let { "Now ends ${Instant.ofEpochMilli(it).clockWithSeconds()}" }
        return Row(resetAt, "$who reset the clock to ${secondsText(60)}", detail)
    }

    private fun endedRow(
        event: TimelineEvent,
        evidence: LotEvidence,
    ): Row {
        val at = event.detailLong("endUtcMillis") ?: evidence.snipe.endTimeUtcMillis
        val count = evidence.extensions.size
        val detail =
            when {
                event.detailText("relistedItemId") != null -> "The seller relisted it"
                count == 0 -> "On time, no extension"
                else -> "After ${plural(count, "extension", "extensions")}"
            }
        return Row(at, "Auction ended", detail, kind = TimelineKind.ENDED)
    }

    /**
     * The end of an auction no ENDED event recorded (Kestrel stopped before it): it ended without Kestrel's bid, so
     * someone else bought it, at the final price when Kestrel read one.
     */
    private fun endedWithoutReport(evidence: LotEvidence): String? {
        if (evidence.sentAtMillis != null && evidence.replyType != "Rejected") return null
        val price = evidence.snipe.result?.finalPrice ?: return "Without a bid from Kestrel"
        return "Sold for ${price.formatted()} to another bidder"
    }

    private fun resultRow(
        event: TimelineEvent,
        evidence: LotEvidence,
    ): Row {
        val at = event.atUtcMillis
        val snipe = evidence.snipe
        val price = event.detailMoney("finalPrice") ?: snipe.result?.finalPrice
        val reason = event.message.withCurlyQuotes()
        return when (event.detailText("outcome")) {
            "WON" -> {
                val title = price?.let { "Won at ${it.formatted()}" } ?: StateNames.won
                Row(at, title, "Confirmed on the listing page", StatusTone.Won, tenths = true)
            }

            "LOST" -> {
                val sold = price?.let { "Sold for ${it.formatted()}, confirmed on the listing page" }
                Row(at, StateNames.lost, sold ?: "Confirmed on the listing page", tenths = true)
            }

            "PRICE_PASSED_MAX" -> {
                Row(at, StateNames.lost, "The price passed your max", tenths = true)
            }

            "ENDED_EARLY" -> {
                Row(at, StateNames.endedEarly, "Nothing was bid", tenths = true)
            }

            "SKIPPED_BY_GROUP" -> {
                Row(at, StateNames.skipped, reason, tenths = true)
            }

            "FAILED", "NOT_PLACED" -> {
                failureRow(at, evidence, reason)
            }

            // The engine's own words say it will check again; the receipt says it as the result card does.
            "UNKNOWN" -> {
                Row(at, "Result not known", ResultStateBuilder.UNKNOWN_NEXT.removeSuffix("."), tenths = true)
            }

            else -> {
                sentenceRow(at, event.message)
            }
        }
    }

    /** "Bid not sent · Signed out" at the moment the bid was due; "Bid failed" when eBay refused it. */
    private fun failureRow(
        at: Long,
        evidence: LotEvidence,
        reason: String,
    ): Row {
        if (evidence.sentAtMillis != null) {
            val words = evidence.replyText?.let { "eBay refused it: “$it”" } ?: "eBay refused it"
            return Row(at, StateNames.bidFailed, words, StatusTone.Failed, tenths = true)
        }
        val bidAt = LotTimes.bidAtUtcMillis(evidence.snipe)
        val signedOut =
            evidence.history.any { it.kind == TimelineKind.NEEDS_USER && it.detailText("reason") == "SIGN_IN" }
        val detail = if (signedOut) "Signed out" else reason
        // The receipt shows the failure where the bid should have been, when Kestrel gave up before then.
        val shownAt = maxOf(at, minOf(bidAt, evidence.snipe.endTimeUtcMillis))
        return Row(shownAt, StateNames.bidNotSent, detail, StatusTone.Failed, tenths = true)
    }

    /** The user's cancel, or the group's: "Skipped: You won …" is written when another member was won. */
    private fun cancelledRow(
        at: Long,
        message: String,
    ): Row =
        if (message.startsWith(GROUP_SKIP_PREFIX)) {
            Row(at, StateNames.cancelledByGroup, message.removePrefix(GROUP_SKIP_PREFIX).trim().withCurlyQuotes())
        } else {
            Row(at, "You stopped this snipe", "Nothing was bid")
        }

    private const val GROUP_SKIP_PREFIX = "Skipped:"

    private fun needsUserRow(event: TimelineEvent): Row {
        val at = event.atUtcMillis
        val message = event.message.withCurlyQuotes()
        return when (event.detailText("reason")) {
            "SIGN_IN" -> Row(at, "eBay ended the session")
            "CAPTCHA" -> Row(at, "eBay asked for a quick check", "Kestrel never solves these for you")
            "VERIFICATION" -> Row(at, "eBay asked to verify you")
            "OFFLINE" -> Row(at, "The phone was offline")
            "DRY_RUN_FAILED" -> Row(at, "Dry run failed", message)
            "BID_FAILED" -> Row(at, "Kestrel couldn’t bid", message)
            else -> Row(at, "eBay showed a page Kestrel doesn’t know", message)
        }
    }

    /** A free message as a row: its first sentence as the title, the rest as the detail. */
    private fun sentenceRow(
        at: Long,
        message: String,
    ): Row {
        val text = message.withCurlyQuotes().trim()
        val end = text.indexOf(". ")
        return if (end in 1 until text.length - 2) {
            Row(at, text.substring(0, end), text.substring(end + 2).removeSuffix("."))
        } else {
            Row(at, text.removeSuffix("."))
        }
    }

    /** Status words for the rows, from the status model itself, so a row never renames a state. */
    private object StateNames {
        private val epoch: Instant = Instant.EPOCH
        private val nothing = Money.zero("GBP")

        val watching = LotStatus.Watching.chipText()
        val armed = LotStatus.Armed(nothing, epoch, 0).chipText()
        val finalMinute = LotStatus.FinalMinute(nothing, epoch, 0, epoch).chipText()
        val inTheLead = LotStatus.InTheLead(null, epoch, null).chipText()
        val outbid = LotStatus.Outbid(null, epoch, null).chipText()
        val won = LotStatus.Won(null, null, epoch).chipText()
        val lost = LotStatus.Lost(null, null, epoch, bidSent = false).chipText()
        val endedEarly = LotStatus.EndedEarly(epoch).chipText()
        val skipped = LotStatus.CancelledByGroup(null, null, auctionRunning = false).chipText()
        val cancelledByGroup = LotStatus.CancelledByGroup(null, wonTitle = "", auctionRunning = false).chipText()
        val bidNotSent = LotStatus.BidNotSent(null, null, epoch).chipText()
        val bidFailed = LotStatus.BidFailed(null, epoch).chipText()
    }
}
