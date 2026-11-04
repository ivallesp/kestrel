package app.kestrel.web

import app.kestrel.engine.BidReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyClassifierTest {
    @Test
    fun `the documented replies map to leading and outbid`() {
        assertEquals(Standing.LEADING, ReplyClassifier.standingOf("Your current bid puts you in the lead."))
        assertEquals(Standing.OUTBID, ReplyClassifier.standingOf("Another bidder is winning. Increase your bid."))
    }

    @Test
    fun `tolerant matches cover wording variants`() {
        listOf(
            "You're the highest bidder",
            "You’re the highest bidder on this item",
            "You are currently the highest bidder.",
            "You're in the lead",
            "You're the high bidder",
            "You're winning",
            "Sie sind derzeit Höchstbietender",
        ).forEach { assertEquals(it, Standing.LEADING, ReplyClassifier.standingOf(it)) }
        listOf(
            "You've been outbid",
            "You've just been outbid by another bidder's maximum bid",
            "Another bidder has a higher maximum",
            "Increase your max bid to win",
            "You're no longer the highest bidder",
            "Sie wurden überboten",
        ).forEach { assertEquals(it, Standing.OUTBID, ReplyClassifier.standingOf(it)) }
    }

    @Test
    fun `other texts are neither`() {
        listOf(
            "Enter £5.57 or more",
            "This listing has ended",
            "Oops. Looks like we're having trouble connecting to our server.",
            "Increase max bid",
            "",
        ).forEach { assertEquals(it, Standing.OTHER, ReplyClassifier.standingOf(it)) }
    }

    @Test
    fun `decisive keeps eBay's text and the latency`() {
        val reply = ReplyClassifier.decisive(listOf("Your current bid puts you in the lead."), emptyList(), 180)
        assertEquals(BidReply.Leading("Your current bid puts you in the lead.", 180), reply)
        val outbid =
            ReplyClassifier.decisive(
                emptyList(),
                listOf("Increase max bid", "Another bidder is winning. Increase your bid."),
                null,
            )
        assertEquals(BidReply.Outbid("Another bidder is winning. Increase your bid.", null), outbid)
    }

    @Test
    fun `notices are read before added lines`() {
        val reply =
            ReplyClassifier.decisive(
                listOf("You've been outbid"),
                listOf("Your current bid puts you in the lead"),
                1,
            )
        assertTrue(reply is BidReply.Outbid)
    }

    @Test
    fun `nothing decisive gives null`() {
        assertNull(ReplyClassifier.decisive(listOf("Enter £5.57 or more"), listOf("1 bid · 5m 33s left"), 1))
        assertNull(ReplyClassifier.decisive(emptyList(), emptyList(), null))
    }

    @Test
    fun `the other notice skips status lines that change by themselves`() {
        assertEquals("Enter £5.57 or more", ReplyClassifier.otherNotice(listOf("5m 31s left", "Enter £5.57 or more")))
        assertNull(ReplyClassifier.otherNotice(listOf("1 bid · 5m 33s left", "Ends in 4m 2s", "3 bids", "1h 4m")))
        assertTrue(ReplyClassifier.isStatusLine("£3.25 current bid + £6.07 shipping · 3 bids · 1h 4m left"))
        assertFalse(
            ReplyClassifier.isStatusLine(
                "This listing was ended by the seller because the item is no longer available.",
            ),
        )
    }
}
