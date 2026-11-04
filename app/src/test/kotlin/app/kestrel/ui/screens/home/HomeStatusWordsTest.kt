package app.kestrel.ui.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kestrel.model.StatusFamily
import app.kestrel.ui.harness.ScreenshotSurface
import app.kestrel.ui.model.chipText
import app.kestrel.ui.model.spokenLabel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * DESIGN Q19: every status renders in a row and, when it can lead the list, in the hero, with the words of the
 * status model (6.1) and none of its own.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel9a)
class HomeStatusWordsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val heroFamilies = setOf(StatusFamily.ARMED, StatusFamily.BIDDING, StatusFamily.ATTENTION)

    @Test
    fun everyStatusSpeaksTheModelsWordsInARowAndInTheHero() {
        var moment by mutableStateOf(HomeFixtures.statusMoments.first())
        composeRule.setContent {
            ScreenshotSurface(darkTheme = false, time = "18:08", reduceMotion = true) {
                val lot = HomeFixtures.lotOf(moment)
                Column {
                    LotRow(lot, moment.now, index = 0, count = 1, actions = HomeActions())
                    if (lot.snipe.maxBid != null && lot.status.family in heroFamilies) {
                        HeroCard(lot, moment.now, index = 0, count = 1, actions = HomeActions())
                    }
                }
            }
        }
        HomeFixtures.statusMoments.forEach { next ->
            moment = next
            composeRule.waitForIdle()
            val lot = HomeFixtures.lotOf(next)
            val spoken = HomeText.spokenRow(lot, next.now)
            assertTrue(spoken, spoken.contains(lot.status.spokenLabel(next.now)))
            composeRule.onNodeWithContentDescription(spoken).assertExists()
            if (lot.snipe.maxBid != null && lot.status.family in heroFamilies) {
                composeRule.onNodeWithContentDescription("Status: ${lot.status.chipText()}").assertExists()
            }
        }
    }
}
