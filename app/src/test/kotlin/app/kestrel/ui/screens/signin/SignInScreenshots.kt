package app.kestrel.ui.screens.signin

import app.kestrel.ui.harness.PhoneInsets
import app.kestrel.ui.harness.ScreenshotTest
import org.junit.Test

/** S6, the sign-in introduction, at 18:05 (DESIGN S6). */
class SignInScreenshots : ScreenshotTest() {
    @Test
    fun signInLight() =
        capture("S6-signin", darkTheme = false, time = "18:05") {
            SignInScreen(onExit = {}, onContinue = {}, onNotNow = {}, windowInsets = PhoneInsets)
        }

    @Test
    fun signInDark() =
        capture("S6-signin", darkTheme = true, time = "18:05") {
            SignInScreen(onExit = {}, onContinue = {}, onNotNow = {}, windowInsets = PhoneInsets)
        }

    @Test
    fun signInLargeText() =
        capture("S6-signin", darkTheme = false, time = "18:05", fontScale = 1.3f) {
            SignInScreen(onExit = {}, onContinue = {}, onNotNow = {}, windowInsets = PhoneInsets)
        }
}
