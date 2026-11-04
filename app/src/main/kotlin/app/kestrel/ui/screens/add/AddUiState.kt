package app.kestrel.ui.screens.add

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import app.kestrel.ui.components.MoneyFigures
import app.kestrel.ui.model.LotPhoto
import app.kestrel.ui.model.LotStatus
import java.time.Instant

/**
 * Everything S2 shows at [now] (DESIGN S2). [page] is the step the user is on; [groups] feeds the group picker.
 * The screen never reads the clock: the ViewModel ticks [now].
 */
@Immutable
data class AddUiState(
    val now: Instant,
    val page: AddPage,
    val groups: GroupPickerState? = null,
)

/** The steps of S2, from a link to a plan. */
@Immutable
sealed interface AddPage {
    /** The listing's address once it is known, for "View listing". */
    val listingUrl: String? get() = null

    /** No link yet: the link field, with Paste when the clipboard holds a link (the screen decides). */
    data class LinkEntry(
        val link: TextFieldState,
    ) : AddPage

    /** Loading the listing at [address] ("ebay.co.uk/itm/127504318862"). */
    data class Fetching(
        val address: String,
    ) : AddPage

    /**
     * "Couldn't open this listing." with [reason]; Try again when [canRetry], Sign in when [signIn]. [failedUrl] is
     * the page eBay showed instead, when the user can open it and deal with it (a quick check).
     */
    data class Failed(
        val reason: String,
        val canRetry: Boolean,
        val signIn: Boolean = false,
        val failedUrl: String? = null,
    ) : AddPage {
        override val listingUrl: String? get() = failedUrl
    }

    /** A listing Kestrel cannot snipe: a fixed-price listing, or an auction that has ended. */
    data class CannotSnipe(
        val header: ListingHeader,
        val reason: CannotSnipeReason,
        override val listingUrl: String,
    ) : AddPage

    /** The listing already has an open snipe, [snipeId], whose state is [status]. */
    data class AlreadySniped(
        val header: ListingHeader,
        val snipeId: Long,
        val status: LotStatus,
        override val listingUrl: String,
    ) : AddPage

    /** The preview and the plan: everything needed to arm. */
    data class Ready(
        val header: ListingHeader,
        override val listingUrl: String,
        val facts: ListingFacts,
        val max: MaxEntry,
        val worstCase: WorstCase,
        val oddPence: OddPence?,
        val timing: BidTimingChoice,
        val group: GroupRowState,
        val bar: ArmBar,
        /** Set while the amount is spelt out before arming. */
        val wordsCheck: WordsCheck? = null,
        /** True while the snipe is being stored, so the button cannot arm twice. */
        val busy: Boolean = false,
    ) : AddPage
}

enum class CannotSnipeReason { BuyItNow, Ended }

/** The lot header (DESIGN S2 item 2): the plate, the title and the seller. */
@Immutable
data class ListingHeader(
    val title: String,
    val photo: LotPhoto?,
    val sellerName: String?,
    /** A UK private seller: eBay adds Buyer Protection to the price. */
    val privateSeller: Boolean,
)

/** The facts card (DESIGN S2 item 3). [endsAt] is formatted against now, because "in 4 d 1 h" ticks. */
@Immutable
data class ListingFacts(
    val figures: MoneyFigures,
    /** "Includes £3.13 Buyer Protection (private seller)." */
    val buyerProtection: String?,
    /** For a listing in another currency: what the max is in, and eBay's conversion when it showed one. */
    val currencyNote: String?,
    /** When the listing names no postage. */
    val postageNote: String?,
    val endsAt: Instant,
)

/** The max field: [field] is hoisted into the ViewModel, so typing never rebuilds it. */
@Immutable
data class MaxEntry(
    val field: TextFieldState,
    val currencySymbol: String,
    /** "£66.00", for "At least £66.00". */
    val minimum: String,
    val error: String?,
    val readOnly: Boolean = false,
)

/** The sum row (DESIGN 3.11), or, when it cannot be added up, one sentence instead. */
@Immutable
sealed interface WorstCase {
    data class Sum(
        val max: String,
        val postage: String,
        val total: String,
    ) : WorstCase

    data class Sentence(
        val text: String,
    ) : WorstCase
}

/** The lead-time toggles: [options] in seconds, the selected one, and whether the listing uses extended bidding. */
@Immutable
data class BidTimingChoice(
    val options: List<Int>,
    val selectedIndex: Int,
    val extendedBidding: Boolean,
) {
    val selectedSeconds: Int get() = options[selectedIndex]
}

/** The group row: "Add to a group" until one is chosen. */
@Immutable
sealed interface GroupRowState {
    data object None : GroupRowState

    /** "Portable audio: win 1 of 3" with what the rule does. */
    data class Chosen(
        val title: String,
        val detail: String,
    ) : GroupRowState
}

/** The pinned bar above the keyboard: the arm summary, and the button. */
@Immutable
data class ArmBar(
    val summary: ArmSummary,
    /** Signed out of eBay: "Sign in to arm this snipe."; the button opens sign-in first. */
    val signInNeeded: Boolean,
    val button: ArmButton,
)

/**
 * The exact contract: "Bids £90.00 on Thu 13 Nov at 20:04:12 GMT". [figure] is the clock time inside [text], set in
 * tabular figures. While the typed max is too low it says what to type instead ("Set at least £66.08 to arm").
 */
@Immutable
data class ArmSummary(
    val text: String,
    val figure: String? = null,
)

@Immutable
sealed interface ArmButton {
    /** "Arm snipe", enabled when the max is at least eBay's minimum. */
    data class Arm(
        val enabled: Boolean,
    ) : ArmButton

    /** The empty-max button: "Watch without a max". */
    data object Watch : ArmButton
}

/** The max spelt out before arming a max far above the price. */
@Immutable
data class WordsCheck(
    /** "£900.00" */
    val amount: String,
    /** "Nine hundred pounds" */
    val words: String,
    /** "That’s more than three times the current price of £64.00." */
    val reason: String,
    /** The button that goes ahead: "Arm snipe", "Save max". */
    val confirmLabel: String,
)

/** The group picker sheet (DESIGN Group screen, "Group picker sheet"). */
@Immutable
data class GroupPickerState(
    val options: List<GroupOption>,
    val selected: GroupPick,
    /** The name of a new group, typed when [selected] is [GroupPick.New]. */
    val newName: TextFieldState,
)

/** An existing group and how many open snipes it has. */
@Immutable
data class GroupOption(
    val id: Long,
    val name: String,
    val openSnipes: Int,
)

/** What the picker has selected. */
@Immutable
sealed interface GroupPick {
    data object NoGroup : GroupPick

    data class Existing(
        val id: Long,
    ) : GroupPick

    data object New : GroupPick
}
