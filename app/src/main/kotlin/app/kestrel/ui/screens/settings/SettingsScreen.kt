package app.kestrel.ui.screens.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import app.kestrel.data.ThemeMode
import app.kestrel.ui.components.BottomFade
import app.kestrel.ui.components.ConnectedToggleRow
import app.kestrel.ui.components.SectionHead
import app.kestrel.ui.components.StateLabel
import app.kestrel.ui.components.SymbolIcon
import app.kestrel.ui.components.Symbols
import app.kestrel.ui.format.clock
import app.kestrel.ui.format.secondsText
import app.kestrel.ui.model.StateWords
import app.kestrel.ui.screens.ready.GlyphCircle
import app.kestrel.ui.screens.ready.InlinePrimaryButton
import app.kestrel.ui.screens.ready.InlineTonalButton
import app.kestrel.ui.screens.ready.SetupTopBar
import app.kestrel.ui.theme.KestrelTheme
import app.kestrel.ui.theme.Spacing
import app.kestrel.ui.theme.StatusTone

/** What Settings can do; the screen only calls these. */
@Stable
interface SettingsActions {
    fun back()

    fun signIn()

    fun askToSignOut()

    fun confirmSignOut()

    fun cancelSignOut()

    fun setDefaultLead(seconds: Int)

    fun setLiveUpdateMinutes(minutes: Int)

    fun openNotificationSettings()

    fun openReady()

    fun setTheme(mode: ThemeMode)

    fun setPureBlack(enabled: Boolean)

    fun setHaptics(enabled: Boolean)

    fun openSheet(sheet: SettingsSheet)

    fun closeSheet()
}

/** The lead times on offer (DESIGN 3.17): 3 s, 6 s and 10 s. */
val LeadTimeChoices: List<Int> = listOf(3, 6, 10)

/** How early the countdown may appear in the status bar, in minutes before the end. */
val LiveUpdateChoices: List<Int> = listOf(5, 10, 30)

/**
 * Settings (DESIGN Settings): the account with sign-out behind a confirmation, bidding and alert defaults, the
 * phone's readiness, appearance, help with eBay's own words on sniping, and the licences.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    scrollState: ScrollState = rememberScrollState(),
) {
    val bottomInset = windowInsets.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
            ) {
                SetupTopBar(navigationIcon = Symbols.ArrowBack, navigationLabel = "Back", onNavigate = actions::back)
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(horizontal = Spacing.ColumnA)
                        .padding(bottom = bottomInset + Spacing.ScreenEnd),
                ) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.semantics { heading() },
                    )
                    Section("Account", first = true) { AccountRow(state, actions) }
                    Section("Bidding") { LeadTimeRow(state, actions) }
                    Section("Alerts") {
                        LiveUpdateRow(state, actions, index = 0, count = 2)
                        Gap()
                        LinkRow(
                            title = "Notification settings",
                            detail = "Live Updates, sounds and lock screen",
                            onClick = actions::openNotificationSettings,
                            index = 1,
                            count = 2,
                        )
                    }
                    Section("This phone") { ReadyRow(state, actions) }
                    Section("Appearance") {
                        ThemeRow(state, actions)
                        Gap()
                        SwitchRow(
                            title = "Pure black",
                            detail =
                                if (state.themeMode == ThemeMode.LIGHT) {
                                    "Only in dark theme"
                                } else {
                                    "A black page in dark theme, for OLED screens"
                                },
                            checked = state.pureBlack,
                            enabled = state.themeMode != ThemeMode.LIGHT,
                            onChange = actions::setPureBlack,
                            index = 1,
                            count = 3,
                        )
                        Gap()
                        SwitchRow(
                            title = "Haptics",
                            detail = "A light tap when you arm or change a snipe",
                            checked = state.haptics,
                            enabled = true,
                            onChange = actions::setHaptics,
                            index = 2,
                            count = 3,
                        )
                    }
                    Section("Help") {
                        LinkRow(
                            title = "How Kestrel bids",
                            detail = "One bid at the end, a dry run first, and you stay in charge",
                            onClick = { actions.openSheet(SettingsSheet.HowKestrelBids) },
                            index = 0,
                            count = 1,
                        )
                        Spacer(Modifier.height(20.dp))
                        SnipingQuote()
                    }
                    Section("About") {
                        TextRow(
                            title = "Kestrel ${state.version}",
                            detail = "Kestrel isn’t made by or affiliated with eBay.",
                            index = 0,
                            count = 2,
                        )
                        Gap()
                        LinkRow(
                            title = "Licences",
                            detail = "Fonts and open-source software",
                            onClick = { actions.openSheet(SettingsSheet.Licences) },
                            index = 1,
                            count = 2,
                        )
                    }
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(
                            windowInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                        ),
            )
            BottomFade(windowInsets = windowInsets)
        }
    }
    if (state.confirmingSignOut) SignOutDialog(state.armedSnipes, actions)
    state.sheet?.let { SettingsSheetContent(it, actions) }
}

/** A section head (12 dp above the first, 24 dp above the others, 8 dp below) and its segments. */
@Composable
private fun Section(
    title: String,
    first: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        SectionHead(title, modifier = Modifier.padding(top = if (first) 12.dp else 24.dp, bottom = 8.dp))
        content()
    }
}

