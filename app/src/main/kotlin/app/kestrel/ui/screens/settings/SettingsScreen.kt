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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
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
import app.kestrel.ui.format.NO_BREAK_SPACE
import app.kestrel.ui.format.clock
import app.kestrel.ui.format.dayDate
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

    fun openNotificationSettings()

    fun openReady()

    fun setTheme(mode: ThemeMode)

    fun setSuggestOddPence(on: Boolean)

    fun openSheet(sheet: SettingsSheet)

    fun closeSheet()

    /** Shares a report of recent freezes and crashes (nothing leaves the phone unless the user sends it). */
    fun sendProblemReport() {}

    /** Plays the win celebration as a preview: nothing is bid and no snipe changes. */
    fun openCelebration() {}

    /** "Buy me a coffee": opens the link, or says it isn't set up yet. */
    fun buyMeACoffee() {}
}

private const val ABOUT_ROWS = 5

/** The lead times on offer (DESIGN 3.17): 3 s, 6 s and 10 s. */
val LeadTimeChoices: List<Int> = listOf(3, 6, 10)

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
                SetupTopBar(
                    navigationIcon = Symbols.ArrowBack,
                    navigationLabel = "Back",
                    onNavigate = actions::back,
                    ruled = { scrollState.canScrollBackward },
                )
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
                    Section("Bidding") {
                        LeadTimeRow(state, actions)
                        Gap()
                        SwitchRow(
                            title = "Suggest odd pence",
                            detail = "Offer £90.37 instead of £90.00",
                            checked = state.suggestOddPence,
                            onCheckedChange = actions::setSuggestOddPence,
                            index = 1,
                            count = 2,
                        )
                    }
                    Section("Alerts") {
                        // Kestrel always alerts once when the price passes the max: the notifications have no
                        // switch to read yet, so this one shows the rule and cannot be turned off.
                        SwitchRow(
                            title = "Price passed your max",
                            detail = "One alert per listing when the price goes past your${NO_BREAK_SPACE}max",
                            checked = true,
                            onCheckedChange = {},
                            enabled = false,
                            index = 0,
                            count = 2,
                        )
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
                    Section("Appearance") { ThemeRow(state, actions) }
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
                            count = ABOUT_ROWS,
                        )
                        Gap()
                        LinkRow(
                            title = "Watch the win celebration",
                            detail = "What plays the first time you open a win. Nothing is bid.",
                            onClick = actions::openCelebration,
                            index = 1,
                            count = ABOUT_ROWS,
                        )
                        Gap()
                        LinkRow(
                            title = "Buy me a coffee",
                            detail = "Kestrel has no ads and no fees. Link coming soon.",
                            onClick = actions::buyMeACoffee,
                            index = 2,
                            count = ABOUT_ROWS,
                        )
                        Gap()
                        LinkRow(
                            title = "Send a problem report",
                            detail = "Recent freezes and crashes, to share in a message",
                            onClick = actions::sendProblemReport,
                            index = 3,
                            count = ABOUT_ROWS,
                        )
                        Gap()
                        LinkRow(
                            title = "Licences",
                            detail = "Fonts and open-source software",
                            onClick = { actions.openSheet(SettingsSheet.Licences) },
                            index = 4,
                            count = ABOUT_ROWS,
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

/** How far a locked switch fades: Material's disabled content alpha. */
private const val LOCKED_SWITCH_ALPHA = 0.38f

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
                    when {
                        account !is AccountState.SignedIn -> "Sign in so Kestrel can bid for you."
                        account.since != null -> "Signed in on this phone since ${account.since.dayDate()}"
                        else -> "Signed in on this phone"
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
        note = "6\u202Fs suits most connections. Choose 10\u202Fs on a\u00A0weak\u00A0signal.",
        index = 0,
        count = 1,
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
        count = 1,
    )
}

/**
 * "Phone checks", with the verdict of the checks in its state line ("1 thing needs attention", "All checks passed at
 * 18:06"); it opens S5. The title stays neutral, so it never says ready while the line below says otherwise, and it
 * does not repeat the section head above it.
 */
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
            "Phone checks",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * A row with a Switch at the end (DESIGN Settings items 3 and 4): the card colour, 16 dp padding, and an M3 Switch
 * with its checked track in primary and its thumb in onPrimary. The whole row toggles; TalkBack reads it as a switch.
 */
@Composable
private fun SwitchRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    index: Int,
    count: Int,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(containerColor = KestrelTheme.catalogue.card),
        contentPadding = RowPadding,
        modifier =
            Modifier.toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                // A locked switch keeps its checked colours, dimmed, so it still reads as on.
                colors =
                    SwitchDefaults.colors(
                        checkedTrackColor = scheme.primary,
                        checkedThumbColor = scheme.onPrimary,
                        disabledCheckedTrackColor = scheme.primary.copy(alpha = LOCKED_SWITCH_ALPHA),
                        disabledCheckedThumbColor = scheme.onPrimary,
                    ),
            )
        },
        supportingContent = {
            Text(detail, style = DetailStyle, color = scheme.onSurfaceVariant)
        },
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
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
