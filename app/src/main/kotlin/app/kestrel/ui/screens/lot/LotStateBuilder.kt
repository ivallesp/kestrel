package app.kestrel.ui.screens.lot

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import app.kestrel.data.PricePoint
import app.kestrel.ebay.BidIncrements
import app.kestrel.model.Money
import app.kestrel.model.Snipe
import app.kestrel.model.StatusFamily
import app.kestrel.model.TimelineEvent
import app.kestrel.ui.components.MoneyFigures
import app.kestrel.ui.components.figureSentence
import app.kestrel.ui.components.lotMoneyFigures
import app.kestrel.ui.format.AuctionZone
import app.kestrel.ui.format.clock
import app.kestrel.ui.format.clockFor
import app.kestrel.ui.format.clockWithSeconds
import app.kestrel.ui.format.clockWithTenths
import app.kestrel.ui.format.countdown
import app.kestrel.ui.format.dayDate
import app.kestrel.ui.format.dayLabel
import app.kestrel.ui.format.forDisplay
import app.kestrel.ui.format.formatted
import app.kestrel.ui.format.isSameDay
import app.kestrel.ui.format.isWithinAnHour
import app.kestrel.ui.format.plural
import app.kestrel.ui.format.secondsText
import app.kestrel.ui.format.secondsWithHundredths
import app.kestrel.ui.format.secondsWithTenths
import app.kestrel.ui.format.spoken
import app.kestrel.ui.format.timeLeft
import app.kestrel.ui.format.wholeSecondsLeft
import app.kestrel.ui.format.zoneName
import app.kestrel.ui.model.Connectivity
import app.kestrel.ui.model.DryRunCause
import app.kestrel.ui.model.GroupContext
import app.kestrel.ui.model.LotAction
import app.kestrel.ui.model.LotPhoto
import app.kestrel.ui.model.LotStatus
import app.kestrel.ui.model.LotStatusMapper
import app.kestrel.ui.model.LotTimes
import app.kestrel.ui.model.action
import app.kestrel.ui.model.chipText
import app.kestrel.ui.model.detail
import app.kestrel.ui.model.label
import app.kestrel.ui.screens.add.AddPlanner
import app.kestrel.ui.screens.add.MaxRules
import app.kestrel.ui.screens.result.ResultInputs
import app.kestrel.ui.screens.result.ResultStateBuilder
import app.kestrel.ui.theme.StatusTone
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/** Everything the lot screens are built from at one moment. [photo] is the plate treatment (tests pass their own). */
data class LotInputs(
    val snipe: Snipe,
    val events: List<TimelineEvent>,
    val prices: List<PricePoint>,
    val now: Instant,
    val connectivity: Connectivity = Connectivity.Online,
    val group: GroupContext? = null,
    val accountName: String? = null,
    val photo: LotPhoto = LotPhoto.of(snipe),
    val sheet: SheetRequest? = null,
    val stampSeal: Boolean = false,
) {
    val status: LotStatus get() = LotStatusMapper.statusOf(snipe, events, now, connectivity, group)
}

/**
 * An open edit sheet: which one, the lead the user picked in it, why the last save was refused, a save running, and
 * whether it suggests odd pence (Settings). [checkingWords] is a max far above the price, spelt out before it is
 * saved; [wordsConfirmed] once the user said it is what they meant.
 */
@Immutable
data class SheetRequest(
    val kind: SheetKind,
    val leadSeconds: Int? = null,
    val error: String? = null,
    val saving: Boolean = false,
    val suggestOddPence: Boolean = true,
    val checkingWords: Money? = null,
    val wordsConfirmed: Boolean = false,
)

/** Picks the page of the lot destination: the live detail, or the result once it is known (S3 crossfades into S4). */
object LotPageBuilder {
    fun page(inputs: LotInputs): LotPage {
        val status = inputs.status
        val evidence = LotEvidence(inputs.snipe, inputs.events, inputs.now.toEpochMilli())
        return if (showsResult(status, evidence, inputs.now)) {
            LotPage.Finished(ResultStateBuilder.build(inputs.toResultInputs(), status))
        } else {
            LotPage.Live(LotStateBuilder.build(inputs, status))
        }
    }

