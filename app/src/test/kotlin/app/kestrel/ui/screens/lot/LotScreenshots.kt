package app.kestrel.ui.screens.lot

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndSelectAll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import app.kestrel.ui.components.SheetDragHandle
import app.kestrel.ui.components.SheetLayout
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.fixture.LotMoment
import app.kestrel.ui.harness.GestureBarHeight
import app.kestrel.ui.harness.PhoneInsets
import app.kestrel.ui.harness.ScreenshotSurface
import app.kestrel.ui.harness.ScreenshotTest
import app.kestrel.ui.screens.lot.LotFixtures.lotState
import app.kestrel.ui.theme.SheetShape
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Test

/**
 * S3 in every state of DESIGN S3, the final minute in both themes and at large text, and the edit sheets (Q30). Each
 * state goes through the real status mapper and the lot's state builder.
 */
class LotScreenshots : ScreenshotTest() {
    private val fixture = CatalogueFixture

    private fun lot(
        name: String,
        moment: LotMoment,
        time: String,
        darkTheme: Boolean = false,
        reduceMotion: Boolean = false,
        fontScale: Float = 1f,
    ) = capture(
        name,
        darkTheme = darkTheme,
        time = time,
        lightIcons = true,
        reduceMotion = reduceMotion,
        fontScale = fontScale,
    ) {
        LotScreen(moment.lotState(), windowInsets = PhoneInsets)
    }

    // The final minute, hero moment one.

    @Test
    fun liveLight() = lot("S3-live", fixture.canonFinalMinute, "18:21")

    @Test
    fun liveDark() = lot("S3-live", fixture.canonFinalMinute, "18:21", darkTheme = true)

    @Test
    fun liveLargeText() = lot("S3-live", fixture.canonFinalMinute, "18:21", fontScale = 1.3f)

    @Test
    fun reducedMotionLight() = lot("S3-reduced-motion", fixture.canonFinalMinute, "18:21", reduceMotion = true)

    @Test
    fun reducedMotionDark() =
        lot("S3-reduced-motion", fixture.canonFinalMinute, "18:21", darkTheme = true, reduceMotion = true)

    @Test
    fun lockedLight() = lot("S3-locked", LotFixtures.canonLocked, "18:21")

    // After the bid.

    @Test
    fun bidSentLight() = lot("S3-bid-sent", fixture.canonBidSent, "18:21")

    @Test
    fun bidSentDark() = lot("S3-bid-sent", fixture.canonBidSent, "18:21", darkTheme = true)

    @Test
    fun inTheLeadLight() = lot("S3-in-the-lead", fixture.canonInTheLead, "18:21")

    @Test
    fun inTheLeadDark() = lot("S3-in-the-lead", fixture.canonInTheLead, "18:21", darkTheme = true)

    @Test
    fun outbidLight() = lot("S3-outbid", fixture.canonOutbid, "18:21")

    @Test
    fun noReplyLight() = lot("S3-no-reply", fixture.canonNoReply, "18:21")

    @Test
    fun extendedLight() = lot("S3-extended", fixture.walkmanExtended, "19:55")

    @Test
    fun extendedDark() = lot("S3-extended", fixture.walkmanExtended, "19:55", darkTheme = true)

    @Test
    fun readingResultLight() = lot("S3-reading-result", fixture.canonReadingResult, "18:21")

    @Test
    fun bidNotSentLight() = lot("S3-bid-not-sent", LotFixtures.canonBidNotSentWhileRunning, "18:21")

    // Before the final minute.

    @Test
    fun armedFarLight() = lot("S3-armed-far", fixture.gameBoyArmed, "18:08")

    @Test
    fun armedFarDark() = lot("S3-armed-far", fixture.gameBoyArmed, "18:08", darkTheme = true)

    @Test
    fun armedTodayLight() = lot("S3-armed-today", LotFixtures.canonArmedToday, "18:08")

    @Test
    fun watchingLight() = lot("S3-watching", fixture.polaroidWatching, "18:08")

    // Every attention case.

    @Test
    fun captchaLight() = lot("S3-captcha", fixture.canonCaptcha, "18:12")

    @Test
    fun captchaDark() = lot("S3-captcha", fixture.canonCaptcha, "18:12", darkTheme = true)

    @Test
    fun overMaxLight() = lot("S3-over-max", fixture.omegaOverMax, "18:08")

    @Test
    fun overMaxDark() = lot("S3-over-max", fixture.omegaOverMax, "18:08", darkTheme = true)

    @Test
    fun signInLight() = lot("S3-sign-in", fixture.canonSignInNeeded, "18:08")

    @Test
    fun phoneCheckLight() = lot("S3-phone-check", fixture.canonPhoneCheck, "18:12")

    @Test
    fun dryRunFailedLight() = lot("S3-dry-run-failed", fixture.canonDryRunFailed, "18:12")

    @Test
    fun offlineLight() = lot("S3-offline", fixture.canonOffline, "18:08")

    @Test
    fun offlineDark() = lot("S3-offline", fixture.canonOffline, "18:08", darkTheme = true)

