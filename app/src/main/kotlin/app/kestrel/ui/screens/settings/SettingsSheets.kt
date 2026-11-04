package app.kestrel.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.kestrel.ui.components.KestrelBottomSheet
import app.kestrel.ui.components.OutlinedActionButton
import app.kestrel.ui.format.secondsText
import app.kestrel.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * The sign-out confirmation (DESIGN Settings and 3.17: sign-out is the one destructive action behind a dialog
 * rather than a menu with undo). 24 dp from each side, as on the phone, whatever window the dialog gets.
 */
@Composable
internal fun SignOutDialog(
    armedSnipes: Int,
    actions: SettingsActions,
) {
    AlertDialog(
        onDismissRequest = actions::cancelSignOut,
        title = { Text("Sign out of eBay?") },
        text = { Text(signOutText(armedSnipes)) },
        confirmButton = {
            TextButton(
                onClick = actions::confirmSignOut,
            ) { Text("Sign out", style = MaterialTheme.typography.labelLarge) }
        },
        dismissButton = {
            TextButton(onClick = actions::cancelSignOut) { Text("Cancel", style = MaterialTheme.typography.labelLarge) }
        },
        modifier = Modifier.padding(horizontal = 24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    )
}

/** "Your 5 armed snipes pause until you sign in again. Kestrel keeps your list." */
internal fun signOutText(armedSnipes: Int): String =
    when (armedSnipes) {
        0 -> "Kestrel keeps your list. Sign in again before you arm a snipe."
        1 -> "Your armed snipe pauses until you sign in again. Kestrel keeps your list."
        else -> "Your $armedSnipes armed snipes pause until you sign in again. Kestrel keeps your list."
    }

@Composable
internal fun SettingsSheetContent(
    sheet: SettingsSheet,
    actions: SettingsActions,
) {
    when (sheet) {
        SettingsSheet.HowKestrelBids -> {
            KestrelBottomSheet(title = "How Kestrel bids", onDismissRequest = actions::closeSheet) {
                Column(
                    modifier = Modifier.heightIn(max = SheetTextMaxHeight).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    HowItWorks.forEach { (title, detail) -> Paragraph(title, detail) }
                }
            }
        }

        SettingsSheet.Licences -> {
            KestrelBottomSheet(title = "Licences", onDismissRequest = actions::closeSheet) {
                Column(
                    modifier = Modifier.heightIn(max = SheetTextMaxHeight).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Licences.forEach { Paragraph(it.name, "${it.licence} · ${it.holder}") }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedActionButton(
                    text = "Read the font licence",
                    onClick = { actions.openSheet(SettingsSheet.FontLicence) },
                    height = Spacing.TouchTarget,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        SettingsSheet.FontLicence -> {
            val context = LocalContext.current
            val text by produceState(initialValue = "", context) {
                value =
                    withContext(Dispatchers.IO) {
                        try {
                            reflowLicence(
                                context.assets
                                    .open(FONT_LICENCE)
                                    .bufferedReader()
                                    .use { it.readText() },
                            )
                        } catch (error: IOException) {
                            ""
                        }
                    }
            }
            KestrelBottomSheet(title = "SIL Open Font License 1.1", onDismissRequest = actions::closeSheet) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(
                                max = SheetTextMaxHeight,
                            ).verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}

@Composable
private fun Paragraph(
    title: String,
    detail: String,
) {
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The licence file is wrapped at 72 characters for a terminal; on a phone those breaks land mid-sentence. Keeps the
 * paragraphs and the section names (PREAMBLE, DEFINITIONS…), joins the lines inside each paragraph and drops the
 * dashed rules. The words stay exactly as written.
 */
internal fun reflowLicence(text: String): String =
    text
        .replace("\r\n", "\n")
        .split(Regex("""\n\s*\n"""))
        .mapNotNull { block ->
            val lines = block.lines().map { it.trim() }.filter { line -> line.isNotEmpty() && !line.all { it == '-' } }
            if (lines.isEmpty()) return@mapNotNull null
            val first = lines.first()
            val sectionName =
                lines.size > 1 &&
                    first.length <= SECTION_NAME_LENGTH &&
                    first == first.uppercase() &&
                    first.none { it.isDigit() }
            if (sectionName) first + "\n" + lines.drop(1).joinToString(" ") else lines.joinToString(" ")
        }.joinToString("\n\n")

private const val SECTION_NAME_LENGTH = 30

/**
 * Sheets keep clear of the status bar: KestrelBottomSheet pads the keyboard and navigation insets only, so a long
 * text scrolls inside this height instead of growing the sheet to the top of the screen.
 */
private val SheetTextMaxHeight = 560.dp

private val HowItWorks: List<Pair<String, String>> =
    listOf(
        "One bid, at the end" to
            "Kestrel places one bid for your full max, ${secondsText(6)} before the end unless you choose another " +
            "lead time. eBay’s proxy bidding then charges one step above the next bidder, never more than your max.",
        "A dry run first" to
            "About 10 minutes before each bid, Kestrel opens eBay’s bid panel and types your max, but never sends " +
            "it. That proves the sign-in and the page work.",
        "You stay in charge" to
            "If eBay shows a captcha or asks to verify you, Kestrel stops and asks you. It never solves these, never " +
            "raises your max and never pays.",
        "From this phone" to
            "Kestrel bids from this phone with your own eBay account, so the phone has to be on and online when an " +
            "auction ends. Your session and your snipes stay on this phone.",
    )

/** One piece of software or type in Kestrel and its licence. */
@Immutable
private data class Licence(
    val name: String,
    val licence: String,
    val holder: String,
)

private val Licences: List<Licence> =
    listOf(
        Licence("Newsreader", "SIL Open Font License 1.1", "© 2020 The Newsreader Project Authors"),
        Licence("Google Sans Flex", "SIL Open Font License 1.1", "© 2015 The Google Sans Flex Authors"),
        Licence("Material Symbols", "Apache License 2.0", "© Google LLC"),
        Licence("Jetpack Compose, Material 3 and AndroidX", "Apache License 2.0", "© The Android Open Source Project"),
        Licence("Kotlin, coroutines and serialisation", "Apache License 2.0", "© JetBrains s.r.o. and contributors"),
        Licence("Coil", "Apache License 2.0", "© Coil Contributors"),
        Licence("OkHttp", "Apache License 2.0", "© Square, Inc."),
    )

/** The OFL text the fonts ship with (assets/licenses); both files carry the same licence under their own notice. */
private const val FONT_LICENCE = "licenses/newsreader_OFL.txt"
