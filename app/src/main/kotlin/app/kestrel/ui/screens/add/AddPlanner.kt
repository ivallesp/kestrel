package app.kestrel.ui.screens.add

import androidx.compose.foundation.text.input.TextFieldState
import app.kestrel.engine.BidTiming
import app.kestrel.model.Listing
import app.kestrel.model.Money
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeGroup
import app.kestrel.ui.components.MoneyFigures
import app.kestrel.ui.format.armSummary
import app.kestrel.ui.format.clockWithSeconds
import app.kestrel.ui.format.dayDate
import app.kestrel.ui.format.formatted
import app.kestrel.ui.format.isSameDay
import app.kestrel.ui.format.plural
import app.kestrel.ui.model.LotPhoto
import java.time.Instant
import kotlin.math.abs

/** The group a new snipe joins: none, an existing one, or one created when it arms. */
sealed interface GroupChoice {
    data object None : GroupChoice

    data class Existing(
        val group: SnipeGroup,
    ) : GroupChoice

    data class New(
        val name: String,
    ) : GroupChoice
}

/**
 * Turns a listing and what the user typed into S2's words and figures (DESIGN S2 and 6.3). Pure, so every state is
 * a unit test and a screenshot.
 */
object AddPlanner {
    /** Shown in the max and total columns of the sum row while no max is typed. */
    const val NO_AMOUNT = "—"

    fun header(listing: Listing): ListingHeader =
        ListingHeader(
            title = listing.title,
            photo = LotPhoto(url = listing.imageUrl),
            sellerName = listing.sellerName,
            privateSeller = listing.buyerProtectionFee != null,
        )

    fun header(snipe: Snipe): ListingHeader =
        ListingHeader(
            title = snipe.title,
            photo = LotPhoto.of(snipe),
            sellerName = snipe.sellerName,
            privateSeller = snipe.buyerProtectionFee != null,
        )

    fun facts(listing: Listing): ListingFacts {
        val current = listing.currentPrice
        val postage = listing.postage
        val bids = listing.bidCount
        val startLabel = if (bids == 0) "Starting price" else "Current · ${plural(bids, "bid", "bids")}"
        val spoken =
            buildString {
                append(if (bids == 0) "Starting price ${current.formatted()}" else "Current bid ${current.formatted()}")
                if (bids > 0) append(", ${plural(bids, "bid", "bids")}")
                listing.buyerProtectionFee?.let { append(", including ${it.formatted()} Buyer Protection") }
                append(". ")
                append(postage?.let { "Postage ${it.formatted()}." } ?: "Postage isn’t listed.")
            }
        return ListingFacts(
            figures =
                MoneyFigures(
                    startLabel = startLabel,
                    startValue = current.formatted(),
                    endLabel = if (postage != null) "Postage" else "",
                    endValue = postage?.formatted().orEmpty(),
                    spoken = spoken,
                ),
            buyerProtection =
                listing.buyerProtectionFee?.let {
                    "Includes ${it.formatted()} Buyer Protection (private seller)."
                },
            currencyNote = currencyNote(listing),
            postageNote = if (postage == null) "Postage isn’t listed. Check it on eBay before you bid." else null,
            endsAt = Instant.ofEpochMilli(listing.endTimeUtcMillis),
        )
    }

    /** "Priced in US dollars, so your max is in US$ too. eBay shows about £110.42 for the current price." */
    private fun currencyNote(listing: Listing): String? {
        val currency = listing.currentPrice.currency
        if (currency == HOME_CURRENCY) return null
        val name = CurrencyWords.of(currency).name
        val symbol = MaxRules.currencySymbol(currency)
        val converted =
            listing.currentPriceInSiteCurrency
                ?.takeIf { it.currency != currency }
                ?.let { " eBay shows about ${it.formatted()} for the current price." }
                .orEmpty()
        return "Priced in $name, so your max is in $symbol too.$converted"
    }

    /** When Kestrel bids on [listing] with [leadSeconds], from the engine's own timing. */
    fun bidAt(
        listing: Listing,
        leadSeconds: Int,
    ): Instant = Instant.ofEpochMilli(BidTiming.fireAtUtcMillis(listing.endTimeUtcMillis, leadSeconds))

    /** The minimum eBay accepts now for [listing], or its price when the increment table cannot say. */
    fun minimum(listing: Listing): Money =
        MaxRules.minimumBid(
            site = listing.ref.site,
            current = listing.currentPrice,
            bidCount = listing.bidCount,
            buyerProtectionIncluded = listing.buyerProtectionFee != null,
        ) ?: listing.currentPrice

    /** The sum row for what is typed: dashes while empty, the typed amount otherwise (DESIGN 3.11). */
    fun worstCase(
        typed: TypedMax,
        postage: Money?,
    ): WorstCase {
        val max =
            when (typed) {
                TypedMax.Empty -> null
                is TypedMax.TooLow -> typed.typed
                is TypedMax.Valid -> typed.max
            }
        if (postage == null) {
            return WorstCase.Sentence(
                max?.let { "You pay at most ${it.formatted()}, plus any postage." }
                    ?: "You pay at most your max, plus any postage.",
            )
        }
        if (max == null) return WorstCase.Sum(NO_AMOUNT, postage.formatted(), NO_AMOUNT)
        val total =
            MaxRules.worstCase(max, postage)
                ?: return WorstCase.Sentence("You pay at most ${max.formatted()}, plus ${postage.formatted()} postage.")
        return WorstCase.Sum(max.formatted(), postage.formatted(), total.formatted())
    }

