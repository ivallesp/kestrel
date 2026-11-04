package app.kestrel.ui.screens.result

import app.kestrel.data.SnipeRules
import app.kestrel.model.Money
import app.kestrel.model.Snipe
import app.kestrel.model.StatusFamily
import app.kestrel.ui.components.MoneyFigures
import app.kestrel.ui.format.AuctionZone
import app.kestrel.ui.format.NO_BREAK_SPACE
import app.kestrel.ui.format.clockFor
import app.kestrel.ui.format.clockWithSeconds
import app.kestrel.ui.format.dayDate
import app.kestrel.ui.format.forDisplay
import app.kestrel.ui.format.formatted
import app.kestrel.ui.format.isSameDay
import app.kestrel.ui.format.secondsText
import app.kestrel.ui.format.secondsWithTenths
import app.kestrel.ui.format.zoneName
import app.kestrel.ui.model.LotAction
import app.kestrel.ui.model.LotStatus
import app.kestrel.ui.model.LotTimes
import app.kestrel.ui.model.StateWords
import app.kestrel.ui.model.action
import app.kestrel.ui.model.chipText
import app.kestrel.ui.model.detail
import app.kestrel.ui.model.label
import app.kestrel.ui.screens.lot.LotEvidence
import app.kestrel.ui.screens.lot.LotTimeline
import app.kestrel.ui.theme.StatusTone
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Builds S4 (DESIGN S4) from the domain. Pure: the same inputs always give the same screen. */
object ResultStateBuilder {
    /** DESIGN 6.3 and S4: what happens after a win. */
    const val PAYMENT_NOTE =
        "eBay takes payment automatically from your saved method. If it can’t, you have 4\u00A0days to\u00A0pay."

    const val SECOND_CHANCE_TITLE = "If the winner doesn’t pay"

    const val SECOND_CHANCE =
        "The seller may offer it to you at your max as a Second Chance Offer. Accept it only in eBay Messages; " +
            "offers by email are often scams."

    fun build(
        inputs: ResultInputs,
        status: LotStatus = inputs.status,
        zone: ZoneId = AuctionZone,
    ): ResultUiState {
        val snipe = inputs.snipe
        val now = inputs.now
        val evidence = LotEvidence(snipe, inputs.events, now.toEpochMilli())
        val variant = variantOf(status)
        val headline = headline(status, variant, now, zone)
        val statusLine = statusLine(status, evidence, now, zone)
        val quote = quote(variant, evidence)
        val paid = (status as? LotStatus.Won)?.let { paid(it, snipe) }
        val sold = (status as? LotStatus.Lost)?.let { sold(it, evidence) }
        return ResultUiState(
            now = now,
            id = snipe.id,
            variant = variant,
            title = snipe.title.forDisplay(),
            photo = inputs.photo,
            tone = if (variant == ResultVariant.Unknown) StatusTone.Ended else status.tone,
            statusLine = statusLine,
            headline = headline,
            receipt = LotTimeline.receipt(evidence, now, zone),
            shareText = shareText(snipe, variant, headline, statusLine, paid, quote, status),
            // As the data layer allows: no window can hold the snipe long after the end.
            canDelete = SnipeRules.deleteRefusal(snipe, now.toEpochMilli()) == null,
            // A bid whose result Kestrel could not read, long after the end: the user says how it ended.
            canSettle = SnipeRules.settleRefusal(snipe, now.toEpochMilli()) == null,
            stampSeal = inputs.stampSeal && variant == ResultVariant.Won,
            paid = paid,
            sold = sold,
            outcome = outcome(status, variant, evidence, now, zone),
            secondChance = status is LotStatus.Lost && status.max != null && evidence.sentAtMillis != null,
            quote = quote,
        )
    }

    fun variantOf(status: LotStatus): ResultVariant =
        when (status) {
            is LotStatus.Won -> ResultVariant.Won
            is LotStatus.Lost -> ResultVariant.Lost
            is LotStatus.EndedEarly -> ResultVariant.EndedEarly
            is LotStatus.CancelledByYou, is LotStatus.CancelledByGroup -> ResultVariant.Cancelled
            is LotStatus.BidNotSent -> ResultVariant.BidNotSent
            is LotStatus.BidFailed -> ResultVariant.BidFailed
            else -> ResultVariant.Unknown
        }

