package app.kestrel.platform

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kestrel.data.TEST_NOW
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SnipeRequestTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun aRequestSurvivesTheIntent() {
        for (action in SnipeAction.entries) {
            val request = SnipeRequest(action, 42, "Canon AE-1", TEST_NOW)
            assertEquals(request, SnipeRequest.from(request.putInto(Intent())))
        }
        val bare = SnipeRequest(SnipeAction.CHECK, 42, null, null)
        assertEquals(bare, SnipeRequest.from(bare.putInto(Intent())))
    }

    @Test
    fun strayIntentsAreNotRequests() {
        assertNull(SnipeRequest.from(Intent()))
        assertNull(SnipeRequest.from(Intent("app.kestrel.action.UNKNOWN")))
        assertNull(SnipeRequest.from(Intent("app.kestrel.action.WINDOW")))
        assertNull(SnipeRequest.from(Intent("app.kestrel.action.WINDOW").putExtra("app.kestrel.extra.SNIPE_ID", -3L)))
    }

    @Test
    fun alarmsOfDifferentSnipesAndActionsNeverShareAPendingIntent() {
        val window = AlarmIntents.intent(context, SnipeRequest(SnipeAction.WINDOW, 1, null, null))
        val backup = AlarmIntents.intent(context, SnipeRequest(SnipeAction.WINDOW_BACKUP, 1, null, null))
        val otherSnipe = AlarmIntents.intent(context, SnipeRequest(SnipeAction.WINDOW, 2, null, null))

        assertNotEquals(window.data, backup.data)
        assertNotEquals(window.data, otherSnipe.data)
        assertNotEquals(NotificationIds.window(1), NotificationIds.window(2))
        assertNotEquals(NotificationIds.window(1), NotificationIds.priceAlert(1))
    }

    @Test
    fun appLinksCarryTheSnipeToTheActivity() {
        val open = AppLinks.openSnipe(context, 9)

        assertEquals(9L, AppLinks.snipeIdFrom(open))
        assertNull(AppLinks.snipeIdFrom(Intent()))
        assertNull(AppLinks.snipeIdFrom(null))
    }
}
