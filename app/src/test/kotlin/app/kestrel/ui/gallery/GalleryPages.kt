package app.kestrel.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import app.kestrel.ui.components.CalmLoadingIndicator
import app.kestrel.ui.components.ConnectedToggleRow
import app.kestrel.ui.components.Figure
import app.kestrel.ui.components.LedgerRow
import app.kestrel.ui.components.LotPlate
import app.kestrel.ui.components.MaxField
import app.kestrel.ui.components.MoneyBlock
import app.kestrel.ui.components.MoneyFigures
import app.kestrel.ui.components.OfflineBanner
import app.kestrel.ui.components.PrimaryButton
import app.kestrel.ui.components.RollingDigits
import app.kestrel.ui.components.SectionHead
import app.kestrel.ui.components.SheetDragHandle
import app.kestrel.ui.components.SheetLayout
import app.kestrel.ui.components.SmallCaps
import app.kestrel.ui.components.StateChip
import app.kestrel.ui.components.StateLabel
import app.kestrel.ui.components.StateTonalButton
import app.kestrel.ui.components.SumRow
import app.kestrel.ui.components.SymbolIcon
import app.kestrel.ui.components.Symbols
import app.kestrel.ui.components.Timeline
import app.kestrel.ui.components.TimelineEntry
import app.kestrel.ui.components.TimelineMoment
import app.kestrel.ui.components.appendFigure
import app.kestrel.ui.components.figureSentence
import app.kestrel.ui.components.lotMoneyFigures
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.fixture.LotMoment
import app.kestrel.ui.format.countdown
import app.kestrel.ui.format.forDisplay
import app.kestrel.ui.format.formatted
import app.kestrel.ui.format.secondsText
import app.kestrel.ui.harness.PhoneInsets
import app.kestrel.ui.model.LotPhoto
import app.kestrel.ui.model.StateWords
import app.kestrel.ui.model.chipText
import app.kestrel.ui.model.label
import app.kestrel.ui.theme.KestrelTheme
import app.kestrel.ui.theme.PlateShapes
import app.kestrel.ui.theme.SheetShape
import app.kestrel.ui.theme.Spacing
import app.kestrel.ui.theme.StatusTone
import java.time.Duration

// Pages of the components gallery: every foundation component in the states the screens use, with fixture data.

private val fixture = CatalogueFixture

/** A page on the paper background, inside the phone insets, with a title. */
@Composable
fun GalleryPage(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .windowInsetsPadding(PhoneInsets)
                .padding(horizontal = Spacing.ColumnA, vertical = 8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/** A card-coloured block with the card inset, as cards and segments draw their content. */
@Composable
private fun Card(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(KestrelTheme.catalogue.card)
            .padding(Spacing.CardInset),
        content = content,
    )
}

/** One moment per status, in ladder order. */
internal val statusMoments: List<LotMoment>
    get() =
        listOf(
            fixture.polaroidWatching,
            fixture.miniDiscArmed,
            fixture.omegaOverMax,
            fixture.canonSignInNeeded,
            fixture.canonCaptcha,
            fixture.canonPhoneCheck,
            fixture.canonDryRunFailed,
            fixture.canonOffline,
            fixture.canonUnreachable,
            fixture.omegaNeedsYou,
            fixture.canonFinalMinute,
            fixture.canonBidSent,
            fixture.canonInTheLead,
            fixture.canonOutbid,
            fixture.canonNoReply,
            fixture.walkmanExtended,
            fixture.canonReadingResult,
            fixture.gameAndWatchWon,
            fixture.nintendo64Lost,
            fixture.gameBoyEndedEarly,
            fixture.gameBoyCancelled,
            fixture.walkmanCancelledByGroup,
            fixture.canonBidNotSent,
            fixture.canonBidFailed,
        )

/** Every status's list label, mapped from the fixture's domain snipes. Large text shows [moments] in parts. */
@Composable
fun StatesPage(
    modifier: Modifier = Modifier,
    title: String = "State labels",
    moments: List<LotMoment> = statusMoments,
) {
    GalleryPage(title, modifier) {
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                moments.forEach { moment ->
                    val status = moment.status
                    StateLabel(tone = status.tone, words = status.label(moment.now))
                }
            }
        }
    }
}