    /** "You won" and "Outbid" come from the status detail's lead words; the others are the chip's words. */
    private fun headline(
        status: LotStatus,
        variant: ResultVariant,
        now: Instant,
        zone: ZoneId,
    ): String =
        when (variant) {
            ResultVariant.Won, ResultVariant.Lost -> {
                val detail = status.detail(now, zone)
                if (detail.contains(" · ")) detail.substringBefore(" · ") else status.chipText(zone)
            }

            ResultVariant.Unknown -> {
                "Result not known"
            }

            else -> {
                status.chipText(zone)
            }
        }

    /**
     * The status line with day, time and zone (rule MF38): "Won · ended Sun 9 Nov, 18:21:40 GMT". Where the headline
     * already names the state ("Bid failed", "Cancelled", "Ended early"), the line keeps the state's glyph and gives
     * only the moment ("Today, 18:21:34 GMT"), so the same words never sit one above the other.
     */
    private fun statusLine(
        status: LotStatus,
        evidence: LotEvidence,
        now: Instant,
        zone: ZoneId,
    ): StateWords {
        val snipe = evidence.snipe
        val end = Instant.ofEpochMilli(snipe.endTimeUtcMillis)
        val words = status.label(now, zone).words

        // "today" on the day itself, so the line stays clear of the seal; seconds only within the hour (rule MF38
        // keeps day, time and zone). The day and the time each stay whole, so a wrap never leaves the zone alone.
        fun at(instant: Instant): String {
            val day = if (isSameDay(now, instant, zone)) "today" else instant.dayDate(zone)
            return "${day.keptTogether()}, ${"${clockFor(
                now,
                instant,
                zone,
            )} ${instant.zoneName(zone)}".keptTogether()}"
        }
        val resultAt =
            Instant.ofEpochMilli(evidence.cancelledAtMillis ?: evidence.result?.atUtcMillis ?: snipe.updatedAtUtcMillis)

        fun moment(instant: Instant) =
            StateWords(words = "", detail = at(instant).replaceFirstChar { it.uppercaseChar() })
        return when (status) {
            is LotStatus.Won, is LotStatus.Lost -> StateWords(words, "ended ${at(end)}")
            is LotStatus.EndedEarly -> moment(status.endedAt)
            is LotStatus.CancelledByYou -> moment(status.cancelledAt)
            is LotStatus.BidNotSent -> moment(LotTimes.bidAt(snipe))
            is LotStatus.BidFailed -> moment(LotTimes.bidAt(snipe))
            is LotStatus.CancelledByGroup -> moment(resultAt)
            else -> StateWords(words, at(end))
        }
    }

    private fun paid(
        status: LotStatus.Won,
        snipe: Snipe,
    ): PaidCardText? {
        val wonFor = status.wonFor ?: return null
        val max = status.max?.takeIf { it.currency == wonFor.currency }
        val under =
            when {
                max == null -> null
                max > wonFor -> "${(max - wonFor).formatted()} under"
                else -> "At your max"
            }
        val postage = snipe.postage?.takeIf { it.currency == wonFor.currency }
        val ledger =
            listOfNotNull(
                LedgerLine("Final price", wonFor.formatted()),
                postage?.let { LedgerLine("Postage", it.formatted()) },
            )
        val total = postage?.let { LedgerLine("Total", (wonFor + it).formatted()) }
        val spoken =
            buildString {
                append("Won for ${wonFor.formatted()}")
                if (max != null && max > wonFor) {
                    append(", ${(max - wonFor).formatted()} under your ${max.formatted()} max")
                } else if (max != null) {
                    append(", your max")
                }
                append(". Final price ${wonFor.formatted()}")
                if (postage != null && total != null) append(", postage ${postage.formatted()}, total ${total.value}")
                append('.')
            }
        return PaidCardText(
            wonFor = wonFor.formatted(),
            under = under,
            maxLine = max?.let { "your ${it.formatted()} max" },
            ledger = ledger,
            total = total,
            note = if (postage != null) PAYMENT_NOTE else "Postage wasn’t listed; check it on eBay. $PAYMENT_NOTE",
            spoken = spoken,
        )
    }

