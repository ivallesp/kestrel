package app.kestrel.platform

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kestrel.data.AddResult
import app.kestrel.data.ArmedSnipesCopy
import app.kestrel.data.KestrelDatabase
import app.kestrel.data.MINUTE
import app.kestrel.data.SnipeRepository
import app.kestrel.data.TEST_NOW
import app.kestrel.data.gbp
import app.kestrel.data.testFormatMoney
import app.kestrel.data.testListing
import app.kestrel.model.Money
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.File

@RunWith(AndroidJUnit4::class)
class NotificationActionHandlerTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val clock = FakeClock(now = TEST_NOW)
    private lateinit var database: KestrelDatabase
    private lateinit var repository: SnipeRepository
    private val registry = SnipeJobRegistry()
    private val rescheduled = mutableListOf<Snipe>()
    private val cancelled = mutableListOf<Long>()
    private var engineMinimum: Money? = null
    private val notifications =
        SnipeNotifications(
            context = context,
            clock = clock,
            systemUtcMillis = { clock.now },
            texts = testTexts(),
            photos = FakePhotos(),
            scope = TestScope(StandardTestDispatcher()),
            canUseFullScreenIntent = { false },
        )
    private lateinit var handler: NotificationActionHandler

    @Before
    fun setUp() {
        NotificationChannels.create(context)
        database = KestrelDatabase.inMemory(context)
        val armedCopy = ArmedSnipesCopy(File(context.noBackupFilesDir, "armed-${System.nanoTime()}.json"))
        repository = SnipeRepository(database, armedCopy, { clock.now }, ::testFormatMoney)
        val actions =
            SnipeActions(
                repository = repository,
                reschedule = { rescheduled += it },
                cancelSchedule = { cancelled += it },
                stopJobs = { registry.cancelAll(it) },
                clearNotifications = { notifications.cancelForSnipe(it) },
            )
        val parse: (
            String,
            String,
        ) -> Money? = { text, currency -> text.toBigDecimalOrNull()?.let { Money.of(it, currency) } }
        handler =
            NotificationActionHandler(
                repository = repository,
                actions = actions,
                notifications = notifications,
                rules = RaiseMaxRules(parse, ::testFormatMoney),
                minimumNextBid = { engineMinimum },
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** A snipe whose price (£52, 3 bids) has passed its £50 max. */
    private suspend fun overMaxSnipe(): Snipe {
        val listing = testListing(currentPrice = gbp("52.00"), bidCount = 3, endTimeUtcMillis = TEST_NOW + 30 * MINUTE)
        val snipe = (repository.addFromListing(listing, gbp("50.00"), leadSeconds = 6) as AddResult.Added).snipe
        assertEquals(SnipeStatus.PRICE_OVER_MAX, snipe.status)
        return snipe
    }

    private fun reply(
        snipeId: Long,
        typed: String,
        minimum: Money? = gbp("53.00"),
    ): Intent {
        val intent = NotificationActions.raiseMax(context, snipeId, "GBP", gbp("52.00"), minimum)
        val input = RemoteInput.Builder(NotificationActions.KEY_NEW_MAX).build()
        RemoteInput.addResultsToIntent(
            arrayOf(input),
            intent,
            Bundle().apply {
                putCharSequence(NotificationActions.KEY_NEW_MAX, typed)
            },
        )
        return intent
    }

    private fun alert(snipeId: Long): Notification? =
        shadowOf(manager).getNotification(NotificationIds.priceAlert(snipeId))

    private fun Notification.text(): String? = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

    @Test
    fun aValidReplyRaisesTheMaxReschedulesAndConfirms() =
        runTest {
            val snipe = overMaxSnipe()

            handler.handle(reply(snipe.id, "60"))

            val updated = repository.snipe(snipe.id)!!
            assertEquals(gbp("60.00"), updated.maxBid)
            assertEquals(SnipeStatus.ARMED, updated.status)
            assertEquals(listOf(updated), rescheduled)
            assertEquals(
                "Max raised to GBP 60.00",
                alert(snipe.id)?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            )
        }

    @Test
    fun aReplyBelowTheMinimumKeepsTheMaxAndSaysWhy() =
        runTest {
            val snipe = overMaxSnipe()

            handler.handle(reply(snipe.id, "52.50"))

            assertEquals(gbp("50.00"), repository.snipe(snipe.id)?.maxBid)
            assertTrue(rescheduled.isEmpty())
            val repost = alert(snipe.id)!!
            assertEquals("eBay needs at least GBP 53.00.", repost.text())
            assertEquals(
                "Raise max",
                repost.actions
                    .first()
                    .title
                    .toString(),
            )
        }

    @Test
    fun aReplyWithAnExtraZeroIsRefused() =
        runTest {
            val snipe = overMaxSnipe()

            handler.handle(reply(snipe.id, "600"))

            assertEquals(gbp("50.00"), repository.snipe(snipe.id)?.maxBid)
            assertEquals(
                "That is more than 10 times the current price. Open Kestrel to set it.",
                alert(snipe.id)?.text(),
            )
        }

    @Test
    fun theHigherOfTheAlertAndEngineMinimumsApplies() =
        runTest {
            val snipe = overMaxSnipe()
            engineMinimum = gbp("55.00")

            handler.handle(reply(snipe.id, "54"))

            assertEquals(gbp("50.00"), repository.snipe(snipe.id)?.maxBid)
            assertEquals("eBay needs at least GBP 55.00.", alert(snipe.id)?.text())
        }

    @Test
    fun aReplyForAFinishedSnipeOnlyClearsTheAlert() =
        runTest {
            val snipe = overMaxSnipe()
            notifications.priceOverMax(snipe, gbp("52.00"), gbp("53.00"))
            repository.cancel(snipe.id)

            handler.handle(reply(snipe.id, "60"))

            assertNull(alert(snipe.id))
            assertEquals(gbp("50.00"), repository.snipe(snipe.id)?.maxBid)
        }

    @Test
    fun cancelStopsTheJobsTheAlarmsAndTheNotifications() =
        runTest {
            val snipe = overMaxSnipe()
            val job = Job()
            registry.register(
                JobKey(snipe.id, JobKind.WINDOW),
                SnipeRequest(SnipeAction.WINDOW, snipe.id, null, null),
                job,
            )
            notifications.windowOpened(snipe, snipe.endTimeUtcMillis - 6_000)

            handler.handle(NotificationActions.cancelSnipe(context, snipe.id))

            assertEquals(SnipeStatus.CANCELLED, repository.snipe(snipe.id)?.status)
            assertTrue(job.isCancelled)
            assertEquals(listOf(snipe.id), cancelled)
            assertNull(shadowOf(manager).getNotification(NotificationIds.window(snipe.id)))
        }

    @Test
    fun cancelAfterTheBidWentOutIsRefusedWithAnExplanation() =
        runTest {
            val snipe = overMaxSnipe()
            repository.update(repository.snipe(snipe.id)!!.copy(status = SnipeStatus.BID_SENT))

            handler.handle(NotificationActions.cancelSnipe(context, snipe.id))

            assertEquals(SnipeStatus.BID_SENT, repository.snipe(snipe.id)?.status)
            assertTrue(cancelled.isEmpty())
            val refusal = shadowOf(manager).getNotification(NotificationIds.needsUser(snipe.id))
            assertNotNull(refusal)
            assertEquals("Can't cancel now", refusal.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        }

    @Test
    fun aDismissedLiveUpdateStaysDismissed() =
        runTest {
            val snipe = overMaxSnipe()
            notifications.windowOpened(snipe, snipe.endTimeUtcMillis - 6_000)
            manager.cancel(NotificationIds.window(snipe.id))

            handler.handle(NotificationActions.windowDismissed(context, snipe.id))
            notifications.windowProgress(snipe, app.kestrel.engine.WindowPhase.READY)

            assertNull(shadowOf(manager).getNotification(NotificationIds.window(snipe.id)))
        }

    @Test
    fun intentsWithoutASnipeAreIgnored() =
        runTest {
            handler.handle(Intent(NotificationActions.ACTION_CANCEL_SNIPE))

            assertTrue(cancelled.isEmpty())
        }
}
