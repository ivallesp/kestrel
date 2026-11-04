package app.kestrel.engine

import app.kestrel.model.SnipeGroup
import app.kestrel.model.SnipeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupPolicyTest {
    private val group = SnipeGroup(7, "Cameras")
    private val self = testSnipe(id = 1, groupId = 7, status = SnipeStatus.BIDDING)

    @Test
    fun wonMemberSkipsTheRestBeforeTheWindow() {
        val members = listOf(self, testSnipe(id = 2, groupId = 7, status = SnipeStatus.WON))
        assertTrue(GroupPolicy.beforeWindow(self, group, members) is GroupPolicy.Decision.Skip)
        assertTrue(GroupPolicy.atFire(self, group, members) is GroupPolicy.Decision.Skip)
    }

    @Test
    fun memberWithABidOutSkipsThisOneAtFireTime() {
        for (status in listOf(SnipeStatus.BID_SENT, SnipeStatus.LEADING, SnipeStatus.OUTBID, SnipeStatus.EXTENDED)) {
            val members = listOf(self, testSnipe(id = 2, groupId = 7, status = status, endUtc = END_UTC + 600_000))
            assertTrue(status.name, GroupPolicy.atFire(self, group, members) is GroupPolicy.Decision.Skip)
            assertEquals(GroupPolicy.Decision.Proceed, GroupPolicy.beforeWindow(self, group, members))
        }
    }

    @Test
    fun memberPreparingToFireFirstWins() {
        val firesFirst = testSnipe(id = 2, groupId = 7, status = SnipeStatus.BIDDING, endUtc = END_UTC - 1_000)
        assertTrue(GroupPolicy.atFire(self, group, listOf(self, firesFirst)) is GroupPolicy.Decision.Skip)
        val firesLater = testSnipe(id = 2, groupId = 7, status = SnipeStatus.BIDDING, endUtc = END_UTC + 1_000)
        assertEquals(GroupPolicy.Decision.Proceed, GroupPolicy.atFire(self, group, listOf(self, firesLater)))
    }

    @Test
    fun sameFireTimeFallsBackToTheLowerId() {
        val twin = testSnipe(id = 2, groupId = 7, status = SnipeStatus.BIDDING)
        assertEquals(GroupPolicy.Decision.Proceed, GroupPolicy.atFire(self, group, listOf(self, twin)))
        assertTrue(GroupPolicy.atFire(twin, group, listOf(self, twin)) is GroupPolicy.Decision.Skip)
    }

    @Test
    fun leadTimeDecidesWhoFiresFirst() {
        // Ends later but fires earlier because of its longer lead.
        val longLead =
            testSnipe(id = 2, groupId = 7, status = SnipeStatus.BIDDING, endUtc = END_UTC + 2_000, leadSeconds = 10)
        assertTrue(GroupPolicy.atFire(self, group, listOf(self, longLead)) is GroupPolicy.Decision.Skip)
    }

    @Test
    fun armedMembersThatHaveNotStartedDoNotBlock() {
        val members =
            listOf(self, testSnipe(id = 2, groupId = 7, status = SnipeStatus.ARMED, endUtc = END_UTC - 60_000))
        assertEquals(GroupPolicy.Decision.Proceed, GroupPolicy.atFire(self, group, members))
    }

    @Test
    fun winTwoOfNCountsWinsAndUndecidedBids() {
        val winTwo = SnipeGroup(8, "Lenses", targetWins = 2)
        val won = testSnipe(id = 2, groupId = 8, status = SnipeStatus.WON)
        assertEquals(GroupPolicy.Decision.Proceed, GroupPolicy.atFire(self, winTwo, listOf(self, won)))
        val undecided = testSnipe(id = 3, groupId = 8, status = SnipeStatus.LEADING)
        assertTrue(GroupPolicy.atFire(self, winTwo, listOf(self, won, undecided)) is GroupPolicy.Decision.Skip)
    }

    @Test
    fun membersToCancelLeavesBiddingAndWatchingMembersAlone() {
        val members =
            listOf(
                testSnipe(id = 1, groupId = 7, status = SnipeStatus.WON),
                testSnipe(id = 2, groupId = 7, status = SnipeStatus.ARMED),
                testSnipe(id = 3, groupId = 7, status = SnipeStatus.PRICE_OVER_MAX),
                testSnipe(id = 4, groupId = 7, status = SnipeStatus.BID_SENT),
                testSnipe(id = 5, groupId = 7, status = SnipeStatus.WATCHING, max = null),
                testSnipe(id = 6, groupId = 7, status = SnipeStatus.LOST),
            )
        assertEquals(listOf(2L, 3L), GroupPolicy.membersToCancel(group, members).map { it.id })
        assertTrue(GroupPolicy.membersToCancel(group, members.drop(1)).isEmpty())
    }

    @Test
    fun closeEndingsAreFlaggedForTheUi() {
        val members =
            listOf(
                testSnipe(id = 1, endUtc = END_UTC + 10 * 60_000),
                testSnipe(id = 2, endUtc = END_UTC),
                testSnipe(id = 3, endUtc = END_UTC + 2 * 60_000),
            )
        val close = GroupPolicy.closeEndings(members)
        assertEquals(1, close.size)
        assertEquals(2L, close.single().earlier.id)
        assertEquals(3L, close.single().later.id)
        assertEquals(2 * 60_000L, close.single().gapMillis)
    }
}
