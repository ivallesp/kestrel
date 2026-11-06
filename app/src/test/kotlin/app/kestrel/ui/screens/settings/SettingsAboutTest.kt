package app.kestrel.ui.screens.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kestrel.ui.harness.PhoneInsets
import app.kestrel.ui.harness.ScreenshotSurface
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** About: the win celebration's preview and the coffee placeholder are one tap away. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel9a)
class SettingsAboutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var celebrations = 0
    private var coffees = 0
    private val sounds = mutableListOf<Boolean>()

    private val actions =
        object : SettingsActions by NoSettingsActions {
            override fun openCelebration() {
                celebrations++
            }

            override fun buyMeACoffee() {
                coffees++
            }

            override fun setCelebrationSound(on: Boolean) {
                sounds += on
            }
        }

    @Test
    fun aboutOpensTheCelebrationAndTheCoffeePlaceholder() {
        composeRule.setContent {
            ScreenshotSurface(darkTheme = false, time = "18:08", reduceMotion = true) {
                SettingsScreen(SettingsFixtures.signedIn, actions, windowInsets = PhoneInsets)
            }
        }
        composeRule.onNodeWithText("What plays the first time you open a win. Nothing is bid.").performScrollTo()
        composeRule.onNodeWithText("Watch the win celebration").performClick()
        composeRule.onNodeWithText("Kestrel has no ads and no fees. Opens ko-fi.com/ivallesp.").performScrollTo()
        composeRule.onNodeWithText("Buy me a coffee").performClick()
        assertEquals(1, celebrations)
        assertEquals(1, coffees)
    }

    @Test
    fun appearanceTurnsTheCelebrationSoundOff() {
        composeRule.setContent {
            ScreenshotSurface(darkTheme = false, time = "18:08", reduceMotion = true) {
                SettingsScreen(SettingsFixtures.signedIn, actions, windowInsets = PhoneInsets)
            }
        }
        composeRule.onNodeWithText("Sound for a win").performScrollTo().performClick()
        assertEquals(listOf(false), sounds)
    }
}
