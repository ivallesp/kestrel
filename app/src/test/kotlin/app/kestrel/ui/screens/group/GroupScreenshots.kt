package app.kestrel.ui.screens.group

import app.kestrel.ui.harness.PhoneInsets
import app.kestrel.ui.harness.ScreenshotTest
import app.kestrel.ui.screens.add.SheetPreview
import org.junit.Test

/** The Group screen (DESIGN "Group screen", Q30 group) in each state, and its picker of armed snipes. */
class GroupScreenshots : ScreenshotTest() {
    private fun group(
        name: String,
        dark: Boolean,
        state: GroupUiState,
        time: String = "18:08",
        fontScale: Float = 1f,
        menuOpen: Boolean = false,
    ) = capture(name, darkTheme = dark, time = time, fontScale = fontScale, wholeScreen = menuOpen) {
        GroupScreen(state = state, windowInsets = PhoneInsets, menuOpen = menuOpen)
    }

    @Test fun groupLight() = group("group", dark = false, GroupFixtures.members)

    @Test fun groupDark() = group("group", dark = true, GroupFixtures.members)

    @Test fun groupLargeText() = group("group", dark = false, GroupFixtures.members, fontScale = 1.3f)

    @Test fun closeEndingsLight() = group("group-close-endings", dark = false, GroupFixtures.closeEndings)

    @Test fun afterWinLight() = group("group-after-win", dark = false, GroupFixtures.afterWin, time = "20:12")

    @Test fun afterWinDark() = group("group-after-win", dark = true, GroupFixtures.afterWin, time = "20:12")

    @Test fun emptyLight() = group("group-empty", dark = false, GroupFixtures.empty)

    @Test fun missingLight() = group("group-missing", dark = false, GroupFixtures.missing)

    @Test fun menuLight() = group("group-menu", dark = false, GroupFixtures.members, menuOpen = true)

    @Test fun addPickerLight() =
        capture("group-add-picker", darkTheme = false, time = "18:08") {
            val state = GroupFixtures.members
            SheetPreview(
                title = "Add to ${state.title}",
                backdrop = { GroupScreen(state = state, windowInsets = PhoneInsets) },
            ) {
                SnipePickerBody(candidates = state.candidates, now = state.now, onPick = {})
            }
        }
}
