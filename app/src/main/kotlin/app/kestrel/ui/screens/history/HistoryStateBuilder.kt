package app.kestrel.ui.screens.history

import androidx.compose.runtime.Immutable
import app.kestrel.data.SnipeStats
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeGroup
import app.kestrel.model.SnipeStatus
import app.kestrel.model.TimelineEvent
import app.kestrel.ui.format.AuctionZone
import app.kestrel.ui.format.formatted
import app.kestrel.ui.format.monthHeading
import app.kestrel.ui.model.Connectivity
import app.kestrel.ui.model.GroupContext
import app.kestrel.ui.model.LotPhoto
import app.kestrel.ui.model.LotStatusMapper
import app.kestrel.ui.screens.home.HomeLot
import app.kestrel.ui.screens.home.HomeStateBuilder
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Every finished snipe at [now], by month, newest first, under the totals. */
@Immutable
data class HistoryUiState(
    val now: Instant,
    val loaded: Boolean,
    val summary: HistorySummary?,
    val months: List<HistoryMonth>,
) {
    /** Nothing has finished yet (DESIGN 3.19, History empty). */
    val isEmpty: Boolean get() = loaded && months.isEmpty()

    companion object {
        fun loading(now: Instant): HistoryUiState =
            HistoryUiState(now, loaded = false, summary = null, months = emptyList())
    }
}

/** The summary card: "Won 1 of 2 in November" over "£11.00 under your max in total". */
@Immutable
data class HistorySummary(
    val title: String,
    val detail: String?,
)

/** A month's head ("NOVEMBER", "DECEMBER 2024") and its finished rows. */
@Immutable
data class HistoryMonth(
    val title: String,
    val lots: List<HomeLot>,
)

/** What History is built from: every finished snipe, their timelines, the groups and the totals. */
data class HistoryInput(
    val now: Instant,
    val finished: List<Snipe>,
    val timelines: Map<Long, List<TimelineEvent>>,
    val groups: List<SnipeGroup>,
    val stats: SnipeStats,
    val photoOf: (Snipe) -> LotPhoto = LotPhoto::of,
)

/** Builds History (DESIGN History): month sections of finished rows, newest first, and the totals card. Pure. */
internal object HistoryStateBuilder {
    fun build(
        input: HistoryInput,
        zone: ZoneId = AuctionZone,
    ): HistoryUiState {
        val lots =
            input.finished.map { snipe ->
                val group = groupContext(snipe, input)
                val events = input.timelines[snipe.id].orEmpty()
                HomeLot(
                    snipe = snipe,
                    status = LotStatusMapper.statusOf(snipe, events, input.now, Connectivity.Online, group),
                    photo = input.photoOf(snipe),
                )
            }
        val months =
            lots
                .sortedByDescending { HomeStateBuilder.finishedAt(it) }
                .groupBy { YearMonth.from(HomeStateBuilder.finishedAt(it).atZone(zone)) }
                .map { (_, inMonth) ->
                    HistoryMonth(HomeStateBuilder.finishedAt(inMonth.first()).monthHeading(input.now, zone), inMonth)
                }
        return HistoryUiState(input.now, loaded = true, summary = summaryOf(input, lots, zone), months = months)
    }

    /**
     * "Won 1 of 2 in November": wins out of the auctions Kestrel bid to the end, named by month when they all fall
     * in the current one. The second line is how far under the max the wins came in, per currency.
     */
    private fun summaryOf(
        input: HistoryInput,
        lots: List<HomeLot>,
        zone: ZoneId,
    ): HistorySummary? {
        val stats = input.stats
        val decided = stats.wins + stats.losses
        if (decided == 0) return null
        val thisMonth = YearMonth.from(input.now.atZone(zone))
        val decidedLots = lots.filter { it.snipe.status == SnipeStatus.WON || it.snipe.status == SnipeStatus.LOST }
        val allThisMonth = decidedLots.all { YearMonth.from(HomeStateBuilder.finishedAt(it).atZone(zone)) == thisMonth }
        val period = if (allThisMonth) " in ${input.now.monthHeading(input.now, zone)}" else ""
        val saved = stats.totalSavedUnderMax.filterNot { it.isZero() }
        val detail =
            if (saved.isEmpty()) {
                null
            } else {
                "${saved.joinToString(
                    " and ",
                ) { it.formatted() }} under your max in total"
            }
        return HistorySummary("Won ${stats.wins} of $decided$period", detail)
    }

    private fun groupContext(
        snipe: Snipe,
        input: HistoryInput,
    ): GroupContext? {
        val groupId = snipe.groupId ?: return null
        val group = input.groups.firstOrNull { it.id == groupId } ?: return null
        val won =
            input.finished.firstOrNull { it.groupId == groupId && it.id != snipe.id && it.status == SnipeStatus.WON }
        return GroupContext(group.name, won?.title)
    }
}