    private fun sold(
        status: LotStatus.Lost,
        evidence: LotEvidence,
    ): SoldCardText {
        val soldFor = status.soldFor
        val max = status.max
        val spoken =
            listOfNotNull(soldFor?.let { "Sold for ${it.formatted()}." }, max?.let { "Your max ${it.formatted()}." })
                .joinToString(" ")
        val figures =
            MoneyFigures(
                startLabel = if (soldFor != null) "Sold for" else "",
                startValue = soldFor?.formatted().orEmpty(),
                endLabel = if (max != null) "Your max" else "",
                endValue = max?.formatted().orEmpty(),
                spoken = spoken,
            )
        val amount = (evidence.sentAmount ?: max)?.formatted()
        val lead = evidence.leadMillis?.let { " ${secondsWithTenths(it)} before the end" } ?: ""
        val acknowledged = evidence.replyType == "Outbid" || evidence.replyType == "Leading"
        return when {
            evidence.sentAtMillis != null && amount != null && acknowledged -> {
                SoldCardText(
                    figures,
                    "Outbid by someone with a higher max",
                    "Your $amount bid reached eBay$lead and counted. Their max was higher, so eBay’s proxy bidding " +
                        "kept them ahead. You pay nothing.",
                )
            }

            evidence.sentAtMillis != null && amount != null -> {
                SoldCardText(
                    figures,
                    "Outbid by someone with a higher max",
                    "Your $amount bid left the phone$lead. Someone’s max was higher, so eBay’s proxy bidding kept " +
                        "them ahead. You pay nothing.",
                )
            }

            max != null -> {
                SoldCardText(
                    figures,
                    "The price passed your max",
                    "Kestrel didn’t bid: eBay needed more than your ${max.formatted()} max before the end. " +
                        "You pay nothing.",
                )
            }

            else -> {
                val explanation = "Kestrel didn’t bid, because this lot had no max. You pay nothing."
                SoldCardText(figures, "You were only watching", explanation)
            }
        }
    }

    private fun outcome(
        status: LotStatus,
        variant: ResultVariant,
        evidence: LotEvidence,
        now: Instant,
        zone: ZoneId,
    ): OutcomeCardText? =
        when (status) {
            is LotStatus.EndedEarly -> {
                OutcomeCardText(StatusTone.Ended, "The seller ended the listing before its end time. Nothing was bid.")
            }

            is LotStatus.CancelledByYou -> {
                OutcomeCardText(StatusTone.Ended, status.detail(now, zone))
            }

            is LotStatus.CancelledByGroup -> {
                val text =
                    if (status.wonTitle != null && status.groupName != null) {
                        "Kestrel cancelled this because you won ${status.wonTitle} in ${status.groupName}."
                    } else {
                        status.detail(now, zone)
                    }
                // "Undo cancel" while the auction runs (DESIGN S4); never once it ended.
                OutcomeCardText(StatusTone.Ended, text, status.action(now))
            }

            is LotStatus.BidNotSent -> {
                val signedOutAt = status.signedOutAt
                val bidAt = LotTimes.bidAt(evidence.snipe)
                val text =
                    if (signedOutAt != null && signedOutAt < bidAt) {
                        // Whole seconds on both sides, so the gap matches the two clock times on screen.
                        val gap = Duration.between(signedOutAt.wholeSecond(), bidAt.wholeSecond())
                        "eBay signed you out at ${signedOutAt.clockWithSeconds(zone)}, ${secondsText(gap.seconds)} " +
                            "before the bid. Nothing was bid."
                    } else {
                        status.detail(now, zone)
                    }
                OutcomeCardText(StatusTone.Failed, text, LotAction.SIGN_IN.takeIf { signedOutAt != null })
            }

            is LotStatus.BidFailed -> {
                // eBay's words may ask to try again; once the auction has ended there is nothing to retry.
                val retryNote =
                    if (status.ebayText?.contains("try again", ignoreCase = true) == true) " $NOTHING_TO_RETRY" else ""
                OutcomeCardText(StatusTone.Failed, status.detail(now, zone) + retryNote)
            }

            else -> {
                if (variant == ResultVariant.Unknown) unknown(evidence) else null
            }
        }