/** State chips, the large label and the tones on their containers. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipsPage(modifier: Modifier = Modifier) {
    GalleryPage("State chips", modifier) {
        val chips =
            listOf(
                fixture.canonArmed,
                fixture.omegaOverMax,
                fixture.canonSignInNeeded,
                fixture.canonCaptcha,
                fixture.canonFinalMinute,
                fixture.canonBidSent,
                fixture.canonInTheLead,
                fixture.canonOutbid,
                fixture.walkmanExtended,
                fixture.canonWon,
                fixture.nintendo64Lost,
                fixture.canonBidNotSent,
            )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEach { moment -> StateChip(tone = moment.status.tone, text = moment.status.chipText()) }
            StateChip(tone = StatusTone.Watching, text = "Watching")
        }
        Spacer(Modifier.height(20.dp))
        SectionHead("Large labels", count = "3 lines", modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StateLabel(StatusTone.Won, StateWords("Won", "ended Sun 9 Nov, 18:21:40 GMT"), large = true)
                StateLabel(StatusTone.Ended, StateWords("Lost", "ended Fri 7 Nov, 19:41:00 GMT"), large = true)
                StateLabel(StatusTone.Failed, StateWords("Bid not sent", "Sun 9 Nov, 18:21:34 GMT"), large = true)
            }
        }
        Spacer(Modifier.height(20.dp))
        SectionHead("Tones", count = "7", modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusTone.entries.forEach { tone ->
                val colours = KestrelTheme.status[tone]
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(colours.container),
                    contentAlignment = Alignment.Center,
                ) { SymbolIcon(tone.icon, tint = colours.accent, size = 22.dp) }
            }
        }
        Spacer(Modifier.height(20.dp))
        SectionHead("Wrapping", count = "1 row", modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Card(Modifier.width(263.dp)) {
            StateLabel(
                tone = StatusTone.Ended,
                words = fixture.walkmanCancelledByGroup.status.label(fixture.walkmanCancelledByGroup.now),
            )
        }
    }
}

/** The money grid in its hero, over-max, S3, S2 and S4 forms, the sum row and the ledger. */
@Composable
fun MoneyPage(modifier: Modifier = Modifier) {
    GalleryPage("Money", modifier) {
        MoneyGrids()
        Spacer(Modifier.height(Spacing.BlockGap))
        MoneyTotals()
    }
}

/** Large text: the money grids on their own page. */
@Composable
fun MoneyGridsPage(modifier: Modifier = Modifier) {
    GalleryPage("Money grids", modifier) { MoneyGrids() }
}

/** Large text: the sum row and the ledger on their own page. */
@Composable
fun MoneyTotalsPage(modifier: Modifier = Modifier) {
    GalleryPage("Money totals", modifier) { MoneyTotals() }
}

/** The hero, over-max and S3 grids: headroom below the max, over it, and a price that has just risen. */
@Composable
private fun MoneyGrids(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.BlockGap)) {
        Card {
            MoneyBlock(
                lotMoneyFigures(fixture.gbp("88.00"), 14, fixture.gbp("120.00"), fixture.gbp("6.50"), StatusTone.Armed),
            )
        }
        Card {
            MoneyBlock(
                lotMoneyFigures(
                    fixture.gbp("2150.00"),
                    31,
                    fixture.gbp("1900.00"),
                    fixture.gbp("12.95"),
                    StatusTone.Attention,
                ),
            )
        }
        Card {
            MoneyBlock(
                lotMoneyFigures(
                    fixture.gbp("96.00"),
                    16,
                    fixture.gbp("120.00"),
                    fixture.gbp("6.50"),
                    StatusTone.Bidding,
                    previousPrice = fixture.gbp("88.00"),
                ),
            )
        }
    }
}

/** The S2 grid with the sum row, and the S4 ledger. */
@Composable
private fun MoneyTotals(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.BlockGap)) {
        Card {
            MoneyBlock(
                MoneyFigures(
                    startLabel = "Current · 7 bids",
                    startValue = fixture.gbp("64.00").formatted(),
                    endLabel = "Postage",
                    endValue = fixture.gbp("8.95").formatted(),
                    spoken = "Current bid £64.00, 7 bids. Postage £8.95.",
                ),
            )
            Spacer(Modifier.height(12.dp))
            SumRow(max = "£90.00", postage = "£8.95", total = "£98.95")
        }
        Card {
            LedgerRow("Final price", "£101.00")
            LedgerRow("Postage", "£6.50")
            LedgerRow("Total", "£107.50", total = true)
        }
    }
}