    /**
     * The result shows once it is known. A bid that was not sent or failed stays on the live page with its red card
     * until the auction ends; a result Kestrel could not read shows as unknown rather than as a wait that never ends.
     */
    fun showsResult(
        status: LotStatus,
        evidence: LotEvidence,
        now: Instant,
    ): Boolean {
        val ended = now.toEpochMilli() >= evidence.snipe.endTimeUtcMillis
        return when (status) {
            is LotStatus.BidNotSent, is LotStatus.BidFailed -> ended
            is LotStatus.ReadingResult -> evidence.resultOutcome == "UNKNOWN"
            else -> status.kind.isFinal
        }
    }

    private fun LotInputs.toResultInputs(): ResultInputs =
        ResultInputs(snipe, events, now, connectivity, group, photo, stampSeal)
}

/** Statuses in which Kestrel still means to bid: armed, the final minute, and the problems it waits out. */
internal fun LotStatus.plansABid(): Boolean =
    when (this) {
        is LotStatus.Armed, is LotStatus.FinalMinute -> true
        is LotStatus.SignInNeeded -> bidAt != null
        is LotStatus.Captcha -> bidAt != null
        is LotStatus.PhoneCheck -> bidAt != null
        is LotStatus.DryRunFailed -> bidAt != null
        is LotStatus.OfflineNearBid -> true
        is LotStatus.Unreachable -> bidAt != null
        is LotStatus.NeedsYou -> bidAt != null
        else -> false
    }

/** eBay's lowest acceptable bid now, as the engine computes it; null when the price is unknown. */
internal fun minimumNextBid(snipe: Snipe): Money? {
    val price = snipe.lastPrice ?: return null
    return try {
        BidIncrements.minimumNextBid(snipe.ref.site, price, snipe.lastBidCount ?: 0, snipe.buyerProtectionFee != null)
    } catch (error: RuntimeException) {
        null
    }
}

/** Builds S3 (DESIGN S3) from the domain at one moment. Pure: the same inputs always give the same screen. */
object LotStateBuilder {
    private const val FINAL_MINUTE_MILLIS = 60_000L
    private const val FINAL_MINUTE_SECONDS = 60L
    private const val RECENT_RISE_MILLIS = 60 * 60_000L
    private const val DRY_RUN_BEFORE_END_MINUTES = 10L

    /** A bid tick closer than this to either end of the scale would sit on the start or the end. */
    private const val TICK_MARGIN = 0.02f

    /** The lead-time choices of the set-a-max sheet (DESIGN S2 bid timing). */
    val LEAD_CHOICES = listOf(3, 6, 10)

    fun build(
        inputs: LotInputs,
        status: LotStatus = inputs.status,
        zone: ZoneId = AuctionZone,
    ): LotUiState {
        val snipe = inputs.snipe
        val now = inputs.now
        val evidence = LotEvidence(snipe, inputs.events, now.toEpochMilli())
        val end = Instant.ofEpochMilli(snipe.endTimeUtcMillis)
        val bidAt = LotTimes.bidAt(snipe)
        val lockAt = LotTimes.editsLockAt(snipe)
        return LotUiState(
            now = now,
            id = snipe.id,
            title = snipe.title.forDisplay(),
            shortTitle = shortTitle(snipe.title).forDisplay(),
            photo = inputs.photo,
            status = status,
            chipText = status.chipText(zone),
            countdown = countdown(status, now, end, bidAt, zone),
            times = times(status, evidence, now, end, bidAt, zone),
            money =
                money(snipe, status, inputs.prices, now).let { figures ->
                    priceReadAt(inputs, status)?.let { readAt ->
                        figures.copy(startLabel = lastRead(readAt, snipe.lastBidCount ?: 0, now, zone))
                    } ?: figures
                },
            menu = menu(snipe, status, evidence),
            offline = offline(inputs.connectivity, status, zone),
            scale = scale(status, evidence, now, end, bidAt),
            card = card(status, evidence, now, bidAt, zone),
            note = note(status, evidence, now, end, zone),
            extendedNote = EXTENDED_BIDDING.takeIf { snipe.extendedBidding && !status.kind.isFinal },
            edit = editRow(status, snipe, now, bidAt, lockAt, zone),
            checks = checks(snipe, status, evidence, inputs.events, inputs.accountName, now, zone),
            timeline = LotTimeline.live(evidence, inputs.prices, status, now, zone),
            announcement = announcement(status, now, bidAt),
            sheet = inputs.sheet?.let { sheet(it, inputs, status, now, end, bidAt, lockAt, zone) },
        )
    }

