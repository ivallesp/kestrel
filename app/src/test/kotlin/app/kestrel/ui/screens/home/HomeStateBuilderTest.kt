package app.kestrel.ui.screens.home

import app.kestrel.model.SnipeStatus
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.model.Connectivity
import app.kestrel.ui.model.LotAction
import app.kestrel.ui.model.LotStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The S1 rules (DESIGN S1, 3.4 to 3.6): buckets, the hero, group blocks, flags, signed out and offline. */
class HomeStateBuilderTest {
    private val fixture = CatalogueFixture
    private val home = HomeFixtures.home

    private fun HomeUiState.lots() = sections.flatMap { section -> section.blocks.flatMap { it.lots } }

    private fun HomeUiState.lot(id: Long) = lots().single { it.id == id }

    @Test
    fun theFixtureFillsEndingSoonThisWeekAndDone() {
        assertEquals(
            listOf(HomeBucket.EndingSoon, HomeBucket.ThisWeek, HomeBucket.Done),
            home.sections.map { it.bucket },
        )
        assertEquals(listOf(2, 4, 2), home.sections.map { it.lotCount })
        assertTrue(home.showFinishedLink)
        assertFalse(home.isEmpty)
    }

    @Test
    fun theCanonIsTheHeroAndOpensEndingSoonWithTheOmegaUnderIt() {
        val block =
            home.sections
                .first()
                .blocks
                .single()
        assertTrue(block.opensWithHero)
        assertEquals(listOf(fixture.canon.id, fixture.omega.id), block.lots.map { it.id })
    }

    @Test
    fun portableAudioGetsItsOwnBlockThenThePolaroidAndTheGameBoy() {
        val blocks = home.sections[1].blocks
        assertEquals(GroupHeader(fixture.portableAudio.id, "Portable audio", 1, 2), blocks[0].group)
        assertEquals(listOf(fixture.miniDisc.id, fixture.walkman.id), blocks[0].lots.map { it.id })
        assertEquals(listOf(fixture.polaroid.id, fixture.gameBoy.id), blocks[1].lots.map { it.id })
        assertNull(blocks[1].group)
        assertTrue(blocks[0].lots.all { it.groupFlag == null })
        assertEquals(3, blocks[0].segmentCount)
    }

    @Test
    fun doneIsNewestFirst() {
        val done = home.sections.last()
        assertEquals(HomeBucket.Done, done.bucket)
        assertEquals(
            listOf(fixture.gameAndWatch.id, fixture.nintendo64.id),
            done.blocks
                .single()
                .lots
                .map { it.id },
        )
    }

    @Test
    fun groupMembersThatAreApartCarryTheGroupFlagInstead() {
        val walkmanLater = fixture.walkman.copy(endTimeUtcMillis = fixture.millis("2025-11-14T10:00:00Z"))
        val moments =
            HomeFixtures.listMoments.map {
                if (it.snipe.id ==
                    fixture.walkman.id
                ) {
                    it.copy(snipe = walkmanLater)
                } else {
                    it
                }
            }
        val state = HomeFixtures.build(HomeFixtures.input(moments))
        val thisWeek = state.sections[1]
        assertTrue(thisWeek.blocks.all { it.group == null })
        assertEquals("Portable audio: win 1 of 2", state.lot(fixture.miniDisc.id).groupFlag)
        assertEquals("Portable audio: win 1 of 2", state.lot(fixture.walkman.id).groupFlag)
        assertNull(state.lot(fixture.polaroid.id).groupFlag)
    }

    @Test
    fun anUngroupedExtendedLotKeepsItsExtendedFlag() {
        val ungrouped = fixture.walkman.copy(groupId = null)
        val moments =
            HomeFixtures.listMoments.map { if (it.snipe.id == fixture.walkman.id) it.copy(snipe = ungrouped) else it }
        val walkman = HomeFixtures.build(HomeFixtures.input(moments)).lot(fixture.walkman.id)
        val flags = HomeText.flags(walkman, fixture.now).map { it.text }
        assertEquals(listOf("Extended bidding: Kestrel’s bid will reset the clock to 60 s."), flags)
    }