/** The type scale, the figure rules and the rolling digits. */
@Composable
fun TypePage(modifier: Modifier = Modifier) {
    GalleryPage("Type and figures", modifier) {
        val ink = MaterialTheme.colorScheme.onSurface
        val soft = MaterialTheme.colorScheme.onSurfaceVariant
        Text("You won", style = MaterialTheme.typography.displayMedium, color = ink)
        Text("Outbid", style = MaterialTheme.typography.displaySmall, color = ink)
        Text(
            fixture.canon.title.forDisplay(),
            style = KestrelTheme.type.lotTitle,
            color = ink,
            modifier = Modifier.width(240.dp),
        )
        Text(
            fixture.miniDisc.title.forDisplay(),
            style = KestrelTheme.type.lotTitleSmall,
            color = ink,
            modifier = Modifier.width(150.dp),
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            RollingDigits(
                "36",
                KestrelTheme.type.countdown,
                color = ink,
                contentDescription = "36 seconds left",
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(8.dp))
            Text("s left", style = KestrelTheme.type.countdownUnit, color = ink, modifier = Modifier.alignByBaseline())
            Spacer(Modifier.weight(1f))
            Text(
                Duration.ofSeconds(787).countdown(),
                style = KestrelTheme.type.countdownMedium,
                color = ink,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("£96.00", style = KestrelTheme.type.priceHero, color = ink, modifier = Modifier.alignByBaseline())
            Text("£120.00", style = KestrelTheme.type.priceLarge, color = ink, modifier = Modifier.alignByBaseline())
            Text("£24.00", style = KestrelTheme.type.priceRow, color = ink, modifier = Modifier.alignByBaseline())
        }
        Spacer(Modifier.height(12.dp))
        SectionHead("Word spaces", count = "Q4", modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Card {
            val bodyLarge = MaterialTheme.typography.bodyLarge
            val samples =
                listOf(
                    figureSentence("Bids at ", "18:21:34", ", ${secondsText(6)} before"),
                    figureSentence("Ends ", "in 4\u202Fd 1\u202Fh"),
                    buildAnnotatedString { append("Includes £3.13 Buyer Protection") },
                    buildAnnotatedString {
                        append("Ends Thu 13 Nov, ")
                        appendFigure("20:04")
                    },
                )
            samples.forEach { sample ->
                Text(sample, style = bodyLarge, color = ink)
                Text(sample.text, style = bodyLarge, color = soft)
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(4.dp))
            Row {
                Figure("18:21:33.9", MaterialTheme.typography.titleMedium, color = ink)
                Spacer(Modifier.width(16.dp))
                Figure("18:21:40", MaterialTheme.typography.titleMedium, color = ink)
            }
        }
        Spacer(Modifier.height(12.dp))
        SmallCaps("Current · 14 bids")
        Text("Final minute", style = KestrelTheme.type.stateWord, color = KestrelTheme.status.bidding.accent)
        Text(
            "Body large: Kestrel bids for you in the last seconds of an eBay auction.",
            style = MaterialTheme.typography.bodyLarge,
            color = ink,
        )
        Text(
            "Body small: Usually less, never more than your max.",
            style = MaterialTheme.typography.bodySmall,
            color = soft,
        )
    }
}

/** The S3 timeline and the S4 receipt. */
@Composable
fun TimelinePage(modifier: Modifier = Modifier) {
    GalleryPage("Timeline", modifier) {
        LotTimeline()
        Spacer(Modifier.height(16.dp))
        ReceiptTimeline()
    }
}

/** Large text: the S3 timeline on its own page. */
@Composable
fun LotTimelinePage(modifier: Modifier = Modifier) {
    GalleryPage("Timeline", modifier) { LotTimeline() }
}

/** Large text: the S4 receipt on its own page. */
@Composable
fun ReceiptPage(modifier: Modifier = Modifier) {
    GalleryPage("Receipt", modifier) { ReceiptTimeline() }
}

/** The S3 timeline in the final minute: two future events, now, and the past. */
@Composable
private fun LotTimeline(modifier: Modifier = Modifier) {
    Column(modifier) {
        SectionHead("Timeline", modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        Timeline(
            listOf(
                TimelineEntry(
                    "18:21:40",
                    "Auction ends",
                    "Kestrel keeps following if it extends",
                    TimelineMoment.Future,
                ),
                TimelineEntry(
                    "18:21:34",
                    "Kestrel bids £120.00",
                    "Your max, ${secondsText(6)} before the end",
                    TimelineMoment.Future,
                ),
                TimelineEntry("18:21:04", "Now", moment = TimelineMoment.Now),
                TimelineEntry("18:20:51", "Price rose to £96.00", "16 bids"),
                TimelineEntry("18:20:40", "Final minute", "Checking every ${secondsText(2)}"),
                TimelineEntry(
                    "18:11:40",
                    "Dry run passed",
                    "Bid panel opened, £120.00 typed, nothing sent",
                    tone = StatusTone.Won,
                ),
                TimelineEntry("09:14:02", "Armed", "£120.00 max, ${secondsText(6)} lead"),
            ),
        )
    }
}

/** The S4 receipt, newest first, in tenths, down to the day it was armed. */
@Composable
private fun ReceiptTimeline(modifier: Modifier = Modifier) {
    Column(modifier) {
        SectionHead("Receipt", modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        Timeline(
            listOf(
                TimelineEntry(
                    "18:21:46.2",
                    "Won at £101.00",
                    "Confirmed on the listing page",
                    tone = StatusTone.Won,
                ),
                TimelineEntry("18:21:40.0", "Auction ended", "On time, no extension"),
                TimelineEntry("18:21:34.7", "eBay replied in 0.8\u202Fs", "In the lead"),
                TimelineEntry(
                    "18:21:33.9",
                    "Bid of £120.00 sent",
                    "6.1\u202Fs before the end",
                    tone = StatusTone.Bidding,
                ),
                TimelineEntry("Wed 5 Nov 21:12:44", "Armed with a £65.00 max", "${secondsText(6)} lead"),
            ),
        )
    }
}

/** Lot plates at their sizes: cutouts and scenes, rows, the S2 header, the hero and sheets. */
@Composable
fun PlatesPage(modifier: Modifier = Modifier) {
    GalleryPage("Plates", modifier) {
        val canon = fixture.photoOf(fixture.canon)
        Card {
            LotPlate(canon, Modifier.fillMaxWidth().height(128.dp), shape = PlateShapes.Hero)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(fixture.omega, fixture.nintendo64, fixture.polaroid, fixture.gameBoy).forEach {
                    LotPlate(fixture.photoOf(it), Modifier.size(72.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                LotPlate(LotPhoto(fixture.sonyA700.imageUrl), Modifier.size(104.dp), cutoutInset = 12.dp)
                LotPlate(
                    fixture.photoOf(fixture.walkman),
                    Modifier.size(40.dp),
                    shape = PlateShapes.Sheet,
                    cutoutInset = 4.dp,
                )
                LotPlate(null, Modifier.size(72.dp))
            }
        }
        Spacer(Modifier.height(Spacing.BlockGap))
        LotPlate(
            fixture.photoOf(fixture.gameAndWatch),
            Modifier.fillMaxWidth().height(176.dp),
            shape = PlateShapes.Result,
            cutoutInset = 18.dp,
        )
        Spacer(Modifier.height(Spacing.BlockGap))
        LotPlate(
            fixture.photoOf(fixture.miniDisc),
            Modifier.fillMaxWidth().height(136.dp),
            shape = PlateShapes.Result,
            cutoutInset = 18.dp,
        )
    }
}

/** Buttons, toggles, the max field, banners, loading and the sheet frame. */
@Composable
fun ControlsPage(
    modifier: Modifier = Modifier,
    reduceMotionSample: Boolean = false,
) {
    GalleryPage("Controls", modifier) {
        PrimaryButton("Arm snipe", onClick = {}, icon = Symbols.Schedule)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StateTonalButton(StatusTone.Attention, "Sign in", onClick = {})
            StateTonalButton(StatusTone.Attention, "Raise max", onClick = {}, icon = Symbols.ArrowUpward)
            StateTonalButton(StatusTone.Failed, "Sign in", onClick = {})
        }
        Spacer(Modifier.height(12.dp))
        ConnectedToggleRow(
            options = listOf(3, 6, 10).map { secondsText(it.toLong()) },
            selectedIndex = 1,
            onSelect = {},
        )
        Spacer(Modifier.height(12.dp))
        val filled = remember { TextFieldState("90.00") }
        MaxField(state = filled, minimum = "£66.00")
        Spacer(Modifier.height(8.dp))
        val tooLow = remember { TextFieldState("60.00") }
        MaxField(state = tooLow, minimum = "£66.00", error = "Too low: eBay needs at least £66.00")
        Spacer(Modifier.height(12.dp))
        OfflineBanner("Offline since 18:02", "Prices are from 18:02. Kestrel can’t bid until the phone reconnects.")
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CalmLoadingIndicator()
            Spacer(Modifier.width(8.dp))
            Text("Bid sent · waiting for eBay", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(16.dp))
            CalmLoadingIndicator(size = 48.dp, contained = true, color = MaterialTheme.colorScheme.primary)
        }
        if (reduceMotionSample) Text("Reduced motion", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(SheetShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SheetDragHandle()
            SheetLayout(title = "Edit your max", windowInsets = WindowInsets(0)) {
                Text(
                    "Change it until 18:21:31, ${secondsText(3)} before the bid.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
