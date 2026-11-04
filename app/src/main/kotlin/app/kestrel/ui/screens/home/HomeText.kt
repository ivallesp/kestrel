package app.kestrel.ui.screens.home

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import app.kestrel.model.Money
import app.kestrel.ui.components.Symbols
import app.kestrel.ui.components.appendFigure
import app.kestrel.ui.components.figureSentence
import app.kestrel.ui.format.AuctionZone
import app.kestrel.ui.format.NARROW_NO_BREAK_SPACE
import app.kestrel.ui.format.clock
import app.kestrel.ui.format.clockFor
import app.kestrel.ui.format.clockWithSeconds
import app.kestrel.ui.format.countdown
import app.kestrel.ui.format.dayDate
import app.kestrel.ui.format.dayLabel
import app.kestrel.ui.format.formatted
import app.kestrel.ui.format.isSameDay
import app.kestrel.ui.format.isWithinAnHour
import app.kestrel.ui.format.plural
import app.kestrel.ui.format.secondsText
import app.kestrel.ui.format.spoken
import app.kestrel.ui.format.timeLeft
import app.kestrel.ui.format.zoneName
import app.kestrel.ui.format.zoneOverline
import app.kestrel.ui.model.LotStatus
import app.kestrel.ui.model.detail
import app.kestrel.ui.model.spokenLabel
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A row flag (DESIGN 3.5): a 16 dp glyph and one line of ink-soft text, with the sentence TalkBack reads. */
@Immutable
data class RowFlag(
    @param:DrawableRes val icon: Int,
    val text: String,
    val spoken: String = text,
)

/** The price line of a finished row: "Won for" "£34.00" "+ £3.10 postage", or "Sold for" "£79.00". */
@Immutable
data class EndedPrice(
    val label: String,
    val amount: String,
    val after: String? = null,
)

/**
 * Every string S1 and History write around the status words (DESIGN 3.3 to 3.8 and 6.3). Pure, so each one is unit
 * tested. Status words themselves always come from LotStatusWords; this only adds money, time and flags.
 */
internal object HomeText {
    /** Small caps hold at most this many characters (DESIGN SC1). */
    private const val SMALL_CAPS_LIMIT = 32

    private val ShortWeekdayLongMonth = DateTimeFormatter.ofPattern("EEE d MMMM", Locale.UK)

    private val QuoteMarks = setOf('“', '”', '"', '‘', '’')

    /** Double quotes only: an apostrophe inside a word is read correctly. */
    private val SpokenQuoteMarks = setOf('“', '”', '"')
    private val TrailingPunctuation = setOf(',', ';', ':', '.')

    // The header.

    /**
     * "SUNDAY 9 NOVEMBER · TIMES IN GMT". The longest dates ("Wednesday 24 September") would pass the small-caps
     * limit, so they shorten the weekday: "WED 24 SEPTEMBER · TIMES IN BST".
     */
    fun overline(
        now: Instant,
        zone: ZoneId = AuctionZone,
    ): String {
        val full = zoneOverline(now, zone)
        if (full.length <= SMALL_CAPS_LIMIT) return full
        val date = ShortWeekdayLongMonth.format(now.atZone(zone))
        return "$date · times in ${now.zoneName(zone)}".uppercase(Locale.UK)
    }

    /** The account button: "Settings and eBay account, lot.42". */
    fun accountDescription(name: String?): String =
        if (name == null) "Settings and eBay account, signed out" else "Settings and eBay account, $name"

    // Group headers.

    /** "Portable audio: win 1 of 2". */
    fun groupTitle(group: GroupHeader): String = "${group.name}: win ${group.targetWins} of ${group.memberCount}"

    /** "Once one is won, Kestrel cancels the other." (or "the rest" for more than two lots). */
    fun groupDetail(group: GroupHeader): String =
        when {
            group.targetWins > 1 -> "Once ${group.targetWins} are won, Kestrel cancels the rest."
            group.memberCount == 2 -> "Once one is won, Kestrel cancels the other."
            else -> "Once one is won, Kestrel cancels the rest."
        }

    // Open rows.

    /** The row's price, or null when Kestrel has not read one. */
    fun price(lot: HomeLot): String? = lot.snipe.lastPrice?.formatted()