    @Test
    fun theHeroSkipsLotsThatOnlyWatch() {
        val polaroidSoon = fixture.polaroid.copy(endTimeUtcMillis = fixture.millis("2025-11-09T18:15:00Z"))
        val moments =
            HomeFixtures.listMoments.map {
                if (it.snipe.id ==
                    fixture.polaroid.id
                ) {
                    it.copy(snipe = polaroidSoon)
                } else {
                    it
                }
            }
        val endingSoon = HomeFixtures.build(HomeFixtures.input(moments)).sections.first()
        val lots = endingSoon.blocks.single().lots
        assertTrue(endingSoon.blocks.single().opensWithHero)
        assertEquals(listOf(fixture.canon.id, fixture.polaroid.id, fixture.omega.id), lots.map { it.id })
    }

    @Test
    fun withNothingToBidThereIsNoHero() {
        val state = HomeFixtures.build(HomeFixtures.input(listOf(fixture.polaroidWatching)))
        assertFalse(
            state.sections
                .single()
                .blocks
                .single()
                .opensWithHero,
        )
    }

    @Test
    fun rowsOfferOnlyTheirOwnActions() {
        assertEquals(LotAction.RAISE_MAX, home.lot(fixture.omega.id).action)
        assertEquals(LotAction.SET_MAX, home.lot(fixture.polaroid.id).action)
        // "Edit max" is on S3, not on the rows; finished rows have no action.
        assertNull(home.lot(fixture.miniDisc.id).action)
        assertNull(home.lot(fixture.gameAndWatch.id).action)
        assertNull(home.lot(fixture.nintendo64.id).action)
    }

    @Test
    fun signedOutPausesArmedLotsAndTheDockOffersSignInOnce() {
        val state = HomeFixtures.signedOutHome
        assertEquals(PillKind.SignedOut, state.pill.kind)
        assertEquals(PillAction.SignIn, state.pill.action)
        listOf(fixture.canon, fixture.miniDisc, fixture.walkman, fixture.gameBoy).forEach { snipe ->
            val lot = state.lot(snipe.id)
            assertTrue("${snipe.title} is ${lot.status}", lot.status is LotStatus.SignInNeeded)
            assertNull(lot.action)
        }
        assertEquals(LotAction.RAISE_MAX, state.lot(fixture.omega.id).action)
        assertTrue(state.lot(fixture.polaroid.id).status is LotStatus.Watching)
    }

    @Test
    fun withSeveralProblemsARowStillOffersSignIn() {
        val state = HomeFixtures.severalProblems
        assertEquals(PillKind.SeveralProblems, state.pill.kind)
        assertEquals(LotAction.SIGN_IN, state.lot(fixture.miniDisc.id).action)
    }

    @Test
    fun offlineDatesThePricesAndWarnsAboutTheNextBid() {
        val state = HomeFixtures.offline
        val since = fixture.at("2025-11-09T18:02:00Z")
        assertEquals(OfflineNotice(since), state.offline)
        assertEquals(PillKind.Offline, state.pill.kind)
        assertEquals("The 18:21:34 bid can’t go out", state.pill.detail)
        assertTrue(state.lot(fixture.canon.id).status is LotStatus.OfflineNearBid)
        assertEquals(since, state.lot(fixture.miniDisc.id).pricesAsOf)
        assertNull(state.lot(fixture.gameAndWatch.id).pricesAsOf)
    }

    @Test
    fun doneKeepsThirtyDaysAndHistoryKeepsTheRest() {
        val old = fixture.gameAndWatch.copy(endTimeUtcMillis = fixture.millis("2025-10-05T12:00:00Z"))
        val moments =
            HomeFixtures.listMoments.map { if (it.snipe.id == fixture.gameAndWatch.id) it.copy(snipe = old) else it }
        val state = HomeFixtures.build(HomeFixtures.input(moments))
        assertEquals(
            listOf(fixture.nintendo64.id),
            state.sections
                .last()
                .blocks
                .single()
                .lots
                .map { it.id },
        )
        assertTrue(state.showFinishedLink)
    }

    @Test
    fun anEmptyListIsTheEmptyCatalogue() {
        val state = HomeFixtures.empty
        assertTrue(state.isEmpty)
        assertFalse(state.showFinishedLink)
        assertEquals(PillKind.Ready, state.pill.kind)
        assertEquals("All checks passed at 18:06", state.pill.detail)
    }