    /** DESIGN S3 extended state and rule MF33. */
    const val EXTENDED_BIDDING =
        "This listing uses extended bidding. Any bid in the last 60\u202Fs, including Kestrel’s, resets the clock to " +
            "60\u202Fs, so others get a minute to respond. Your max still caps what you pay."

    // The countdown: seconds in the final minute, otherwise the precision of Duration.countdown(). A partial second
    // counts as a whole one, so "0 s left" never shows: from the end on it reads "Ended". Once the bid failed nothing
    // more can happen, so the end time replaces the ticking figure.
    private fun countdown(
        status: LotStatus,
        now: Instant,
        end: Instant,
        bidAt: Instant,
        zone: ZoneId,
    ): CountdownText {
        val endsAt = "${clockFor(now, end, zone)} ${end.zoneName(zone)}"
        if (now >= end) {
            val ended = LotStatus.ReadingResult(end).label(now, zone).words
            return CountdownText(figure = "", unit = ended, finalMinute = true, spoken = "$ended at $endsAt.")
        }
        if (status is LotStatus.BidNotSent || status is LotStatus.BidFailed) {
            val ends = "Ends ${clockFor(now, end, zone)}"
            return CountdownText(figure = "", unit = ends, finalMinute = true, spoken = "Ends at $endsAt.")
        }
        val left = Duration.between(now, end)
        val bidSentence =
            when (status) {
                is LotStatus.FinalMinute -> {
                    if (now < bidAt) " Kestrel bids in ${Duration.between(now, bidAt).spoken()}." else ""
                }

                is LotStatus.Armed -> {
                    " Kestrel bids ${if (isSameDay(now, bidAt, zone)) "at " else "on "}${momentText(now, bidAt, zone)}."
                }

                else -> {
                    ""
                }
            }
        val spoken = "${left.spoken()} left.$bidSentence"
        return if (left.toMillis() <= FINAL_MINUTE_MILLIS) {
            CountdownText(
                figure = left.wholeSecondsLeft().toString(),
                unit = "s left",
                finalMinute = true,
                spoken = spoken,
            )
        } else {
            CountdownText(figure = left.countdown(), unit = "left", finalMinute = false, spoken = spoken)
        }
    }

    // The times column: when Kestrel bids (or sent), when it ends, and now within the hour.
    private fun times(
        status: LotStatus,
        evidence: LotEvidence,
        now: Instant,
        end: Instant,
        bidAt: Instant,
        zone: ZoneId,
    ): TimesColumnText {
        val sentAt = evidence.sentAt
        // More than an hour out the clocks have no seconds, so "BIDS" and "ENDS" would read the same: the bid goes
        // under the end time in words instead.
        val close = isWithinAnHour(now, end)
        val rows =
            buildList {
                when {
                    sentAt != null -> add(TimeRowText("Sent", sentAt.clockWithTenths(zone)))
                    status.plansABid() && close -> add(TimeRowText("Bids", clockFor(now, bidAt, zone)))
                }
                add(TimeRowText("Ends", clockFor(now, end, zone)))
                if (close) add(TimeRowText("Now", now.clockWithSeconds(zone), ticks = true))
            }
        val note =
            if (sentAt == null && status.plansABid() && !close) {
                "Bids ${secondsText(evidence.snipe.leadSeconds.toLong())} before"
            } else {
                null
            }
        val sameDay = isSameDay(now, end, zone)
        val ends =
            if (sameDay) {
                "Ends at ${clockFor(now, end, zone)} ${end.zoneName(zone)} today."
            } else {
                "Ends on ${end.dayDate(zone)} at ${clockFor(now, end, zone)} ${end.zoneName(zone)}."
            }
        val bid =
            when {
                sentAt != null -> " Bid sent at ${sentAt.clockWithTenths(zone)}."

                note != null -> " Kestrel bids ${plural(
                    evidence.snipe.leadSeconds,
                    "second",
                    "seconds",
                )} before the end."

                status.plansABid() -> " Kestrel bids at ${clockFor(now, bidAt, zone)}."

