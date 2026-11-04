package app.kestrel.ui.screens.add

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import app.kestrel.ui.components.CalmLoadingIndicator
import app.kestrel.ui.components.MoneyBlock
import app.kestrel.ui.components.PrimaryButton
import app.kestrel.ui.components.StateLabel
import app.kestrel.ui.components.SymbolIcon
import app.kestrel.ui.components.Symbols
import app.kestrel.ui.components.figureSentence
import app.kestrel.ui.format.NO_BREAK_SPACE
import app.kestrel.ui.format.absoluteTime
import app.kestrel.ui.format.spoken
import app.kestrel.ui.format.timeLeft
import app.kestrel.ui.model.label
import app.kestrel.ui.theme.KestrelTheme
import app.kestrel.ui.theme.Spacing
import java.time.Duration
import java.time.Instant

/** What the user can do on S2. Each method has a no-op default, so a test passes only what it checks. */
@Stable
interface AddActions {
    fun close() {}

    fun viewListing(url: String) {}

    /** Paste: the destination reads the clipboard, on this tap only. */
    fun paste() {}

    fun submitLink() {}

    fun retry() {}

    fun pasteDifferentLink() {}

    fun signIn() {}

    fun openSnipe(snipeId: Long) {}

    fun useOddPence() {}

    fun selectLead(index: Int) {}

    fun chooseGroup() {}

    /** "Arm snipe" or "Watch without a max". */
    fun arm() {}

    fun confirmWords() {}

    fun dismissWords() {}
}

/**
 * S2, a new snipe (DESIGN S2): full screen, because it is a money decision and the keyboard is open. The top bar,
 * the page for the current step, and the pinned bar with the exact plan above the keyboard.
 *
 * Stateless: [state] carries every word and figure, [actions] every tap. [canPaste] shows Paste in the link entry,
 * when the clipboard holds a link (the destination asks the clipboard's description, never its contents).
 * [focusMax] puts the cursor in the max field once the listing has loaded, which opens the keyboard.
 */
@Composable
fun AddScreen(
    state: AddUiState,
    modifier: Modifier = Modifier,
    actions: AddActions = remember { object : AddActions {} },
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    canPaste: Boolean = false,
    focusMax: Boolean = true,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    scrollState: ScrollState = rememberScrollState(),
) {
    val page = state.page
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(windowInsets),
        ) {
            AddTopBar(
                listingUrl = page.listingUrl,
                ruled = scrollState.canScrollBackward,
                onClose = actions::close,
                onViewListing = actions::viewListing,
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = Spacing.ColumnA)
                        .padding(bottom = Spacing.Section),
                ) {
                    when (page) {
                        is AddPage.LinkEntry -> LinkEntryPage(page, canPaste, actions)
                        is AddPage.Fetching -> FetchingPage(page)
                        is AddPage.Failed -> FailedPage(page, actions)
                        is AddPage.CannotSnipe -> CannotSnipePage(page, actions)
                        is AddPage.AlreadySniped -> AlreadySnipedPage(page, state.now, actions)
                        is AddPage.Ready -> ReadyPage(page, state.now, focusMax, actions)
                    }
                }
                // Content under the pinned bar fades into the page instead of being cut (DESIGN 2.8, Q14).
                if (scrollState.canScrollForward) EdgeFade(Modifier.align(Alignment.BottomCenter))
                SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
            }
            when (page) {
                is AddPage.LinkEntry -> {
                    BottomBar(ruled = scrollState.canScrollForward) {
                        PrimaryButton(
                            text = "Get listing",
                            onClick = actions::submitLink,
                            enabled = page.link.text.isNotBlank(),
                        )
                    }
                }

                is AddPage.Fetching -> {
                    BottomBar(ruled = scrollState.canScrollForward) {
                        PrimaryButton(text = "Arm snipe", onClick = {}, icon = Symbols.Schedule, enabled = false)
                    }
                }

                is AddPage.Ready -> {
                    ArmBarView(page.bar, ruled = scrollState.canScrollForward, onArm = actions::arm)
                }

                // A page without a plan has nothing to arm, so it has no bar.
                is AddPage.Failed, is AddPage.CannotSnipe, is AddPage.AlreadySniped -> {}
            }
        }
    }
    if (page is AddPage.Ready) {
        page.wordsCheck?.let { check ->
            WordsCheckDialog(check, onConfirm = actions::confirmWords, onDismiss = actions::dismissWords)
        }
    }
}

/**
 * The S2 top bar (64 dp): close, "New snipe", and "View listing" once the listing's address is known. A 1 dp rule
 * runs under it while the page is scrolled, so scrolled content never meets the bar without an edge.
 */
