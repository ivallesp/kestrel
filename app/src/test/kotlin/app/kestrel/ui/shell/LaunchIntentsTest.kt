package app.kestrel.ui.shell

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kestrel.platform.AppLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaunchIntentsTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun aNotificationLinkOpensTheSnipe() {
        assertEquals(Destination.Lot(42), LaunchIntents.destinationOf(AppLinks.openSnipe(context, 42)))
    }

    @Test
    fun aSharedTextOpensAddWithTheText() {
        val share =
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "  Look at this on eBay https://www.ebay.co.uk/itm/127504318862  ")
        assertEquals(
            Destination.Add("Look at this on eBay https://www.ebay.co.uk/itm/127504318862"),
            LaunchIntents.destinationOf(share),
        )
        val empty = Intent(Intent.ACTION_SEND).setType("text/plain")
        assertEquals(Destination.Add(null), LaunchIntents.destinationOf(empty))
    }

    @Test
    fun theLauncherAndOtherSharesAskForNothing() {
        assertNull(LaunchIntents.destinationOf(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)))
        assertNull(LaunchIntents.destinationOf(Intent(Intent.ACTION_SEND).setType("image/png")))
        assertNull(LaunchIntents.destinationOf(null))
    }

    @Test
    fun theFirstLaunchOpensOnboardingAndLaterLaunchesStackOnHome() {
        assertEquals(
            listOf(Destination.Onboarding),
            LaunchIntents.startStack(onboardingDone = false, asked = Destination.Lot(1)),
        )
        assertEquals(listOf(Destination.Home), LaunchIntents.startStack(onboardingDone = true, asked = null))
        assertEquals(
            listOf(Destination.Home, Destination.Lot(1)),
            LaunchIntents.startStack(onboardingDone = true, asked = Destination.Lot(1)),
        )
    }
}
