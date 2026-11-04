package app.kestrel.platform

import app.kestrel.engine.NeedsUserReason
import app.kestrel.engine.WindowPhase
import app.kestrel.model.Money
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeOutcome
import app.kestrel.model.SnipeResult
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

/**
 * Every word Kestrel shows in a notification, in British English. Titles stay under 29 characters, and bad news
 * never carries an emoji. Times are shown in the phone's time zone and clock format.
 */
internal class NotificationTexts(
    private val formatMoney: (Money) -> String,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val use24HourClock: () -> Boolean = { true },
) {
    fun clockTime(utcMillis: Long): String = format(utcMillis, if (use24HourClock()) "HH:mm" else "h:mm a")

    /** "Sun 21:43": the day is named rather than "today" or "tomorrow", which readers often get wrong. */
    fun dayAndTime(utcMillis: Long): String = format(utcMillis, if (use24HourClock()) "EEE HH:mm" else "EEE h:mm a")

    /** "18:21:40 GMT": a time with seconds carries its zone (DESIGN 5). */
    fun clockWithSeconds(utcMillis: Long): String =
        format(utcMillis, if (use24HourClock()) "HH:mm:ss z" else "h:mm:ss a z")

    // The final window's Live Update.

    fun windowTitle(
        snipe: Snipe,
        bidAtUtcMillis: Long?,
    ): String {
        val max = snipe.maxBid ?: return PREPARING_TITLE
        val amount = "Bidding ${formatMoney(max)}"
        if (bidAtUtcMillis == null) return fit(amount)
        val withTime = "$amount at ${clockTime(bidAtUtcMillis)}"
        return if (withTime.length <= TITLE_LIMIT) withTime else fit(amount)
    }

    fun preparingTitle(): String = PREPARING_TITLE

    fun windowText(
        snipe: Snipe,
        phase: WindowPhase?,
        detail: String?,
    ): String = joinParts(detail ?: phaseLine(snipe, phase), snipe.title)

    fun preparingText(title: String?): String = joinParts("Getting ready", title)

    fun windowPublicText(bidAtUtcMillis: Long?): String =
        bidAtUtcMillis?.let { "Kestrel bids at ${clockTime(it)}" } ?: "Kestrel is getting ready to bid"

    /** The status-bar chip: a countdown while waiting, then these words once the bid is under way. */
    fun chipText(phase: WindowPhase?): String? =
        when (phase) {
            null, WindowPhase.CHECKING, WindowPhase.READY -> null
            else -> BIDDING_CHIP
        }

    fun chipText(outcome: SnipeOutcome): String =
        when (outcome) {
            SnipeOutcome.WON -> "Won"
            SnipeOutcome.LOST -> "Lost"
            else -> "Ended"
        }

    private fun phaseLine(
        snipe: Snipe,
        phase: WindowPhase?,
    ): String =
        when (phase) {
            null -> "Getting ready"
            WindowPhase.CHECKING -> "Checking the listing"
            WindowPhase.READY -> snipe.lastPrice?.let { "Ready, now ${formatMoney(it)}" } ?: "Ready"
            WindowPhase.BIDDING -> "Sending your bid"
            WindowPhase.BID_SENT -> "Bid sent, waiting for eBay"
            WindowPhase.LEADING -> "You're in the lead"
            WindowPhase.OUTBID -> "Another bidder is ahead"
            WindowPhase.EXTENDED -> "Extended bidding, ends ${clockTime(snipe.endTimeUtcMillis)}"
            WindowPhase.RESOLVING -> "Checking the result"
        }

    // Price passed the max.

    fun priceOverMaxTitle(): String = "Price passed your max"

    fun priceOverMaxText(
        snipe: Snipe,
        current: Money?,
    ): String {
        val max = snipe.maxBid?.let { "your max is ${formatMoney(it)}" }
        val now = current?.let { "Now ${formatMoney(it)}" }
        return when {
            now != null && max != null -> "$now, $max"
            now != null -> now
            max != null -> max.replaceFirstChar { it.uppercase() }
            else -> snipe.title
        }
    }

    fun priceOverMaxDetail(
        snipe: Snipe,
        current: Money?,
        minimumNext: Money?,
        problem: String?,
        canRaise: Boolean = true,
    ): String {
        val advice =
            when {
                !canRaise -> TOO_LATE_TO_RAISE
                minimumNext != null -> "Raise it to at least ${formatMoney(minimumNext)} to keep bidding."
                else -> "Raise it to keep bidding."
            }
        return listOfNotNull(problem, "${priceOverMaxText(snipe, current)}. $advice", snipe.title).joinToString("\n")
    }

    fun raiseMaxLabel(currency: String): String = "New max (${currencySymbol(currency)})"

    fun raiseMaxDoneTitle(newMax: Money): String = fit("Max raised to ${formatMoney(newMax)}")

    fun raiseMaxDoneText(snipe: Snipe): String =
        joinParts("Kestrel will bid ${snipe.leadSeconds} s before the end", snipe.title)

    // Kestrel needs the user.

    fun needsUserTitle(reason: NeedsUserReason): String =
        when (reason) {
            NeedsUserReason.SIGN_IN -> "Sign in to eBay"
            NeedsUserReason.CAPTCHA -> "eBay wants a quick check"
            NeedsUserReason.VERIFICATION -> "eBay needs to verify you"
            NeedsUserReason.UNKNOWN_PAGE -> "Kestrel needs you"
            NeedsUserReason.DRY_RUN_FAILED -> "Dry run failed"
            NeedsUserReason.OFFLINE -> "No internet connection"
            NeedsUserReason.BID_FAILED -> "Bid failed"
        }

    fun needsUserText(
        reason: NeedsUserReason,
        detail: String?,
    ): String =
        detail?.takeIf { it.isNotBlank() } ?: when (reason) {
            NeedsUserReason.SIGN_IN -> "Kestrel can't bid until you sign in."
            NeedsUserReason.CAPTCHA -> "eBay showed a security check. Open Kestrel to finish it."
            NeedsUserReason.VERIFICATION -> "Open Kestrel to finish eBay's check."
            NeedsUserReason.UNKNOWN_PAGE -> "eBay showed a page Kestrel doesn't recognise."
            NeedsUserReason.DRY_RUN_FAILED -> "The practice run before the bid didn't work."
            NeedsUserReason.OFFLINE -> "Kestrel couldn't reach eBay."
            NeedsUserReason.BID_FAILED -> "Kestrel couldn't place the bid."
        }

    /** The window stopped before its bid, and the auction runs until [endUtcMillis]. */
    fun bidLeftToUserText(endUtcMillis: Long): String =
        "Kestrel did not bid. You can still bid on eBay yourself before ${clockWithSeconds(endUtcMillis)}."

    fun needsUserDetail(
        snipe: Snipe,
        reason: NeedsUserReason,
        detail: String?,
    ): String =
        listOf(
            needsUserText(reason, detail),
            "${snipe.title}, ends ${dayAndTime(snipe.endTimeUtcMillis)}",
        ).joinToString("\n")

    // Results.

    fun resultTitle(
        snipe: Snipe,
        result: SnipeResult,
    ): String {
        val prefix =
            when (result.outcome) {
                SnipeOutcome.WON -> "Won"

                SnipeOutcome.LOST -> result.finalPrice?.let { "Sold for ${formatMoney(it)}" } ?: "Not won"

                SnipeOutcome.ENDED_EARLY -> "Ended early"

                SnipeOutcome.PRICE_PASSED_MAX -> "Over your max"

                SnipeOutcome.NOT_PLACED -> "Bid not placed"

                // DESIGN MF24: "Bid failed" means eBay had the bid; one that never left the phone was not sent.
                SnipeOutcome.FAILED -> if (result.bidSentAtUtcMillis == null) "Bid not sent" else "Bid failed"

                SnipeOutcome.CANCELLED -> "Cancelled"

                SnipeOutcome.SKIPPED_BY_GROUP -> "Skipped"

                SnipeOutcome.UNKNOWN -> "Result unknown"
            }
        return fitWithItem(prefix, snipe.title)
    }

    fun resultText(
        snipe: Snipe,
        result: SnipeResult,
    ): String {
        val max = snipe.maxBid
        val reason = result.reason ?: result.ebayReplyText
        return when (result.outcome) {
            SnipeOutcome.WON -> wonText(result.finalPrice, max)
            SnipeOutcome.LOST -> max?.let { "Your max was ${formatMoney(it)}" } ?: "Someone else won"
            SnipeOutcome.ENDED_EARLY -> result.ebayReplyText ?: result.reason ?: ENDED_EARLY_TEXT
            SnipeOutcome.PRICE_PASSED_MAX -> overMaxText(result.finalPrice, max)
            SnipeOutcome.NOT_PLACED, SnipeOutcome.FAILED -> reason ?: "Kestrel couldn't place the bid."
            SnipeOutcome.CANCELLED -> result.reason ?: "You cancelled this snipe."
            SnipeOutcome.SKIPPED_BY_GROUP -> result.reason ?: "You already won one in this group."
            SnipeOutcome.UNKNOWN -> "Open the listing to check the result."
        }
    }

    private fun wonText(
        finalPrice: Money?,
        max: Money?,
    ): String {
        if (finalPrice == null) return "Open Kestrel to see the final price"
        val under = max?.takeIf { it.currency == finalPrice.currency && it >= finalPrice }?.minus(finalPrice)
        if (under == null) return "Final price ${formatMoney(finalPrice)}"
        return "${formatMoney(finalPrice)}, ${formatMoney(under)} under your max"
    }

    private fun overMaxText(
        finalPrice: Money?,
        max: Money?,
    ): String =
        when {
            max == null -> "The price passed your max."
            finalPrice == null -> "The price passed your max of ${formatMoney(max)}."
            else -> "Sold for ${formatMoney(finalPrice)}. Your max was ${formatMoney(max)}."
        }

    // When Android stops Kestrel from starting in the background.

    fun tapToArmTitle(action: SnipeAction): String =
        if (action == SnipeAction.DRY_RUN) "Tap to check your snipe" else "Tap to arm your snipe"

    fun tapToArmText(
        title: String?,
        endTimeUtcMillis: Long?,
    ): String {
        val ends = endTimeUtcMillis?.let { "ends ${dayAndTime(it)}" }
        return joinParts(
            "Android stopped Kestrel starting",
            listOfNotNull(title, ends).joinToString(", ").ifEmpty { null },
        )
    }

    // After a reboot, before the first unlock.

    fun unlockTitle(): String = "Unlock your phone"

    fun unlockText(
        count: Int,
        nextEndUtcMillis: Long,
    ): String =
        if (count == 1) {
            "To arm 1 snipe. It ends ${dayAndTime(nextEndUtcMillis)}."
        } else {
            "To arm $count snipes. The next ends ${dayAndTime(nextEndUtcMillis)}."
        }

    // Scheduling problems and refusals.

    fun scheduleProblemTitle(problem: ScheduleProblem): String =
        when (problem) {
            ScheduleProblem.EXACT_ALARMS_DENIED -> "Exact alarms are off"
            ScheduleProblem.ALARM_LIMIT_REACHED -> "Too many alarms"
            ScheduleProblem.BACKGROUND_CHECK_FAILED -> "Background checks failed"
        }

    fun scheduleProblemText(problem: ScheduleProblem): String =
        when (problem) {
            ScheduleProblem.EXACT_ALARMS_DENIED -> "Bids may be late. Tap to allow exact alarms."
            ScheduleProblem.ALARM_LIMIT_REACHED -> "Android refused another alarm. Remove old snipes."
            ScheduleProblem.BACKGROUND_CHECK_FAILED -> "Price checks may not run. Open Kestrel."
        }

    // The quiet notification while a check or a dry run runs.

    fun serviceTitle(action: SnipeAction?): String =
        when (action) {
            SnipeAction.DRY_RUN -> "Getting ready to bid"
            SnipeAction.WINDOW, SnipeAction.WINDOW_BACKUP -> PREPARING_TITLE
            SnipeAction.CHECK, null -> "Checking a listing"
        }

    fun serviceText(titles: List<String>): String =
        when (titles.size) {
            0 -> "Kestrel is working"
            1 -> titles.single()
            else -> "${titles.first()} and ${titles.size - 1} more"
        }

    private fun format(
        utcMillis: Long,
        pattern: String,
    ): String = DateTimeFormatter.ofPattern(pattern, Locale.UK).format(Instant.ofEpochMilli(utcMillis).atZone(zone()))

    private fun currencySymbol(currency: String): String =
        try {
            Currency.getInstance(currency).getSymbol(Locale.UK)
        } catch (error: IllegalArgumentException) {
            currency
        }

    companion object {
        /** Material's notification guidance: titles under 29 characters. */
        const val TITLE_LIMIT = 28
        private const val ELLIPSIS = "…"
        private const val SEPARATOR = " · "
        private const val TOO_LATE_TO_RAISE =
            "Kestrel bids in a few seconds, too late to raise it here. Bid on eBay yourself to go higher."
        private const val PREPARING_TITLE = "Getting ready to bid"
        private const val BIDDING_CHIP = "Bidding"
        private const val ENDED_EARLY_TEXT = "The listing ended before Kestrel could bid."

        fun fit(text: String): String {
            if (text.length <= TITLE_LIMIT) return text
            return text.take(TITLE_LIMIT - 1).trimEnd() + ELLIPSIS
        }

        fun fitWithItem(
            prefix: String,
            item: String,
        ): String {
            val itemText = item.trim()
            if (itemText.isEmpty()) return fit(prefix)
            val room = TITLE_LIMIT - prefix.length - SEPARATOR.length
            if (room < MINIMUM_ITEM_CHARACTERS) return fit(prefix)
            val shownItem = if (itemText.length <= room) itemText else itemText.take(room - 1).trimEnd() + ELLIPSIS
            return prefix + SEPARATOR + shownItem
        }

        private const val MINIMUM_ITEM_CHARACTERS = 4

        private fun joinParts(vararg parts: String?): String =
            parts.filterNot { it.isNullOrBlank() }.joinToString(SEPARATOR)
    }
}
