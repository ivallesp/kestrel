package app.kestrel.ui.screens.history

import app.kestrel.model.SnipeStatus
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.model.LotStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** History (DESIGN History): months newest first, the year only when it is not this one, and the totals. */
class HistoryStateBuilderTest {
    private val fixture = CatalogueFixture

    @Test
    fun theFixtureIsOneNovemberSectionUnderItsTotals() {
        val state = HistoryFixtures.history
        assertEquals(HistorySummary("Won 1 of 2 in November", "£11.00 under your max in total"), state.summary)
        assertEquals(listOf("November"), state.months.map { it.title })
        assertEquals(
            listOf(fixture.gameAndWatch.id, fixture.nintendo64.id),
            state.months
                .single()
                .lots
                .map { it.id },
        )
    }

    @Test
    fun olderMonthsFollowAndAPastYearIsNamed() {
        val state = HistoryFixtures.olderHistory
        assertEquals(listOf("November", "October", "December 2024"), state.months.map { it.title })
        // Spread over months, so the summary names no month.
        assertEquals(HistorySummary("Won 2 of 4", "£30.37 under your max in total"), state.summary)
    }

    @Test
    fun withNothingFinishedHistoryIsEmpty() {
        val state = HistoryFixtures.empty
        assertTrue(state.isEmpty)
        assertNull(state.summary)
    }

    @Test
    fun aGroupCancelNamesTheLotThatWasWon() {
        val wonMiniDisc = fixture.miniDisc.copy(status = SnipeStatus.WON)
        val state =
            HistoryStateBuilder.build(
                HistoryFixtures.input(listOf(fixture.walkmanCancelledByGroup.snipe, wonMiniDisc), emptyList()),
            )
        val walkman = state.months.flatMap { it.lots }.single { it.id == fixture.walkman.id }
        assertEquals(fixture.miniDisc.title, (walkman.status as LotStatus.CancelledByGroup).wonTitle)
    }

    @Test
    fun rowsHaveNoActionsOrFlags() {
        val lots =
            HistoryStateBuilder
                .build(
                    HistoryFixtures.input(HistoryFixtures.everyEnding),
                ).months
                .flatMap { it.lots }
        assertTrue(lots.all { it.action == null && it.groupFlag == null && it.failedCheckAt == null })
        assertTrue(lots.all { it.status.kind.isFinal })
    }
}
