package app.kestrel.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.format.secondsText
import app.kestrel.ui.model.chipText
import app.kestrel.ui.theme.KestrelTheme
import app.kestrel.ui.theme.StatusTone
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Touch targets (DESIGN Q13), semantics and the max field's input rules. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel9a)
class ComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun everyClickableIsAtLeast48DpSquare() {
        var selected = 1
        composeRule.setContent {
            KestrelTheme(darkTheme = false, reduceMotion = true) {
                Column {
                    PrimaryButton("Arm snipe", onClick = {})
                    StateTonalButton(StatusTone.Attention, "Sign in", onClick = {})
                    ConnectedToggleRow(
                        options = listOf(3L, 6L, 10L).map(::secondsText),
                        selectedIndex = 1,
                        onSelect = { selected = it },
                    )
                }
            }
        }
        listOf("Arm snipe", "Sign in", secondsText(3), secondsText(6), secondsText(10)).forEach { label ->
            val touch = composeRule.onNode(hasText(label) and hasClickAction()).fetchSemanticsNode().touchBoundsInRoot
            val minimum = with(composeRule.density) { 48.dp.toPx() }
            assertTrue(
                "$label is ${touch.width} x ${touch.height} px",
                touch.width >= minimum && touch.height >= minimum,
            )
        }
        composeRule.onNodeWithText("Arm snipe").assertHeightIsAtLeast(56.dp)
        composeRule.onNodeWithText(secondsText(10)).performClick()
        assertEquals(2, selected)
    }

    @Test
    fun theStateChipSpeaksItsStatus() {
        val status = CatalogueFixture.canonFinalMinute.status
        composeRule.setContent {
            KestrelTheme(darkTheme = false, reduceMotion = true) { StateChip(status.tone, status.chipText()) }
        }
        composeRule
            .onNodeWithContentDescription(
                "Status: Final minute",
            ).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
    }

    @Test
    fun theMaxFieldSaysWhatItIsAndWhenItIsTooLow() {
        composeRule.setContent {
            KestrelTheme(darkTheme = false, reduceMotion = true) {
                MaxField(TextFieldState("60.00"), minimum = "£66.00", error = "Too low: eBay needs at least £66.00")
            }
        }
        composeRule
            .onNodeWithContentDescription("Your max bid")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Too low: eBay needs at least £66.00"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "60.00"))
    }

    @Test
    fun theMaxFilterKeepsDigitsAndOnePointWithTwoDecimals() {
        assertEquals("1234.56", MaxInput.filter("£1,234.567"))
        assertEquals("90.37", MaxInput.filter("90.3.7"))
        assertEquals("", MaxInput.filter("£"))
        assertEquals("90.00", MaxInput.formatOnBlur("90"))
        assertEquals("90.30", MaxInput.formatOnBlur("90.3"))
        assertEquals("0.50", MaxInput.formatOnBlur(".5"))
        assertEquals("", MaxInput.formatOnBlur(""))
    }

    @Test
    fun theMoneyBlockIsOneSentenceForTalkBack() {
        val figures =
            lotMoneyFigures(
                current = CatalogueFixture.gbp("88.00"),
                bidCount = 14,
                max = CatalogueFixture.gbp("120.00"),
                postage = CatalogueFixture.gbp("6.50"),
                tone = StatusTone.Armed,
            )
        assertEquals(
            "Current bid £88.00, 14 bids. Your max £120.00, at most £126.50 with postage. £32.00 below your max.",
            figures.spoken,
        )
        assertEquals("At most £126.50 with postage", figures.note)
        assertEquals("£32.00 below your max", figures.startCaption?.text)
        assertEquals("Postage £6.50", figures.endCaption?.text)
    }

    @Test
    fun overTheMaxTheCaptionTurnsAttention() {
        val figures =
            lotMoneyFigures(
                current = CatalogueFixture.gbp("2150.00"),
                bidCount = 31,
                max = CatalogueFixture.gbp("1900.00"),
                postage = CatalogueFixture.gbp("12.95"),
                tone = StatusTone.Attention,
            )
        assertEquals(MoneyCaption("£250.00 over your max", overMax = true), figures.startCaption)
        assertEquals(StatusTone.Attention, figures.headroom?.tone)
    }

    @Test
    fun aRisenPriceTakesTheStartCaption() {
        val figures =
            lotMoneyFigures(
                current = CatalogueFixture.gbp("96.00"),
                bidCount = 16,
                max = CatalogueFixture.gbp("120.00"),
                postage = CatalogueFixture.gbp("6.50"),
                tone = StatusTone.Bidding,
                previousPrice = CatalogueFixture.gbp("88.00"),
            )
        assertEquals("Up £8.00 from £88.00", figures.startCaption?.text)
        assertEquals("£24.00 below your max", figures.endCaption?.text)
    }
}