                else -> ""
            }
        return TimesColumnText("${dayLabel(now, end, zone)} · ${end.zoneName(zone)}", rows, ends + bid, note)
    }

    private fun money(
        snipe: Snipe,
        status: LotStatus,
        prices: List<PricePoint>,
        now: Instant,
    ): MoneyFigures {
        val current = snipe.lastPrice
        val max = snipe.maxBid
        val bids = snipe.lastBidCount ?: 0
        val postage = snipe.postage
        if (current != null && max != null) {
            val previous = recentRise(prices, current, now)
            return lotMoneyFigures(current, bids, max, postage, status.tone, previousPrice = previous)
        }
        val priceLabel = if (bids == 0) "Starting price" else "Current · ${plural(bids, "bid", "bids")}"
        val spokenPrice =
            current?.let { price ->
                if (bids == 0) {
                    "Starting price ${price.formatted()}."
                } else {
                    "Current bid ${price.formatted()}, ${plural(bids, "bid", "bids")}."
                }
            }
        val spokenPostage = postage?.let { " Postage ${it.formatted()}." } ?: ""
        return MoneyFigures(
            startLabel = if (current != null) priceLabel else "Your max",
            startValue = current?.formatted() ?: max?.formatted().orEmpty(),
            endLabel = if (postage != null) "Postage" else "",
            endValue = postage?.formatted().orEmpty(),
            spoken = (spokenPrice ?: "Your max ${max?.formatted().orEmpty()}.") + spokenPostage,
        )
    }

    /**
     * When the price shown was read, if it is old: offline (the banner's time) or after three failed checks. Null
     * while the price is current.
     */
    private fun priceReadAt(
        inputs: LotInputs,
        status: LotStatus,
    ): Instant? {
        val connectivity = inputs.connectivity
        return when {
            connectivity is Connectivity.Offline -> connectivity.sinceUtcMillis?.let(Instant::ofEpochMilli)
            status is LotStatus.Unreachable -> inputs.snipe.lastCheckedAtUtcMillis?.let(Instant::ofEpochMilli)
            else -> null
        }
    }

    /** "Last read 18:02 · 16 bids": an old price never reads as the current one. */
    private fun lastRead(
        readAt: Instant,
        bids: Int,
        now: Instant,
        zone: ZoneId,
    ): String {
        val count = if (bids == 0) "" else " · ${plural(bids, "bid", "bids")}"
        val at =
            if (isSameDay(
                    now,
                    readAt,
                    zone,
                )
            ) {
                readAt.clock(zone)
            } else {
                "${readAt.dayDate(zone)} ${readAt.clock(zone)}"
            }
        return "Last read $at$count"
    }

    /** The price before the latest rise, when that rise came within the hour: "Up £8.00 from £88.00". */
    private fun recentRise(
        prices: List<PricePoint>,
        current: Money,
        now: Instant,
    ): Money? {
        val sorted = prices.sortedBy { it.atUtcMillis }
        val latest = sorted.lastOrNull() ?: return null
        if (now.toEpochMilli() - latest.atUtcMillis > RECENT_RISE_MILLIS) return null
        // The latest point is the new price, or a rise came after it (the reply's price, not yet recorded).
        val before =
            if (latest.price == current) sorted.dropLast(1).lastOrNull { it.price != current }?.price else latest.price
        return before?.takeIf { it.currency == current.currency && it < current }
    }

    private fun menu(
        snipe: Snipe,
        status: LotStatus,
        evidence: LotEvidence,
    ): LotMenuState {
        val bidSent =
            evidence.sentAtMillis != null ||
                status is LotStatus.BidSent ||
                status is LotStatus.InTheLead ||
                status is LotStatus.Outbid ||
                status is LotStatus.NoReply ||
                status is LotStatus.Extended
        val stop =
            when {
                snipe.maxBid == null || status.kind.isFinal -> null
                bidSent -> MenuEntry("Bid already sent", enabled = false)
                status is LotStatus.ReadingResult -> null
                else -> MenuEntry("Stop sniping", enabled = true)
            }
        // The data layer refuses to delete while the bid window is open.
        val windowOpen = snipe.status.family == StatusFamily.BIDDING && !snipe.status.isFinal
        return LotMenuState(stop, canDelete = !windowOpen)
    }

    // The banner says the phone is offline once. When a bid is due within the hour its card says what that does to
    // the bid, so the banner keeps only how old the prices are.
    private fun offline(
        connectivity: Connectivity,
        status: LotStatus,
        zone: ZoneId,
    ): OfflineNote? {
        if (connectivity !is Connectivity.Offline) return null
        val since = connectivity.sinceUtcMillis?.let { Instant.ofEpochMilli(it).clock(zone) }
        val consequence = "Kestrel can’t bid until the phone reconnects.".takeIf { status !is LotStatus.OfflineNearBid }
        return if (since != null) {
            OfflineNote("Offline since $since", listOfNotNull("Prices are from $since.", consequence).joinToString(" "))
        } else {
            OfflineNote("Offline", consequence ?: "Prices may be out of date.")
        }
    }

    // The final-minute scale: the 60 s before the end (re-based after an extension), stepping once a second.
    private fun scale(
        status: LotStatus,
        evidence: LotEvidence,
        now: Instant,
        end: Instant,
        bidAt: Instant,
    ): FinalMinuteScaleState? {
        val live =
            status is LotStatus.FinalMinute ||
                status is LotStatus.BidSent ||
                status is LotStatus.InTheLead ||
                status is LotStatus.Outbid ||
                status is LotStatus.NoReply ||
                status is LotStatus.Extended ||
                status is LotStatus.ReadingResult
        val leftMillis = end.toEpochMilli() - now.toEpochMilli()
        if (!live || leftMillis > FINAL_MINUTE_MILLIS) return null
        val leftSeconds = Duration.ofMillis(leftMillis).wholeSecondsLeft()
        val elapsed = (FINAL_MINUTE_SECONDS - leftSeconds).coerceIn(0, FINAL_MINUTE_SECONDS).toInt()
        val startMillis = end.toEpochMilli() - FINAL_MINUTE_MILLIS
        val bidMillis = evidence.sentAtMillis ?: bidAt.toEpochMilli()
        val fraction = (bidMillis - startMillis).toFloat() / FINAL_MINUTE_MILLIS
        val extended = evidence.extensions.isNotEmpty() || status is LotStatus.Extended
        val startLabel =
            when {
                leftMillis <= 0 -> LotStatus.ReadingResult(end).label(now).words
                extended -> LotStatus.Extended(end).label(now).words
                else -> LotStatus.FinalMinute(Money.zero("GBP"), bidAt, 0, bidAt).chipText()
            }
        val sent = evidence.sentAtMillis != null
        val left = Duration.ofSeconds(leftSeconds).spoken()
        val bidSentence =
            when {
                sent -> {
                    " ${LotStatus.BidSent(null, now).chipText()}."
                }

                status is LotStatus.FinalMinute && now < bidAt -> {
                    " Kestrel bids in ${Duration.between(now, bidAt).spoken()}."
                }

                else -> {
                    ""
                }
            }
        return FinalMinuteScaleState(
            elapsedSeconds = elapsed,
            bidFraction = fraction.takeIf { it > TICK_MARGIN && it < 1f - TICK_MARGIN },
            startLabel = startLabel,
            flat = sent || status is LotStatus.ReadingResult,
            spoken = "$startLabel: $left left.$bidSentence",
        )
    }

    // The status card of DESIGN 3.13, with the attention and failure variants of S3.
    private fun card(
        status: LotStatus,
        evidence: LotEvidence,
        now: Instant,
        bidAt: Instant,
        zone: ZoneId,
    ): StatusCard? {
        val chip = status.chipText(zone)
        return when (status) {
            is LotStatus.Watching, is LotStatus.Armed -> {
                null
            }

            is LotStatus.FinalMinute -> {
                val title =
                    if (now < bidAt) {
                        figureSentence("Kestrel bids ${status.max.formatted()} in ", timeLeft(now, bidAt))
                    } else {
                        AnnotatedString("Kestrel bids ${status.max.formatted()} now")
                    }
                val lead = secondsText(status.leadSeconds.toLong())
                val detail = "At ${bidAt.clockWithSeconds(zone)}, $lead before the end"
                StatusCard(StatusTone.Bidding, CardGlyph.Gavel, title, detail)
            }

            is LotStatus.BidSent -> {
                StatusCard(
                    StatusTone.Bidding,
                    CardGlyph.Waiting,
                    AnnotatedString("$chip at ${status.sentAt.clockWithTenths(zone)}"),
                    "Waiting for eBay’s reply",
                )
            }

            is LotStatus.InTheLead -> {
                val quote = status.ebayText?.let { "“$it” " } ?: ""
                val title = replied(chip, status.replyMillis)
                StatusCard(StatusTone.Bidding, CardGlyph.Gavel, title, "${quote}Not won until it ends.")
            }

            is LotStatus.Outbid -> {
                // eBay's sentence already says why; Kestrel adds its own words only when eBay gave none.
                val detail = status.ebayText?.let { "“$it”" } ?: status.detail(now, zone)
                StatusCard(StatusTone.Bidding, CardGlyph.Gavel, replied(chip, status.replyMillis), detail)
            }

            is LotStatus.NoReply -> {
                StatusCard(
                    StatusTone.Bidding,
                    CardGlyph.Gavel,
                    AnnotatedString("No reply from eBay yet"),
                    "Kestrel will read the result when the auction ends.",
                )
            }

            is LotStatus.Extended -> {
                val sentAt = evidence.sentAt
                val resetAt = status.newEnd.toEpochMilli() - FINAL_MINUTE_MILLIS
                val ownBid = sentAt != null && abs(resetAt - sentAt.toEpochMilli()) <= OWN_EXTENSION_TOLERANCE_MILLIS
                val reset =
                    if (ownBid) {
                        "Kestrel’s bid at ${sentAt.clockWithSeconds(zone)} reset the clock to " +
                            "${secondsText(FINAL_MINUTE_SECONDS)}. Kestrel is still following."
                    } else {
                        status.detail(now, zone)
                    }
                // Only while eBay's latest word is still "in the lead" (DESIGN 3.13): a later outbid line, or a
                // price above the max, says otherwise even when the same reply also extended the auction.
                val leading = if (evidence.stillLeading) " You’re in the lead so far." else ""
                val title = AnnotatedString(status.label(now, zone).text)
                StatusCard(StatusTone.Bidding, CardGlyph.Gavel, title, reset + leading)
            }

            is LotStatus.ReadingResult -> {
                val title = AnnotatedString(status.label(now, zone).text)
                StatusCard(StatusTone.Bidding, CardGlyph.Waiting, title, "Checking the listing page")
            }

            is LotStatus.Captcha -> {
                val detail =
                    status.bidAt?.let {
                        "Solve it before ${momentText(now, it, zone)} so Kestrel can bid. " +
                            "Kestrel never solves these for you."
                    } ?: status.detail(now, zone)
                attentionCard(chip, detail, status.action(now))
            }

            is LotStatus.OfflineNearBid -> {
                // The offline banner above says the phone is offline; the card says what that does to the bid.
                val title = "The ${momentText(now, status.bidAt, zone)} bid can’t go out"
                attentionCard(title, "Kestrel bids once the phone reconnects.", null)
            }

            // The chip already says "Needs you"; the card says what to do.
            is LotStatus.NeedsYou -> {
                attentionCard(NEEDS_YOU_CARD_TITLE, status.detail(now, zone), status.action(now))
            }

            is LotStatus.OverMax,
            is LotStatus.SignInNeeded,
            is LotStatus.PhoneCheck,
            is LotStatus.DryRunFailed,
            is LotStatus.Unreachable,
            -> {
                attentionCard(chip, status.detail(now, zone), status.action(now))
            }

            is LotStatus.BidNotSent, is LotStatus.BidFailed -> {
                val detail = status.detail(now, zone)
                StatusCard(StatusTone.Failed, CardGlyph.Error, AnnotatedString(chip), detail, status.action(now))
            }

            is LotStatus.Won,
            is LotStatus.Lost,
            is LotStatus.EndedEarly,
            is LotStatus.CancelledByYou,
            is LotStatus.CancelledByGroup,
            -> {
                null
            }
        }
    }

    /** An extension this close to Kestrel's bid plus 60 s was caused by Kestrel's bid. */
    private const val OWN_EXTENSION_TOLERANCE_MILLIS = 1_500L

    /** The first line of the card of a page Kestrel handed over (DESIGN 6.1 NeedsYou). */
    const val NEEDS_YOU_CARD_TITLE = "Look at the listing on eBay"

    /** "In the lead · eBay replied in 0.8 s". */
    private fun replied(
        chip: String,
        replyMillis: Long?,
    ): AnnotatedString =
        AnnotatedString(if (replyMillis != null) "$chip · eBay replied in ${secondsWithTenths(replyMillis)}" else chip)

    private fun attentionCard(
        chip: String,
        detail: String,
        action: LotAction?,
    ): StatusCard = StatusCard(StatusTone.Attention, CardGlyph.Warning, AnnotatedString(chip), detail, action)

    // A quiet line where there is no card: the coming dry run, or what watching means.
    private fun note(
        status: LotStatus,
        evidence: LotEvidence,
        now: Instant,
        end: Instant,
        zone: ZoneId,
    ): String? =
        when (status) {
            is LotStatus.Watching -> {
                status.detail(now, zone)
            }

            is LotStatus.Armed -> {
                val dryRunAt = end.minus(Duration.ofMinutes(DRY_RUN_BEFORE_END_MINUTES))
                if (!evidence.dryRunDone && now < dryRunAt) {
                    "Dry run about ${dryRunAt.clock(zone)}, ${minutesText(DRY_RUN_BEFORE_END_MINUTES)} before the end."
                } else {
                    null
                }
            }

            else -> {
                null
            }
        }

    private fun editRow(
        status: LotStatus,
        snipe: Snipe,
        now: Instant,
        bidAt: Instant,
        lockAt: Instant,
        zone: ZoneId,
    ): EditRowState? {
        val max = snipe.maxBid
        val beforeTheBid = secondsText(Duration.between(lockAt, bidAt).seconds)
        val until = "Until ${momentText(now, lockAt, zone)}, $beforeTheBid before the bid"
        return when {
            status is LotStatus.FinalMinute && now >= lockAt -> {
                val locked =
                    "Locked at ${lockAt.clockWithSeconds(zone)}: Kestrel bids ${status.max.formatted()} at " +
                        bidAt.clockWithSeconds(zone)
                EditRowState(LotAction.EDIT_MAX, locked, enabled = false)
            }

            status is LotStatus.FinalMinute -> {
                EditRowState(LotAction.EDIT_MAX, until, enabled = true)
            }

            status is LotStatus.Watching -> {
                EditRowState(LotAction.SET_MAX, null, enabled = true)
            }

            status is LotStatus.OverMax -> {
                null
            }

            status.kind.family == StatusFamily.ARMED || status.kind.family == StatusFamily.ATTENTION -> {
                if (max != null) {
                    EditRowState(LotAction.EDIT_MAX, until, enabled = now < lockAt)
                } else {
                    EditRowState(LotAction.SET_MAX, null, enabled = true)
                }
            }

            else -> {
                null
            }
        }
    }

    // "CHECKS PASSED": the account, the dry run and the clock, only when each passed for this lot.
    private fun checks(
        snipe: Snipe,
        status: LotStatus,
        evidence: LotEvidence,
        events: List<TimelineEvent>,
        accountName: String?,
        now: Instant,
        zone: ZoneId,
    ): List<String> {
        if (status.kind.isFinal) return emptyList()
        return buildList {
            val signedOut =
                status is LotStatus.SignInNeeded ||
                    (status is LotStatus.DryRunFailed && status.cause == DryRunCause.SIGN_IN_PAGE)
            if (accountName != null && !signedOut) {
                val lastCheckFailed = LotStatusMapper.failedCheckAt(snipe, events) != null
                val lastGoodCheck = snipe.lastCheckedAtUtcMillis.takeIf { !lastCheckFailed }
                val checkedAt = listOfNotNull(evidence.signedInSeenAtMillis, lastGoodCheck).maxOrNull()
                add(
                    if (checkedAt != null) {
                        "Signed in as $accountName · checked ${momentText(now, Instant.ofEpochMilli(checkedAt), zone)}"
                    } else {
                        "Signed in as $accountName"
                    },
                )
            }
            evidence.dryRunPassedAtMillis?.let { dryRunAt ->
                add("Dry run at ${momentText(now, Instant.ofEpochMilli(dryRunAt), zone)} · nothing was sent")
            }
            evidence.clockOffsetMillis?.let { add("Clock ${secondsWithHundredths(it)} off Google time") }
        }
    }

    /** The polite live region speaks only at these moments (DESIGN S3 TalkBack). */
    private fun announcement(
        status: LotStatus,
        now: Instant,
        bidAt: Instant,
    ): String? =
        when (status) {
            is LotStatus.FinalMinute -> {
                val toBid = Duration.between(now, bidAt).toMillis()
                when {
                    toBid <= 0 -> null
                    toBid <= TEN_SECONDS_MILLIS -> "10 seconds to the bid"
                    toBid <= THIRTY_SECONDS_MILLIS -> "30 seconds to the bid"
                    else -> null
                }
            }

            is LotStatus.BidSent -> {
                status.chipText()
            }

            is LotStatus.NoReply -> {
                "No reply from eBay yet"
            }

            is LotStatus.InTheLead -> {
                "eBay replied: ${status.ebayText ?: status.chipText()}"
            }

            is LotStatus.Outbid -> {
                "eBay replied: ${status.ebayText ?: status.chipText()}"
            }

            else -> {
                null
            }
        }

    private const val TEN_SECONDS_MILLIS = 10_000L
    private const val THIRTY_SECONDS_MILLIS = 30_000L

    // The edit sheet: edit an armed max, raise one the price passed, or set the first max of a watched lot.
    private fun sheet(
        request: SheetRequest,
        inputs: LotInputs,
        status: LotStatus,
        now: Instant,
        end: Instant,
        bidAt: Instant,
        lockAt: Instant,
        zone: ZoneId,
    ): EditSheetState {
        val snipe = inputs.snipe
        val max = snipe.maxBid
        val currency = max?.currency ?: snipe.lastPrice?.currency ?: snipe.ref.site.currency
        val endsAt =
            if (isSameDay(now, end, zone)) {
                "ends ${clockFor(now, end, zone)} ${end.zoneName(zone)}"
            } else {
                "ends ${end.dayDate(zone)}, ${clockFor(now, end, zone)} ${end.zoneName(zone)}"
            }
        val lotLine = listOfNotNull(snipe.lastPrice?.let { "${it.formatted()} now" }, endsAt).joinToString(" · ")
        val locked =
            if (request.kind != SheetKind.Set && max != null && now >= lockAt && !status.kind.isFinal) {
                "${MaxRules.LOCKED_SENTENCE} Kestrel bids ${max.formatted()} at ${bidAt.clockWithSeconds(zone)}."
            } else {
                null
            }
        val bidsAt =
            if (isSameDay(now, bidAt, zone)) {
                "Bids at ${bidAt.clockWithSeconds(zone)} ${bidAt.zoneName(zone)}"
            } else {
                "Bids on ${bidAt.dayDate(zone)} at ${bidAt.clockWithSeconds(zone)} ${bidAt.zoneName(zone)}"
            }
        val beforeTheBid = secondsText(Duration.between(lockAt, bidAt).seconds)
        val change = "Change it until ${momentText(now, lockAt, zone)}, $beforeTheBid before the bid."
        val (title, deadline, primary) =
            when (request.kind) {
                SheetKind.Edit -> Triple("Edit your max", change, "Save max")
                SheetKind.Raise -> Triple("Raise your max", bidsAt, LotAction.RAISE_MAX.label)
                SheetKind.Set -> Triple("Set your max", null, "Arm snipe")
            }
        val setting = request.kind == SheetKind.Set
        return EditSheetState(
            kind = request.kind,
            title = title,
            photo = inputs.photo,
            lotTitle = snipe.title.forDisplay(),
            lotLine = lotLine,
            currency = currency,
            currencySymbol = currencySymbol(currency),
            minimum = minimumNextBid(snipe),
            postage = snipe.postage?.takeIf { it.currency == currency },
            current = max,
            deadline = deadline,
            locked = locked,
            primary = primary,
            endAt = end,
            leadOptions = if (setting) LEAD_CHOICES else emptyList(),
            selectedLead = if (setting) request.leadSeconds ?: snipe.leadSeconds else null,
            leadHint = if (setting) leadHint(snipe) else null,
            error = request.error,
            saving = request.saving,
            suggestOddPence = request.suggestOddPence,
            wordsCheck =
                request.checkingWords?.let { checking ->
                    snipe.lastPrice?.let { price -> AddPlanner.wordsCheck(checking, price, confirmLabel = primary) }
                },
        )
    }

    private fun leadHint(snipe: Snipe): String =
        if (snipe.extendedBidding) {
            "This listing uses extended bidding: any bid in the last 60\u202Fs, Kestrel’s too, resets the clock " +
                "to 60\u202Fs."
        } else {
            "${secondsText(6)} suits most connections. Choose ${secondsText(10)} on a\u00A0weak\u00A0signal."
        }
}
