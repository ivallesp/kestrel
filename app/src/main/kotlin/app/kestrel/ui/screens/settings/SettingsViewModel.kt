package app.kestrel.ui.screens.settings

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kestrel.AppContainer
import app.kestrel.BuildConfig
import app.kestrel.data.Settings
import app.kestrel.data.ThemeMode
import app.kestrel.model.EbaySite
import app.kestrel.model.Snipe
import app.kestrel.ui.screens.ready.CheckRows
import app.kestrel.ui.screens.ready.CheckStanding
import app.kestrel.ui.screens.ready.ContainerReadySources
import app.kestrel.ui.screens.ready.ReadySources
import app.kestrel.ui.shell.UiPreferenceValues
import app.kestrel.ui.shell.UiPreferences
import app.kestrel.web.EbaySession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

/** Who is signed in to eBay on this phone. */
sealed interface AccountState {
    /**
     * [name] is the eBay user name; null when only the session cookie says so. [since] is when the phone signed in
     * through Kestrel, when the screens saw it happen.
     */
    data class SignedIn(
        val name: String?,
        val since: Instant? = null,
    ) : AccountState

    data object SignedOut : AccountState
}

/** The THIS PHONE row: how many checks need attention, as of [checkedAt]. */
@Immutable
data class PhoneReadiness(
    val problems: Int,
    val checkedAt: Instant,
)

/** A sheet over Settings. */
enum class SettingsSheet { HowKestrelBids, Licences, FontLicence }

@Immutable
data class SettingsUiState(
    val now: Instant,
    val account: AccountState,
    /** Snipes that will bid, for the sign-out dialog. */
    val armedSnipes: Int,
    val defaultLeadSeconds: Int,
    /** "Suggest odd pence": S2 and the set-max sheets offer £90.37 for a round £90.00. */
    val suggestOddPence: Boolean = true,
    val themeMode: ThemeMode,
    /** Null until the first readiness check finishes. */
    val readiness: PhoneReadiness?,
    /** "1.0 (1)" */
    val version: String,
    val confirmingSignOut: Boolean,
    val sheet: SettingsSheet?,
)

/** What Settings reads and changes, behind one seam so the ViewModel runs on fakes in tests. */
interface SettingsSources {
    fun settings(): Flow<Settings>

    fun snipes(): Flow<List<Snipe>>

    /** True when the WebView cookie store holds a signed-in eBay UK session. */
    fun hasSessionCookie(): Boolean

    suspend fun setDefaultLeadSeconds(seconds: Int)

    suspend fun setThemeMode(mode: ThemeMode)

    /** The screens' own choices: odd pence, and when the phone signed in. */
    fun uiPreferences(): Flow<UiPreferenceValues> = flowOf(UiPreferenceValues())

    suspend fun setSuggestOddPence(on: Boolean) {}

    /** Removes the eBay cookies and the stored name. */
    suspend fun signOut()

    fun nowUtcMillis(): Long
}

