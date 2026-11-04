package app.kestrel.ui.screens.add

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import app.kestrel.model.EbaySite
import app.kestrel.model.ItemRef
import app.kestrel.model.Listing
import app.kestrel.model.ListingStatus
import app.kestrel.model.Money
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeStatus
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.fixture.CatalogueFixture.at
import app.kestrel.ui.fixture.CatalogueFixture.gbp
import app.kestrel.ui.fixture.CatalogueFixture.millis
import app.kestrel.ui.harness.FixturePhotos
import app.kestrel.ui.model.LotStatusMapper
import java.time.Instant

/**
 * S2 and its sheets in every state, built from the design fixture (DESIGN Appendix A) through the real planner, so
 * the screenshots exercise the same words and rules as the app. Lane-only listings live here.
 */
object AddFixtures {
    /** S2 renders at 18:09:10 (DESIGN S2 "Now"). */
    val now: Instant = at("2025-11-09T18:09:10Z")

    val sonyA700: Listing = CatalogueFixture.sonyA700

    /** The same body as a fixed-price listing. */
    val sonyA700BuyItNow: Listing = sonyA700.copy(isAuction = false, bidCount = 0, buyItNow = gbp("129.00"))

    val sonyA700Ended: Listing =
        sonyA700.copy(
            status = ListingStatus.ENDED,
            endTimeUtcMillis = millis("2025-11-09T17:58:00Z"),
        )

    val sonyA700Extended: Listing = sonyA700.copy(extendedBidding = true)

    /** A US listing seen on ebay.co.uk: priced in dollars, with eBay's conversion to pounds. */
    val nintendo64Usd: Listing =
        sonyA700.copy(
            ref = ItemRef(EbaySite.UK, "395812004417"),
            title = "Nintendo 64 console, boxed",
            imageUrl = FixturePhotos.url("n64"),
            sellerName = "retro_attic_us",
            currentPrice = Money.of("145.00", "USD"),
            currentPriceInSiteCurrency = gbp("110.42"),
            bidCount = 12,
            postage = Money.of("38.50", "USD"),
            buyerProtectionFee = null,
            endTimeUtcMillis = millis("2025-11-12T02:15:30Z"),
            scheduledEndUtcMillis = millis("2025-11-12T02:15:30Z"),
        )

    /** S2 with [maxText] typed into the A700's max field. */
    fun ready(
        maxText: String = "90.00",
        listing: Listing = sonyA700,
        leadSeconds: Int = 6,
        group: GroupRowState = GroupRowState.None,
        signedIn: Boolean = true,
        checkingWords: Boolean = false,
        at: Instant = now,
    ): AddUiState {
        val minimum = AddPlanner.minimum(listing)
        val typed = MaxRules.read(maxText, listing.currentPrice.currency, minimum)
        val page =
            AddPlanner.ready(
                listing = listing,
                minimum = minimum,
                maxField = TextFieldState(maxText),
                typed = typed,
                leadSeconds = leadSeconds,
                group = group,
                signedIn = signedIn,
                checkingWords = checkingWords,
                busy = false,
                now = at,
            )
        return AddUiState(at, page)
    }

    val linkEntry: AddUiState get() = AddUiState(now, AddPage.LinkEntry(TextFieldState()))

    val fetching: AddUiState get() = AddUiState(now, AddPage.Fetching("ebay.co.uk/itm/…"))

    val fetchFailed: AddUiState get() = AddUiState(now, AddPage.Failed(AddViewModel.NO_RESPONSE, canRetry = true))

    val notAListing: AddUiState get() = AddUiState(now, AddPage.Failed(AddViewModel.NOT_A_LISTING, canRetry = false))

    val notAnAuction: AddUiState
        get() =
            AddUiState(
                now,
                AddPage.CannotSnipe(AddPlanner.header(sonyA700BuyItNow), CannotSnipeReason.BuyItNow, sonyA700.ref.url),
            )

    val ended: AddUiState
        get() =
            AddUiState(
                now,
                AddPage.CannotSnipe(AddPlanner.header(sonyA700Ended), CannotSnipeReason.Ended, sonyA700.ref.url),
            )

    /** The Canon is already armed: S2 offers to open it. */
    val alreadySniped: AddUiState
        get() {
            val canon = CatalogueFixture.canon
            val status = LotStatusMapper.statusOf(canon, emptyList(), now, app.kestrel.ui.model.Connectivity.Online)
            val header = AddPlanner.header(canon).copy(photo = CatalogueFixture.photoOf(canon))
            return AddUiState(now, AddPage.AlreadySniped(header, canon.id, status, canon.ref.url))
        }

    val emptyMax: AddUiState get() = ready(maxText = "")

