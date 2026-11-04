package app.kestrel.ui.screens.handoff

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kestrel.AppContainer
import app.kestrel.model.Snipe
import app.kestrel.model.StatusFamily
import app.kestrel.model.TimelineEvent
import app.kestrel.platform.SnipeAction
import app.kestrel.platform.SnipeService
import app.kestrel.ui.model.Connectivity
import app.kestrel.ui.model.LotStatus
import app.kestrel.ui.model.LotStatusMapper
import app.kestrel.ui.screens.signinbrowser.BrowserSheet
import app.kestrel.ui.screens.signinbrowser.SignInPages
import app.kestrel.ui.shell.ConnectivityMonitor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/** The page over the listing: where it is, and whether something went wrong with it. */
@Immutable
data class HandOffPage(
    val host: String?,
    /** From 0 to 1 while a page loads; null once it has. */
    val progress: Float?,
    val sheet: BrowserSheet?,
    /** Bumped to replace the WebView, after its renderer is gone or to retry after a network failure. */
    val generation: Int,
)

/** A snipe's listing handed to the user. [snipe] is null while it loads and when it no longer exists ([gone]). */
@Immutable
data class HandOffUiState(
    val now: Instant,
    val snipe: Snipe?,
    val status: LotStatus?,
    val gone: Boolean,
    val page: HandOffPage,
) {
    /** The listing on its own site; null until the snipe is read. */
    val url: String? get() = snipe?.ref?.url
}

/** What the hand-off reads and starts, behind one seam so the ViewModel runs on fakes in tests. */
interface HandOffSources {
    fun snipe(id: Long): Flow<Snipe?>

    fun timeline(id: Long): Flow<List<TimelineEvent>>

    fun connectivity(): Flow<Connectivity>

    fun nowUtcMillis(): Long

    /** Starts [action] for [snipe] now, as SnipeService.start does for a job the user asked for. */
    fun start(
        action: SnipeAction,
        snipe: Snipe,
    )
}

/**
 * The listing in a visible WebView so the user can solve what eBay asks (a captcha, a verification), which Kestrel
 * never does. Done runs the dry run again, so the snipe's status follows what eBay's bid panel shows now.
 */