    @Test
    fun onlyOldResultsLeaveTheEmptyCatalogueWithTheHistoryLink() {
        val old = fixture.gameAndWatch.copy(endTimeUtcMillis = fixture.millis("2025-09-01T12:00:00Z"))
        val state = HomeFixtures.build(HomeFixtures.input(listOf(fixture.gameAndWatchWon.copy(snipe = old))))
        assertTrue(state.isEmpty)
        assertTrue(state.showFinishedLink)
    }

    @Test
    fun bucketsFollowLondonCalendarDays() {
        val now = fixture.now

        fun bucket(end: String) = HomeStateBuilder.bucketOf(fixture.at(end), now)
        assertEquals(HomeBucket.EndingSoon, bucket("2025-11-10T18:08:32Z"))
        assertEquals(HomeBucket.ThisWeek, bucket("2025-11-10T18:08:33Z"))
        assertEquals(HomeBucket.ThisWeek, bucket("2025-11-16T23:59:59Z"))
        assertEquals(HomeBucket.Later, bucket("2025-11-17T00:00:00Z"))
        // An open lot whose end has just passed stays where its result will appear.
        assertEquals(HomeBucket.EndingSoon, bucket("2025-11-09T18:00:00Z"))
        // Summer: London is an hour ahead of UTC, so 23:30 UTC on Sunday is already Monday there.
        val summer = fixture.at("2025-06-01T09:00:00Z")
        assertEquals(HomeBucket.Later, HomeStateBuilder.bucketOf(fixture.at("2025-06-08T23:30:00Z"), summer))
        assertEquals(HomeBucket.ThisWeek, HomeStateBuilder.bucketOf(fixture.at("2025-06-08T22:30:00Z"), summer))
    }

    @Test
    fun theClockTicksEverySecondOnlyInsideALotsLastHour() {
        assertTrue(HomeStateBuilder.ticksEverySecond(fixture.snipes, fixture.now))
        val later = fixture.snipes.filter { it.id != fixture.canon.id }
        assertFalse(HomeStateBuilder.ticksEverySecond(later, fixture.now))
        val won = fixture.canon.copy(status = SnipeStatus.WON)
        assertFalse(HomeStateBuilder.ticksEverySecond(later + won, fixture.now))
    }

    @Test
    fun theHeroOfAnOverMaxLotIsTheAttentionVariant() {
        val state = HomeFixtures.attentionHero
        val endingSoon = state.sections.first()
        val hero =
            endingSoon.blocks
                .first()
                .lots
                .first()
        assertTrue(endingSoon.blocks.first().opensWithHero)
        assertEquals(fixture.omega.id, hero.id)
        assertEquals(LotAction.RAISE_MAX, hero.action)
        assertEquals(HomeBucket.Done, state.sections.last().bucket)
        assertEquals(
            fixture.canon.id,
            state.sections
                .last()
                .blocks
                .single()
                .lots
                .first()
                .id,
        )
    }

    @Test
    fun aGroupCancelNamesTheWonLot() {
        val wonMiniDisc = fixture.miniDisc.copy(status = SnipeStatus.WON)
        val moments = listOf(fixture.walkmanCancelledByGroup, fixture.miniDiscArmed.copy(snipe = wonMiniDisc))
        val state = HomeFixtures.build(HomeFixtures.input(moments, now = fixture.walkmanCancelledByGroup.now))
        val walkman = state.lot(fixture.walkman.id).status as LotStatus.CancelledByGroup
        assertEquals(fixture.miniDisc.title, walkman.wonTitle)
        assertEquals("Portable audio", walkman.groupName)
    }

    @Test
    fun aFailedCheckFlagsTheRow() {
        val state = HomeFixtures.build(HomeFixtures.input(listOf(fixture.canonUnreachable)))
        val canon = state.lot(fixture.canon.id)
        assertEquals(fixture.at("2025-11-09T18:05:00.200Z"), canon.failedCheckAt)
        assertEquals("Couldn’t check at 18:05 · retrying", HomeText.flags(canon, fixture.now).single().text)
    }

    @Test
    fun theAccountNameAndTheLeadTimeComeThrough() {
        assertEquals(fixture.ACCOUNT, home.accountName)
        assertEquals(6, home.defaultLeadSeconds)
        val offline = HomeFixtures.build(HomeFixtures.input(emptyList(), connectivity = Connectivity.Offline(null)))
        assertEquals(OfflineNotice(null), offline.offline)
    }
}