    val tooLow: AddUiState get() = ready(maxText = "60.00")

    val signedOut: AddUiState get() = ready(signedIn = false)

    val otherCurrency: AddUiState
        get() = ready(maxText = "200.00", listing = nintendo64Usd)

    val extendedBidding: AddUiState get() =
        ready(
            maxText = "90.37",
            listing = sonyA700Extended,
        )

    val groupChosen: AddUiState
        get() =
            ready(
                maxText = "90.37",
                group = AddPlanner.groupRow(GroupChoice.Existing(CatalogueFixture.portableAudio), openSnipes = 2),
            )

    /** "£900.00" typed by mistake for £90.00: more than three times the price, so it is spelt out first. */
    val wordsCheck: AddUiState get() = ready(maxText = "900.00", checkingWords = true)

    /** The group picker over S2: "No group" selected. */
    fun picker(
        selected: GroupPick = GroupPick.NoGroup,
        newName: String = "",
    ): GroupPickerState =
        GroupPickerState(
            options =
                listOf(
                    GroupOption(CatalogueFixture.portableAudio.id, CatalogueFixture.portableAudio.name, openSnipes = 2),
                ),
            selected = selected,
            newName = TextFieldState(newName),
        )

    // The edit sheets (DESIGN "Edit-max sheet").

    /** Edit: the Canon at 18:21:12, in its final minute. */
    val editSheet: EditMaxUiState
        get() =
            sheet(
                snipe = CatalogueFixture.canonFinalMinute.snipe,
                mode = EditMaxMode.Edit,
                field = TextFieldState("120.00"),
                at = at("2025-11-09T18:21:12Z"),
            )

    /** The deadline passed while £130.00 was being typed: 18:21:32, after the 18:21:31 lock; the £120.00 max stands. */
    val editSheetLocked: EditMaxUiState
        get() =
            sheet(
                snipe = CatalogueFixture.canonFinalMinute.snipe,
                mode = EditMaxMode.Edit,
                field = TextFieldState("120.00"),
                at = at("2025-11-09T18:21:32Z"),
            )

    /** Raise: the Omega at 18:08:40, prefilled with eBay's next bid and selected. */
    val raiseSheet: EditMaxUiState
        get() =
            sheet(
                snipe = CatalogueFixture.omega,
                mode = EditMaxMode.Raise,
                field = TextFieldState("2200.00", initialSelection = TextRange(0, 7)),
                at = at("2025-11-09T18:08:40Z"),
            )

    /** Set: the watched Polaroid, empty, with odd pence and the lead time. */
    val setSheet: EditMaxUiState
        get() = sheet(snipe = CatalogueFixture.polaroid, mode = EditMaxMode.Set, field = TextFieldState(), at = now)

    /** The Edit sheet when the data layer refused a £130.00 save (the bid window is open); the £120.00 max stands. */
    val editSheetRefused: EditMaxUiState
        get() =
            sheet(
                snipe = CatalogueFixture.canonFinalMinute.snipe,
                mode = EditMaxMode.Edit,
                field = TextFieldState("120.00"),
                at = at("2025-11-09T18:21:12Z"),
                refusal = "The bid window is open, so this can’t change now.",
            )

    fun sheet(
        snipe: Snipe,
        mode: EditMaxMode,
        field: TextFieldState,
        at: Instant,
        refusal: String? = null,
    ): EditMaxUiState {
        val state =
            EditMaxPlanner.state(
                snipe = snipe,
                session =
                    EditMaxViewModel.Session(
                        mode = mode,
                        leadSeconds = snipe.leadSeconds,
                        previousMax = snipe.maxBid,
                        refusal = refusal,
                    ),
                field = field,
                text = field.text.toString(),
                now = at,
            )
        // The design's own photo treatment (the Canon and the Polaroid are scenes).
        return state.copy(lot = state.lot.copy(photo = CatalogueFixture.photoOf(snipe)))
    }

    /** A snipe with nothing special, for ViewModel tests that add the A700. */
    fun added(
        id: Long,
        listing: Listing = sonyA700,
        max: Money? = gbp("90.00"),
    ): Snipe =
        CatalogueFixture.canon.copy(
            id = id,
            ref = listing.ref,
            title = listing.title,
            imageUrl = listing.imageUrl,
            maxBid = max,
            status = if (max == null) SnipeStatus.WATCHING else SnipeStatus.ARMED,
            endTimeUtcMillis = listing.endTimeUtcMillis,
            lastPrice = listing.currentPrice,
            lastBidCount = listing.bidCount,
            postage = listing.postage,
            buyerProtectionFee = listing.buyerProtectionFee,
        )
}
