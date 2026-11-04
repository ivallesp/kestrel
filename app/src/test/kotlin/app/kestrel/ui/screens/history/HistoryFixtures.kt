package app.kestrel.ui.screens.history

import app.kestrel.data.SnipeStats
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeOutcome
import app.kestrel.model.SnipeResult
import app.kestrel.model.SnipeStatus
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.fixture.LotMoment
import app.kestrel.ui.harness.FixturePhotos

/** History from the design fixture, plus two older results so month heads and a past year show. */
object HistoryFixtures {
    private val fixture = CatalogueFixture

    /** The two finished lots of the fixture: the Game & Watch won on Sat 8 Nov, the N64 lost on Fri 7 Nov. */
    val designMoments: List<LotMoment> get() = listOf(fixture.gameAndWatchWon, fixture.nintendo64Lost)

    /** Won in October for £71.00 under a £90.37 max. */
    val sonyWon: Snipe =
        fixture.gameAndWatch.copy(
            id = 21,
            title = "Sony Alpha A700 body",
            imageUrl = FixturePhotos.url("sony_a700"),
            maxBid = fixture.gbp("90.37"),
            endTimeUtcMillis = fixture.millis("2025-10-16T19:04:18Z"),
            lastPrice = fixture.gbp("71.00"),
            postage = fixture.gbp("8.95"),
            result =
                SnipeResult(
                    outcome = SnipeOutcome.WON,
                    finalPrice = fixture.gbp("71.00"),
                    bidSentAtUtcMillis = fixture.millis("2025-10-16T19:04:12.100Z"),
                    bidLeadMillis = 5_900,
                    replyLatencyMillis = 800,
                    ebayReplyText = "Your current bid puts you in the lead.",
                    reason = "eBay says you are the winning bidder.",
                ),
        )

    /** Lost in December 2024, the year before the fixture. */
    val polaroidLost: Snipe =
        fixture.nintendo64.copy(
            id = 22,
            title = "Polaroid SX-70 Land Camera",
            imageUrl = FixturePhotos.url("polaroid"),
            maxBid = fixture.gbp("75.00"),
            endTimeUtcMillis = fixture.millis("2024-12-01T20:15:00Z"),
            lastPrice = fixture.gbp("88.00"),
            postage = fixture.gbp("7.50"),
            result =
                SnipeResult(
                    outcome = SnipeOutcome.LOST,
                    finalPrice = fixture.gbp("88.00"),
                    bidSentAtUtcMillis = fixture.millis("2024-12-01T20:14:54.000Z"),
                    bidLeadMillis = 6_000,
                    replyLatencyMillis = 900,
                    ebayReplyText = "You’ve been outbid. Another bidder’s maximum is higher.",
                    reason = "eBay says another bidder won.",
                ),
        )

    fun input(
        finished: List<Snipe>,
        moments: List<LotMoment> = designMoments,
    ): HistoryInput {
        val sorted = finished.sortedByDescending { it.endTimeUtcMillis }
        return HistoryInput(
            now = fixture.now,
            finished = sorted,
            timelines = moments.associate { it.snipe.id to it.events },
            groups = listOf(fixture.portableAudio),
            stats = SnipeStats.of(sorted),
            photoOf = fixture::photoOf,
        )
    }

    /** History as DESIGN shows it: November, two lots, "Won 1 of 2 in November". */
    val history: HistoryUiState
        get() = HistoryStateBuilder.build(input(designMoments.map { it.snipe }))

    /** With an October win and a lost lot from December 2024. */
    val olderHistory: HistoryUiState
        get() = HistoryStateBuilder.build(input(designMoments.map { it.snipe } + sonyWon + polaroidLost))

    val empty: HistoryUiState get() = HistoryStateBuilder.build(input(emptyList(), emptyList()))

    /** Finished snipes of every kind, for the rows. */
    val everyEnding: List<Snipe>
        get() =
            listOf(
                fixture.gameAndWatchWon.snipe,
                fixture.nintendo64Lost.snipe,
                fixture.gameBoyEndedEarly.snipe,
                fixture.gameBoyCancelled.snipe.copy(id = 31),
                fixture.walkmanCancelledByGroup.snipe,
                fixture.miniDisc.copy(status = SnipeStatus.WON),
            )
}
