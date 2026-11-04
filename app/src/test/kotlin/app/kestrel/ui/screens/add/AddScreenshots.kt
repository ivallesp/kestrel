package app.kestrel.ui.screens.add

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import app.kestrel.ui.harness.PhoneInsets
import app.kestrel.ui.harness.ScreenshotTest
import org.junit.Test

/**
 * S2 in every state of DESIGN S2 (and Q30's S2 set), light, with the main ones dark and S2 at 1.3. Every state
 * goes through [AddPlanner], as the app's does.
 */
class AddScreenshots : ScreenshotTest() {
    private fun s2(
        name: String,
        dark: Boolean,
        state: AddUiState,
        fontScale: Float = 1f,
        canPaste: Boolean = false,
        scrolled: Boolean = false,
        wholeScreen: Boolean = false,
    ) = capture(name, darkTheme = dark, time = "18:09", fontScale = fontScale, wholeScreen = wholeScreen) {
        S2(state, canPaste, scrolled)
    }

    @Composable
    private fun S2(
        state: AddUiState,
        canPaste: Boolean = false,
        scrolled: Boolean = false,
    ) {
        val scroll = remember { ScrollState(0) }
        // Scrolled to the very end once the page is laid out, as a thumb would leave it.
        if (scrolled) LaunchedEffect(scroll) { snapshotFlow { scroll.maxValue }.collect { scroll.scrollTo(it) } }
        AddScreen(state = state, windowInsets = PhoneInsets, canPaste = canPaste, scrollState = scroll)
    }

    // DESIGN Q30: S2-add, S2-link, S2-fetching, S2-empty-max, S2-too-low, both themes.

    @Test fun addLight() = s2("S2-add", dark = false, AddFixtures.ready())

    @Test fun addDark() = s2("S2-add", dark = true, AddFixtures.ready())

    @Test fun addScrolledLight() = s2("S2-add-scrolled", dark = false, AddFixtures.ready(), scrolled = true)

    @Test fun addLargeText() = s2("S2-add", dark = false, AddFixtures.ready(), fontScale = 1.3f)

    @Test fun addScrolledLargeText() =
        s2("S2-add-scrolled", dark = false, AddFixtures.ready(), fontScale = 1.3f, scrolled = true)

    @Test fun linkLight() = s2("S2-link", dark = false, AddFixtures.linkEntry)

    @Test fun linkDark() = s2("S2-link", dark = true, AddFixtures.linkEntry)

    @Test fun linkWithPasteLight() = s2("S2-link-paste", dark = false, AddFixtures.linkEntry, canPaste = true)

    @Test fun fetchingLight() = s2("S2-fetching", dark = false, AddFixtures.fetching)

    @Test fun fetchingDark() = s2("S2-fetching", dark = true, AddFixtures.fetching)

    /** With animations off the loading indicator is a still hourglass with the same words. */
    @Test fun fetchingReducedMotionLight() =
        capture("S2-fetching-reduced-motion", darkTheme = false, time = "18:09", reduceMotion = true) {
            S2(AddFixtures.fetching)
        }

    @Test fun emptyMaxLight() = s2("S2-empty-max", dark = false, AddFixtures.emptyMax)

    @Test fun emptyMaxDark() = s2("S2-empty-max", dark = true, AddFixtures.emptyMax)

    @Test fun tooLowLight() = s2("S2-too-low", dark = false, AddFixtures.tooLow)

    @Test fun tooLowDark() = s2("S2-too-low", dark = true, AddFixtures.tooLow)

    // The other S2 states.

    @Test fun fetchFailedLight() = s2("S2-fetch-failed", dark = false, AddFixtures.fetchFailed)

    @Test fun fetchFailedDark() = s2("S2-fetch-failed", dark = true, AddFixtures.fetchFailed)

    @Test fun notAListingLight() = s2("S2-not-a-listing", dark = false, AddFixtures.notAListing)

    @Test fun notAnAuctionLight() = s2("S2-not-auction", dark = false, AddFixtures.notAnAuction)

    @Test fun endedLight() = s2("S2-ended", dark = false, AddFixtures.ended)

    @Test fun alreadySnipedLight() = s2("S2-already-sniped", dark = false, AddFixtures.alreadySniped)

    @Test fun signedOutLight() = s2("S2-signed-out", dark = false, AddFixtures.signedOut)

    @Test fun signedOutDark() = s2("S2-signed-out", dark = true, AddFixtures.signedOut)

    @Test fun otherCurrencyLight() = s2("S2-other-currency", dark = false, AddFixtures.otherCurrency)

    @Test fun extendedBiddingLight() = s2("S2-extended", dark = false, AddFixtures.extendedBidding, scrolled = true)

    @Test fun groupChosenLight() = s2("S2-group-chosen", dark = false, AddFixtures.groupChosen, scrolled = true)

    @Test fun wordsCheckLight() = s2("S2-words-check", dark = false, AddFixtures.wordsCheck, wholeScreen = true)

    @Test fun wordsCheckDark() = s2("S2-words-check", dark = true, AddFixtures.wordsCheck, wholeScreen = true)

    // The group picker sheet over S2.

    @Test fun groupPickerLight() = picker("group-picker-sheet", dark = false, AddFixtures.picker())

    @Test fun groupPickerDark() = picker("group-picker-sheet", dark = true, AddFixtures.picker())

    @Test fun groupPickerNewLight() =
        picker("group-picker-sheet-new", dark = false, AddFixtures.picker(GroupPick.New, newName = "Sony bodies"))

    private fun picker(
        name: String,
        dark: Boolean,
        picker: GroupPickerState,
    ) = capture(name, darkTheme = dark, time = "18:09") {
        SheetPreview(title = GROUP_PICKER_TITLE, backdrop = { S2(AddFixtures.ready(), scrolled = true) }) {
            GroupPickerBody(state = picker, onSelect = {}, onDone = {})
        }
    }
}