    /**
     * A bid went out but the result could not be read yet: one message, the same as the receipt's. Kestrel keeps
     * checking; the user can look on eBay meanwhile.
     */
    private fun unknown(evidence: LotEvidence): OutcomeCardText {
        val amount = evidence.sentAmount?.formatted()
        val lead = evidence.leadMillis?.let { " ${secondsWithTenths(it)} before the end" } ?: ""
        val sent = if (amount != null) "Kestrel sent your $amount bid$lead, but" else "Kestrel"
        val text =
            if (evidence.resultGivenUp) {
                "$sent couldn’t read the result on eBay’s listing page and has stopped checking. $GIVEN_UP_NEXT"
            } else {
                "$sent couldn’t read the result on eBay’s listing page yet. $UNKNOWN_NEXT"
            }
        return OutcomeCardText(StatusTone.Ended, text, LotAction.OPEN_ON_EBAY)
    }

    /** Once Kestrel stopped reading the result: the menu's "Mark as won" and "Mark as lost". */
    const val GIVEN_UP_NEXT = "Look on eBay, then mark it won or lost from the menu."

    /** What happens after a result Kestrel couldn't read, on the card and in the receipt. */
    const val UNKNOWN_NEXT = "It keeps checking the page, and you can look on eBay too."

    /** Under a refusal that asks to try again, once the auction has ended. */
    const val NOTHING_TO_RETRY = "The auction has ended, so there is nothing to retry."

    private fun String.keptTogether(): String = replace(' ', NO_BREAK_SPACE)

    private fun Instant.wholeSecond(): Instant = truncatedTo(ChronoUnit.SECONDS)

    /** eBay's reply to the bid as a pull-quote, when the bid went out and eBay answered in words. */
    private fun quote(
        variant: ResultVariant,
        evidence: LotEvidence,
    ): PullQuoteText? {
        if (variant !in setOf(ResultVariant.Won, ResultVariant.Lost)) return null
        val text = evidence.replyText ?: return null
        if (evidence.sentAtMillis == null) return null
        val sent = evidence.leadMillis?.let { "Sent ${secondsWithTenths(it)} before the end" }
        val reply = evidence.replyMillis?.let { "reply in ${secondsWithTenths(it)}" }
        val timing =
            listOfNotNull(sent, reply)
                .joinToString(" · ")
                .ifEmpty { "eBay’s reply" }
                .replaceFirstChar { it.uppercase() }
        val spokenDelay = evidence.replyMillis?.let { " in ${speakSeconds(it)}" } ?: ""
        return PullQuoteText(timing, "“$text”", "eBay replied$spokenDelay: $text")
    }

    private fun speakSeconds(millis: Long): String {
        val tenths = millis.coerceAtLeast(0) / 100
        val number = "${tenths / 10}.${tenths % 10}"
        return "$number seconds"
    }

    /** The text receipt (DESIGN S4): the title, what happened, the total, the end time and eBay's reply. */
    private fun shareText(
        snipe: Snipe,
        variant: ResultVariant,
        headline: String,
        statusLine: StateWords,
        paid: PaidCardText?,
        quote: PullQuoteText?,
        status: LotStatus,
    ): String {
        val end = Instant.ofEpochMilli(snipe.endTimeUtcMillis)
        val ended = "Ended ${end.dayDate()}, ${end.clockWithSeconds()} ${end.zoneName()}"
        val lines =
            buildList {
                add(snipe.title)
                when (variant) {
                    ResultVariant.Won -> {
                        add(paid?.let { "Won for ${it.wonFor}" } ?: headline)
                        paid?.total?.let { add("Total with postage ${it.value}") }
                    }

                    ResultVariant.Lost -> {
                        val lost = status as LotStatus.Lost
                        val soldFor = lost.soldFor?.let { "sold for ${it.formatted()}" }
                        add(listOfNotNull(statusLine.words, soldFor).joinToString(" · "))
                        lost.max?.let { add("Your max ${it.formatted()}") }
                    }

                    else -> {
                        add(headline)
                    }
                }
                add(ended)
                quote?.let { add("eBay replied: ${it.quote}") }
            }
        return lines.joinToString("\n")
    }
}
