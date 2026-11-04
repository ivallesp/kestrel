package app.kestrel.ui.screens.lot

import app.kestrel.model.SnipeStatus
import app.kestrel.model.TimelineEvent
import app.kestrel.model.TimelineKind
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.fixture.LotMoment
import app.kestrel.ui.screens.result.ResultInputs
import app.kestrel.ui.screens.result.ResultStateBuilder
import app.kestrel.ui.screens.result.ResultUiState
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The lot lane's moments on top of the design fixture, and the screens' states built through the real builders. */
object LotFixtures {
    private val fixture = CatalogueFixture

    /** The inputs of a moment, as the ViewModel would assemble them (signed in as the fixture account). */
    fun LotMoment.inputs(
        sheet: SheetRequest? = null,
        stampSeal: Boolean = false,
    ): LotInputs =
        LotInputs(
            snipe = snipe,
            events = events,
            prices = pricePoints,
            now = now,
            connectivity = connectivity,
            group = group,
            accountName = CatalogueFixture.ACCOUNT,
            photo = photo,
            sheet = sheet,
            stampSeal = stampSeal,
        )

    fun LotMoment.lotState(sheet: SheetRequest? = null): LotUiState = LotStateBuilder.build(inputs(sheet))

    fun LotMoment.page(): LotPage = LotPageBuilder.page(inputs())

    fun LotMoment.resultState(stampSeal: Boolean = true): ResultUiState =
        ResultStateBuilder.build(ResultInputs(snipe, events, now, connectivity, group, photo, stampSeal))

    /** S3 at 18:08:33, 13 m 07 s before the Canon ends: armed today, the dry run still to come. */
    val canonArmedToday: LotMoment get() = fixture.canonArmed.copy(pricePoints = canonPricesAtArming)

    /** The edit sheet's moment (DESIGN: Canon at 18:21:12). */
    val canonEditing: LotMoment get() = fixture.canonFinalMinute.copy(now = CatalogueFixture.at("2025-11-09T18:21:12Z"))

    /** 18:21:32: the edits locked at 18:21:31 and the bid goes at 18:21:34. */
    val canonLocked: LotMoment get() = fixture.canonFinalMinute.copy(now = CatalogueFixture.at("2025-11-09T18:21:32Z"))

    /** The raise sheet's moment (DESIGN: Omega at 18:08:40). */
    val omegaRaising: LotMoment get() = fixture.omegaOverMax.copy(now = CatalogueFixture.at("2025-11-09T18:08:40Z"))

    /** 18:21:36: eBay signed the user out before the bid, and the auction still runs until 18:21:40. */
    val canonBidNotSentWhileRunning: LotMoment get() =
        fixture.canonBidNotSent.copy(
            now = CatalogueFixture.at("2025-11-09T18:21:36Z"),
        )

    /** A bid went out, then Kestrel could not read the result from the listing page. */
    val canonResultUnknown: LotMoment
        get() {
            val leading = fixture.canonInTheLead
            val unknown =
                event(
                    leading.snipe.id,
                    32,
                    "2025-11-09T18:22:20.400Z",
                    TimelineKind.RESULT,
                    "Kestrel could not read the result from the listing page; it will check again later.",
                    "outcome" to "UNKNOWN",
                )
            return leading.copy(events = leading.events + unknown, now = CatalogueFixture.at("2025-11-09T18:22:30Z"))
        }

    /** The MiniDisc was won, so the group skipped the Walkman, with the CANCELLED event the group writes. */
    val walkmanSkippedByGroup: LotMoment
        get() {
            val moment = fixture.walkmanCancelledByGroup
            val skipped =
                event(
                    moment.snipe.id,
                    9,
                    "2025-11-10T20:10:16.400Z",
                    TimelineKind.CANCELLED,
                    "Skipped: You won \"Sony MiniDisc Walkman MZ-N707\" in Portable audio.",
                )
            return moment.copy(events = moment.events + skipped)
        }

    /** The Omega, armed again at £2,200.00 after a raise, to read the edited max on the timeline. */
    val omegaRaised: LotMoment
        get() {
            val moment = fixture.omegaOverMax
            val raised = moment.snipe.copy(maxBid = CatalogueFixture.gbp("2200.00"), status = SnipeStatus.ARMED)
            val edit =
                event(
                    raised.id,
                    8,
                    "2025-11-09T18:08:52Z",
                    TimelineKind.EDITED,
                    "Max changed from £1,900.00 to £2,200.00.",
                )
            return moment.copy(
                snipe = raised,
                events = moment.events + edit,
                now = CatalogueFixture.at("2025-11-09T18:09:00Z"),
            )
        }

    private val canonPricesAtArming =
        listOf(
            app.kestrel.data.PricePoint(
                CatalogueFixture.millis("2025-11-09T09:14:02Z"),
                CatalogueFixture.gbp("88.00"),
                14,
            ),
        )

    private fun event(
        snipeId: Long,
        sequence: Long,
        time: String,
        kind: TimelineKind,
        message: String,
        vararg details: Pair<String, String>,
    ): TimelineEvent {
        val at = CatalogueFixture.millis(time)
        val detail =
            buildJsonObject {
                put("trueUtcMillis", at)
                details.forEach { (key, value) -> put(key, value) }
            }.toString()
        return TimelineEvent(snipeId * 100 + sequence, snipeId, at, kind, message, detail)
    }
}
