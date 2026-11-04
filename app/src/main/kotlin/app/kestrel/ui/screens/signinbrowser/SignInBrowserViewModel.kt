package app.kestrel.ui.screens.signinbrowser

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kestrel.AppContainer
import app.kestrel.data.SnipeRepository
import app.kestrel.model.Snipe
import app.kestrel.model.TimelineEvent
import app.kestrel.platform.SnipeAction
import app.kestrel.platform.SnipeService
import app.kestrel.ui.model.Connectivity
import app.kestrel.ui.model.DryRunCause
import app.kestrel.ui.model.LotStatus
import app.kestrel.ui.model.LotStatusMapper
import app.kestrel.ui.screens.handoff.HandOffViewModel
import app.kestrel.ui.shell.ConnectivityMonitor
import app.kestrel.ui.shell.UiPreferences
import app.kestrel.web.EbaySession
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/** A sheet over eBay's page: the success, or one of the failures of DESIGN S6. */
sealed interface BrowserSheet {
    /** Signed in; [name] is the eBay user name, when the page gave one. */
    data class SignedIn(
        val name: String?,
    ) : BrowserSheet

    /** The page tried to open Google, Facebook or Apple sign-in, which refuse apps. */
    data class ThirdParty(
        val provider: String,
    ) : BrowserSheet

    /** A page could not reach eBay: it finished loading while the phone was offline, or failed to load. */
    data object Offline : BrowserSheet

    /** Android closed the page's renderer; the WebView has to be replaced. */
    data object PageStopped : BrowserSheet
}

/** The sign-in browser: the host bar, the load progress and the sheet over the page. */
@Immutable
data class SignInBrowserUiState(
    /** The host of the page the WebView shows now ("signin.ebay.co.uk"). */
    val host: String?,
    /** From 0 to 1 while a page loads; null once it has. */
    val progress: Float?,
    val sheet: BrowserSheet?,
    /** Bumped to replace the WebView, after its renderer is gone or to retry after a network failure. */
    val pageGeneration: Int,
    val signedIn: Boolean,
)

/** The session side of signing in, behind one seam so the ViewModel runs on fakes in tests. */
interface SignInSession {
    /** True when the WebView cookie store holds a signed-in eBay cookie for [host]. A hint only. */
    fun hasSessionCookie(host: String): Boolean

    /** Writes the cookies to disk and stores [name] for the app to show; outlives the screen. */
    fun keep(name: String?)

    fun connectivity(): Flow<Connectivity>
}

/**
 * Follows eBay's sign-in page in a visible WebView. A normal eBay page whose header says the user is signed in ends
 * it: Kestrel keeps the session and shows "Signed in as oakshelf". Google, Facebook and Apple sign-in are refused by
 * the web layer and explained here; a page that finishes offline asks to try again.
 */
