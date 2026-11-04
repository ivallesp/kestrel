package app.kestrel.platform

import android.app.Application
import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kestrel.data.TEST_NOW
import app.kestrel.data.testSnipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class AlarmReceiverTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun anAlarmStartsTheServiceWithItsActionAndSnipe() {
        val alarm = AlarmIntents.intent(context, SnipeRequest.of(SnipeAction.WINDOW, testSnipe(id = 12)))

        AlarmReceiver().onReceive(context, alarm)

        val started = shadowOf(context).nextStartedService
        assertNotNull(started)
        assertEquals(SnipeService::class.java.name, started.component?.className)
        val request = SnipeRequest.from(started)!!
        assertEquals(SnipeAction.WINDOW, request.action)
        assertEquals(12L, request.snipeId)
        assertEquals("Canon AE-1 Program", request.title)
    }

    @Test
    fun aMalformedIntentStartsNothing() {
        AlarmReceiver().onReceive(
            context,
            Intent(context, AlarmReceiver::class.java).setAction("app.kestrel.action.SOMETHING_ELSE"),
        )
        AlarmReceiver().onReceive(
            context,
            Intent(context, AlarmReceiver::class.java).setAction("app.kestrel.action.WINDOW"),
        )

        assertNull(shadowOf(context).nextStartedService)
    }

    @Test
    fun whenAndroidRefusesTheStartTheUserGetsATapToArmNotification() {
        val request = SnipeRequest(SnipeAction.WINDOW_BACKUP, 12, "Canon AE-1", TEST_NOW)
        val notifications = context.kestrelContainer.notifications

        val started =
            AlarmReceiver.forwardToService(
                context = context,
                request = request,
                startService = { throw ForegroundServiceStartNotAllowedException("not allowed") },
                onBlocked = notifications::showTapToArm,
            )

        assertFalse(started)
        val tap =
            shadowOf(
                context.getSystemService(NotificationManager::class.java),
            ).getNotification(NotificationIds.tapToArm(12))
        assertNotNull(tap)
        assertEquals(NotificationChannels.ATTENTION, tap.channelId)
        assertTrue(shadowOf(tap.contentIntent).isForegroundService)
        assertEquals(SnipeAction.WINDOW_BACKUP, SnipeRequest.from(shadowOf(tap.contentIntent).savedIntent)?.action)
    }

    @Test
    fun otherStartFailuresAlsoFallBackToTheNotification() {
        val blocked = mutableListOf<SnipeRequest>()
        val request = SnipeRequest(SnipeAction.WINDOW, 3, null, null)

        val record: (SnipeRequest) -> Unit = { blocked += it }

        AlarmReceiver.forwardToService(context, request, { throw IllegalStateException("background start") }, record)
        AlarmReceiver.forwardToService(context, request, { throw SecurityException("missing permission") }, record)

        assertEquals(listOf(request, request), blocked)
    }
}
