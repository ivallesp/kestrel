package app.kestrel.engine

import app.kestrel.model.Listing
import app.kestrel.model.ListingStatus
import app.kestrel.model.Money
import app.kestrel.model.SnipeOutcome

/** What eBay last said about Kestrel's own bid. */
enum class BidStanding { LEADING, OUTBID, REJECTED, UNCONFIRMED, UNKNOWN }

/** What Kestrel knows about its own bid when it reads the final page. */
data class BidEvidence(
    val sent: Boolean,
    val standing: BidStanding,
)

data class Resolution(
    val outcome: SnipeOutcome,
    /** Plain-language explanation for the result screen. */
    val reason: String,
)

/**
 * Decides won or lost from the ended item page. Structured fields come first: the listing status, the signed-in
 * user's standing (userBiddingStatus, isHighBidder), the reserve and the final price. A final price equal to the max is
 * never called a win on the price or the banner text alone, because eBay gives a tie to the earlier bid; a high bidder
 * on a reserve auction is a winner only once the reserve is known to be met. Contradicting fields give UNKNOWN rather
 * than a guess, and banner text is only a last hint.
 */
object ResultResolver {
    private val wonStatuses = setOf("WINNING", "WON", "WINNER", "HIGH_BIDDER", "HIGHEST_BIDDER")
    private val lostStatuses = setOf("OUTBID", "LOST", "LOSING", "NOT_WINNING")
    private val wonPhrases = listOf("you won", "you've won", "you have won")
    private val lostPhrases = listOf("you didn't win", "you did not win", "you were outbid", "you've been outbid")

    /**
     * [knownEndUtcMillis] is the end Kestrel was working towards; an end well before it means the seller ended the
     * listing early.
     */
    fun resolve(
        listing: Listing,
        max: Money?,
        bid: BidEvidence,
        knownEndUtcMillis: Long,
    ): Resolution {
        if (listing.status != ListingStatus.ENDED) {
            return Resolution(SnipeOutcome.UNKNOWN, "eBay still shows the auction as running.")
        }
        val verdict = structuredVerdict(listing)
        if (!bid.sent) return resolveWithoutBid(listing, verdict, knownEndUtcMillis)
        if (listing.bidCount == 0) {
            return Resolution(SnipeOutcome.FAILED, "eBay shows no bids on the listing, so the bid did not register.")
        }
        val reserve = reserveSatisfied(listing)
        if (reserve == false) {
            return Resolution(SnipeOutcome.LOST, "The reserve price was not met, so the item did not sell.")
        }
        val finalVersusMax = max?.let { listing.currentPrice.compareSafely(it) }
        val belowMaxWhileLeading = finalVersusMax != null && finalVersusMax < 0 && bid.standing == BidStanding.LEADING
        when (verdict) {
            SnipeOutcome.WON -> {
                return when {
                    finalVersusMax != null && finalVersusMax > 0 -> {
                        Resolution(
                            SnipeOutcome.UNKNOWN,
                            "eBay's page says you won, but the final price is above your max. Check eBay.",
                        )
                    }

                    reserve == null -> {
                        Resolution(
                            SnipeOutcome.UNKNOWN,
                            "You were the highest bidder, but eBay did not say whether the reserve was met. Check eBay.",
                        )
                    }

                    else -> {
                        Resolution(SnipeOutcome.WON, "eBay says you are the winning bidder.")
                    }
                }
            }

            SnipeOutcome.LOST -> {
                return if (belowMaxWhileLeading) {
                    Resolution(
                        SnipeOutcome.UNKNOWN,
                        "eBay's page says you did not win, but the price ended below your max " +
                            "after eBay said you led. Check eBay.",
                    )
                } else {
                    Resolution(SnipeOutcome.LOST, "eBay says another bidder won.")
                }
            }

            else -> {}
        }
        when (textHint(listing.endedText)) {
            SnipeOutcome.WON -> {
                // Text never settles a tie at the max.
                if (finalVersusMax != null && finalVersusMax < 0 && reserve == true) {
                    return Resolution(SnipeOutcome.WON, "eBay's message says you won.")
                }
            }

            SnipeOutcome.LOST -> {
                if (!belowMaxWhileLeading) return Resolution(SnipeOutcome.LOST, "eBay's message says you did not win.")
            }

            else -> {}
        }
        return resolveFromPrice(listing, max, finalVersusMax, bid)
    }