    /** "5 bids · £3.49 postage", plus " · as of 18:02" while offline. */
    fun priceMeta(
        lot: HomeLot,
        now: Instant,
    ): String {
        val bids =
            when (val count = lot.snipe.lastBidCount ?: 0) {
                0 -> "No bids"
                else -> plural(count, "bid", "bids")
            }
        val asOf = lot.pricesAsOf?.let { " · as of ${moment(now, it)}" } ?: ""
        return "$bids · ${postageWords(lot.snipe.postage)}$asOf"
    }

    /** "£3.49 postage", "free postage" or "postage not listed". */
    fun postageWords(postage: Money?): String =
        when {
            postage == null -> "postage not listed"
            postage.isZero() -> "free postage"
            else -> "${postage.formatted()} postage"
        }

    /**
     * The row's time line: "Mon 10 Nov, 20:10 · in 1 d 2 h", "Today 21:02 · in 2 h 53 m", and inside the hour
     * "Today 18:21:40 GMT · in 13 m 07 s". The clock and the countdown carry tabular figures (rule T4).
     */
    fun timeLine(
        lot: HomeLot,
        now: Instant,
    ): AnnotatedString {
        val end = Instant.ofEpochMilli(lot.snipe.endTimeUtcMillis)
        return buildAnnotatedString {
            if (end <= now) {
                append("Ended ")
                appendMoment(now, end, sentence = true)
                return@buildAnnotatedString
            }
            appendMoment(now, end, sentence = false)
            append(" · in ")
            appendFigure(timeLeft(now, end))
        }
    }

    /** The flags under a row's time line. Each renders on its own (MF34). */
    fun flags(
        lot: HomeLot,
        now: Instant,
    ): List<RowFlag> =
        buildList {
            lot.groupFlag?.let { add(RowFlag(Symbols.Link, it)) }
            if (lot.snipe.extendedBidding) {
                val text =
                    if (lot.snipe.maxBid != null) {
                        "Extended bidding: Kestrel’s bid will reset the clock to ${secondsText(60)}."
                    } else {
                        "Extended bidding: a late bid resets the clock to ${secondsText(60)}."
                    }
                add(RowFlag(Symbols.Timer, text, text.replace(secondsText(60), "60 seconds")))
            }
            lot.failedCheckAt?.let { failed ->
                val at =
                    if (isSameDay(
                            now,
                            failed,
                        )
                    ) {
                        "at ${failed.clock()}"
                    } else {
                        "on ${failed.dayDate()} at ${failed.clock()}"
                    }
                add(RowFlag(Symbols.Sync, "Couldn’t check $at · retrying", "Couldn’t check $at, retrying."))
            }
        }

    // Finished rows.

    /** "Won for £34.00 + £3.10 postage", "Sold for £79.00", or null when there is no price to show. */
    fun endedPrice(lot: HomeLot): EndedPrice? =
        when (val status = lot.status) {
            is LotStatus.Won -> {
                status.wonFor?.let { wonFor ->
                    val postage = lot.snipe.postage
                    val after =
                        when {
                            postage == null -> null
                            postage.isZero() -> "· free postage"
                            else -> "+ ${postage.formatted()} postage"
                        }
                    EndedPrice("Won for", wonFor.formatted(), after)
                }
            }

            is LotStatus.Lost -> {
                status.soldFor?.let { EndedPrice("Sold for", it.formatted()) }
            }

            is LotStatus.BidNotSent, is LotStatus.BidFailed, is LotStatus.CancelledByGroup -> {
                lot.snipe.result
                    ?.finalPrice
                    ?.let { EndedPrice("Sold for", it.formatted()) }
            }

            else -> {
                null
            }
        }

    /** The line under a finished row's price: "£11.00 under your £45.00 max", "Your max was £65.00", or the detail. */
    fun endedNote(
        lot: HomeLot,
        now: Instant,
    ): String? =
        when (val status = lot.status) {
            is LotStatus.Won -> {
                val wonFor = status.wonFor
                val max = status.max
                when {
                    wonFor == null || max == null || wonFor.currency != max.currency -> null
                    max.minor > wonFor.minor -> "${(max - wonFor).formatted()} under your ${max.formatted()} max"
                    else -> "At your ${max.formatted()} max"
                }
            }

            is LotStatus.Lost -> {
                status.max?.let { "Your max was ${it.formatted()}" }
            }

            else -> {
                status.detail(now)
            }
        }