/** The 2 dp between segments of one group. */
@Composable
private fun Gap() {
    Spacer(Modifier.height(ListItemDefaults.SegmentedGap))
}

private val RowPadding = PaddingValues(16.dp)

/** Row details break into balanced lines, so a wrapped detail never leaves one word alone. */
private val DetailStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyMedium.copy(lineBreak = LineBreak.Heading)

@Composable
private fun AccountRow(
    state: SettingsUiState,
    actions: SettingsActions,
) {
    val account = state.account
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
        colors = ListItemDefaults.segmentedColors(containerColor = KestrelTheme.catalogue.card),
        contentPadding = RowPadding,
        leadingContent = {
            GlyphCircle(
                icon = Symbols.AccountCircle,
                container = MaterialTheme.colorScheme.surfaceContainerHigh,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            if (account is AccountState.SignedIn) {
                InlineTonalButton("Sign out", onClick = actions::askToSignOut)
            } else {
                InlinePrimaryButton("Sign in", onClick = actions::signIn)
            }
        },
        supportingContent = {
            Text(
                text =
                    if (account is AccountState.SignedIn) {
                        "Signed in on this phone"
                    } else {
                        "Sign in so Kestrel can bid for you."
                    },
                style = DetailStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        Text(
            text =
                when (account) {
                    is AccountState.SignedIn -> account.name ?: "eBay account"
                    AccountState.SignedOut -> "Not signed in"
                },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun LeadTimeRow(
    state: SettingsUiState,
    actions: SettingsActions,
) {
    ChoiceRow(
        title = "Default lead time",
        detail = "How long before the end Kestrel bids",
        options = LeadTimeChoices.map { secondsText(it.toLong()) },
        selectedIndex = LeadTimeChoices.indexOf(state.defaultLeadSeconds),
        onSelect = { actions.setDefaultLead(LeadTimeChoices[it]) },
        note = "6\u202Fs suits most connections. Choose 10\u202Fs on a weak signal.",
        index = 0,
        count = 1,
    )
}

@Composable
private fun LiveUpdateRow(
    state: SettingsUiState,
    actions: SettingsActions,
    index: Int,
    count: Int,
) {
    ChoiceRow(
        title = "Live Update",
        detail = "How long before the end the countdown appears in the status bar",
        options = LiveUpdateChoices.map { "$it\u202Fmin" },
        selectedIndex = LiveUpdateChoices.indexOf(state.liveUpdateMinutes),
        onSelect = { actions.setLiveUpdateMinutes(LiveUpdateChoices[it]) },
        note = null,
        index = index,
        count = count,
    )
}

@Composable
private fun ThemeRow(
    state: SettingsUiState,
    actions: SettingsActions,
) {
    val modes = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
    ChoiceRow(
        title = "Theme",
        detail = null,
        options = listOf("System", "Light", "Dark"),
        selectedIndex = modes.indexOf(state.themeMode),
        onSelect = { actions.setTheme(modes[it]) },
        note = null,
        index = 0,
        count = 3,
    )
}

/** "Ready to snipe", with the state of the checks; it opens S5. */
@Composable
private fun ReadyRow(
    state: SettingsUiState,
    actions: SettingsActions,
) {
    val readiness = state.readiness
    SegmentedListItem(
        onClick = actions::openReady,
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
        colors = ListItemDefaults.segmentedColors(containerColor = KestrelTheme.catalogue.card),
        contentPadding = RowPadding,
        trailingContent = { Chevron() },
        supportingContent = {
            when {
                readiness == null -> {
                    Text(
                        "Checking…",
                        style = DetailStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                readiness.problems > 0 -> {
                    val words =
                        if (readiness.problems ==
                            1
                        ) {
                            "1 thing needs attention"
                        } else {
                            "${readiness.problems} things need attention"
                        }
                    StateLabel(StatusTone.Attention, StateWords(words))
                }

                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SymbolIcon(Symbols.CheckCircle, tint = KestrelTheme.status.won.accent, size = 18.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "All checks passed at ${readiness.checkedAt.clock()}",
                            style = DetailStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    ) {
        Text(
            "Ready to snipe",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A row with connected toggles under its words. */
@Composable
private fun ChoiceRow(
    title: String,
    detail: String?,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    note: String?,
    index: Int,
    count: Int,
) {
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(containerColor = KestrelTheme.catalogue.card),
        contentPadding = RowPadding,
        supportingContent = {
            Column {
                detail?.let {
                    Text(
                        it,
                        style = DetailStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                ConnectedToggleRow(options = options, selectedIndex = selectedIndex, onSelect = onSelect)
                note?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** A row that toggles a switch; the whole row is the target. */
@Composable
private fun SwitchRow(
    title: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
    index: Int,
    count: Int,
) {
    val card = KestrelTheme.catalogue.card
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        // The switch shows the state; the row stays a card whether it is on, off or unavailable.
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = card,
                selectedContainerColor = card,
                disabledContainerColor = card,
            ),
        contentPadding = RowPadding,
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        supportingContent = {
            Text(
                detail,
                style = DetailStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** A row that opens a page or a sheet, with a chevron. */
@Composable
private fun LinkRow(
    title: String,
    detail: String,
    onClick: () -> Unit,
    index: Int,
    count: Int,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(containerColor = KestrelTheme.catalogue.card),
        contentPadding = RowPadding,
        trailingContent = { Chevron() },
        supportingContent = {
            Text(
                detail,
                style = DetailStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun TextRow(
    title: String,
    detail: String,
    index: Int,
    count: Int,
) {
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(containerColor = KestrelTheme.catalogue.card),
        contentPadding = RowPadding,
        supportingContent = {
            Text(
                detail,
                style = DetailStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Chevron() {
    SymbolIcon(Symbols.ChevronRight, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 24.dp)
}

/**
 * eBay's own words on sniping, as a pull-quote (DESIGN 3.14): a 3 dp ink bar, the source in labelMedium and the
 * sentence in titleLarge. From eBay UK's help page "Bid sniping" (archived 18 Oct 2025).
 */
@Composable
private fun SnipingQuote() {
    Row(
        Modifier
            .height(IntrinsicSize.Min)
            .semantics(mergeDescendants = true) {
                contentDescription =
                    "eBay’s help page on bid sniping says: $SNIPING_QUOTE"
            },
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.onSurface))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = "eBay’s help page on bid sniping",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "“$SNIPING_QUOTE”",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** ebay.co.uk/help/buying/auctions-bidding/bid-sniping, quoted exactly (curly apostrophes, as everywhere). */
const val SNIPING_QUOTE =
    "Bid sniping – including the use of software that places bids for you – is allowed on eBay, but it doesn’t " +
        "guarantee you’ll win an auction."
