package app.kestrel.ui

import android.app.Application
import android.app.KeyguardManager
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kestrel.platform.AppLinks
import app.kestrel.platform.TestKestrelApp
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Kestrel never shows over the lock screen (DESIGN 5.4 and MF46: no full-screen intents). The hand-off page is signed
 * in to the user's eBay account, so the phone must be unlocked to reach it.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = TestKestrelApp::class)
class MainActivityLockScreenTest {
    @Test
    fun aSnipeLinkOpenedWhileThePhoneIsLockedNeverShowsOverTheKeyguard() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val keyguard = shadowOf(context.getSystemService(KeyguardManager::class.java))
        keyguard.setKeyguardLocked(true)

        ActivityScenario.launch<MainActivity>(AppLinks.openSnipe(context, 5)).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(shadowOf(activity).showWhenLocked)
                assertFalse(shadowOf(activity).turnScreenOn)
            }
            // Back from the background with the phone still locked: still not over the keyguard.
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity -> assertFalse(shadowOf(activity).showWhenLocked) }
        }
    }
}
