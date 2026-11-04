package app.kestrel.ui.screens.handoff

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.fixture.LotMoment
import app.kestrel.ui.harness.PhoneInsets
import app.kestrel.ui.harness.ScreenshotTest
import app.kestrel.ui.screens.signinbrowser.BrowserSheet
import org.junit.Test

/**
 * The Canon's listing handed to the user for eBay's check (18:12:05, DESIGN 3.13 "Attention (captcha)"). A blank
 * white page stands in for the WebView: tests never load eBay.
 */
class HandOffScreenshots : ScreenshotTest() {
    @Composable
    private fun HandOff(state: HandOffUiState) {
        HandOffScreen(
            state = state,
            onClose = {},
            onDone = {},
            onSheetAction = {},
            onSheetDismiss = {},
            windowInsets = PhoneInsets,
            page = { modifier -> Box(modifier.background(Color.White)) },
        )
    }

    private fun state(
        moment: LotMoment = CatalogueFixture.canonCaptcha,
        progress: Float? = 0.6f,
        sheet: BrowserSheet? = null,
    ) = HandOffUiState(
        now = moment.now,
        snipe = moment.snipe,
        status = moment.status,
        gone = false,
        page = HandOffPage(host = moment.snipe.ref.site.host, progress = progress, sheet = sheet, generation = 0),
    )

    @Test
    fun captchaLight() = capture("handoff", darkTheme = false, time = "18:12") { HandOff(state()) }

    @Test
    fun captchaDark() = capture("handoff", darkTheme = true, time = "18:12") { HandOff(state()) }

    @Test
    fun phoneCheckLight() =
        capture("handoff-phone-check", darkTheme = false, time = "18:12") {
            HandOff(state(CatalogueFixture.canonPhoneCheck, progress = null))
        }

    @Test
    fun captchaLargeText() =
        capture("handoff", darkTheme = false, time = "18:12", fontScale = 1.3f) { HandOff(state(progress = null)) }

    @Test
    fun offlineLight() =
        capture("handoff-offline", darkTheme = false, time = "18:12", wholeScreen = true, online = false) {
            HandOff(state(progress = null, sheet = BrowserSheet.Offline))
        }

    @Test
    fun goneLight() =
        capture("handoff-gone", darkTheme = false, time = "18:12") {
            HandOff(
                HandOffUiState(
                    now = CatalogueFixture.now,
                    snipe = null,
                    status = null,
                    gone = true,
                    page = HandOffPage(host = null, progress = null, sheet = null, generation = 0),
                ),
            )
        }
}
