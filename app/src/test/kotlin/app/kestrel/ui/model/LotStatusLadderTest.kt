package app.kestrel.ui.model

import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.model.LotStatusKind.Armed
import app.kestrel.ui.model.LotStatusKind.BidFailed
import app.kestrel.ui.model.LotStatusKind.BidNotSent
import app.kestrel.ui.model.LotStatusKind.BidSent
import app.kestrel.ui.model.LotStatusKind.CancelledByGroup
import app.kestrel.ui.model.LotStatusKind.CancelledByYou
import app.kestrel.ui.model.LotStatusKind.Captcha
import app.kestrel.ui.model.LotStatusKind.DryRunFailed
import app.kestrel.ui.model.LotStatusKind.EndedEarly
import app.kestrel.ui.model.LotStatusKind.Extended
import app.kestrel.ui.model.LotStatusKind.FinalMinute
import app.kestrel.ui.model.LotStatusKind.InTheLead
import app.kestrel.ui.model.LotStatusKind.Lost
import app.kestrel.ui.model.LotStatusKind.NeedsYou
import app.kestrel.ui.model.LotStatusKind.NoReply
import app.kestrel.ui.model.LotStatusKind.OfflineNearBid
import app.kestrel.ui.model.LotStatusKind.Outbid
import app.kestrel.ui.model.LotStatusKind.OverMax
import app.kestrel.ui.model.LotStatusKind.PhoneCheck
import app.kestrel.ui.model.LotStatusKind.ReadingResult
import app.kestrel.ui.model.LotStatusKind.SignInNeeded
import app.kestrel.ui.model.LotStatusKind.Unreachable
import app.kestrel.ui.model.LotStatusKind.Watching
import app.kestrel.ui.model.LotStatusKind.Won
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every allowed and every forbidden step of the ladder, written out independently of the implementation. */
class LotStatusLadderTest {
    private val attention =
        setOf(OverMax, SignInNeeded, Captcha, PhoneCheck, DryRunFailed, OfflineNearBid, Unreachable, NeedsYou)

    private fun fromAttention(kind: LotStatusKind): Set<LotStatusKind> {
        val handOffs = setOf(SignInNeeded, Captcha, PhoneCheck, NeedsYou)
        val toWatching = if (kind in handOffs) setOf(Watching) else emptySet()
        return attention + toWatching +
            setOf(Armed, FinalMinute, Lost, EndedEarly, CancelledByYou, CancelledByGroup, BidNotSent, ReadingResult)
    }

    private val expected: Map<LotStatusKind, Set<LotStatusKind>> =
        mapOf(
            Watching to
                setOf(
                    Armed,
                    SignInNeeded,
                    Captcha,
                    PhoneCheck,
                    NeedsYou,
                    Lost,
                    EndedEarly,
                    CancelledByYou,
                    ReadingResult,
                ),
            Armed to
                attention +
                setOf(Watching, FinalMinute, EndedEarly, CancelledByYou, CancelledByGroup, BidNotSent, ReadingResult),
            FinalMinute to
                setOf(
                    BidSent,
                    Armed,
                    OverMax,
                    OfflineNearBid,
                    BidFailed,
                    Lost,
                    EndedEarly,
                    CancelledByYou,
                    CancelledByGroup,
                    BidNotSent,
                    ReadingResult,
                ),
            BidSent to setOf(InTheLead, Outbid, NoReply, Extended, BidNotSent, BidFailed, ReadingResult),
            InTheLead to setOf(Extended, Outbid, ReadingResult),
            Outbid to setOf(Extended, ReadingResult),
            NoReply to setOf(Extended, BidSent, InTheLead, Outbid, ReadingResult),
            Extended to setOf(Outbid, ReadingResult),
            ReadingResult to
                setOf(Won, Lost, EndedEarly, CancelledByYou, CancelledByGroup, BidNotSent, BidFailed, Extended),
            Won to emptySet(),
            Lost to emptySet(),
            EndedEarly to emptySet(),
            CancelledByYou to emptySet(),
            CancelledByGroup to setOf(Armed),
            BidNotSent to emptySet(),
            BidFailed to emptySet(),
        ) + attention.associateWith { fromAttention(it) }

    @Test
    fun everyStepIsAllowedOrForbiddenExactlyAsWritten() {
        assertEquals(LotStatusKind.entries.toSet(), expected.keys)
        for (from in LotStatusKind.entries) {
            for (to in LotStatusKind.entries) {
                val allowed = from == to || to in expected.getValue(from)
                assertEquals("$from to $to", allowed, LotStatusLadder.allows(from, to))
            }
        }
    }

    @Test
    fun theDesignLadderHolds() {
        val design =
            listOf(
                Watching to Armed,
                Armed to Watching,
                Armed to OverMax,
                OverMax to Armed,
                Armed to FinalMinute,
                FinalMinute to BidSent,
                BidSent to InTheLead,
                BidSent to Outbid,
                BidSent to NoReply,
                InTheLead to Extended,
                Outbid to Extended,
                NoReply to Extended,
                Extended to Extended,
                Extended to ReadingResult,
                ReadingResult to Won,
                ReadingResult to Lost,
                FinalMinute to BidNotSent,
                FinalMinute to BidFailed,
                BidSent to BidNotSent,
                BidSent to BidFailed,
                Armed to EndedEarly,
                Armed to CancelledByYou,
                Armed to CancelledByGroup,
            )
        design.forEach { (from, to) -> assertTrue("$from to $to", LotStatusLadder.allows(from, to)) }
    }

    @Test
    fun noLabelClaimsAStepBeforeItHappens() {
        assertFalse(LotStatusLadder.allows(Armed, BidSent))
        assertFalse(LotStatusLadder.allows(FinalMinute, InTheLead))
        assertFalse(LotStatusLadder.allows(InTheLead, Won))
        assertFalse(LotStatusLadder.allows(BidSent, Won))
        assertFalse(LotStatusLadder.allows(Won, Lost))
        assertFalse(LotStatusLadder.allows(BidNotSent, Armed))
    }

    @Test
    fun advanceReturnsTheNextStatusOrRefusesTheJump() {
        val armed = CatalogueFixture.canonArmed.status
        val finalMinute = CatalogueFixture.canonFinalMinute.status
        val won = CatalogueFixture.canonWon.status
        assertEquals(finalMinute, armed.advance(finalMinute))
        assertTrue(armed.canAdvanceTo(finalMinute))
        assertFalse(armed.canAdvanceTo(won))
        assertThrows(IllegalArgumentException::class.java) { armed.advance(won) }
    }

    @Test
    fun theLiveFixtureWalksTheLadderInOrder() {
        val fixture = CatalogueFixture
        val path =
            listOf(
                fixture.canonArmed,
                fixture.canonFinalMinute,
                fixture.canonBidSent,
                fixture.canonInTheLead,
                fixture.canonReadingResult,
                fixture.canonWon,
            ).map { it.status }
        path.zipWithNext().forEach { (from, to) -> assertEquals(to, from.advance(to)) }
    }
}