    @Test
    fun unreachableLight() = lot("S3-unreachable", fixture.canonUnreachable, "18:08")

    @Test
    fun needsYouLight() = lot("S3-needs-you", fixture.omegaNeedsYou, "18:08")

    // Scrolled: once the sheet slides under the status bar, the bar gets the page colour and the theme's icons.

    @Test
    fun scrolledLight() =
        capture("S3-scrolled", darkTheme = false, time = "18:21") {
            LotScreen(
                fixture.canonFinalMinute.lotState(),
                windowInsets = PhoneInsets,
                scrollState = remember { ScrollState(900) },
            )
        }

    @Test
    fun scrolledDark() =
        capture("S3-scrolled", darkTheme = true, time = "18:21") {
            LotScreen(
                fixture.canonFinalMinute.lotState(),
                windowInsets = PhoneInsets,
                scrollState = remember { ScrollState(900) },
            )
        }

    /** The overflow menu, opened with a tap and captured with its popup window. */
    @Test
    fun menuLight() {
        composeRule.setContent {
            ScreenshotSurface(darkTheme = false, time = "18:08", lightIcons = true) {
                LotScreen(LotFixtures.canonArmedToday.lotState(), windowInsets = PhoneInsets)
            }
        }
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/${fileName("S3-menu", darkTheme = false, fontScale = 1f)}.png")
    }

    @Test
    fun menuAfterTheBidLight() {
        composeRule.setContent {
            ScreenshotSurface(darkTheme = false, time = "18:21", lightIcons = true) {
                LotScreen(fixture.canonBidSent.lotState(), windowInsets = PhoneInsets)
            }
        }
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.waitForIdle()
        captureScreenRoboImage(
            "src/test/screenshots/${fileName("S3-menu-bid-sent", darkTheme = false, fontScale = 1f)}.png",
        )
    }

    // The edit sheets, drawn over S3 without a window so the phone's bars stay on top.

    private fun sheet(
        name: String,
        moment: LotMoment,
        kind: SheetKind,
        typed: String,
        time: String,
        darkTheme: Boolean = false,
        selectAll: Boolean = false,
    ) = capture(name, darkTheme = darkTheme, time = time, lightIcons = true) {
        val state =
            moment.lotState(
                SheetRequest(
                    kind,
                    leadSeconds =
                        if (kind ==
                            SheetKind.Set
                        ) {
                            moment.snipe.leadSeconds
                        } else {
                            null
                        },
                ),
            )
        val input =
            remember {
                TextFieldState(typed).also { field ->
                    if (selectAll) field.setTextAndSelectAll(typed)
                }
            }
        SheetOver(state, input)
    }

    @Test
    fun editMaxSheetLight() = sheet("S3-edit-max-sheet", LotFixtures.canonEditing, SheetKind.Edit, "120.00", "18:21")

    @Test
    fun editMaxSheetDark() =
        sheet("S3-edit-max-sheet", LotFixtures.canonEditing, SheetKind.Edit, "120.00", "18:21", darkTheme = true)

    @Test
    fun editMaxSheetTooLowLight() =
        sheet("S3-edit-max-sheet-too-low", LotFixtures.canonEditing, SheetKind.Edit, "97", "18:21")

    @Test
    fun editMaxSheetLockedLight() =
        sheet("S3-edit-max-sheet-locked", LotFixtures.canonLocked, SheetKind.Edit, "120.00", "18:21")

    @Test
    fun raiseMaxSheetLight() =
        sheet("S3-raise-max-sheet", LotFixtures.omegaRaising, SheetKind.Raise, "2200.00", "18:08", selectAll = true)

    @Test
    fun raiseMaxSheetDark() =
        sheet(
            "S3-raise-max-sheet",
            LotFixtures.omegaRaising,
            SheetKind.Raise,
            "2200.00",
            "18:08",
            darkTheme = true,
            selectAll = true,
        )

    @Test
    fun setMaxSheetLight() = sheet("S3-set-max-sheet", fixture.polaroidWatching, SheetKind.Set, "", "18:08")

    @Test
    fun setMaxSheetTypedLight() = sheet("S3-set-max-sheet-typed", fixture.polaroidWatching, SheetKind.Set, "70", "18:08")
}

/** S3 under the sheet's scrim, with the sheet drawn at the bottom the way KestrelBottomSheet draws it. */
@Composable
private fun SheetOver(
    state: LotUiState,
    input: TextFieldState,
) {
    val sheet = state.sheet ?: return
    Box(Modifier.fillMaxSize()) {
        LotScreen(state, windowInsets = PhoneInsets)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f)),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(SheetShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SheetDragHandle()
            SheetLayout(title = sheet.title, windowInsets = WindowInsets(bottom = GestureBarHeight)) {
                EditMaxSheetContent(
                    state = sheet,
                    input = input,
                    now = state.now,
                    onSave = {},
                    onClose = {},
                    onUseAmount = {},
                    onSelectLead = {},
                )
            }
        }
    }
}
