package app.kestrel.ui.screens.add

import app.kestrel.AppContainer
import app.kestrel.data.AddResult
import app.kestrel.data.EditResult
import app.kestrel.ebay.ItemUrlParser
import app.kestrel.model.EbaySite
import app.kestrel.model.Listing
import app.kestrel.model.Money
import app.kestrel.model.ParsedLink
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeGroup
import app.kestrel.ui.shell.UiPreferences
import app.kestrel.web.EbaySession
import app.kestrel.web.FetchResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.time.Instant

/** What S2 needs from the app, behind one seam so its ViewModel runs on the JVM with fakes. */
interface AddServices {
    /** Finds a listing in shared or pasted text. Pure: it never touches the network. */
    fun parse(text: String): ParsedLink

    /** Loads the listing in a headless page, following short links. */
    suspend fun fetch(link: ParsedLink): FetchResult

    /** Every snipe the user has not deleted, to find one already open on the same listing. */
    fun snipes(): Flow<List<Snipe>>

    fun groups(): Flow<List<SnipeGroup>>

    /** Stores the snipe; [newGroupName] creates its group with it, only if the snipe is added. */
    suspend fun add(
        listing: Listing,
        maxBid: Money?,
        leadSeconds: Int,
        groupId: Long?,
        newGroupName: String? = null,
    ): AddResult

    suspend fun defaultLeadSeconds(): Int

    /** Settings "Suggest odd pence": whether S2 offers £90.37 for a round £90.00. */
    suspend fun suggestOddPence(): Boolean = true

    /** A cheap hint (no network): whether the phone holds a signed-in eBay session for [site]. */
    fun isSignedIn(site: EbaySite): Boolean

    /** True UTC now (SNTP-corrected when synced). */
    fun nowUtcMillis(): Long

    /** [nowUtcMillis] once a second, for the countdowns. */
    fun ticks(): Flow<Instant>
}

/** What the edit-max sheet needs from the app. */
interface EditMaxServices {
    fun snipe(snipeId: Long): Flow<Snipe?>

    /** Settings "Suggest odd pence": whether the set-max sheet offers £90.37 for a round £90.00. */
    suspend fun suggestOddPence(): Boolean = true

    suspend fun updateMax(
        snipeId: Long,
        maxBid: Money?,
    ): EditResult

    suspend fun setLead(
        snipeId: Long,
        leadSeconds: Int,
    ): EditResult

    fun nowUtcMillis(): Long

    fun ticks(): Flow<Instant>

    /** Runs [work] outside any screen, so an Undo still completes after the sheet and its screen have gone. */
    fun runDetached(work: suspend () -> Unit)
}

/** [AddServices] over the app's container: the listing fetcher, the repository and [app.kestrel.platform.SnipeActions]. */
internal class ContainerAddServices(
    private val container: AppContainer,
) : AddServices {
    override fun parse(text: String): ParsedLink = ItemUrlParser.parse(text)

    override suspend fun suggestOddPence(): Boolean =
        UiPreferences
            .of(container.context)
            .values
            .first()
            .suggestOddPence

    override suspend fun fetch(link: ParsedLink): FetchResult = container.listingFetcher.fetch(link)

    override fun snipes(): Flow<List<Snipe>> = container.repository.observeSnipes()

    override fun groups(): Flow<List<SnipeGroup>> = container.repository.observeGroups()

    override suspend fun add(
        listing: Listing,
        maxBid: Money?,
        leadSeconds: Int,
        groupId: Long?,
        newGroupName: String?,
    ): AddResult = container.snipeActions.add(listing, maxBid, leadSeconds, groupId, newGroupName)

    override suspend fun defaultLeadSeconds(): Int = container.settings.current().defaultLeadSeconds

    override fun isSignedIn(site: EbaySite): Boolean = EbaySession.hasCookies(site.host)

    override fun nowUtcMillis(): Long = container.clock.nowUtcMillis()

    override fun ticks(): Flow<Instant> = tickingNow(::nowUtcMillis)
}

/** [EditMaxServices] over the app's container. */
internal class ContainerEditMaxServices(
    private val container: AppContainer,
) : EditMaxServices {
    override fun snipe(snipeId: Long): Flow<Snipe?> = container.repository.observeSnipe(snipeId)

    override suspend fun suggestOddPence(): Boolean =
        UiPreferences
            .of(container.context)
            .values
            .first()
            .suggestOddPence

    override suspend fun updateMax(
        snipeId: Long,
        maxBid: Money?,
    ): EditResult = container.snipeActions.updateMax(snipeId, maxBid)

    override suspend fun setLead(
        snipeId: Long,
        leadSeconds: Int,
    ): EditResult = container.snipeActions.setLead(snipeId, leadSeconds)

    override fun nowUtcMillis(): Long = container.clock.nowUtcMillis()

    override fun ticks(): Flow<Instant> = tickingNow(::nowUtcMillis)

    override fun runDetached(work: suspend () -> Unit) {
        container.applicationScope.launch { work() }
    }
}

/** The time now, once per [periodMillis], on the clock's whole seconds so every countdown on screen ticks together. */
internal fun tickingNow(
    nowUtcMillis: () -> Long,
    periodMillis: Long = 1_000L,
): Flow<Instant> =
    flow {
        while (true) {
            val now = nowUtcMillis()
            emit(Instant.ofEpochMilli(now))
            delay(periodMillis - Math.floorMod(now, periodMillis))
        }
    }
