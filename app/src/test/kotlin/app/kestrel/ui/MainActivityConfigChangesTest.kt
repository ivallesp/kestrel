package app.kestrel.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The one activity handles the configuration changes a phone makes on its own (rotation, a scheduled dark theme, a
 * font size), so they never recreate it: the sign-in page's WebView would load eBay's first sign-in page again and
 * lose what the user typed and the 2-step code page. Compose lays the screens out again by itself.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityConfigChangesTest {
    @Test
    fun rotationThemeAndFontChangesDoNotRecreateTheActivity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        val handled =
            ActivityInfo.CONFIG_ORIENTATION or ActivityInfo.CONFIG_SCREEN_SIZE or ActivityInfo.CONFIG_SCREEN_LAYOUT or
                ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE or ActivityInfo.CONFIG_KEYBOARD_HIDDEN or
                ActivityInfo.CONFIG_UI_MODE or ActivityInfo.CONFIG_FONT_SCALE

        assertEquals(handled, info.configChanges and handled)
    }
}
