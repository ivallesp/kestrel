package app.kestrel.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import app.kestrel.model.EbaySite
import app.kestrel.model.ItemRef
import app.kestrel.model.Listing
import app.kestrel.model.ListingStatus
import app.kestrel.model.Money
import app.kestrel.model.SnipeOutcome
import app.kestrel.model.SnipeResult
import app.kestrel.model.SnipeStatus
import app.kestrel.model.TimelineKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SnipeRepositoryTest {
    private lateinit var database: KestrelDatabase
    private lateinit var armedCopy: ArmedSnipesCopy
    private lateinit var repository: SnipeRepository
    private var now = TEST_NOW

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = KestrelDatabase.inMemory(context)
        armedCopy = ArmedSnipesCopy(File(context.noBackupFilesDir, "armed-test-${System.nanoTime()}.json"))
        repository = SnipeRepository(database, armedCopy, nowUtcMillis = { now }, formatMoney = ::testFormatMoney)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun addArmed(
        listing: Listing = testListing(),
        max: Money = gbp("48.00"),
    ) = (repository.addFromListing(listing, max, leadSeconds = 6) as AddResult.Added).snipe

    private suspend fun addWatching(listing: Listing = testListing()) =
        (repository.addFromListing(listing, maxBid = null, leadSeconds = 6) as AddResult.Added).snipe

    private suspend fun lastMessage(snipeId: Long): String =
        repository
            .observeTimeline(snipeId)
            .first()
            .last()
            .message

    // Adding.

    @Test
    fun addWithMaxArmsTheSnipeAndRecordsWhatThePageSaid() =
        runTest {
            val listing = testListing()
            val result = repository.addFromListing(listing, gbp("48.00"), leadSeconds = 6)

            val snipe = (result as AddResult.Added).snipe
            assertEquals(SnipeStatus.ARMED, snipe.status)
            assertEquals(gbp("48.00"), snipe.maxBid)
            assertEquals(listing.endTimeUtcMillis, snipe.endTimeUtcMillis)
            assertEquals(listing.currentPrice, snipe.lastPrice)
            assertEquals(listing.fetchedAtUtcMillis, snipe.lastCheckedAtUtcMillis)
            assertEquals(snipe, repository.snipe(snipe.id))

            val events = repository.observeTimeline(snipe.id).first()
            assertEquals(listOf(TimelineKind.ADDED), events.map { it.kind })
            assertEquals("Added with a max of GBP 48.00. Kestrel bids 6 s before the end.", events.single().message)
            val points = repository.observePricePoints(snipe.id).first()
            assertEquals(listOf(PricePoint(listing.fetchedAtUtcMillis, gbp("31.50"), 4)), points)
            assertEquals(listOf(ArmedSnipe(snipe.id, listing.title, listing.endTimeUtcMillis)), armedCopy.read())
        }

    @Test
    fun addWithoutMaxOnlyWatches() =
        runTest {
            val snipe = addWatching(testListing())

            assertEquals(SnipeStatus.WATCHING, snipe.status)
            assertTrue(snipe.isWatchOnly)
            assertEquals("Added to watch. Kestrel will not bid.", lastMessage(snipe.id))
            assertTrue(armedCopy.read().isEmpty())
        }

    @Test
    fun aNewGroupIsStoredOnlyWithTheSnipeThatJoinsIt() =
        runTest {
            val ended =
                repository.addFromListing(
                    testListing(itemId = "1", endTimeUtcMillis = now - MINUTE),
                    gbp("48.00"),
                    leadSeconds = 6,
                    newGroupName = "Christmas",
                )
            assertTrue(ended is AddResult.Rejected)
            addArmed(testListing(itemId = "2"))
            val again =
                repository.addFromListing(testListing(itemId = "2"), gbp("48.00"), 6, newGroupName = "Christmas")
            assertTrue(again is AddResult.AlreadyAdded)
            assertTrue("a refused add leaves no empty group", repository.observeGroups().first().isEmpty())

            val added =
                repository.addFromListing(testListing(itemId = "3"), gbp("48.00"), 6, newGroupName = " Christmas ")
                    as AddResult.Added

            val group = repository.observeGroups().first().single()
            assertEquals("Christmas", group.name)
            assertEquals(group.id, added.snipe.groupId)
        }

    @Test
    fun aGroupThatHasItsWinTakesNoNewMemberEvenAfterTheWinIsDeleted() =
        runTest {
            val group = repository.createGroup("Portable audio")
            val won =
                (repository.addFromListing(testListing(itemId = "1"), gbp("45.00"), 6, group.id) as AddResult.Added)
                    .snipe
            repository.update(won.copy(status = SnipeStatus.WON))
            val outside = addArmed(testListing(itemId = "2"))
            val refusal =
                "The group \"Portable audio\" already has its win, so Kestrel would cancel this snipe before it bids."

            assertEquals(
                AddResult.Rejected(refusal),
                repository.addFromListing(testListing(itemId = "3"), gbp("40.00"), 6, group.id),
            )
            assertEquals(EditResult.NotAllowed(refusal), repository.setGroup(outside.id, group.id))
            assertEquals(mapOf(group.id to 1), repository.observeGroupWins().first())

            now += 11 * MINUTE
            assertTrue(repository.delete(won.id) is EditResult.Done)
            assertEquals(EditResult.NotAllowed(refusal), repository.setGroup(outside.id, group.id))
            assertEquals("the hidden win still counts", mapOf(group.id to 1), repository.observeGroupWins().first())
        }

    @Test
    fun addingTheSameListingTwiceReturnsTheOpenSnipe() =
        runTest {
            val first = addArmed()
            val second = repository.addFromListing(testListing(), gbp("60.00"), leadSeconds = 6)

            assertEquals(AddResult.AlreadyAdded(first), second)
            assertEquals(1, repository.observeSnipes().first().size)
        }

    @Test
    fun aFinishedSnipeDoesNotBlockAddingTheListingAgain() =
        runTest {
            val first = addArmed()
            repository.cancel(first.id)

            assertTrue(repository.addFromListing(testListing(), gbp("48.00"), leadSeconds = 6) is AddResult.Added)
        }

    @Test
    fun addRefusesListingsAndMaxesItCannotBidOn() =
        runTest {
            val refusals =
                listOf(
                    repository.addFromListing(testListing(isAuction = false), gbp("48.00"), 6),
                    repository.addFromListing(testListing(status = ListingStatus.ENDED), gbp("48.00"), 6),
                    repository.addFromListing(testListing(endTimeUtcMillis = now - 1), gbp("48.00"), 6),
                    repository.addFromListing(testListing(), Money.of("48.00", "USD"), 6),
                    repository.addFromListing(testListing(), gbp("0.00"), 6),
                    repository.addFromListing(testListing(), gbp("48.00"), leadSeconds = 1),
                    repository.addFromListing(testListing(), gbp("48.00"), leadSeconds = 61),
                    repository.addFromListing(testListing(), gbp("48.00"), 6, groupId = 99),
                )

            for (refusal in refusals) assertTrue("$refusal", refusal is AddResult.Rejected)
            assertTrue(repository.observeSnipes().first().isEmpty())
        }

    @Test
    fun addTargetsTheRelistedItemWhenEbayRedirected() =
        runTest {
            val snipe = addArmed(testListing(itemId = "111", relistedItemId = "222"))

            assertEquals(ItemRef(EbaySite.UK, "222"), snipe.ref)
        }

    @Test
    fun aMaxBelowThePriceStartsAsPriceOverMax() =
        runTest {
            val snipe = addArmed(testListing(currentPrice = gbp("52.00"), bidCount = 3), max = gbp("50.00"))

            assertEquals(SnipeStatus.PRICE_OVER_MAX, snipe.status)
        }

    // Reading.

    @Test
    fun snipesComeSoonestEndFirstAndDeletedOnesAreHidden() =
        runTest {
            val later = addArmed(testListing(itemId = "1", endTimeUtcMillis = now + 5 * HOUR))
            val sooner = addArmed(testListing(itemId = "2", endTimeUtcMillis = now + 1 * HOUR))
            val deleted = addArmed(testListing(itemId = "3", endTimeUtcMillis = now + 2 * HOUR))
            repository.delete(deleted.id)

            assertEquals(listOf(sooner.id, later.id), repository.observeSnipes().first().map { it.id })
            assertNull(repository.snipe(deleted.id))
            assertEquals(listOf(sooner.id, later.id), repository.pendingSnipes().map { it.id })
        }

    @Test
    fun observedSnipeFollowsEdits() =
        runTest {
            val snipe = addArmed()
            repository.observeSnipe(snipe.id).test {
                assertEquals(gbp("48.00"), awaitItem()?.maxBid)
                repository.updateMax(snipe.id, gbp("55.00"))
                assertEquals(gbp("55.00"), awaitItem()?.maxBid)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // Editing the max.

    @Test
    fun settingAMaxOnAWatchedListingArmsIt() =
        runTest {
            val watched = (repository.addFromListing(testListing(), null, 6) as AddResult.Added).snipe

            val updated = (repository.updateMax(watched.id, gbp("60.00")) as EditResult.Done).snipe

            assertEquals(SnipeStatus.ARMED, updated.status)
            val event = repository.observeTimeline(watched.id).first().last()
            assertEquals(TimelineKind.ARMED, event.kind)
            assertEquals("Armed with a max of GBP 60.00.", event.message)
        }

    @Test
    fun raisingTheMaxAboveThePriceClearsPriceOverMax() =
        runTest {
            val snipe = addArmed(testListing(currentPrice = gbp("52.00"), bidCount = 3), max = gbp("50.00"))

            val updated = (repository.updateMax(snipe.id, gbp("70.00")) as EditResult.Done).snipe

            assertEquals(SnipeStatus.ARMED, updated.status)
            assertEquals("Max changed from GBP 50.00 to GBP 70.00.", lastMessage(snipe.id))
        }

    @Test
    fun removingTheMaxTurnsTheSnipeIntoWatching() =
        runTest {
            val snipe = addArmed()

            val updated = (repository.updateMax(snipe.id, null) as EditResult.Done).snipe

            assertEquals(SnipeStatus.WATCHING, updated.status)
            assertTrue(armedCopy.read().isEmpty())
        }

    @Test
    fun maxAndLeadCanChangeWhileTheWindowIsOpenUntilTheEditsLock() =
        runTest {
            val snipe = addArmed()
            forceStatus(snipe.id, SnipeStatus.BIDDING)
            val end = snipe.endTimeUtcMillis
            // Lead 6 s: the bid goes at end - 6 s, and edits lock 3 s before that.
            now = end - 70_000
            val raised = repository.updateMax(snipe.id, gbp("60.00"))
            assertEquals(gbp("60.00"), (raised as EditResult.Done).snipe.maxBid)
            assertEquals("the window keeps its status", SnipeStatus.BIDDING, raised.snipe.status)
            now = end - 9_001
            assertTrue(repository.updateMax(snipe.id, gbp("61.00")) is EditResult.Done)
            now = end - 9_000
            assertTrue(repository.updateMax(snipe.id, gbp("62.00")) is EditResult.NotAllowed)
            assertTrue(repository.setLead(snipe.id, 5) is EditResult.NotAllowed)
            assertEquals(gbp("61.00"), repository.snipe(snipe.id)?.maxBid)
            // The group still cannot change once the window is open: the window decided on it at its start.
            now = end - 70_000
            val group = repository.createGroup("Cameras")
            assertTrue(repository.setGroup(snipe.id, group.id) is EditResult.NotAllowed)
        }

    @Test
    fun aLeadChangeInTheWindowMustBeBeforeBothTheOldAndTheNewLock() =
        runTest {
            val snipe = addArmed()
            forceStatus(snipe.id, SnipeStatus.BIDDING)
            now = snipe.endTimeUtcMillis - 20_000
            // 30 s would bid 30 s before the end, which has passed.
            assertTrue(repository.setLead(snipe.id, 30) is EditResult.NotAllowed)
            // 15 s locks 18 s before the end, still ahead; the old 6 s lead locks later still.
            assertEquals(15, (repository.setLead(snipe.id, 15) as EditResult.Done).snipe.leadSeconds)
        }

    @Test
    fun maxCannotChangeOnceTheBidIsOutOrTheSnipeFinished() =
        runTest {
            val snipe = addArmed()
            val statuses =
                listOf(
                    SnipeStatus.BID_SENT,
                    SnipeStatus.LEADING,
                    SnipeStatus.EXTENDED,
                    SnipeStatus.WON,
                    SnipeStatus.CANCELLED,
                )
            for (status in statuses) {
                forceStatus(snipe.id, status)
                assertTrue("$status", repository.updateMax(snipe.id, gbp("90.00")) is EditResult.NotAllowed)
                assertTrue("$status", repository.setLead(snipe.id, 9) is EditResult.NotAllowed)
            }
        }

    @Test
    fun aWindowThatEndedWithAnOpenResultTakesNoEdits() =
        runTest {
            val snipe = addArmed()
            val open = SnipeResult(SnipeOutcome.UNKNOWN, null, null, null, null, null, "Check eBay.")
            repository.update(snipe.copy(status = SnipeStatus.BIDDING, result = open))

            assertTrue(repository.updateMax(snipe.id, gbp("90.00")) is EditResult.NotAllowed)
        }

    @Test
    fun maxMustStayInTheListingCurrency() =
        runTest {
            val snipe = addArmed()

            assertTrue(repository.updateMax(snipe.id, Money.of("60.00", "USD")) is EditResult.NotAllowed)
            assertTrue(repository.updateMax(snipe.id, gbp("0.00")) is EditResult.NotAllowed)
            assertEquals(EditResult.NotFound, repository.updateMax(999, gbp("60.00")))
        }

    // Lead and group.

    @Test
    fun leadChangesWithinTheAllowedRange() =
        runTest {
            val snipe = addArmed()

            assertEquals(9, (repository.setLead(snipe.id, 9) as EditResult.Done).snipe.leadSeconds)
            assertTrue(repository.setLead(snipe.id, 1) is EditResult.NotAllowed)
            assertTrue(repository.setLead(snipe.id, 61) is EditResult.NotAllowed)
            assertEquals("Kestrel now bids 9 s before the end.", lastMessage(snipe.id))
        }

    @Test
    fun groupsAreCreatedAndAssigned() =
        runTest {
            val snipe = addArmed()
            val group = repository.createGroup("  Cameras ", targetWins = 1)

            val updated = (repository.setGroup(snipe.id, group.id) as EditResult.Done).snipe

            assertEquals("Cameras", group.name)
            assertEquals(group.id, updated.groupId)
            assertEquals(group, repository.group(group.id))
            assertEquals(listOf(updated), repository.groupMembers(group.id))
            assertEquals(listOf(group), repository.observeGroups().first())
            assertTrue(repository.setGroup(snipe.id, 999) is EditResult.NotAllowed)
            assertNull((repository.setGroup(snipe.id, null) as EditResult.Done).snipe.groupId)
        }

    @Test
    fun aSnipeMovesBetweenGroupsAndLeavesKeepingItsMaxLeadAndTimeline() =
        runTest {
            val snipe = addArmed(max = gbp("48.00"))
            val cameras = repository.createGroup("Cameras")
            val lenses = repository.createGroup("Lenses")
            repository.setGroup(snipe.id, cameras.id)
            val before = repository.observeTimeline(snipe.id).first().size

            val moved = (repository.setGroup(snipe.id, lenses.id) as EditResult.Done).snipe
            assertEquals(lenses.id, moved.groupId)
            assertEquals(gbp("48.00"), moved.maxBid)
            assertEquals(6, moved.leadSeconds)
            assertEquals(SnipeStatus.ARMED, moved.status)
            assertEquals("Moved from the group \"Cameras\" to \"Lenses\".", lastMessage(snipe.id))

            val left = (repository.setGroup(snipe.id, null) as EditResult.Done).snipe
            assertNull(left.groupId)
            assertEquals("Removed from the group \"Lenses\".", lastMessage(snipe.id))
            assertEquals("the history only grows", before + 2, repository.observeTimeline(snipe.id).first().size)
        }

    @Test
    fun aSnipeStartsANewGroupInOneStep() =
        runTest {
            val snipe = addArmed()
            val moved = (repository.moveToNewGroup(snipe.id, "  Watches ") as EditResult.Done).snipe
            val group = repository.observeGroups().first().single()
            assertEquals("Watches", group.name)
            assertEquals(1, group.targetWins)
            assertEquals(group.id, moved.groupId)
            assertEquals("Added to the new group \"Watches\".", lastMessage(snipe.id))
        }

    @Test
    fun aFinishedSnipeNeverChangesGroupAndLeavesNoEmptyGroupBehind() =
        runTest {
            val snipe = addArmed()
            finish(snipe.id, SnipeStatus.WON, gbp("40.00"))
            val group = repository.createGroup("Cameras")
            assertEquals(
                EditResult.NotAllowed("This snipe has already finished."),
                repository.setGroup(snipe.id, group.id),
            )
            assertEquals(
                EditResult.NotAllowed("This snipe has already finished."),
                repository.moveToNewGroup(snipe.id, "Watches"),
            )
            assertEquals(listOf("Cameras"), repository.observeGroups().first().map { it.name })
            assertTrue(repository.moveToNewGroup(addArmed(testListing(itemId = "9")).id, "  ") is EditResult.NotAllowed)
        }

    // Cancel and delete.

    @Test
    fun cancelEndsTheSnipeWithAReason() =
        runTest {
            val snipe = addArmed()

            val cancelled = (repository.cancel(snipe.id) as EditResult.Done).snipe

            assertEquals(SnipeStatus.CANCELLED, cancelled.status)
            assertEquals(SnipeOutcome.CANCELLED, cancelled.result?.outcome)
            assertEquals(
                TimelineKind.CANCELLED,
                repository
                    .observeTimeline(snipe.id)
                    .first()
                    .last()
                    .kind,
            )
            assertTrue(armedCopy.read().isEmpty())
            assertTrue(repository.cancel(snipe.id) is EditResult.NotAllowed)
        }

    @Test
    fun uncancelBringsBackTheSameSnipeArmedWithItsTimeline() =
        runTest {
            val snipe = addArmed()
            repository.cancel(snipe.id)

            val restored = (repository.uncancel(snipe.id) as EditResult.Done).snipe

            assertEquals(snipe.id, restored.id)
            assertEquals(SnipeStatus.ARMED, restored.status)
            assertEquals(null, restored.result)
            assertEquals(gbp("48.00"), restored.maxBid)
            val kinds = repository.observeTimeline(snipe.id).first().map { it.kind }
            assertTrue(TimelineKind.CANCELLED in kinds)
            assertEquals("You brought this snipe back.", lastMessage(snipe.id))
            assertTrue(armedCopy.read().isNotEmpty())
        }

    @Test
    fun uncancelOfASnipeItsGroupCancelledTakesItOutOfTheGroup() =
        runTest {
            val group = repository.createGroup("Cameras")
            val snipe = addArmed()
            repository.setGroup(snipe.id, group.id)
            forceStatus(snipe.id, SnipeStatus.SKIPPED_BY_GROUP)

            val restored = (repository.uncancel(snipe.id) as EditResult.Done).snipe

            assertEquals(SnipeStatus.ARMED, restored.status)
            assertEquals(null, restored.groupId)
            assertEquals("You brought this snipe back, outside its group.", lastMessage(snipe.id))
        }

    @Test
    fun uncancelIsRefusedForASnipeThatIsNotCancelledOrTooCloseToTheEnd() =
        runTest {
            val snipe = addArmed()
            assertEquals(EditResult.NotAllowed("Only a cancelled snipe can come back."), repository.uncancel(snipe.id))

            repository.cancel(snipe.id)
            now = snipe.endTimeUtcMillis - 30_000L

            assertTrue(repository.uncancel(snipe.id) is EditResult.NotAllowed)
            assertEquals(SnipeStatus.CANCELLED, repository.snipe(snipe.id)?.status)
        }

    @Test
    fun renameGroupTrimsTheNameAndExplainsRefusals() =
        runTest {
            val group = repository.createGroup("Cameras")

            assertEquals(null, repository.renameGroup(group.id, "  Film cameras "))
            assertEquals("Film cameras", repository.group(group.id)?.name)
            assertEquals("A group needs a name.", repository.renameGroup(group.id, "   "))
            assertTrue(repository.renameGroup(group.id, "x".repeat(61)) != null)
            assertEquals("That group no longer exists.", repository.renameGroup(group.id + 100, "Lenses"))
        }

    @Test
    fun cancelIsRefusedOnceEbayHasTheBid() =
        runTest {
            val snipe = addArmed()
            forceStatus(snipe.id, SnipeStatus.BID_SENT)

            val result = repository.cancel(snipe.id)

            assertEquals(EditResult.NotAllowed("eBay already has your bid, so Kestrel can't cancel it."), result)
            assertEquals(SnipeStatus.BID_SENT, repository.snipe(snipe.id)?.status)
        }

    @Test
    fun deleteCanBeUndone() =
        runTest {
            val snipe = addArmed()

            assertTrue(repository.delete(snipe.id) is EditResult.Done)
            assertNull(repository.snipe(snipe.id))
            assertTrue(armedCopy.read().isEmpty())

            val restored = (repository.undoDelete(snipe.id) as EditResult.Done).snipe
            assertEquals(snipe, restored)
            assertEquals(1, armedCopy.read().size)
            assertEquals(EditResult.NotFound, repository.undoDelete(snipe.id))
        }

    @Test
    fun aBidWhoseResultNeverCameIsSettledByTheUserOnceTheAuctionEnded() =
        runTest {
            val snipe = addArmed()
            forceStatus(snipe.id, SnipeStatus.BID_SENT)
            assertTrue("not before the end", repository.settle(snipe.id, won = true) is EditResult.NotAllowed)

            now = snipe.endTimeUtcMillis + 2 * HOUR
            val settled = repository.settle(snipe.id, won = false)

            val lost = (settled as EditResult.Done).snipe
            assertEquals(SnipeStatus.LOST, lost.status)
            assertEquals(SnipeOutcome.LOST, lost.result?.outcome)
            assertEquals("You marked it lost after checking eBay.", lastMessage(snipe.id))
            assertTrue("once only", repository.settle(snipe.id, won = true) is EditResult.NotAllowed)
        }

    @Test
    fun aSnipeThatNeverBidHasNothingToSettle() =
        runTest {
            val snipe = addArmed()
            now = snipe.endTimeUtcMillis + 2 * HOUR

            assertTrue(repository.settle(snipe.id, won = true) is EditResult.NotAllowed)
        }

    @Test
    fun aSnipeLeftInABidStatusCanBeDeletedLongAfterItsEnd() =
        runTest {
            val snipe = addArmed()
            forceStatus(snipe.id, SnipeStatus.BID_SENT)
            assertTrue(repository.delete(snipe.id) is EditResult.NotAllowed)

            now = snipe.endTimeUtcMillis + 2 * HOUR

            assertTrue(repository.delete(snipe.id) is EditResult.Done)
        }

    @Test
    fun deleteIsRefusedWhileTheWindowIsOpen() =
        runTest {
            val snipe = addArmed()
            forceStatus(snipe.id, SnipeStatus.BIDDING)

            assertTrue(repository.delete(snipe.id) is EditResult.NotAllowed)
        }

    @Test
    fun purgeRemovesOldDeletedSnipesWithTheirHistory() =
        runTest {
            val snipe = addArmed()
            repository.delete(snipe.id)
            now += 2 * 24 * HOUR

            assertEquals(1, repository.purgeDeleted(now - 24 * HOUR))
            assertEquals(EditResult.NotFound, repository.undoDelete(snipe.id))
            assertTrue(repository.observeTimeline(snipe.id).first().isEmpty())
            assertTrue(repository.observePricePoints(snipe.id).first().isEmpty())
        }

    // Engine writes.

    @Test
    fun engineWritesNeverChangeTheUsersOwnFields() =
        runTest {
            val group = repository.createGroup("Cameras")
            val snipe = addArmed()
            repository.setGroup(snipe.id, group.id)
            val stored = repository.snipe(snipe.id)!!

            repository.update(
                stored.copy(
                    maxBid = gbp("999.00"),
                    leadSeconds = 30,
                    groupId = null,
                    lastPrice = gbp("40.00"),
                    lastBidCount = 7,
                    endTimeUtcMillis = stored.endTimeUtcMillis + MINUTE,
                ),
            )

            val after = repository.snipe(snipe.id)!!
            assertEquals(gbp("48.00"), after.maxBid)
            assertEquals(6, after.leadSeconds)
            assertEquals(group.id, after.groupId)
            assertEquals(gbp("40.00"), after.lastPrice)
            assertEquals(7, after.lastBidCount)
            assertEquals(stored.endTimeUtcMillis + MINUTE, after.endTimeUtcMillis)
        }

    @Test
    fun aStaleEngineCopyCannotUndoTheUsersCancel() =
        runTest {
            val snipe = addArmed()
            val staleCopy = repository.snipe(snipe.id)!!
            repository.cancel(snipe.id)

            repository.update(staleCopy.copy(status = SnipeStatus.BIDDING, lastPrice = gbp("33.00")))

            assertEquals(SnipeStatus.CANCELLED, repository.snipe(snipe.id)?.status)
            assertEquals(SnipeOutcome.CANCELLED, repository.snipe(snipe.id)?.result?.outcome)
        }

    @Test
    fun aBidThatWentOutAfterCancelIsStillRecorded() =
        runTest {
            val snipe = addArmed()
            val engineCopy = repository.snipe(snipe.id)!!
            repository.cancel(snipe.id)

            repository.update(engineCopy.copy(status = SnipeStatus.BID_SENT))

            assertEquals(SnipeStatus.BID_SENT, repository.snipe(snipe.id)?.status)
        }

    @Test
    fun aFinishedSnipeNeverBecomesActiveAgainButCanBeCorrected() =
        runTest {
            val snipe = addArmed()
            val stale = repository.snipe(snipe.id)!!
            forceStatus(snipe.id, SnipeStatus.FAILED)

            repository.update(stale.copy(status = SnipeStatus.ARMED))
            assertEquals(SnipeStatus.FAILED, repository.snipe(snipe.id)?.status)

            val won = SnipeResult(SnipeOutcome.WON, gbp("41.00"), now, 6_000, 300, "You won", null)
            repository.update(stale.copy(status = SnipeStatus.WON, result = won))
            assertEquals(SnipeStatus.WON, repository.snipe(snipe.id)?.status)
            assertEquals(won, repository.snipe(snipe.id)?.result)
        }

    @Test
    fun onceTheWindowOpenedTheSnipeNeverGoesBackToNotStarted() =
        runTest {
            val snipe = addArmed()
            val stale = repository.snipe(snipe.id)!!
            forceStatus(snipe.id, SnipeStatus.BID_SENT)

            repository.update(stale.copy(status = SnipeStatus.ARMED))
            assertEquals(SnipeStatus.BID_SENT, repository.snipe(snipe.id)?.status)

            repository.update(stale.copy(status = SnipeStatus.LEADING))
            assertEquals(SnipeStatus.LEADING, repository.snipe(snipe.id)?.status)
        }

    @Test
    fun aWindowHandsTheSnipeBackWhenTheEndMovedFarAway() =
        runTest {
            val snipe = addArmed()
            forceStatus(snipe.id, SnipeStatus.BIDDING)
            // eBay moved the end an hour out: no window holds a snipe that far from its end.
            val farEnd = now + 60 * MINUTE
            repository.update(repository.snipe(snipe.id)!!.copy(endTimeUtcMillis = farEnd))

            repository.update(repository.snipe(snipe.id)!!.copy(status = SnipeStatus.ARMED))

            assertEquals(SnipeStatus.ARMED, repository.snipe(snipe.id)?.status)
        }

    @Test
    fun aStaleArmedCopyCannotResetAWindowThatHoldsTheSnipe() =
        runTest {
            val snipe = addArmed(testListing(endTimeUtcMillis = now + 2 * MINUTE))
            val stale = repository.snipe(snipe.id)!!
            forceStatus(snipe.id, SnipeStatus.BIDDING)

            repository.update(stale.copy(status = SnipeStatus.ARMED))

            assertEquals(SnipeStatus.BIDDING, repository.snipe(snipe.id)?.status)
        }

    @Test
    fun eventsAndPricesForUnknownSnipesAreIgnored() =
        runTest {
            repository.addEvent(404, TimelineKind.PROBLEM, "nobody")
            repository.recordPrice(404, now, gbp("1.00"), 1)

            assertTrue(repository.observeTimeline(404).first().isEmpty())
        }

    @Test
    fun repeatedPricesAreStoredOnce() =
        runTest {
            val snipe = addArmed()
            repository.recordPrice(snipe.id, now, gbp("31.50"), 4)
            repository.recordPrice(snipe.id, now + 1_000, gbp("33.00"), 5)
            repository.recordPrice(snipe.id, now + 2_000, gbp("33.00"), 5)

            assertEquals(
                listOf(gbp("31.50"), gbp("33.00")),
                repository.observePricePoints(snipe.id).first().map { it.price },
            )
        }

    @Test
    fun engineEventsAreTimestampedWithTrueTime() =
        runTest {
            val snipe = addArmed()
            now += 42

            repository.addEvent(snipe.id, TimelineKind.CLOCK_SYNCED, "Clock 0.3 s behind", detail = "{\"offset\":300}")

            val event = repository.observeTimeline(snipe.id).first().last()
            assertEquals(TEST_NOW + 42, event.atUtcMillis)
            assertEquals("{\"offset\":300}", event.detail)
            assertEquals(event, repository.latestEvent(snipe.id, TimelineKind.CLOCK_SYNCED))
        }

    @Test
    fun aStaleCheckCannotDisarmASnipeTheUserJustArmed() =
        runTest {
            val watched = addWatching(testListing())
            val staleCopy = repository.snipe(watched.id)!!
            repository.updateMax(watched.id, gbp("60.00"))

            repository.update(staleCopy.copy(lastPrice = gbp("33.00")))

            val after = repository.snipe(watched.id)!!
            assertEquals(SnipeStatus.ARMED, after.status)
            assertEquals(gbp("60.00"), after.maxBid)
            assertEquals(gbp("33.00"), after.lastPrice)
        }

    @Test
    fun aStaleCopyCannotArmASnipeTheUserTurnedIntoWatching() =
        runTest {
            val armed = addArmed()
            val staleCopy = repository.snipe(armed.id)!!
            repository.updateMax(armed.id, null)

            repository.update(staleCopy)

            assertEquals(SnipeStatus.WATCHING, repository.snipe(armed.id)?.status)
        }

    @Test
    fun aWatchedListingCanStillReportThatSignInIsNeeded() =
        runTest {
            val watched = addWatching(testListing())

            repository.update(repository.snipe(watched.id)!!.copy(status = SnipeStatus.NEEDS_SIGN_IN))

            assertEquals(SnipeStatus.NEEDS_SIGN_IN, repository.snipe(watched.id)?.status)
        }

    @Test
    fun onceEbayHasTheBidAStaleProblemCannotReopenEditsOrCancel() =
        runTest {
            val snipe = addArmed()
            val stale = repository.snipe(snipe.id)!!
            forceStatus(snipe.id, SnipeStatus.BID_SENT)

            repository.update(stale.copy(status = SnipeStatus.PRICE_OVER_MAX))
            repository.update(stale.copy(status = SnipeStatus.NEEDS_SIGN_IN))

            assertEquals(SnipeStatus.BID_SENT, repository.snipe(snipe.id)?.status)
            assertTrue(repository.updateMax(snipe.id, gbp("90.00")) is EditResult.NotAllowed)
            assertTrue(repository.cancel(snipe.id) is EditResult.NotAllowed)

            val won = SnipeResult(SnipeOutcome.WON, gbp("41.00"), now, 6_000, 300, "You won", null)
            repository.update(stale.copy(status = SnipeStatus.WON, result = won))
            assertEquals(SnipeStatus.WON, repository.snipe(snipe.id)?.status)
        }

    @Test
    fun aDeletedWinStillCountsForItsGroupAndIsNeverPurged() =
        runTest {
            val group = repository.createGroup("Cameras")
            val won = addArmed(testListing(itemId = "1"))
            val other = addArmed(testListing(itemId = "2"))
            repository.setGroup(won.id, group.id)
            repository.setGroup(other.id, group.id)
            finish(won.id, SnipeStatus.WON, gbp("40.00"))

            repository.delete(won.id)
            now += 2 * 24 * HOUR
            repository.purgeDeleted(now - 24 * HOUR)

            assertNull("hidden from the user", repository.snipe(won.id))
            assertEquals(setOf(won.id, other.id), repository.groupMembers(group.id).map { it.id }.toSet())
        }

    @Test
    fun aDeletedLossLeavesItsGroup() =
        runTest {
            val group = repository.createGroup("Cameras")
            val lost = addArmed(testListing(itemId = "1"))
            repository.setGroup(lost.id, group.id)
            finish(lost.id, SnipeStatus.LOST, gbp("90.00"))

            repository.delete(lost.id)

            assertTrue(repository.groupMembers(group.id).isEmpty())
        }

    // Other eBay sites.

    @Test
    fun aSnipeStoredWithAnotherSiteIsReadOnTheHomeSiteAndBlocksASecondSnipe() =
        runTest {
            // Before 1.0.6 a listing kept the site of its link; the same item number is one listing everywhere.
            val older = addArmed(testListing(itemId = "1", site = EbaySite.UK))
            database.snipeDao().update(
                database.snipeDao().find(older.id)!!.copy(site = EbaySite.US),
            )
            assertEquals(ItemRef(EbaySite.HOME, "1"), repository.snipe(older.id)?.ref)
            val again = repository.addFromListing(testListing(itemId = "1", site = EbaySite.UK), gbp("48.00"), 6)
            assertEquals(older.id, (again as AddResult.AlreadyAdded).snipe.id)
        }

    // History.

    @Test
    fun historyAndStatsCountFinishedSnipesPerCurrency() =
        runTest {
            val won = addArmed(testListing(itemId = "1", endTimeUtcMillis = now + HOUR), max = gbp("50.00"))
            val wonAgain = addArmed(testListing(itemId = "2", endTimeUtcMillis = now + 2 * HOUR), max = gbp("100.00"))
            val lost = addArmed(testListing(itemId = "3", endTimeUtcMillis = now + 3 * HOUR))
            val usWin =
                addArmed(
                    testListing(
                        itemId = "4",
                        site = EbaySite.US,
                        currentPrice = Money.of("10.00", "USD"),
                        endTimeUtcMillis =
                            now + 4 * HOUR,
                    ),
                    max = Money.of("30.00", "USD"),
                )
            addArmed(testListing(itemId = "5"))
            finish(won.id, SnipeStatus.WON, gbp("35.00"))
            finish(wonAgain.id, SnipeStatus.WON, gbp("82.50"))
            finish(lost.id, SnipeStatus.LOST, gbp("131.00"))
            finish(usWin.id, SnipeStatus.WON, Money.of("21.00", "USD"))

            assertEquals(
                listOf(usWin.id, lost.id, wonAgain.id, won.id),
                repository.observeHistory().first().map { it.id },
            )
            val stats = repository.observeStats().first()
            assertEquals(3, stats.wins)
            assertEquals(1, stats.losses)
            assertEquals(listOf(gbp("117.50"), Money.of("21.00", "USD")), stats.totalPaid)
            assertEquals(listOf(gbp("32.50"), Money.of("9.00", "USD")), stats.totalSavedUnderMax)
        }

    @Test
    fun latestDryRunIsTheMostRecentOne() =
        runTest {
            val first = addArmed(testListing(itemId = "1"))
            val second = addArmed(testListing(itemId = "2"))
            repository.update(repository.snipe(first.id)!!.copy(dryRunAtUtcMillis = now - HOUR, dryRunPassed = true))
            repository.update(
                repository.snipe(second.id)!!.copy(dryRunAtUtcMillis = now - MINUTE, dryRunPassed = false),
            )

            assertEquals(second.id, repository.latestDryRun()?.id)
        }

    @Test
    fun aFinishedOrWatchedSnipesDryRunIsNoLongerTheLatest() =
        runTest {
            val lost = addArmed(testListing(itemId = "1"))
            repository.update(
                repository.snipe(lost.id)!!.copy(
                    status = SnipeStatus.LOST,
                    dryRunAtUtcMillis = now - HOUR,
                    dryRunPassed = false,
                ),
            )
            val watched = addArmed(testListing(itemId = "2"))
            repository.update(
                repository.snipe(watched.id)!!.copy(dryRunAtUtcMillis = now - MINUTE, dryRunPassed = false),
            )
            repository.updateMax(watched.id, null)

            assertNull("no snipe that will bid had a dry run", repository.latestDryRun())
        }

    @Test
    fun aFailedDryRunOfASnipeThatWillBidOutranksALaterPass() =
        runTest {
            val later = addArmed(testListing(itemId = "1", endTimeUtcMillis = now + 5 * HOUR))
            val sooner = addArmed(testListing(itemId = "2", endTimeUtcMillis = now + 4 * HOUR))
            val passed = addArmed(testListing(itemId = "3"))
            for (failed in listOf(later, sooner)) {
                repository.update(
                    repository.snipe(failed.id)!!.copy(dryRunAtUtcMillis = now - HOUR, dryRunPassed = false),
                )
            }
            repository.update(repository.snipe(passed.id)!!.copy(dryRunAtUtcMillis = now - MINUTE, dryRunPassed = true))

            assertEquals("the failure that bids first", sooner.id, repository.latestDryRun()?.id)
        }

    @Test
    fun theArmedCopyHoldsOnlySnipesThatWillBid() =
        runTest {
            val armed = addArmed(testListing(itemId = "1"))
            repository.addFromListing(testListing(itemId = "2"), null, 6)
            val cancelled = addArmed(testListing(itemId = "3"))
            repository.cancel(cancelled.id)

            assertEquals(listOf(armed.id), armedCopy.read().map { it.id })
            assertNotNull(armedCopy.read().single().title)
        }

    @Test
    fun anEngineWriteLeavesTheArmedCopyToLaterWork() =
        runTest {
            val later = mutableListOf<suspend () -> Unit>()
            val deferring =
                SnipeRepository(
                    database,
                    armedCopy,
                    nowUtcMillis = { now },
                    formatMoney = ::testFormatMoney,
                    refreshArmedCopyLater = { later += it },
                )
            val armed =
                (
                    deferring.addFromListing(
                        testListing(),
                        gbp("48.00"),
                        leadSeconds = 6,
                    ) as AddResult.Added
                ).snipe
            assertEquals(listOf(armed.id), armedCopy.read().map { it.id })

            // The pre-tap BID_SENT write: the file write and its fsync must not run inside it.
            deferring.update(armed.copy(status = SnipeStatus.BID_SENT))

            assertEquals(listOf(armed.id), armedCopy.read().map { it.id })
            assertEquals(1, later.size)
            later.single().invoke()
            assertTrue(armedCopy.read().isEmpty())
        }

    private suspend fun forceStatus(
        snipeId: Long,
        status: SnipeStatus,
    ) {
        val dao = database.snipeDao()
        val row = dao.findIncludingDeleted(snipeId)!!
        dao.update(row.copy(status = status))
    }

    private suspend fun finish(
        snipeId: Long,
        status: SnipeStatus,
        finalPrice: Money,
    ) {
        val outcome = if (status == SnipeStatus.WON) SnipeOutcome.WON else SnipeOutcome.LOST
        val snipe = repository.snipe(snipeId)!!
        repository.update(
            snipe.copy(status = status, result = SnipeResult(outcome, finalPrice, now, 6_000, 250, null, null)),
        )
    }
}
