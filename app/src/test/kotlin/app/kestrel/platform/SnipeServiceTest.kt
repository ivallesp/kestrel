package app.kestrel.platform

import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kestrel.data.MINUTE
import app.kestrel.data.TEST_NOW
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestKestrelApp::class)
class SnipeServiceTest {
    private val app = ApplicationProvider.getApplicationContext<TestKestrelApp>()
    private val runner get() = app.testContainer.fakeRunner

    private fun request(
        action: SnipeAction,
        snipeId: Long = 1,
    ) = SnipeRequest(action, snipeId, "Canon AE-1", TEST_NOW + 2 * MINUTE)

    private fun start(
        action: SnipeAction,
        snipeId: Long = 1,
    ): ServiceController<SnipeService> =
        Robolectric
            .buildService(
                SnipeService::class.java,
                SnipeService.intent(app, request(action, snipeId)),
            ).create()
            .startCommand(0, 1)

    /** A further start of the running service, as Android delivers it for a second alarm. */
    private fun ServiceController<SnipeService>.startAgain(
        action: SnipeAction,
        snipeId: Long,
        startId: Int,
    ) {
        get().onStartCommand(SnipeService.intent(app, request(action, snipeId)), 0, startId)
    }

    /** Jobs finish on the main looper after a database read on Room's own thread, so wait for both. */
    private fun idleUntil(
        rounds: Int = 200,
        condition: () -> Boolean,
    ) {
        repeat(rounds) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
    }

    /** Lets queued work run when there is nothing specific to wait for. */
    private fun settle() = idleUntil(rounds = 20) { false }

    @Test
    fun aWindowStartsInTheForegroundAtOnceAndStopsWhenItIsDone() {
        runner.holdJobs = true

        val controller = start(SnipeAction.WINDOW)

        val service = controller.get()
        val shadow = shadowOf(service)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE, service.foregroundServiceType)
        assertEquals(NotificationIds.window(1), shadow.lastForegroundNotificationId)
        assertEquals(NotificationChannels.LIVE, shadow.lastForegroundNotification.channelId)
        idleUntil { runner.runs.isNotEmpty() }
        assertEquals(listOf(JobKind.WINDOW to 1L), runner.runs)
        assertFalse(shadow.isStoppedBySelf)
        assertEquals(1, app.testContainer.fakeWakeLocks.held)

        runner.release(JobKind.WINDOW, 1)
        idleUntil { shadow.isStoppedBySelf }

        assertTrue(shadow.isStoppedBySelf)
        assertEquals(0, app.testContainer.fakeWakeLocks.held)
    }

    @Test
    fun theBackupAlarmDoesNothingWhileTheWindowRuns() {
        runner.holdJobs = true
        val controller = start(SnipeAction.WINDOW)
        idleUntil { runner.runs.isNotEmpty() }

        controller.startAgain(SnipeAction.WINDOW_BACKUP, snipeId = 1, startId = 2)
        settle()

        assertEquals(listOf(JobKind.WINDOW to 1L), runner.runs)
        assertEquals(NotificationIds.window(1), shadowOf(controller.get()).lastForegroundNotificationId)
        assertFalse(shadowOf(controller.get()).isStoppedBySelf)
    }

    @Test
    fun aCheckRunsUnderTheQuietNotification() {
        runner.holdJobs = true

        val controller = start(SnipeAction.CHECK)

        val shadow = shadowOf(controller.get())
        assertEquals(NotificationIds.SERVICE, shadow.lastForegroundNotificationId)
        assertEquals(NotificationChannels.BACKGROUND, shadow.lastForegroundNotification.channelId)
        idleUntil { runner.runs.isNotEmpty() }
        assertEquals(listOf(JobKind.CHECK to 1L), runner.runs)
    }

    @Test
    fun whenTheFirstWindowEndsTheNextOneTakesOverTheForegroundNotification() {
        runner.holdJobs = true
        val controller = start(SnipeAction.WINDOW, snipeId = 1)
        controller.startAgain(SnipeAction.WINDOW, snipeId = 2, startId = 2)
        idleUntil { runner.runs.size == 2 }
        assertEquals(NotificationIds.window(1), shadowOf(controller.get()).lastForegroundNotificationId)

        runner.release(JobKind.WINDOW, 1)
        idleUntil { shadowOf(controller.get()).lastForegroundNotificationId == NotificationIds.window(2) }

        assertEquals(NotificationIds.window(2), shadowOf(controller.get()).lastForegroundNotificationId)
        assertFalse(shadowOf(controller.get()).isStoppedBySelf)
    }

    @Test
    fun aLiveUpdateTheUserSwipedAwayIsNotBroughtBackByTheNextJob() {
        runner.holdJobs = true
        val controller = start(SnipeAction.WINDOW, snipeId = 1)
        idleUntil { runner.runs.isNotEmpty() }
        app.testContainer.notifications.onWindowDismissed(1)

        controller.startAgain(SnipeAction.CHECK, snipeId = 2, startId = 2)

        assertEquals(NotificationIds.SERVICE, shadowOf(controller.get()).lastForegroundNotificationId)
    }

    @Test
    fun ifAndroidRefusesTheForegroundStartTheUserIsAskedToTap() {
        val controller =
            Robolectric
                .buildService(
                    SnipeService::class.java,
                    SnipeService.intent(app, request(SnipeAction.WINDOW)),
                ).create()
        shadowOf(controller.get()).setThrowInStartForeground(ForegroundServiceStartNotAllowedException("refused"))

        controller.startCommand(0, 1)
        settle()

        assertTrue(runner.runs.isEmpty())
        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
        val tap =
            shadowOf(
                app.getSystemService(NotificationManager::class.java),
            ).getNotification(NotificationIds.tapToArm(1))
        assertNotNull(tap)
    }

    @Test
    fun aStartWithoutAValidRequestStillEntersTheForegroundAndThenStops() {
        val controller = Robolectric.buildService(SnipeService::class.java).create().startCommand(0, 1)

        assertEquals(NotificationIds.SERVICE, shadowOf(controller.get()).lastForegroundNotificationId)
        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
        assertTrue(runner.runs.isEmpty())
    }
}
