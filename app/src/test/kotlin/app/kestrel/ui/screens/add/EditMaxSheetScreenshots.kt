package app.kestrel.ui.screens.add

import app.kestrel.ui.harness.ScreenshotTest
import org.junit.Test

/** The edit, raise and set sheets (DESIGN "Edit-max sheet", Q30 edit-max-sheet and raise-max-sheet), drawn as phones show them. */
class EditMaxSheetScreenshots : ScreenshotTest() {
    private fun sheet(
        name: String,
        dark: Boolean,
        state: EditMaxUiState,
        time: String,
        fontScale: Float = 1f,
    ) = capture(name, darkTheme = dark, time = time, fontScale = fontScale) {
        SheetPreview(title = state.title) { EditMaxSheetBody(state = state) }
    }

    @Test fun editLight() = sheet("edit-max-sheet", dark = false, AddFixtures.editSheet, time = "18:21")

    @Test fun editDark() = sheet("edit-max-sheet", dark = true, AddFixtures.editSheet, time = "18:21")

    @Test fun editLargeText() =
        sheet(
            "edit-max-sheet",
            dark = false,
            AddFixtures.editSheet,
            time = "18:21",
            fontScale = 1.3f,
        )

    @Test fun editLockedLight() =
        sheet(
            "edit-max-sheet-locked",
            dark = false,
            AddFixtures.editSheetLocked,
            time = "18:21",
        )

    @Test fun editRefusedLight() =
        sheet(
            "edit-max-sheet-refused",
            dark = false,
            AddFixtures.editSheetRefused,
            time = "18:21",
        )

    @Test fun raiseLight() = sheet("raise-max-sheet", dark = false, AddFixtures.raiseSheet, time = "18:08")

    @Test fun raiseDark() = sheet("raise-max-sheet", dark = true, AddFixtures.raiseSheet, time = "18:08")

    @Test fun setLight() = sheet("set-max-sheet", dark = false, AddFixtures.setSheet, time = "18:09")

    @Test fun setDark() = sheet("set-max-sheet", dark = true, AddFixtures.setSheet, time = "18:09")
}
