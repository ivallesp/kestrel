package app.kestrel.platform

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.PowerManager
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.kestrel.data.AddResult
import app.kestrel.data.ArmedSnipe
import app.kestrel.data.ArmedSnipesCopy
import app.kestrel.data.HOUR
import app.kestrel.data.KestrelDatabase
import app.kestrel.data.MINUTE
import app.kestrel.data.SnipeRepository
import app.kestrel.data.TEST_NOW
import app.kestrel.data.gbp
import app.kestrel.data.testFormatMoney
import app.kestrel.data.testListing
import app.kestrel.model.Snipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.File

@RunWith(AndroidJUnit4::class)
class SystemEventsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val clock = FakeClock(now = TEST_NOW)
    private lateinit var database: KestrelDatabase
    private lateinit var repository: SnipeRepository
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

    @Before
    fun setUp() {
        NotificationChannels.create(context)
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        shadowOf(
            context.getSystemService(PowerManager::class.java),
        ).setIgnoringBatteryOptimizations(context.packageName, true)
        database = KestrelDatabase.inMemory(context)
        repository =
            SnipeRepository(
                database,
                ArmedSnipesCopy(
                    File(context.noBackupFilesDir, "armed-${System.nanoTime()}.json"),
                ),
                {
                    clock.now
                },
                ::testFormatMoney,
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun scheduler() =
        SnipeScheduler(
            context = context,
            clock = clock,
            systemUtcMillis = { clock.now },
            planner = SchedulePlanner(FakePolicy()),
            pendingSnipes = { repository.pendingSnipes() },
            permissions = AndroidAlarmPermissions(context),
            workManager = { WorkManager.getInstance(context) },
            problemNotifier = notifications,
            isWindowRunning = { false },
        )

    private fun windowAlarmsFor(snipeId: Long) =
        shadowOf(alarmManager).scheduledAlarms.filter { alarm ->
            val request = SnipeRequest.from(alarm.savedIntent)
            request?.snipeId == snipeId && request.action == SnipeAction.WINDOW
        }

    private suspend fun arm(
        itemId: String,
        endTimeUtcMillis: Long,
    ): Snipe {
        val listing = testListing(itemId = itemId, endTimeUtcMillis = endTimeUtcMillis)
        return (repository.addFromListing(listing, gbp("48.00"), leadSeconds = 6) as AddResult.Added).snipe
    }

    @Test
    fun afterABootEveryPendingSnipeIsRearmedAndTheUnlockNoticeGoes() =
        runTest {
            val first = arm(itemId = "1", endTimeUtcMillis = TEST_NOW + 40 * MINUTE)
            val second = arm(itemId = "2", endTimeUtcMillis = TEST_NOW + 3 * HOUR)
            val finished = arm(itemId = "3", endTimeUtcMillis = TEST_NOW + 50 * MINUTE)
            repository.cancel(finished.id)
            notifications.showUnlockToArm(2, TEST_NOW + HOUR)
            val scheduler = scheduler()
            var purgedBefore: Long? = null
            val handler =
                SystemEventHandler(
                    rescheduleAll = { scheduler.rescheduleAll() },
                    purgeDeletedBefore = { purgedBefore = it },
                    cancelUnlockNotice = notifications::cancelUnlockToArm,
                    finishLater = { throw AssertionError("everything fitted in the budget") },
                    nowUtcMillis = { clock.now },
                )

            // Real time, not the test clock: the database answers on its own thread while the budget runs.
            withContext(Dispatchers.IO) { handler.onRescheduleEvent(Intent.ACTION_BOOT_COMPLETED) }

            assertEquals(1, windowAlarmsFor(first.id).size)
            assertEquals(1, windowAlarmsFor(second.id).size)
            assertTrue(windowAlarmsFor(finished.id).isEmpty())
            assertNull(shadowOf(manager).getNotification(NotificationIds.UNLOCK_TO_ARM))
            assertEquals(TEST_NOW - 24 * HOUR, purgedBefore)
        }

    @Test
    fun aTimeChangeAlsoReschedulesButKeepsTheUnlockNotice() =
        runTest {
            var rescheduled = 0
            notifications.showUnlockToArm(1, TEST_NOW + HOUR)
            val handler =
                SystemEventHandler(
                    rescheduleAll = { rescheduled++ },
                    purgeDeletedBefore = {},
                    cancelUnlockNotice = notifications::cancelUnlockToArm,
                    finishLater = {},
                    nowUtcMillis = { clock.now },
                )

            handler.onRescheduleEvent(Intent.ACTION_TIME_CHANGED)

            assertEquals(1, rescheduled)
            assertNotNull(shadowOf(manager).getNotification(NotificationIds.UNLOCK_TO_ARM))
        }

    @Test
    fun aFailedRescheduleIsHandedToWorkManager() =
        runTest {
            var handedOver = false
            var purged = false
            val handler =
                SystemEventHandler(
                    rescheduleAll = { throw IllegalStateException("database locked") },
                    purgeDeletedBefore = { purged = true },
                    cancelUnlockNotice = {},
                    finishLater = { handedOver = true },
                    nowUtcMillis = { clock.now },
                )

            handler.onRescheduleEvent(Intent.ACTION_MY_PACKAGE_REPLACED)

            assertTrue(handedOver)
            assertFalse("the purge can wait", purged)
        }

    @Test
    fun aRescheduleThatOverrunsTheBroadcastBudgetIsHandedToWorkManager() =
        runTest {
            var handedOver = false
            val handler =
                SystemEventHandler(
                    rescheduleAll = { delay(60_000) },
                    purgeDeletedBefore = {},
                    cancelUnlockNotice = {},
                    finishLater = { handedOver = true },
                    nowUtcMillis = { clock.now },
                    budgetMillis = 7_000,
                )

            handler.onRescheduleEvent(Intent.ACTION_TIME_CHANGED)

            assertTrue(handedOver)
        }

    @Test
    fun theWorkManagerFallbackIsQueuedAsUniqueExpeditedWork() {
        RescheduleWorker.enqueue(context)

        val queued = WorkManager.getInstance(context).getWorkInfosForUniqueWork(RescheduleWorker.UNIQUE_NAME).get()
        assertEquals(1, queued.size)
    }

    @Test
    fun beforeTheFirstUnlockTheNoticeCountsOnlyFutureSnipes() {
        val copy = ArmedSnipesCopy(File(context.noBackupFilesDir, "armed-${System.nanoTime()}.json"))
        copy.write(
            listOf(
                ArmedSnipe(1, "Ended already", TEST_NOW - MINUTE),
                ArmedSnipe(2, "Later", TEST_NOW + 3 * HOUR),
                ArmedSnipe(3, "Sooner", TEST_NOW + HOUR),
            ),
        )

        LockedBootNotice(copy, notifications, systemUtcMillis = { TEST_NOW }).post()

        val notice = shadowOf(manager).getNotification(NotificationIds.UNLOCK_TO_ARM)!!
        assertEquals(
            "To arm 2 snipes. The next ends Sun 22:00.",
            notice.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
        )
    }

    @Test
    fun withNothingArmedThereIsNoNotice() {
        val copy = ArmedSnipesCopy(File(context.noBackupFilesDir, "armed-${System.nanoTime()}.json"))

        LockedBootNotice(copy, notifications, systemUtcMillis = { TEST_NOW }).post()

        assertNull(shadowOf(manager).getNotification(NotificationIds.UNLOCK_TO_ARM))
    }

    @Test
    fun theReceiverPostsTheNoticeFromDeviceProtectedStorageWhileLocked() {
        shadowOf(context.getSystemService(UserManager::class.java)).setUserUnlocked(false)
        ArmedSnipesCopy.inDeviceProtectedStorage(context).write(
            listOf(
                ArmedSnipe(
                    1,
                    "Canon AE-1",
                    System.currentTimeMillis() + HOUR,
                ),
            ),
        )

        SystemEventReceiver().onReceive(context, Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED))

        val notice = shadowOf(manager).getNotification(NotificationIds.UNLOCK_TO_ARM)
        assertNotNull(notice)
        assertEquals("Unlock your phone", notice.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
    }

    @Test
    fun theReceiverIgnoresALateLockedBootOnceUnlocked() {
        shadowOf(context.getSystemService(UserManager::class.java)).setUserUnlocked(true)
        ArmedSnipesCopy.inDeviceProtectedStorage(context).write(
            listOf(
                ArmedSnipe(
                    1,
                    "Canon AE-1",
                    System.currentTimeMillis() + HOUR,
                ),
            ),
        )

        SystemEventReceiver().onReceive(context, Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED))

        assertNull(shadowOf(manager).getNotification(NotificationIds.UNLOCK_TO_ARM))
    }
}
