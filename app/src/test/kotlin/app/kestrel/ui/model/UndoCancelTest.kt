package app.kestrel.ui.model

import app.kestrel.data.AddResult
import app.kestrel.data.EditResult
import app.kestrel.model.Listing
import app.kestrel.model.ListingStatus
import app.kestrel.model.Money
import app.kestrel.model.SnipeStatus
import app.kestrel.ui.fixture.CatalogueFixture
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Undo cancel": the listing comes back armed on its own, with the old max and lead, and the cancel goes. */
class UndoCancelTest {
    private val fixture = CatalogueFixture
    private val cancelled = fixture.walkman.copy(status = SnipeStatus.SKIPPED_BY_GROUP)
    private val before = fixture.at("2025-11-10T20:12:00Z").toEpochMilli()

    @Test
    fun theListingIsArmedAgainOutsideTheGroupAndTheCancelIsRemoved() =
        runTest {
            val added = mutableListOf<Triple<Listing, Money?, Int>>()
            val deleted = mutableListOf<Long>()
            val armed = cancelled.copy(id = 40, status = SnipeStatus.ARMED, groupId = null)
            val result =
                UndoCancel.run(
                    snipe = cancelled,
                    nowUtcMillis = before,
                    add = { listing, max, lead ->
                        added += Triple(listing, max, lead)
                        AddResult.Added(armed)
                    },
                    delete = { id ->
                        deleted += id
                        EditResult.Done(cancelled)
                    },
                )
            assertEquals(EditResult.Done(armed), result)
            val (listing, max, lead) = added.single()
            assertEquals(cancelled.ref, listing.ref)
            assertEquals(ListingStatus.ACTIVE, listing.status)
            assertEquals(cancelled.endTimeUtcMillis, listing.endTimeUtcMillis)
            assertEquals(cancelled.lastPrice, listing.currentPrice)
            assertEquals(cancelled.maxBid, max)
            assertEquals(cancelled.leadSeconds, lead)
            assertEquals(listOf(cancelled.id), deleted)
        }

    @Test
    fun anEndedAuctionOrAListingWithoutAPriceStaysCancelled() =
        runTest {
            val never: suspend (Listing, Money?, Int) -> AddResult = { _, _, _ -> error("must not add") }
            val noDelete: suspend (Long) -> EditResult = { error("must not delete") }
            val ended = UndoCancel.run(cancelled, cancelled.endTimeUtcMillis, never, noDelete)
            assertEquals(EditResult.NotAllowed(UndoCancel.ENDED), ended)
            val unpriced = UndoCancel.run(cancelled.copy(lastPrice = null), before, never, noDelete)
            assertEquals(EditResult.NotAllowed(UndoCancel.NO_PRICE), unpriced)
        }

    @Test
    fun aRefusedAddKeepsTheCancelAndSaysWhy() =
        runTest {
            var deleted = false
            val result =
                UndoCancel.run(
                    snipe = cancelled,
                    nowUtcMillis = before,
                    add = { _, _, _ -> AddResult.Rejected("That group no longer exists.") },
                    delete = {
                        deleted = true
                        EditResult.NotFound
                    },
                )
            assertEquals(EditResult.NotAllowed("That group no longer exists."), result)
            assertTrue(!deleted)
        }
}