    // TalkBack.

    /**
     * One sentence per row (DESIGN 3.5): "Omega Speedmaster Pre-moon 1960s. Needs attention: £250.00 over your
     * £1,900.00 max. Current bid £2,150.00, 31 bids, postage £12.95. Ends today at 21:02, in 2 hours 53 minutes."
     */
    fun spokenRow(
        lot: HomeLot,
        now: Instant,
    ): String =
        buildString {
            append(spokenTitle(lot.snipe.title)).append(". ")
            append(lot.status.spokenLabel(now)).append('.')
            if (lot.status.kind.isFinal) {
                endedPrice(lot)?.let { price ->
                    append(" ${price.label} ${price.amount}")
                    price.after?.let { append(" ").append(it.replace("+ ", "plus ").replace("· ", "")) }
                    append('.')
                }
                endedNote(lot, now)?.let { append(" ").append(it.trimEnd('.')).append('.') }
                return@buildString
            }
            append(" ").append(spokenMoney(lot)).append('.')
            append(" ").append(spokenEnd(now, Instant.ofEpochMilli(lot.snipe.endTimeUtcMillis))).append('.')
            flags(lot, now).forEach { append(" ").append(it.spoken.trimEnd('.')).append('.') }
            lot.pricesAsOf?.let { append(" Prices as of ${moment(now, it)}.") }
        }

    /** Titles without quote marks, which TalkBack reads out. */
    fun spokenTitle(title: String): String = title.filterNot { it in SpokenQuoteMarks }

    private fun spokenMoney(lot: HomeLot): String {
        val price = lot.snipe.lastPrice
        val bids = lot.snipe.lastBidCount ?: 0
        val start =
            when {
                price == null -> "Price not known yet"
                bids == 0 -> "Starting price ${price.formatted()}, no bids"
                else -> "Current bid ${price.formatted()}, ${plural(bids, "bid", "bids")}"
            }
        val postage = lot.snipe.postage
        val postageWords =
            when {
                postage == null -> "postage not listed"
                postage.isZero() -> "free postage"
                else -> "postage ${postage.formatted()}"
            }
        return "$start, $postageWords"
    }

    /** "Ends today at 21:02, in 2 hours 53 minutes", "Ends Mon 10 Nov at 20:10, in 1 day 2 hours". */
    fun spokenEnd(
        now: Instant,
        end: Instant,
    ): String {
        if (end <= now) return "Ended ${spokenMoment(now, end)}"
        return "Ends ${spokenMoment(now, end)}, in ${Duration.between(now, end).spoken()}"
    }

    private fun spokenMoment(
        now: Instant,
        instant: Instant,
    ): String {
        val clock = clockFor(now, instant)
        val zone = if (isWithinAnHour(now, instant)) " ${instant.zoneName()}" else ""
        return if (isSameDay(now, instant)) "today at $clock$zone" else "${instant.dayDate()} at $clock$zone"
    }

    // The hero (DESIGN 3.3 and 3.4).

    /** The ticking figure of "Ends in 13 m 07 s", or null once the end has passed. */
    fun heroCountdown(
        now: Instant,
        end: Instant,
    ): String? = if (end > now) Duration.between(now, end).countdown() else null

    /** The end clock on the right: "18:21:40" inside the hour, "21:02" otherwise. */
    fun heroClock(
        now: Instant,
        end: Instant,
    ): String = clockFor(now, end)

    /** Under the end clock: "Today · GMT", "Mon 10 Nov · GMT". */
    fun heroDayAndZone(
        now: Instant,
        end: Instant,
    ): String = "${dayLabel(now, end)} · ${end.zoneName()}"