    /** The lead-time toggles, selecting the option nearest to [leadSeconds]. */
    fun timing(
        leadSeconds: Int,
        extendedBidding: Boolean,
    ): BidTimingChoice {
        val options = MaxRules.LeadOptions
        val index = options.indices.minBy { abs(options[it] - leadSeconds) }
        return BidTimingChoice(options, index, extendedBidding)
    }

    /** The group row for [choice]; [openSnipes] counts the open snipes already in an existing group. */
    fun groupRow(
        choice: GroupChoice,
        openSnipes: Int,
    ): GroupRowState =
        when (choice) {
            GroupChoice.None -> {
                GroupRowState.None
            }

            is GroupChoice.Existing -> {
                val total = openSnipes + 1
                GroupRowState.Chosen(
                    title = "${choice.group.name}: win ${choice.group.targetWins} of $total",
                    detail = cancelsSentence(total),
                )
            }

            is GroupChoice.New -> {
                GroupRowState.Chosen(
                    title = "${choice.name}: win 1 of 1",
                    detail = "A new group. Add more snipes to it later.",
                )
            }
        }

    /** "Once one is won, Kestrel cancels the other." (two lots) or "the rest" (more). */
    fun cancelsSentence(lots: Int): String =
        if (lots <= 2) "Once one is won, Kestrel cancels the other." else "Once one is won, Kestrel cancels the rest."

    /**
     * The pinned bar: the exact plan when the max is valid, the last valid plan greyed while it is too low, and the
     * watch sentence while it is empty.
     */
    fun bar(
        typed: TypedMax,
        lastValidMax: Money?,
        bidAt: Instant,
        now: Instant,
        signedIn: Boolean,
        busy: Boolean,
    ): ArmBar {
        val summary =
            when (typed) {
                TypedMax.Empty -> {
                    ArmSummary("No max yet: Kestrel will only watch the price.")
                }

                is TypedMax.Valid -> {
                    plan(typed.max, bidAt, now)
                }

                is TypedMax.TooLow -> {
                    lastValidMax?.let { plan(it, bidAt, now).copy(greyed = true) }
                        ?: ArmSummary("", greyed = true)
                }
            }
        val button =
            if (typed ==
                TypedMax.Empty
            ) {
                ArmButton.Watch
            } else {
                ArmButton.Arm(enabled = typed is TypedMax.Valid && !busy)
            }
        return ArmBar(summary = summary, signInNeeded = !signedIn && typed != TypedMax.Empty, button = button)
    }

    /** "Bids £90.00 on Thu 13 Nov at 20:04:12 GMT", with the clock time marked for tabular figures. */
    fun plan(
        max: Money,
        bidAt: Instant,
        now: Instant,
    ): ArmSummary = ArmSummary(text = armSummary(max, bidAt, now), figure = bidAt.clockWithSeconds())

    /** The snackbar after arming: "Armed: bids Thu 13 Nov at 20:04:12". */
    fun armedMessage(
        bidAt: Instant,
        now: Instant,
    ): String {
        val day = if (isSameDay(now, bidAt)) "today" else bidAt.dayDate()
        return "Armed: bids $day at ${bidAt.clockWithSeconds()}"
    }

    /** The amount spelt out, when [max] is more than three times [current]. */
    fun wordsCheck(
        max: Money,
        current: Money,
        confirmLabel: String,
    ): WordsCheck =
        WordsCheck(
            amount = max.formatted(),
            words = AmountInWords.of(max),
            reason = "That’s more than three times the current price of ${current.formatted()}.",
            confirmLabel = confirmLabel,
        )

    /** Everything S2 shows once the listing is loaded. */
    fun ready(
        listing: Listing,
        minimum: Money,
        maxField: TextFieldState,
        typed: TypedMax,
        lastValidMax: Money?,
        leadSeconds: Int,
        group: GroupRowState,
        signedIn: Boolean,
        checkingWords: Boolean,
        busy: Boolean,
        now: Instant,
    ): AddPage.Ready {
        val timing = timing(leadSeconds, listing.extendedBidding)
        return AddPage.Ready(
            header = header(listing),
            listingUrl = listing.ref.url,
            facts = facts(listing),
            max =
                MaxEntry(
                    field = maxField,
                    currencySymbol = MaxRules.currencySymbol(listing.currentPrice.currency),
                    minimum = minimum.formatted(),
                    error = (typed as? TypedMax.TooLow)?.let { MaxRules.tooLowMessage(it.minimum) },
                ),
            worstCase = worstCase(typed, listing.postage),
            oddPence = MaxRules.oddPence(typed),
            timing = timing,
            group = group,
            bar = bar(typed, lastValidMax, bidAt(listing, timing.selectedSeconds), now, signedIn, busy),
            wordsCheck =
                (typed as? TypedMax.Valid)
                    ?.takeIf { checkingWords }
                    ?.let { wordsCheck(it.max, listing.currentPrice, confirmLabel = "Arm snipe") },
            busy = busy,
        )
    }

    /** The currency Kestrel's user thinks in: listings in any other one get the currency note. */
    private const val HOME_CURRENCY = "GBP"
}