class SignInBrowserViewModel(
    private val session: SignInSession,
) : ViewModel() {
    val url: String = SignInPages.signInUrl

    private val mutableState =
        MutableStateFlow(
            SignInBrowserUiState(
                host = SignInPages.hostOf(url),
                progress = 0f,
                sheet = null,
                pageGeneration = 0,
                signedIn = false,
            ),
        )
    val state: StateFlow<SignInBrowserUiState> = mutableState.asStateFlow()

    private val noticeChannel = Channel<String>(Channel.BUFFERED)

    /** One-off words for the snackbar, such as a refused link. */
    val notices: Flow<String> = noticeChannel.receiveAsFlow()

    private var online = true

    init {
        viewModelScope.launch { session.connectivity().collect { online = it is Connectivity.Online } }
    }

    fun onProgress(
        progress: Int,
        pageUrl: String?,
    ) {
        mutableState.update { current ->
            current.copy(
                host = SignInPages.hostOf(pageUrl) ?: current.host,
                progress = if (progress >= FULL_PROGRESS) null else progress / FULL_PROGRESS.toFloat(),
            )
        }
    }

    /** True when the screen should read eBay's header on this page, to see whether it is signed in. */
    fun onPageFinished(
        pageUrl: String,
        title: String,
    ): Boolean {
        mutableState.update { it.copy(host = SignInPages.hostOf(pageUrl) ?: it.host, progress = null) }
        val current = mutableState.value
        if (current.signedIn) return false
        if (!online) {
            if (current.sheet == null) mutableState.update { it.copy(sheet = BrowserSheet.Offline) }
            return false
        }
        return SignInPages.canProveSignIn(pageUrl, title)
    }

    /**
     * True when the screen should read eBay's header on a page that just drew. The signed-in home page can keep
     * loading for a long time (a stalled ad or tracker), so success is not left to [onPageFinished] alone.
     */
    fun onPageCommitted(
        pageUrl: String,
        title: String,
    ): Boolean {
        val current = mutableState.value
        return !current.signedIn && current.sheet == null && online && SignInPages.canProveSignIn(pageUrl, title)
    }

    /**
     * True when the page the user closes on could prove the sign-in, so the screen reads its header first
     * ([onHeaderRead]): closing while the signed-in page still loads is a success, not "Sign-in didn’t finish."
     */
    fun shouldReadHeaderBeforeClosing(
        pageUrl: String?,
        title: String?,
    ): Boolean = !mutableState.value.signedIn && pageUrl != null && SignInPages.canProveSignIn(pageUrl, title.orEmpty())

    /** [result] is what evaluateJavascript returned for SignInPages.headerScript on [pageUrl]. */
    fun onHeaderRead(
        pageUrl: String,
        result: String?,
    ) {
        if (mutableState.value.signedIn) return
        val host = SignInPages.hostOf(pageUrl)
        val evidence =
            SignInPages.evidence(
                SignInPages.parseHeader(result),
                host?.let(session::hasSessionCookie) == true,
            )
        if (evidence !is SignInEvidence.SignedIn) return
        session.keep(evidence.name)
        mutableState.update { it.copy(signedIn = true, sheet = BrowserSheet.SignedIn(evidence.name)) }
    }

    /**
     * eBay's page did not load although the phone counts as online (DNS, a timeout, a reset, TLS): the WebView shows
     * Chromium's error page, so the sheet offers to try again. A sheet already up (Google sign-in refused) stays.
     */
    fun onMainFrameError(pageUrl: String) {
        val current = mutableState.value
        if (current.signedIn || current.sheet != null) return
        mutableState.update {
            it.copy(host = SignInPages.hostOf(pageUrl) ?: it.host, sheet = BrowserSheet.Offline, progress = null)
        }
    }

    fun onThirdPartySignIn(pageUrl: String) {
        if (mutableState.value.signedIn) return
        mutableState.update {
            it.copy(
                sheet = BrowserSheet.ThirdParty(SignInPages.providerOf(pageUrl)),
                progress = null,
            )
        }
    }

    fun onNavigationBlocked() {
        noticeChannel.trySend(ONLY_EBAY_PAGES)
    }

    fun onRendererGone() {
        mutableState.update { it.copy(sheet = BrowserSheet.PageStopped, progress = null) }
    }

    /** The sheet's button. True when the screen should close (after the success). */
    fun onSheetAction(): Boolean {
        val sheet = mutableState.value.sheet
        mutableState.update { current ->
            when (sheet) {
                BrowserSheet.Offline, BrowserSheet.PageStopped -> {
                    current.copy(sheet = null, pageGeneration = current.pageGeneration + 1, progress = 0f)
                }

                else -> {
                    current.copy(sheet = null)
                }
            }
        }
        return sheet is BrowserSheet.SignedIn
    }

    /** Swiped away or tapped outside. True when the screen should close (after the success). */
    fun onSheetDismissed(): Boolean {
        val sheet = mutableState.value.sheet
        mutableState.update { it.copy(sheet = null) }
        return sheet is BrowserSheet.SignedIn
    }

    companion object {
        private const val FULL_PROGRESS = 100
        const val ONLY_EBAY_PAGES = "Kestrel only opens eBay’s own pages here."

        fun from(
            container: AppContainer,
            context: Context,
        ): SignInBrowserViewModel =
            SignInBrowserViewModel(ContainerSignInSession(container, context.applicationContext))
    }
}

/**
 * What a successful sign-in runs again, so the lots that met eBay's sign-in page leave "Sign-in needed" and "Dry run
 * failed" now rather than at the next scheduled check, which can be hours away. Lots whose bid window runs are left
 * to it ([HandOffViewModel.recheckAction]).
 */
internal object SignInRechecks {
    suspend fun due(
        repository: SnipeRepository,
        nowUtcMillis: Long,
    ): List<Pair<SnipeAction, Snipe>> {
        val now = Instant.ofEpochMilli(nowUtcMillis)
        return repository.pendingSnipes().mapNotNull { snipe ->
            actionFor(snipe, repository.observeTimeline(snipe.id).first(), now)?.let { it to snipe }
        }
    }

    /**
     * A check clears "Sign-in needed" once the page shows the user signed in; a dry run that failed stays failed
     * until a dry run passes (ListingCheckProcedure.statusAfterCheck), so that one runs the dry run again.
     */
    fun actionFor(
        snipe: Snipe,
        events: List<TimelineEvent>,
        now: Instant,
    ): SnipeAction? {
        HandOffViewModel.recheckAction(snipe) ?: return null
        val dryRunAgain = snipe.maxBid != null && snipe.dryRunPassed == false
        return when (val status = LotStatusMapper.statusOf(snipe, events, now, Connectivity.Online)) {
            is LotStatus.SignInNeeded -> if (dryRunAgain) SnipeAction.DRY_RUN else SnipeAction.CHECK
            is LotStatus.DryRunFailed -> SnipeAction.DRY_RUN.takeIf { status.cause == DryRunCause.SIGN_IN_PAGE }
            else -> null
        }
    }
}

/** The app's session: the WebView cookie store, the settings and the connection. */
internal class ContainerSignInSession(
    private val container: AppContainer,
    private val context: Context,
) : SignInSession {
    override fun hasSessionCookie(host: String): Boolean = EbaySession.hasCookies(host)

    override fun keep(name: String?) {
        container.applicationScope.launch {
            EbaySession.flush()
            if (name != null) container.settings.setSignedInName(name)
            // For Settings: "Signed in on this phone since Sun 2 Nov".
            UiPreferences.of(context).recordSignIn(container.clock.nowUtcMillis())
            // Lots that met eBay's sign-in page read as paused until Kestrel looks again, so it looks now.
            for ((action, snipe) in SignInRechecks.due(container.repository, container.clock.nowUtcMillis())) {
                SnipeService.start(context, action, snipe)
            }
        }
    }

    override fun connectivity(): Flow<Connectivity> =
        ConnectivityMonitor.observe(context) { container.clock.nowUtcMillis() }
}