/** Settings: reads the store and the readiness checks; every change goes straight to the store. */
class SettingsViewModel(
    private val sources: SettingsSources,
    private val readiness: ReadySources,
    private val version: String = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
) : ViewModel() {
    private val phone = MutableStateFlow<PhoneReadiness?>(null)
    private val confirmingSignOut = MutableStateFlow(false)
    private val sheet = MutableStateFlow<SettingsSheet?>(null)
    private val sessionCookie = MutableStateFlow(sources.hasSessionCookie())
    private var checking = false

    private val messageChannel = Channel<String>(Channel.BUFFERED)

    /** One-off words for the snackbar. */
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    private val dialogs = combine(confirmingSignOut, sheet) { confirming, sheet -> confirming to sheet }

    private val stored = combine(sources.settings(), sources.uiPreferences()) { settings, ui -> settings to ui }

    val state: StateFlow<SettingsUiState> =
        combine(
            stored,
            sources.snipes(),
            phone,
            sessionCookie,
            dialogs,
        ) { (settings, ui), snipes, phone, cookie, dialogs ->
            val name = settings.signedInName
            val since = ui.signedInSinceUtcMillis?.let(Instant::ofEpochMilli)
            SettingsUiState(
                now = now(),
                account = if (name != null || cookie) AccountState.SignedIn(name, since) else AccountState.SignedOut,
                armedSnipes = snipes.count { it.maxBid != null && it.status.isActiveBid },
                defaultLeadSeconds = settings.defaultLeadSeconds,
                suggestOddPence = ui.suggestOddPence,
                themeMode = settings.themeMode,
                readiness = phone,
                version = version,
                confirmingSignOut = dialogs.first,
                sheet = dialogs.second,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            SettingsUiState(
                now = now(),
                account = AccountState.SignedOut,
                armedSnipes = 0,
                defaultLeadSeconds = Settings.DEFAULT_LEAD_SECONDS,
                themeMode = ThemeMode.SYSTEM,
                readiness = null,
                version = version,
                confirmingSignOut = false,
                sheet = null,
            ),
        )

    /** Runs the readiness checks again, for the THIS PHONE row (on every return to Settings). */
    fun refresh() {
        sessionCookie.value = sources.hasSessionCookie()
        if (checking) return
        checking = true
        viewModelScope.launch {
            try {
                val items = readiness.check()
                val checkedAt = now()
                val facts = readiness.facts(checkedAt)
                val nextBid = CheckRows.nextBid(readiness.snipes().first(), checkedAt)
                val rows = CheckRows.build(items, facts, nextBid, checkedAt)
                phone.value = PhoneReadiness(rows.count { it.standing == CheckStanding.Problem }, checkedAt)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "The readiness check failed", error)
            } finally {
                checking = false
            }
        }
    }

    fun setDefaultLead(seconds: Int) = launchStore { sources.setDefaultLeadSeconds(seconds) }

    fun setThemeMode(mode: ThemeMode) = launchStore { sources.setThemeMode(mode) }

    fun setSuggestOddPence(on: Boolean) = launchStore { sources.setSuggestOddPence(on) }

    fun askToSignOut() {
        confirmingSignOut.value = true
    }

    fun cancelSignOut() {
        confirmingSignOut.value = false
    }

    fun signOut() {
        confirmingSignOut.value = false
        viewModelScope.launch {
            sources.signOut()
            sessionCookie.value = sources.hasSessionCookie()
            messageChannel.trySend(SIGNED_OUT)
            refresh()
        }
    }

    fun openSheet(which: SettingsSheet) {
        sheet.value = which
    }

    fun closeSheet() {
        sheet.value = null
    }

    private fun launchStore(change: suspend () -> Unit) {
        viewModelScope.launch { change() }
    }

    private fun now(): Instant = Instant.ofEpochMilli(sources.nowUtcMillis())

    companion object {
        private const val TAG = "SettingsViewModel"
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        const val SIGNED_OUT = "Signed out of eBay."

        fun from(
            container: AppContainer,
            context: Context,
        ): SettingsViewModel =
            SettingsViewModel(
                ContainerSettingsSources(container),
                ContainerReadySources(container, context.applicationContext),
            )
    }
}

/** The app's settings store, repository and WebView session. */
internal class ContainerSettingsSources(
    private val container: AppContainer,
) : SettingsSources {
    override fun settings(): Flow<Settings> = container.settings.settings

    override fun snipes(): Flow<List<Snipe>> = container.repository.observeSnipes()

    override fun hasSessionCookie(): Boolean = EbaySession.hasCookies(EbaySite.UK.host)

    override suspend fun setDefaultLeadSeconds(seconds: Int) = container.settings.setDefaultLeadSeconds(seconds)

    override suspend fun setThemeMode(mode: ThemeMode) = container.settings.setThemeMode(mode)

    private val ui by lazy { UiPreferences.of(container.context) }

    override fun uiPreferences(): Flow<UiPreferenceValues> = ui.values

    override suspend fun setSuggestOddPence(on: Boolean) = ui.setSuggestOddPence(on)

    override suspend fun signOut() {
        EbaySession.signOut()
        container.settings.setSignedInName(null)
        ui.clearSignIn()
    }

    override fun nowUtcMillis(): Long = container.clock.nowUtcMillis()
}