@Composable
private fun AddTopBar(
    listingUrl: String?,
    ruled: Boolean,
    onClose: () -> Unit,
    onViewListing: (String) -> Unit,
) {
    val rule = KestrelTheme.catalogue.rule
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .drawBehind {
                    if (ruled) {
                        val y = size.height - 0.5.dp.toPx()
                        drawLine(rule, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    }
                }.padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { SymbolIcon(Symbols.Close, contentDescription = "Close") }
        Spacer(Modifier.width(4.dp))
        Text(
            text = "New snipe",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        listingUrl?.let { url -> InkTextButton("View listing", onClick = { onViewListing(url) }) }
    }
}

/** The link entry (DESIGN S2 "Link entry"): the field, Paste when the clipboard holds a link, and the share hint. */
@Composable
private fun LinkEntryPage(
    page: AddPage.LinkEntry,
    canPaste: Boolean,
    actions: AddActions,
) {
    Column {
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                state = page.link,
                modifier = Modifier.weight(1f),
                label = { Text("eBay listing link") },
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                onKeyboardAction = { actions.submitLink() },
                shape = MaterialTheme.shapes.medium,
            )
            if (canPaste) {
                Spacer(Modifier.width(8.dp))
                TonalButton(
                    text = "Paste",
                    onClick = actions::paste,
                    icon = Symbols.ContentPaste,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Or share a listing from the eBay app: tap Share, then${NO_BREAK_SPACE}Kestrel.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Fetching (DESIGN S2 "Fetching"): the contained loading indicator where the lot header goes, and the address. */
@Composable
private fun FetchingPage(page: AddPage.Fetching) {
    Column {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 104.dp)
                    .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CalmLoadingIndicator(size = 48.dp, contained = true, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Getting the listing from eBay…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = page.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun FailedPage(
    page: AddPage.Failed,
    actions: AddActions,
) {
    Column {
        Spacer(Modifier.height(16.dp))
        NoticeCard(icon = Symbols.Info, title = "Couldn’t open this listing.", detail = page.reason)
        if (page.signIn || page.canRetry) {
            Spacer(Modifier.height(16.dp))
            NoticeActions {
                if (page.signIn) InkButton("Sign in", onClick = actions::signIn)
                if (page.canRetry) TonalButton("Try again", onClick = actions::retry, icon = Symbols.Refresh)
            }
        }
        PasteDifferentLink(actions)
    }
}

/**
 * "Paste a different link", in the same place on every page that cannot go on: a text button on its own line under
 * the page's message and actions, its words on column A.
 */
@Composable
private fun PasteDifferentLink(
    actions: AddActions,
    modifier: Modifier = Modifier,
) {
    InkTextButton(
        "Paste a different link",
        onClick = actions::pasteDifferentLink,
        modifier = modifier.padding(top = 8.dp).offset(x = -TextButtonInset),
    )
}

/** The horizontal padding inside an expressive text button (16 dp), before its words. */
private val TextButtonInset = 16.dp

@Composable
private fun CannotSnipePage(
    page: AddPage.CannotSnipe,
    actions: AddActions,
) {
    Column {
        Spacer(Modifier.height(8.dp))
        LotHeader(page.header)
        Spacer(Modifier.height(24.dp))
        when (page.reason) {
            CannotSnipeReason.BuyItNow -> {
                NoticeCard(
                    icon = Symbols.Storefront,
                    title = "This is a Buy it now listing, so there’s nothing to snipe.",
                )
            }

            CannotSnipeReason.Ended -> {
                NoticeCard(icon = Symbols.Flag, title = "This auction has already ended.")
            }
        }
        if (page.reason == CannotSnipeReason.BuyItNow) {
            Spacer(Modifier.height(16.dp))
            TonalButton("Open on eBay", onClick = { actions.viewListing(page.listingUrl) })
        }
        PasteDifferentLink(actions)
    }
}

@Composable
private fun AlreadySnipedPage(
    page: AddPage.AlreadySniped,
    now: Instant,
    actions: AddActions,
) {
    Column {
        Spacer(Modifier.height(8.dp))
        LotHeader(page.header)
        Spacer(Modifier.height(24.dp))
        NoticeCard(icon = Symbols.Info, title = "You already have a snipe on this listing.") {
            StateLabel(tone = page.status.tone, words = page.status.label(now), modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(text = "Open snipe", onClick = { actions.openSnipe(page.snipeId) })
        PasteDifferentLink(actions)
    }
}

@Composable
private fun NoticeActions(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        content()
    }
}

/** The preview and the plan (DESIGN S2 items 2 to 8). */
@Composable
private fun ReadyPage(
    page: AddPage.Ready,
    now: Instant,
    focusMax: Boolean,
    actions: AddActions,
) {
    Column {
        val maxFocus = remember { FocusRequester() }
        if (focusMax) {
            LaunchedEffect(page.listingUrl) { maxFocus.requestFocus() }
        }
        Spacer(Modifier.height(8.dp))
        LotHeader(page.header)
        Spacer(Modifier.height(16.dp))
        FactsCard(page.facts, now)
        Spacer(Modifier.height(24.dp))
        // Done only hides the keyboard: the odd pence, the timing and the group below are still to come, and
        // arming is a money decision taken with its own button.
        val focusManager = LocalFocusManager.current
        MaxEntryField(
            page.max,
            onDone = { focusManager.clearFocus() },
            modifier = Modifier.focusRequester(maxFocus),
        )
        Spacer(Modifier.height(16.dp))
        WorstCaseRow(page.worstCase)
        page.oddPence?.let { oddPence ->
            Spacer(Modifier.height(16.dp))
            OddPenceCard(oddPence, onUse = actions::useOddPence)
        }
        Spacer(Modifier.height(24.dp))
        BidTimingSection(page.timing, onSelect = actions::selectLead)
        Spacer(Modifier.height(24.dp))
        GroupRow(page.group, onClick = actions::chooseGroup)
    }
}

/** The facts card (DESIGN S2 item 3): the money grid without a rule, the fee, then when it ends. */
@Composable
private fun FactsCard(
    facts: ListingFacts,
    now: Instant,
) {
    val inkSoft = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = KestrelTheme.catalogue.card) {
        Column(Modifier.padding(Spacing.CardInset)) {
            MoneyBlock(facts.figures)
            listOfNotNull(facts.buyerProtection, facts.currencyNote, facts.postageNote).forEach { note ->
                Spacer(Modifier.height(4.dp))
                Text(text = note, style = MaterialTheme.typography.bodySmall, color = inkSoft)
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(thickness = 1.dp, color = KestrelTheme.catalogue.rule)
            Spacer(Modifier.height(12.dp))
            EndsRow(facts.endsAt, now)
        }
    }
}

/** "Ends Thu 13 Nov, 20:04" and, at the end, "in 4 d 1 h"; one spoken sentence. */
@Composable
private fun EndsRow(
    endsAt: Instant,
    now: Instant,
) {
    val absolute = absoluteTime(now, endsAt).replaceFirst("Today", "today")
    val left = Duration.between(now, endsAt)
    val spoken = "Ends $absolute, in ${left.spoken()}."
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = spoken },
    ) {
        SymbolIcon(
            Symbols.Schedule,
            tint = MaterialTheme.colorScheme.onSurface,
            size = 20.dp,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Ends $absolute",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).alignByBaseline(),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = figureSentence("in ", timeLeft(now, endsAt)),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

/** The group row (DESIGN S2 item 8): a card segment that opens the group picker. */
@Composable
private fun GroupRow(
    group: GroupRowState,
    onClick: () -> Unit,
) {
    val inkSoft = MaterialTheme.colorScheme.onSurfaceVariant
    val (title, detail) =
        when (group) {
            GroupRowState.None -> "Add to a group" to "Optional. Win one of several; the rest cancel."
            is GroupRowState.Chosen -> group.title to group.detail
        }
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
        colors = ListItemDefaults.segmentedColors(containerColor = KestrelTheme.catalogue.card),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        leadingContent = { SymbolIcon(Symbols.Link, tint = inkSoft, size = 24.dp) },
        trailingContent = {
            if (group is GroupRowState.Chosen) {
                Text("Change", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            } else {
                SymbolIcon(Symbols.ChevronRight, tint = inkSoft, size = 24.dp)
            }
        },
        supportingContent = {
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium.copy(lineBreak = LineBreak.Heading),
                color = inkSoft,
            )
        },
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** The pinned bar: page colour, with a top rule while content is under it. */
@Composable
private fun BottomBar(
    ruled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val rule = KestrelTheme.catalogue.rule
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .drawBehind {
                    if (ruled) drawLine(rule, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
                }.padding(start = Spacing.ColumnA, end = Spacing.ColumnA, top = 12.dp, bottom = 16.dp),
    ) {
        content()
    }
}

/**
 * The arm summary and the button (DESIGN S2 item 9): "Bids £90.00 on Thu 13 Nov at 20:04:12 GMT" with its clock time
 * in tabular figures, then "Arm snipe", or "Watch without a max" while the max is empty.
 */
@Composable
private fun ArmBarView(
    bar: ArmBar,
    ruled: Boolean,
    onArm: () -> Unit,
) {
    BottomBar(ruled = ruled) {
        val summary = bar.summary
        Text(
            text = figureLine(summary.text, summary.figure),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            minLines = 1,
        )
        if (bar.signInNeeded) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SymbolIcon(Symbols.Warning, tint = KestrelTheme.status.attention.accent, size = 16.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Sign in to arm this snipe.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = KestrelTheme.status.attention.accent,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        when (val button = bar.button) {
            is ArmButton.Arm -> {
                PrimaryButton(text = "Arm snipe", onClick = onArm, icon = Symbols.Schedule, enabled = button.enabled)
            }

            ArmButton.Watch -> {
                TonalButton(
                    text = "Watch without a max",
                    onClick = onArm,
                    icon = Symbols.Visibility,
                    height = Spacing.PrimaryButtonHeight,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** A 28 dp page-colour fade at the foot of the scrolling page, above the pinned bar (DESIGN 2.8 BottomFade). */
@Composable
private fun EdgeFade(modifier: Modifier = Modifier) {
    val page = MaterialTheme.colorScheme.background
    Box(
        modifier
            .fillMaxWidth()
            .height(Spacing.BottomFadeHeight)
            .background(Brush.verticalGradient(listOf(page.copy(alpha = 0f), page))),
    )
}
