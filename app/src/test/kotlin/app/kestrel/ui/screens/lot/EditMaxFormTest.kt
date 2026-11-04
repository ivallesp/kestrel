package app.kestrel.ui.screens.lot

import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.screens.lot.LotFixtures.lotState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The edit sheet's rules (DESIGN 3.18 and the edit-max sheet): the minimum, the sum row, odd pence and the lock. */
class EditMaxFormTest {
    private val fixture = CatalogueFixture
    private val editing = LotFixtures.canonEditing
    private val editSheet = editing.lotState(SheetRequest(SheetKind.Edit)).sheet!!

    @Test
    fun aValidMaxShowsWhatYouPayAtMost() {
        val form = EditMaxForm.evaluate("130.00", editSheet, editing.now)
        assertEquals(CatalogueFixture.gbp("130.00"), form.max)
        assertEquals(SumFigures("£130.00", "£6.50", "£136.50"), form.sum)
        assertNull(form.error)
        assertTrue(form.canSave)
        assertNull("odd pence are offered only when setting a first max", form.oddPence)
    }

    @Test
    fun belowEbaysNextBidIsTooLowInAttentionWordsNotRed() {
        val form = EditMaxForm.evaluate("97", editSheet, editing.now)
        assertEquals("Too low: eBay needs at least £98.00", form.error)
        assertFalse(form.canSave)
        // The minimum itself is enough.
        assertTrue(EditMaxForm.evaluate("98", editSheet, editing.now).canSave)
    }

    @Test
    fun anEmptyFieldCannotBeSaved() {
        val form = EditMaxForm.evaluate("", editSheet, editing.now)
        assertNull(form.max)
        assertNull(form.sum)
        assertFalse(form.canSave)
    }

    @Test
    fun theLockedSheetCannotSave() {
        val locked = LotFixtures.canonLocked.lotState(SheetRequest(SheetKind.Edit)).sheet!!
        assertFalse(EditMaxForm.evaluate("130.00", locked, LotFixtures.canonLocked.now).canSave)
    }

    @Test
    fun theRaiseMinimumComesFromTheIncrementTable() {
        // Rule MF32: the Omega's next bid is £2,200.00, everywhere.
        val raise = LotFixtures.omegaRaising.lotState(SheetRequest(SheetKind.Raise)).sheet!!
        assertEquals(
            "Too low: eBay needs at least £2,200.00",
            EditMaxForm.evaluate("2199.99", raise, LotFixtures.omegaRaising.now).error,
        )
        val form = EditMaxForm.evaluate("2200.00", raise, LotFixtures.omegaRaising.now)
        assertEquals(SumFigures("£2,200.00", "£12.95", "£2,212.95"), form.sum)
        assertTrue(form.canSave)
    }

    @Test
    fun settingAMaxOffersOddPenceAndSaysWhenItBids() {
        val moment = fixture.polaroidWatching
        val set = moment.lotState(SheetRequest(SheetKind.Set, leadSeconds = 6)).sheet!!
        val round = EditMaxForm.evaluate("70", set, moment.now)
        assertEquals(CatalogueFixture.gbp("70.37"), round.oddPence)
        assertEquals("Bids £70.00 on Thu 13 Nov at 19:29:54 GMT", round.armLine)
        assertNull(EditMaxForm.evaluate("70.37", set, moment.now).oddPence)
        assertEquals(
            "No max yet: Kestrel will only watch the price.",
            EditMaxForm.evaluate("", set, moment.now).armLine,
        )
        // The lead time moves the bid.
        val tenSeconds = moment.lotState(SheetRequest(SheetKind.Set, leadSeconds = 10)).sheet!!
        assertEquals(
            "Bids £70.00 on Thu 13 Nov at 19:29:50 GMT",
            EditMaxForm.evaluate("70", tenSeconds, moment.now).armLine,
        )
    }

    @Test
    fun aRefusalFromTheDataLayerIsShownInTheSheet() {
        val refused =
            editing
                .lotState(
                    SheetRequest(SheetKind.Edit, error = "The bid window is open, so this can’t change now."),
                ).sheet!!
        assertEquals(
            "The bid window is open, so this can’t change now.",
            EditMaxForm.evaluate("130.00", refused, editing.now).error,
        )
    }
}
