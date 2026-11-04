package app.kestrel.ui.screens.add

import app.kestrel.data.AddResult
import app.kestrel.data.EditResult
import app.kestrel.ebay.ItemUrlParser
import app.kestrel.model.EbaySite
import app.kestrel.model.Listing
import app.kestrel.model.Money
import app.kestrel.model.ParsedLink
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeGroup
import app.kestrel.web.FetchResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

/** One call to [FakeAddServices.add]. */
data class AddCall(
    val listing: Listing,
    val maxBid: Money?,
    val leadSeconds: Int,
    val groupId: Long?,
    val newGroupName: String? = null,
)

/**
 * S2's services without the phone: links are parsed by the real [ItemUrlParser] (pure), fetches answer from
 * [fetchResult] and never touch a network, and adds are recorded.
 */
class FakeAddServices(
    var fetchResult: (ParsedLink) -> FetchResult = { FetchResult.Loaded(AddFixtures.sonyA700) },
    snipes: List<Snipe> = emptyList(),
    groups: List<SnipeGroup> = emptyList(),
    var signedIn: Boolean = true,
    var defaultLead: Int = 6,
    nowMillis: Long = AddFixtures.now.toEpochMilli(),
) : AddServices {
    val snipeList = MutableStateFlow(snipes)
    val groupList = MutableStateFlow(groups)
    val groupWins = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val clock = MutableStateFlow(Instant.ofEpochMilli(nowMillis))
    val fetched = mutableListOf<ParsedLink>()
    val added = mutableListOf<AddCall>()
    var addResult: (
        AddCall,
    ) -> AddResult = { call -> AddResult.Added(AddFixtures.added(id = 42, listing = call.listing, max = call.maxBid)) }

    override fun parse(text: String): ParsedLink = ItemUrlParser.parse(text)

    override suspend fun fetch(link: ParsedLink): FetchResult {
        fetched += link
        return fetchResult(link)
    }

    override fun snipes(): Flow<List<Snipe>> = snipeList

    override fun groups(): Flow<List<SnipeGroup>> = groupList

    override fun groupWins(): Flow<Map<Long, Int>> = groupWins

    override suspend fun add(
        listing: Listing,
        maxBid: Money?,
        leadSeconds: Int,
        groupId: Long?,
        newGroupName: String?,
    ): AddResult {
        val call = AddCall(listing, maxBid, leadSeconds, groupId, newGroupName)
        added += call
        return addResult(call)
    }

    override suspend fun defaultLeadSeconds(): Int = defaultLead

    override fun isSignedIn(site: EbaySite): Boolean = signedIn

    override fun nowUtcMillis(): Long = clock.value.toEpochMilli()

    override fun ticks(): Flow<Instant> = clock
}

/** One change the edit sheet asked for. */
sealed interface EditCall {
    data class Max(
        val snipeId: Long,
        val maxBid: Money?,
    ) : EditCall

    data class Lead(
        val snipeId: Long,
        val leadSeconds: Int,
    ) : EditCall
}

/** The edit sheet's services over one stored snipe, with recorded edits and a controllable clock. */
class FakeEditMaxServices(
    snipe: Snipe,
    now: Instant,
) : EditMaxServices {
    val stored = MutableStateFlow<Snipe?>(snipe)
    val clock = MutableStateFlow(now)
    val calls = mutableListOf<EditCall>()

    /** Set to refuse every edit with this reason, as the data layer does during the bid window. */
    var refusal: String? = null

    override fun snipe(snipeId: Long): Flow<Snipe?> = stored

    override suspend fun updateMax(
        snipeId: Long,
        maxBid: Money?,
    ): EditResult {
        calls += EditCall.Max(snipeId, maxBid)
        refusal?.let { return EditResult.NotAllowed(it) }
        val updated = stored.value?.copy(maxBid = maxBid) ?: return EditResult.NotFound
        stored.value = updated
        return EditResult.Done(updated)
    }

    override suspend fun setLead(
        snipeId: Long,
        leadSeconds: Int,
    ): EditResult {
        calls += EditCall.Lead(snipeId, leadSeconds)
        refusal?.let { return EditResult.NotAllowed(it) }
        val updated = stored.value?.copy(leadSeconds = leadSeconds) ?: return EditResult.NotFound
        stored.value = updated
        return EditResult.Done(updated)
    }

    override fun nowUtcMillis(): Long = clock.value.toEpochMilli()

    override fun ticks(): Flow<Instant> = clock

    /** Work handed off outside any screen (an Undo), kept so a test runs it when it chooses. */
    val detached = mutableListOf<suspend () -> Unit>()

    override fun runDetached(work: suspend () -> Unit) {
        detached += work
    }
}