    /**
     * The second line of the time block. Armed: when Kestrel bids ("Bids at 18:21:34, 6 s before",
     * "Bids Mon 10 Nov at 20:10"). Every other status: its detail line from the status model, with the ticking figure
     * of the final minute in tabular figures.
     */
    fun heroLine(
        lot: HomeLot,
        now: Instant,
    ): AnnotatedString =
        when (val status = lot.status) {
            is LotStatus.Armed -> {
                bidsAt(now, status.bidAt, status.leadSeconds)
            }

            is LotStatus.FinalMinute -> {
                spanFigure(status.detail(now), timeLeft(now, status.bidAt))
            }

            else -> {
                AnnotatedString(status.detail(now))
            }
        }

    /** One sentence for the whole time block, which is not a live region. */
    fun heroSpokenTime(
        lot: HomeLot,
        now: Instant,
    ): String {
        val end = Instant.ofEpochMilli(lot.snipe.endTimeUtcMillis)
        val ends =
            if (end <= now) {
                "Ended ${spokenMoment(now, end)}."
            } else {
                val day = if (isSameDay(now, end)) "today" else "on ${end.dayDate()}"
                "Ends in ${Duration.between(now, end).spoken()}, at ${clockFor(now, end)} ${end.zoneName()} $day."
            }
        val line =
            when (val status = lot.status) {
                is LotStatus.Armed -> {
                    val lead = "${plural(status.leadSeconds, "second", "seconds")} before the end"
                    val at = clockFor(now, status.bidAt)
                    if (isSameDay(now, status.bidAt)) {
                        "Kestrel bids at $at, $lead."
                    } else {
                        "Kestrel bids on ${status.bidAt.dayDate()} at $at, $lead."
                    }
                }

                else -> {
                    status.detail(now).replace(NARROW_NO_BREAK_SPACE, ' ')
                }
            }
        return "$ends $line"
    }

    // Snackbars.

    /** The first two words of a title, for snackbars: "Omega Speedmaster", "Canon AE-1", "Game & Watch". */
    fun shortTitle(title: String): String {
        val words = title.split(' ').filter { it.isNotBlank() }
        val kept = mutableListOf<String>()
        var counted = 0
        for (word in words) {
            if (counted == 2) break
            kept += word
            if (word.any { it.isLetterOrDigit() }) counted++
        }
        return kept.joinToString(" ").trim { it in QuoteMarks || it in TrailingPunctuation }.ifEmpty { title }
    }

    // Helpers.

    private fun bidsAt(
        now: Instant,
        bidAt: Instant,
        leadSeconds: Int,
    ): AnnotatedString =
        if (isSameDay(now, bidAt)) {
            figureSentence("Bids at ", clockFor(now, bidAt), ", ${secondsText(leadSeconds.toLong())} before")
        } else {
            figureSentence("Bids ${bidAt.dayDate()} at ", clockFor(now, bidAt))
        }

    /** [text] with tabular figures on the first [figure] it contains. */
    private fun spanFigure(
        text: String,
        figure: String,
    ): AnnotatedString {
        val start = text.indexOf(figure)
        if (start < 0) return AnnotatedString(text)
        return buildAnnotatedString {
            append(text.substring(0, start))
            appendFigure(figure)
            append(text.substring(start + figure.length))
        }
    }

    /**
     * "Today 18:21:40 GMT", "Today 21:02", "Mon 10 Nov, 20:10" (absoluteTime's form) with the clock as a figure.
     * [sentence] writes it for "Ended …": "Ended at 18:21:40 GMT", "Ended Sat 8 Nov, 20:14".
     */
    private fun AnnotatedString.Builder.appendMoment(
        now: Instant,
        instant: Instant,
        sentence: Boolean,
    ) {
        val close = isWithinAnHour(now, instant)
        val clock = if (close) instant.clockWithSeconds() else instant.clock()
        when {
            isSameDay(now, instant) -> append(if (sentence) "at " else "Today ")
            else -> append("${instant.dayDate()}, ")
        }
        appendFigure(clock)
        if (close) append(" ${instant.zoneName()}")
    }

    /** "18:02" today, "Sat 8 Nov 23:10" on another day. */
    private fun moment(
        now: Instant,
        instant: Instant,
    ): String = if (isSameDay(now, instant)) instant.clock() else "${instant.dayDate()} ${instant.clock()}"
}
