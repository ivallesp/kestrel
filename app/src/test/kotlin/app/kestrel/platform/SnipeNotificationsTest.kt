package app.kestrel.platform

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kestrel.data.MINUTE
import app.kestrel.data.TEST_NOW
import app.kestrel.data.gbp
import app.kestrel.data.testSnipe
import app.kestrel.engine.NeedsUserReason
import app.kestrel.engine.WindowPhase
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeOutcome
import app.kestrel.model.SnipeResult
import app.kestrel.model.SnipeStatus
import app.kestrel.ui.MainActivity
import app.kestrel.ui.shell.Destination
import app.kestrel.ui.shell.LaunchIntents
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class SnipeNotificationsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val clock = FakeClock(now = TEST_NOW)
    private val photos = FakePhotos()
    private val scope = TestScope(StandardTestDispatcher())

    // The phone's clock runs 2.5 s behind true time.
    private val notifications =
        SnipeNotifications(
            context = context,
            clock = clock,
            systemUtcMillis = { clock.now - 2_500 },
            texts = testTexts(),
            photos = photos,
            scope = scope,
        )

    private val end = TEST_NOW + 2 * MINUTE
    private val snipe = testSnipe(id = 5, title = "Canon AE-1", maxBid = gbp("48.00"), endTimeUtcMillis = end)
    private val windowRequest = SnipeRequest(SnipeAction.WINDOW, 5, "Canon AE-1", end)

    @Before
    fun setUp() {
        NotificationChannels.create(context)
    }

    private fun posted(id: Int): Notification? = shadowOf(manager).getNotification(id)

    private fun Notification.title(): String? = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    private fun Notification.text(): String? = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

    private fun Notification.progressStyle(): Notification.ProgressStyle =
        Notification.Builder.recoverBuilder(context, this).style as Notification.ProgressStyle

    @Test
    fun channelsHaveTheirImportance() {
        val importance = shadowOf(manager).notificationChannels.associate { it.id to it.importance }

        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, importance[NotificationChannels.LIVE])
        assertEquals(NotificationManager.IMPORTANCE_HIGH, importance[NotificationChannels.ATTENTION])
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, importance[NotificationChannels.RESULTS])
        assertEquals(NotificationManager.IMPORTANCE_MIN, importance[NotificationChannels.BACKGROUND])
        assertNull("the countdown is silent", manager.getNotificationChannel(NotificationChannels.LIVE).sound)
    }

    @Test
    fun theWindowIsAPromotedLiveUpdateCountingDownToTheBid() {
        val bidAt = snipe.endTimeUtcMillis - 6_000

        notifications.windowOpened(snipe, bidAt)

        val live = posted(NotificationIds.window(snipe.id))!!
        assertEquals(NotificationChannels.LIVE, live.channelId)
        assertTrue("ongoing", live.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue("asks to be promoted", live.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
        assertTrue(live.hasPromotableCharacteristics())
        assertFalse("never colorized", live.extras.getBoolean(Notification.EXTRA_COLORIZED))
        assertEquals("Bidding GBP 48.00 at 21:01", live.title())
        assertEquals("Checking the listing · Canon AE-1", live.text())
        assertTrue(live.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(live.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertEquals("countdown on the phone's clock", bidAt - 2_500, live.`when`)
        assertNull(live.shortCriticalText)
        assertNotNull(live.deleteIntent)
        // DESIGN 5.1: "Edit max" and "Open", and no cancel action.
        assertEquals(listOf("Edit max", "Open"), live.actions.map { it.title.toString() })
        val editMax = shadowOf(live.actions[0].actionIntent).savedIntent
        assertEquals(Destination.Lot(5, editMax = true), LaunchIntents.destinationOf(editMax))
        assertEquals(
            Destination.Lot(5),
            LaunchIntents.destinationOf(shadowOf(live.actions[1].actionIntent).savedIntent),
        )

        val style = live.progressStyle()
        assertEquals(listOf(25, 50, 75, 100), style.progressPoints.map { it.position })
        assertEquals(100, style.progressMax)
        assertEquals(10, style.progress)
    }

    @Test
    fun afterTheBidTheCountdownFollowsTheEndThroughExtensions() {
        val bidAt = snipe.endTimeUtcMillis - 6_000
        notifications.windowOpened(snipe, bidAt)

        // 20 s after the bid a late bid reset the clock: the countdown is to the new end, never to the past bid.
        clock.now = bidAt + 20_000
        val extended = snipe.copy(endTimeUtcMillis = snipe.endTimeUtcMillis + 60_000)
        notifications.windowProgress(extended, WindowPhase.EXTENDED, "Ends 1 min later")
        val live = posted(NotificationIds.window(snipe.id))!!
        assertEquals("on the phone's clock", extended.endTimeUtcMillis - 2_500, live.`when`)
        assertTrue(live.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(live.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))

        // Past the end, while the result is read, there is nothing to count down to.
        clock.now = extended.endTimeUtcMillis + 2_000
        notifications.windowProgress(extended, WindowPhase.RESOLVING)
        val reading = posted(NotificationIds.window(snipe.id))!!
        assertFalse(reading.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
    }

    /** Moves the true clock and the coroutine clock on together, as real time does. */
    private fun elapse(millis: Long) {
        repeat((millis / 10).toInt()) {
            clock.now += 10
            scope.testScheduler.advanceTimeBy(10)
        }
        scope.testScheduler.runCurrent()
    }

    @Test
    fun theCountdownMovesOnAtZeroAndNeverRunsBelowIt() {
        val bidAt = snipe.endTimeUtcMillis - 6_000
        clock.now = bidAt - 10_000
        notifications.windowOpened(snipe, bidAt)
        assertEquals(bidAt - 2_500, posted(NotificationIds.window(snipe.id))!!.`when`)

        // The bid moment passes before the engine says the bid went out: the countdown goes on to the end.
        elapse(10_000 + 100)
        val toTheEnd = posted(NotificationIds.window(snipe.id))!!
        assertEquals(snipe.endTimeUtcMillis - 2_500, toTheEnd.`when`)
        assertTrue(toTheEnd.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))

        // The end passes and the result is not in yet: no countdown at all, rather than one below zero.
        elapse(6_000)
        assertFalse(posted(NotificationIds.window(snipe.id))!!.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))

        // The engine's next step arrives late, 5 s after the end: still no countdown.
        elapse(5_000)
        notifications.windowProgress(snipe, WindowPhase.LEADING)
        assertFalse(posted(NotificationIds.window(snipe.id))!!.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
    }

    @Test
    fun aClosedWindowIsNotBroughtBackByItsCountdown() {
        val bidAt = snipe.endTimeUtcMillis - 6_000
        clock.now = bidAt - 1_000
        notifications.windowOpened(snipe, bidAt)
        notifications.windowClosed(snipe.id)

        clock.now = snipe.endTimeUtcMillis + 1_000
        scope.testScheduler.advanceUntilIdle()
        assertNull(posted(NotificationIds.window(snipe.id)))
    }

    @Test
    fun theLiveUpdateDropsEditMaxOnceTheEditsLock() {
        val bidAt = snipe.endTimeUtcMillis - 6_000
        clock.now = bidAt - 2_000
        notifications.windowOpened(snipe.copy(status = SnipeStatus.BIDDING), bidAt)

        val live = posted(NotificationIds.window(snipe.id))!!
        assertEquals(listOf("Open"), live.actions.map { it.title.toString() })
    }

    @Test
    fun theChipSaysBiddingDuringTheBidAndTheOutcomeAtTheEnd() {
        notifications.windowOpened(snipe, snipe.endTimeUtcMillis - 6_000)

        notifications.windowProgress(snipe, WindowPhase.BIDDING)
        val bidding = posted(NotificationIds.window(snipe.id))!!
        assertEquals("Bidding", bidding.shortCriticalText)
        assertEquals(60, bidding.progressStyle().progress)

        notifications.windowProgress(snipe, WindowPhase.BID_SENT)
        assertEquals(75, posted(NotificationIds.window(snipe.id))!!.progressStyle().progress)

        notifications.result(snipe, SnipeResult(SnipeOutcome.WON, gbp("41.00"), TEST_NOW, 6_000, 300, null, null))
        val ended = posted(NotificationIds.window(snipe.id))!!
        assertEquals("Won", ended.shortCriticalText)
        assertEquals(100, ended.progressStyle().progress)

        notifications.result(snipe, SnipeResult(SnipeOutcome.LOST, gbp("60.00"), TEST_NOW, 6_000, 300, null, null))
        assertEquals("Lost", posted(NotificationIds.window(snipe.id))!!.shortCriticalText)
    }

    @Test
    fun beforeTheSnipeIsReadTheWindowShowsGettingReady() {
        val request = SnipeRequest(SnipeAction.WINDOW, 5, "Canon AE-1", TEST_NOW)

        val preparing = notifications.foregroundWindowNotification(request)!!

        assertEquals(NotificationChannels.LIVE, preparing.channelId)
        assertEquals("Getting ready to bid", preparing.title())
        assertTrue(preparing.progressStyle().isProgressIndeterminate)
        assertEquals(preparing, notifications.foregroundWindowNotification(request))
    }

    @Test
    fun closingTheWindowRemovesTheLiveUpdate() {
        notifications.windowOpened(snipe, snipe.endTimeUtcMillis - 6_000)

        notifications.windowClosed(snipe.id)

        assertNull(posted(NotificationIds.window(snipe.id)))
        assertNull("a closed window never comes back", notifications.foregroundWindowNotification(windowRequest))
        assertNotNull("a new run of the window starts afresh", notifications.preparingWindowNotification(windowRequest))
    }

    @Test
    fun aDismissedLiveUpdateIsNotPostedAgain() {
        notifications.windowOpened(snipe, snipe.endTimeUtcMillis - 6_000)
        manager.cancel(NotificationIds.window(snipe.id))

        notifications.onWindowDismissed(snipe.id)
        notifications.windowProgress(snipe, WindowPhase.READY)

        assertNull(posted(NotificationIds.window(snipe.id)))
        assertNull(
            "the service must not show it again either",
            notifications.foregroundWindowNotification(windowRequest),
        )
    }

    @Test
    fun aPhotoThatLoadsLaterIsAddedToTheOpenWindowOnly() {
        photos.photo = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val withPhoto = snipe.copy(imageUrl = "https://example.invalid/photo.jpg")
        notifications.windowOpened(withPhoto, withPhoto.endTimeUtcMillis - 6_000)
        notifications.windowProgress(withPhoto.copy(lastPrice = gbp("33.00")), WindowPhase.READY)

        scope.testScheduler.advanceUntilIdle()

        val live = posted(NotificationIds.window(snipe.id))!!
        assertNotNull(live.getLargeIcon())
        assertEquals("the refresh keeps the newest text", "Ready, now GBP 33.00 · Canon AE-1", live.text())

        notifications.windowClosed(snipe.id)
        photos.cachedPhoto = null
        notifications.windowOpened(withPhoto, withPhoto.endTimeUtcMillis - 6_000)
        notifications.windowClosed(snipe.id)
        scope.testScheduler.advanceUntilIdle()
        assertNull("a closed window is not posted again by a late photo", posted(NotificationIds.window(snipe.id)))
    }

    @Test
    fun priceOverMaxOffersRaiseMaxAsATypedReplyAndOpen() {
        notifications.priceOverMax(snipe, current = gbp("52.00"), minimumNext = gbp("53.00"))

        val alert = posted(NotificationIds.priceAlert(snipe.id))!!
        assertEquals(NotificationChannels.ATTENTION, alert.channelId)
        assertEquals("Price passed your max", alert.title())
        assertEquals("Now GBP 52.00, your max is GBP 48.00", alert.text())
        assertEquals(listOf("Raise max", "Stop sniping", "Open"), alert.actions.map { it.title.toString() })

        val raise = alert.actions[0]
        val input = raise.remoteInputs.single()
        assertEquals(NotificationActions.KEY_NEW_MAX, input.resultKey)
        assertEquals("New max, GBP 53.00 or more", input.label.toString())
        val reply = shadowOf(raise.actionIntent)
        assertTrue("the reply intent must be mutable", reply.flags and PendingIntent.FLAG_MUTABLE != 0)
        assertEquals(NotificationActionReceiver::class.java.name, reply.savedIntent.component?.className)
        assertEquals(NotificationActions.ACTION_RAISE_MAX, reply.savedIntent.action)
        assertEquals(5L, reply.savedIntent.getLongExtra(NotificationActions.EXTRA_SNIPE_ID, 0))
        assertEquals(5300L, reply.savedIntent.getLongExtra(NotificationActions.EXTRA_MINIMUM_MINOR, 0))
        assertEquals(5200L, reply.savedIntent.getLongExtra(NotificationActions.EXTRA_CURRENT_MINOR, 0))

        val open = shadowOf(alert.actions[2].actionIntent).savedIntent
        assertEquals(MainActivity::class.java.name, open.component?.className)
        assertEquals(5L, AppLinks.snipeIdFrom(open))
    }

    @Test
    fun needsUserOpensTheAppAtTheSnipe() {
        notifications.needsUser(
            snipe.copy(endTimeUtcMillis = TEST_NOW + 60 * MINUTE),
            NeedsUserReason.SIGN_IN,
            detail = null,
        )

        val alert = posted(NotificationIds.needsUser(snipe.id))!!
        assertEquals(NotificationChannels.ATTENTION, alert.channelId)
        assertEquals("Sign in to eBay", alert.title())
        assertEquals("Kestrel can't bid until you sign in.", alert.text())
        assertEquals(5L, AppLinks.snipeIdFrom(shadowOf(alert.contentIntent).savedIntent))
        assertNull("not imminent, so no full-screen takeover", alert.fullScreenIntent)
    }

    @Test
    fun aCheckOnlyTheUserCanAnswerOpensEbaysPageInKestrel() {
        // DESIGN 5.4: "Open eBay page" (in-app, at the challenge).
        for (reason in listOf(NeedsUserReason.CAPTCHA, NeedsUserReason.VERIFICATION)) {
            notifications.needsUser(snipe, reason, detail = "eBay showed a security check.")
            val alert = posted(NotificationIds.needsUser(snipe.id))!!
            assertEquals(listOf("Open eBay page"), alert.actions.map { it.title.toString() })
            val action = shadowOf(alert.actions.single().actionIntent).savedIntent
            assertEquals(Destination.HandOff(5), LaunchIntents.destinationOf(action))
            assertEquals(Destination.HandOff(5), LaunchIntents.destinationOf(shadowOf(alert.contentIntent).savedIntent))
        }
        // A sign-in is fixed in S6 from the lot, so that alert still opens the lot.
        notifications.needsUser(snipe, NeedsUserReason.SIGN_IN, detail = null)
        val signIn = posted(NotificationIds.needsUser(snipe.id))!!
        assertEquals(listOf("Open"), signIn.actions.map { it.title.toString() })
        assertEquals(Destination.Lot(5), LaunchIntents.destinationOf(shadowOf(signIn.contentIntent).savedIntent))
    }

    @Test
    fun alertsTheSnipesNewStateMadeUntrueAreTakenBack() {
        val overMax = snipe.copy(status = SnipeStatus.PRICE_OVER_MAX, lastPrice = gbp("60.00"), lastBidCount = 5)
        notifications.priceOverMax(overMax, gbp("60.00"), gbp("61.00"))
        notifications.result(overMax, SnipeResult(SnipeOutcome.SKIPPED_BY_GROUP, null, null, null, null, null, "Won"))
        notifications.priceOverMax(overMax, gbp("60.00"), gbp("61.00"))

        // Still under the price: the alert stands.
        notifications.withdrawStale(overMax.copy(maxBid = gbp("55.00")))
        assertNotNull(posted(NotificationIds.priceAlert(snipe.id)))

        // Raised above it and armed again: neither the price alert nor the old result applies any more.
        notifications.withdrawStale(overMax.copy(status = SnipeStatus.ARMED, maxBid = gbp("80.00")))
        assertNull(posted(NotificationIds.priceAlert(snipe.id)))
        assertNull(posted(NotificationIds.result(snipe.id)))
    }

    @Test
    fun theRaiseMaxReplyNamesTheMinimumAndOffersItAndTheNextStep() {
        val overMax = snipe.copy(status = SnipeStatus.PRICE_OVER_MAX, lastPrice = gbp("2150.00"), lastBidCount = 9)
        notifications.priceOverMax(overMax, gbp("2150.00"), gbp("2200.00"))

        val raise = posted(NotificationIds.priceAlert(snipe.id))!!.actions.first()
        val input = raise.remoteInputs.single()
        // DESIGN 5.2: the minimum as the hint (a RemoteInput has only its label), and two choices.
        assertEquals("New max, GBP 2200.00 or more", input.label.toString())
        assertEquals(listOf("GBP 2200.00", "GBP 2250.00"), input.choices.map { it.toString() })
    }

    @Test
    fun aNeedsYouAlertNeverTakesOverTheScreen() {
        // DESIGN 5.4 and MF46: never full-screen, neither minutes before the bid nor days after the end.
        notifications.needsUser(snipe, NeedsUserReason.CAPTCHA, detail = "eBay showed a security check.")
        assertNull(posted(NotificationIds.needsUser(snipe.id))!!.fullScreenIntent)

        val longEnded = snipe.copy(endTimeUtcMillis = TEST_NOW - 3 * 24 * 60 * MINUTE)
        notifications.needsUser(longEnded, NeedsUserReason.UNKNOWN_PAGE, detail = null)
        assertNull(posted(NotificationIds.needsUser(snipe.id))!!.fullScreenIntent)
    }

    @Test
    fun aWinShowsTheItemPhotoAndClearsOldAlerts() {
        val photo = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        photos.cachedPhoto = photo
        notifications.priceOverMax(snipe, gbp("52.00"), null)

        notifications.result(
            snipe.copy(imageUrl = "https://example.invalid/photo.jpg"),
            SnipeResult(SnipeOutcome.WON, gbp("41.00"), TEST_NOW, 6_000, 300, null, null),
        )

        val result = posted(NotificationIds.result(snipe.id))!!
        assertEquals(NotificationChannels.RESULTS, result.channelId)
        assertEquals("Won · Canon AE-1", result.title())
        assertEquals("GBP 41.00, GBP 7.00 under your max", result.text())
        assertNotNull(result.getLargeIcon())
        assertTrue(result.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertNull(posted(NotificationIds.priceAlert(snipe.id)))
    }

    @Test
    fun aPhotoThatArrivesLaterIsAddedToTheShownResult() {
        photos.photo = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val withPhoto = snipe.copy(imageUrl = "https://example.invalid/photo.jpg")

        notifications.result(withPhoto, SnipeResult(SnipeOutcome.LOST, gbp("60.00"), null, null, null, null, null))
        assertNull(posted(NotificationIds.result(snipe.id))!!.getLargeIcon())
        scope.testScheduler.advanceUntilIdle()

        val result = posted(NotificationIds.result(snipe.id))!!
        assertNotNull(result.getLargeIcon())
        assertEquals("Sold for GBP 60.00 · Canon…", result.title())
    }

    @Test
    fun tapToArmStartsTheServiceFromTheNotification() {
        notifications.showTapToArm(SnipeRequest(SnipeAction.WINDOW, 5, "Canon AE-1", TEST_NOW))

        val tap = posted(NotificationIds.tapToArm(5))!!
        assertEquals(NotificationChannels.ATTENTION, tap.channelId)
        assertEquals("Tap to arm your snipe", tap.title())
        val start = shadowOf(tap.contentIntent)
        assertTrue(start.isForegroundService)
        assertEquals(SnipeService::class.java.name, start.savedIntent.component?.className)
        assertEquals(SnipeAction.WINDOW, SnipeRequest.from(start.savedIntent)?.action)
    }

    @Test
    fun aBlockedBackgroundCheckDoesNotBotherTheUser() {
        notifications.showTapToArm(SnipeRequest(SnipeAction.CHECK, 5, "Canon AE-1", TEST_NOW))

        assertNull(posted(NotificationIds.tapToArm(5)))
    }

    @Test
    fun theUnlockNoticeCountsTheWaitingSnipes() {
        notifications.showUnlockToArm(count = 2, nextEndUtcMillis = TEST_NOW)

        val notice = posted(NotificationIds.UNLOCK_TO_ARM)!!
        assertEquals("Unlock your phone", notice.title())
        assertEquals("To arm 2 snipes. The next ends Sun 21:00.", notice.text())
    }

    @Test
    fun theServiceNotificationIsQuiet() {
        val quiet = notifications.serviceNotification(listOf(SnipeRequest(SnipeAction.CHECK, 5, "Canon AE-1", null)))

        assertEquals(NotificationChannels.BACKGROUND, quiet.channelId)
        assertEquals("Checking a listing", quiet.title())
        assertEquals("Canon AE-1", quiet.text())
    }

    @Test
    fun aRaisedMaxReplacesTheAlertWithAConfirmation() {
        notifications.priceOverMax(snipe, gbp("52.00"), gbp("53.00"))

        notifications.showRaiseMaxDone(snipe.copy(maxBid = gbp("60.00")), gbp("60.00"))

        val confirmation = posted(NotificationIds.priceAlert(snipe.id))!!
        assertEquals("Max raised to GBP 60.00", confirmation.title())
        assertTrue(confirmation.actions.isNullOrEmpty())
    }

    @Test
    fun cancellingASnipeClearsItsWindowAndAlertsButKeepsResults() {
        val other: Snipe = snipe
        notifications.windowOpened(other, other.endTimeUtcMillis - 6_000)
        notifications.needsUser(other, NeedsUserReason.SIGN_IN, null)
        notifications.result(other, SnipeResult(SnipeOutcome.LOST, null, null, null, null, null, null))

        notifications.cancelForSnipe(other.id)

        assertNull(posted(NotificationIds.window(other.id)))
        assertNull(posted(NotificationIds.needsUser(other.id)))
        assertNotNull(posted(NotificationIds.result(other.id)))
    }

    @Test
    fun aPhotoThatCannotBeReadIsLeftOutWithoutHarm() {
        val brokenPhotos =
            object : ItemPhotos {
                override fun cached(url: String?): Bitmap? = null

                override suspend fun load(url: String): Bitmap? = throw IllegalStateException("cache unreadable")
            }
        val withBrokenPhotos =
            SnipeNotifications(context, clock, { clock.now }, testTexts(), brokenPhotos, scope)

        withBrokenPhotos.result(
            snipe.copy(imageUrl = "https://example.invalid/photo.jpg"),
            SnipeResult(SnipeOutcome.WON, null, null, null, null, null, null),
        )
        scope.testScheduler.advanceUntilIdle()

        assertTrue("the failure stayed inside the photo job", scope.isActive)
        assertNotNull(posted(NotificationIds.result(snipe.id)))
    }

    @Test
    fun scheduleProblemsAreShownAndCleared() {
        notifications.showScheduleProblem(ScheduleProblem.EXACT_ALARMS_DENIED)
        assertEquals("Exact alarms are off", posted(NotificationIds.SCHEDULE_PROBLEM)!!.title())

        notifications.clearScheduleProblem()
        assertNull(posted(NotificationIds.SCHEDULE_PROBLEM))
    }
}
