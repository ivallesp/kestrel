package app.kestrel.platform

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kestrel.engine.BASE_UTC
import app.kestrel.engine.BidPageFactory
import app.kestrel.engine.END_UTC
import app.kestrel.engine.FakeAuction
import app.kestrel.engine.FakeStore
import app.kestrel.engine.FakeTimeSources
import app.kestrel.engine.ScriptedBidPage
import app.kestrel.engine.SnipeEngine
import app.kestrel.engine.TEST_MINIMUM_RULE
import app.kestrel.engine.TrueTime
import app.kestrel.engine.VirtualClock
import app.kestrel.engine.testSnipe
import app.kestrel.model.PageKind
import app.kestrel.model.SnipeStatus
import app.kestrel.ui.shell.Destination
import app.kestrel.ui.shell.LaunchIntents
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * A window that stops before its bid (a captcha at T-90 s) hands the auction to the user (ARCHITECTURE rule 4): the
 * alert must outlive the result notification the window posts as it ends, and lead to eBay's page.
 */
@RunWith(AndroidJUnit4::class)
class WindowHandOffAlertTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        NotificationChannels.create(context)
    }

    private fun posted(id: Int): Notification? = shadowOf(manager).getNotification(id)

    @Test
    fun aCaptchaInTheWindowLeavesAnAlertToBidOnEbayBeforeTheEnd() =
        runTest {
            val store = FakeStore().apply { put(testSnipe()) }
            val auction = FakeAuction(testScheduler)
            val page = ScriptedBidPage(testScheduler, auction).apply { pageKinds += PageKind.CAPTCHA }
            val notifications =
                SnipeNotifications(
                    context,
                    VirtualClock(testScheduler),
                    { BASE_UTC + testScheduler.currentTime },
                    testTexts(),
                    FakePhotos(),
                    TestScope(StandardTestDispatcher(testScheduler)),
                )
            val engine =
                SnipeEngine(
                    store,
                    notifications,
                    TrueTime(FakeTimeSources(testScheduler)),
                    BidPageFactory { page },
                    TEST_MINIMUM_RULE,
                )

            engine.runWindow(1)

            assertEquals(SnipeStatus.FAILED, store.snipes.getValue(1).status)
            assertNotNull("the result is there", posted(NotificationIds.result(1)))
            val alert = posted(NotificationIds.needsUser(1))
            assertNotNull("and the hand-off alert is still there", alert)
            assertEquals(NotificationChannels.ATTENTION, alert!!.channelId)
            assertEquals(
                "Kestrel did not bid. You can still bid on eBay yourself before ${testTexts().clockWithSeconds(
                    END_UTC,
                )}.",
                alert.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            )
            val opens = shadowOf(alert.contentIntent).savedIntent
            assertEquals(Destination.HandOff(1), LaunchIntents.destinationOf(opens))
            assertEquals(listOf("Open eBay page"), alert.actions.map { it.title.toString() })
        }
}