    private fun resolveWithoutBid(
        listing: Listing,
        verdict: SnipeOutcome?,
        knownEndUtcMillis: Long,
    ): Resolution =
        when {
            verdict == SnipeOutcome.WON && reserveSatisfied(listing) == true -> {
                Resolution(SnipeOutcome.WON, "eBay says you won. Kestrel did not bid, so this was your own bid.")
            }

            listing.endedBeforeSchedule(knownEndUtcMillis) -> {
                Resolution(
                    SnipeOutcome.ENDED_EARLY,
                    "The listing ended before its scheduled end, so Kestrel did not bid.",
                )
            }

            else -> {
                Resolution(SnipeOutcome.NOT_PLACED, "The auction ended without a bid from Kestrel.")
            }
        }

    private fun resolveFromPrice(
        listing: Listing,
        max: Money?,
        finalVersusMax: Int?,
        bid: BidEvidence,
    ): Resolution {
        val finalText = listing.currentPrice.displayText()
        val maxText = max?.displayText() ?: "your max"
        return when {
            finalVersusMax == null -> {
                Resolution(SnipeOutcome.UNKNOWN, "Kestrel could not compare the final price with your max. Check eBay.")
            }

            finalVersusMax > 0 -> {
                Resolution(SnipeOutcome.LOST, "The final price $finalText is above your max $maxText.")
            }

            finalVersusMax == 0 -> {
                Resolution(
                    SnipeOutcome.UNKNOWN,
                    "The final price equals your max $maxText. A tie goes to the earlier bid, so check eBay.",
                )
            }

            bid.standing == BidStanding.LEADING && reserveSatisfied(listing) == true -> {
                Resolution(
                    SnipeOutcome.WON,
                    "The auction ended at $finalText, below your max $maxText, after eBay said your bid was leading.",
                )
            }

            else -> {
                Resolution(
                    SnipeOutcome.UNKNOWN,
                    "The auction ended at $finalText, below your max, but eBay did not confirm your bid. Check eBay.",
                )
            }
        }
    }

    // True when there is no reserve or it was met, false when it was not, null when unknown. "Item sold" means a sale.
    private fun reserveSatisfied(listing: Listing): Boolean? =
        when {
            !listing.hasReserve -> true
            listing.reserveMet != null -> listing.reserveMet
            listing.endedText?.contains("item sold", ignoreCase = true) == true -> true
            else -> null
        }

    // The signed-in user's standing. isHighBidder is false on every signed-out page, so it only counts when signed in.
    private fun structuredVerdict(listing: Listing): SnipeOutcome? {
        val status =
            listing.userBiddingStatus
                ?.trim()
                ?.uppercase()
                ?.replace(' ', '_')
        return when {
            status in wonStatuses -> SnipeOutcome.WON
            status in lostStatuses -> SnipeOutcome.LOST
            listing.signedIn == true && listing.userIsHighBidder == true -> SnipeOutcome.WON
            listing.signedIn == true && listing.userIsHighBidder == false -> SnipeOutcome.LOST
            else -> null
        }
    }

    // The 2026 signed-in wording was never captured, so these older English phrases are hints, not proof.
    private fun textHint(endedText: String?): SnipeOutcome? {
        val text = endedText?.lowercase()?.replace('\u2019', '\'') ?: return null
        return when {
            lostPhrases.any { it in text } -> SnipeOutcome.LOST
            wonPhrases.any { it in text } -> SnipeOutcome.WON
            else -> null
        }
    }
}