class HandOffViewModel(
    private val snipeId: Long,
    private val sources: HandOffSources,
    private val tickMillis: Long = TICK_MILLIS,
) : ViewModel() {
    private val page =
        MutableStateFlow(HandOffPage(host = null, progress = 0f, sheet = null, generation = 0))

    private val connection = MutableStateFlow<Connectivity>(Connectivity.Online)

    private val clock: Flow<Instant> =
        flow {
            while (true) {
                emit(now())
                delay(tickMillis)
            }
        }

    private val reads: Flow<SnipeRead> =
        sources.snipe(snipeId).map<Snipe?, SnipeRead> { SnipeRead.Read(it) }.onStart { emit(SnipeRead.Loading) }

    val state: StateFlow<HandOffUiState> =
        combine(
            reads,
            sources.timeline(snipeId).onStart { emit(emptyList()) },
            connection,
            clock,
            page,
        ) { read, events, connection, now, page ->
            val snipe = (read as? SnipeRead.Read)?.snipe
            // Until the first page reports its address, the bar shows the listing's host.
            val listingHost = snipe?.let { SignInPages.hostOf(it.ref.url) }
            HandOffUiState(
                now = now,
                snipe = snipe,
                status = snipe?.let { LotStatusMapper.statusOf(it, events, now, connection) },
                gone = read is SnipeRead.Read && read.snipe == null,
                page = page.copy(host = page.host ?: listingHost),
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            HandOffUiState(now = now(), snipe = null, status = null, gone = false, page = page.value),
        )

    init {
        viewModelScope.launch { sources.connectivity().collect { connection.value = it } }
    }

    fun onProgress(
        progress: Int,
        pageUrl: String?,
    ) {
        page.update { current ->
            current.copy(
                host = SignInPages.hostOf(pageUrl) ?: current.host,
                progress = if (progress >= FULL_PROGRESS) null else progress / FULL_PROGRESS.toFloat(),
            )
        }
    }

    fun onPageFinished(pageUrl: String) {
        val offline = connection.value !is Connectivity.Online
        page.update { current ->
            current.copy(
                host = SignInPages.hostOf(pageUrl) ?: current.host,
                progress = null,
                sheet = current.sheet ?: BrowserSheet.Offline.takeIf { offline },
            )
        }
    }

    fun onThirdPartySignIn(pageUrl: String) {
        page.update { it.copy(sheet = BrowserSheet.ThirdParty(SignInPages.providerOf(pageUrl)), progress = null) }
    }

    fun onRendererGone() {
        page.update { it.copy(sheet = BrowserSheet.PageStopped, progress = null) }
    }

    /** The sheet's button: a failed page is opened again; the others just close the sheet. */
    fun onSheetAction() {
        page.update { current ->
            when (current.sheet) {
                BrowserSheet.Offline, BrowserSheet.PageStopped -> {
                    current.copy(sheet = null, generation = current.generation + 1, progress = 0f)
                }

                else -> {
                    current.copy(sheet = null)
                }
            }
        }
    }

    fun onSheetDismissed() {
        page.update { it.copy(sheet = null) }
    }

    /**
     * Done: starts the dry run again (or a check, for a snipe without a max) and returns the words to leave for the
     * screen underneath, or null when nothing was started (the snipe ended, is gone, or its window is running).
     */
    fun done(): String? {
        val snipe = state.value.snipe ?: return null
        val action = recheckAction(snipe) ?: return null
        sources.start(action, snipe)
        return if (action == SnipeAction.DRY_RUN) DRY_RUN_AGAIN else CHECK_AGAIN
    }

    private fun now(): Instant = Instant.ofEpochMilli(sources.nowUtcMillis())

    /** The snipe as the repository answered, so "not read yet" differs from "gone". */
    private sealed interface SnipeRead {
        data object Loading : SnipeRead

        data class Read(
            val snipe: Snipe?,
        ) : SnipeRead
    }

    companion object {
        const val TICK_MILLIS = 1_000L
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val FULL_PROGRESS = 100
        const val DRY_RUN_AGAIN = "Kestrel is running the dry run again. Nothing is sent."
        const val CHECK_AGAIN = "Kestrel is checking the listing again."

        /**
         * What re-checks [snipe] after the user dealt with eBay's page: the dry run for a snipe that will bid, a
         * listing check for one that only watches. Nothing for a finished snipe, or while its bid window runs.
         */
        fun recheckAction(snipe: Snipe): SnipeAction? =
            when {
                snipe.status.isFinal -> null
                snipe.status.family == StatusFamily.BIDDING -> null
                snipe.maxBid != null -> SnipeAction.DRY_RUN
                else -> SnipeAction.CHECK
            }

        fun from(
            snipeId: Long,
            container: AppContainer,
            context: Context,
        ): HandOffViewModel = HandOffViewModel(snipeId, ContainerHandOffSources(container, context.applicationContext))
    }
}

/** The app's sources: the repository, the connection, the clock and the snipe service. */
internal class ContainerHandOffSources(
    private val container: AppContainer,
    private val context: Context,
) : HandOffSources {
    override fun snipe(id: Long): Flow<Snipe?> = container.repository.observeSnipe(id)

    override fun timeline(id: Long): Flow<List<TimelineEvent>> = container.repository.observeTimeline(id)

    override fun connectivity(): Flow<Connectivity> =
        ConnectivityMonitor.observe(context) { container.clock.nowUtcMillis() }

    override fun nowUtcMillis(): Long = container.clock.nowUtcMillis()

    override fun start(
        action: SnipeAction,
        snipe: Snipe,
    ) = SnipeService.start(context, action, snipe)
}
