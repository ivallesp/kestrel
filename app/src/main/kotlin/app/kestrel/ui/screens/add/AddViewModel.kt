package app.kestrel.ui.screens.add

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kestrel.data.AddResult
import app.kestrel.data.Settings
import app.kestrel.model.ItemRef
import app.kestrel.model.Listing
import app.kestrel.model.ListingStatus
import app.kestrel.model.Money
import app.kestrel.model.PageKind
import app.kestrel.model.ParsedLink
import app.kestrel.model.Snipe
import app.kestrel.ui.format.withCurlyQuotes
import app.kestrel.ui.model.Connectivity
import app.kestrel.ui.model.LotStatusMapper
import app.kestrel.web.FetchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/** What S2 asks its destination to do once: close with a snackbar for Home, open a screen, or show a message here. */
sealed interface AddEvent {
    /** The snipe is stored: close S2 and leave [message] for the next screen. */
    data class Close(
        val message: String,
    ) : AddEvent

    data object OpenSignIn : AddEvent

    /** The listing already has a snipe: open it instead of S2. */
    data class OpenSnipe(
        val snipeId: Long,
    ) : AddEvent

    data class Message(
        val text: String,
    ) : AddEvent
}

/**
 * S2's logic (DESIGN S2): finds the listing in the link ([AddServices.parse]), loads it, judges the typed max
 * against eBay's minimum, and stores the snipe through the app's snipe actions. The max is a hard ceiling: it is
 * stored exactly as typed, and a max more than three times the price is spelt out in words before it arms.
 */
class AddViewModel(
    link: String?,
    private val services: AddServices,
) : ViewModel() {
    /** The link entry's field. */
    val linkField = TextFieldState()

    /** The max field, hoisted here so typing never rebuilds it. Cleared for each listing. */
    val maxField = TextFieldState()

    /** A new group's name, in the group picker. */
    val newGroupName = TextFieldState()

    private val model = MutableStateFlow(Model(stage = if (link == null) Stage.LinkEntry else stageFor(link)))
    private val eventChannel = Channel<AddEvent>(Channel.BUFFERED)
    private var fetchJob: Job? = null

    val events: Flow<AddEvent> = eventChannel.receiveAsFlow()

    private val groupOptions: Flow<List<GroupOption>> =
        combine(services.groups(), services.snipes()) { groups, snipes ->
            groups.map { group ->
                GroupOption(group.id, group.name, snipes.count { it.groupId == group.id && !it.status.isFinal })
            }
        }.onStart { emit(emptyList()) }

    val uiState: StateFlow<AddUiState> =
        combine(
            model,
            services.ticks().onStart { emit(Instant.ofEpochMilli(services.nowUtcMillis())) },
            snapshotFlow { maxField.text.toString() },
            groupOptions,
        ) { model, now, maxText, groups -> stateOf(model, now, maxText, groups) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = stateOf(model.value, Instant.ofEpochMilli(services.nowUtcMillis()), "", emptyList()),
            )

    init {
        viewModelScope.launch {
            val lead = services.defaultLeadSeconds()
            val suggest = services.suggestOddPence()
            model.update { it.copy(leadSeconds = lead, suggestOddPence = suggest) }
        }
        if (link != null) start(link, fromShare = true)
    }

    // The link.

    /** The link entry's own button: reads the field. */
    fun submitLink() {
        start(linkField.text.toString(), fromShare = false)
    }

    /** Paste: the clipboard's text, read only on that tap. */
    fun paste(text: String) {
        linkField.setTextAndPlaceCursorAtEnd(text.trim())
        start(text, fromShare = false)
    }

    fun retry() {
        val failed = model.value.stage as? Stage.Failed ?: return
        failed.retry?.let { fetch(it) }
    }

    /** Back to the link entry, discarding the listing. */
    fun pasteDifferentLink() {
        fetchJob?.cancel()
        linkField.clearText()
        maxField.clearText()
        model.update { it.copy(stage = Stage.LinkEntry, group = GroupChoice.None, checkingWords = false) }
    }

    /** Called when S2 is shown again, for example after sign-in: a new session lets the snipe arm. */
    fun refreshSession() {
        val loaded = model.value.stage as? Stage.Loaded ?: return
        if (services.isSignedIn(loaded.listing.ref.site)) model.update { it.copy(signedIn = true) }
    }

    // The plan.

    fun selectLead(index: Int) {
        val seconds = MaxRules.LeadOptions.getOrNull(index) ?: return
        model.update { it.copy(leadSeconds = seconds) }
    }

    /** "Use £90.37": writes the suggestion into the field, the cursor at the end. */
    fun useOddPence() {
        val loaded = model.value.stage as? Stage.Loaded ?: return
        val typed = MaxRules.read(maxField.text.toString(), loaded.listing.currentPrice.currency, loaded.minimum)
        val currency = loaded.listing.currentPrice.currency
        val suggestion = MaxRules.oddPence(typed, currency) as? OddPence.Suggestion ?: return
        maxField.setTextAndPlaceCursorAtEnd(MaxRules.fieldText(suggestion.amount))
    }

    fun openGroupPicker() {
        val pick =
            when (val group = model.value.group) {
                GroupChoice.None -> GroupPick.NoGroup
                is GroupChoice.Existing -> GroupPick.Existing(group.group.id)
                is GroupChoice.New -> GroupPick.New
            }
        model.update { it.copy(picking = pick) }
    }

    fun selectGroup(pick: GroupPick) {
        model.update { if (it.picking == null) it else it.copy(picking = pick) }
    }

    fun confirmGroup() {
        val pick = model.value.picking ?: return
        viewModelScope.launch {
            val choice =
                when (pick) {
                    GroupPick.NoGroup -> {
                        GroupChoice.None
                    }

                    is GroupPick.Existing -> {
                        services
                            .groups()
                            .first()
                            .firstOrNull { it.id == pick.id }
                            ?.let { GroupChoice.Existing(it) }
                            ?: GroupChoice.None
                    }

                    GroupPick.New -> {
                        val name = newGroupName.text.toString().trim()
                        if (name.isEmpty()) return@launch
                        GroupChoice.New(name)
                    }
                }
            model.update { it.copy(group = choice, picking = null) }
        }
    }

    fun dismissGroupPicker() {
        model.update { it.copy(picking = null) }
    }

    // Arming.

    /** "Arm snipe" or "Watch without a max". */
    fun arm() {
        val current = model.value
        val loaded = current.stage as? Stage.Loaded ?: return
        if (current.busy) return
        when (val typed = typedMax(loaded)) {
            TypedMax.Empty -> {
                store(loaded.listing, max = null)
            }

            // The field shows why; the button is disabled.
            is TypedMax.TooLow -> {}

            is TypedMax.Valid -> {
                val siteRefusal = MaxRules.siteRefusal(loaded.listing.ref.site)
                when {
                    // Sign-in could not help here: S6 signs in to ebay.co.uk only.
                    siteRefusal != null -> eventChannel.trySend(AddEvent.Message(siteRefusal))

                    !current.signedIn -> eventChannel.trySend(AddEvent.OpenSignIn)

                    MaxRules.needsWordsCheck(
                        typed.max,
                        loaded.listing.currentPrice,
                    ) -> model.update { it.copy(checkingWords = true) }

                    else -> store(loaded.listing, typed.max)
                }
            }
        }
    }

    /** The max spelt out was what the user meant. */
    fun confirmWords() {
        val loaded = model.value.stage as? Stage.Loaded ?: return
        model.update { it.copy(checkingWords = false) }
        val typed = typedMax(loaded) as? TypedMax.Valid ?: return
        store(loaded.listing, typed.max)
    }

    /** "Change max": back to the field. */
    fun dismissWords() {
        model.update { it.copy(checkingWords = false) }
    }

    private fun typedMax(loaded: Stage.Loaded): TypedMax =
        MaxRules.read(maxField.text.toString(), loaded.listing.currentPrice.currency, loaded.minimum)

    private fun store(
        listing: Listing,
        max: Money?,
    ) {
        model.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val lead = model.value.leadSeconds
                val groupId =
                    when (val group = model.value.group) {
                        GroupChoice.None -> null
                        is GroupChoice.Existing -> group.group.id
                        is GroupChoice.New -> services.createGroup(group.name).id
                    }
                when (val result = services.add(listing, max, lead, groupId)) {
                    is AddResult.Added -> {
                        val now = Instant.ofEpochMilli(services.nowUtcMillis())
                        val message =
                            if (max == null) {
                                WATCHING_MESSAGE
                            } else {
                                AddPlanner.armedMessage(AddPlanner.bidAt(listing, result.snipe.leadSeconds), now)
                            }
                        eventChannel.trySend(AddEvent.Close(message))
                    }

                    is AddResult.AlreadyAdded -> {
                        model.update { it.copy(stage = alreadySniped(listing.ref.url, result.snipe)) }
                    }

                    is AddResult.Rejected -> {
                        eventChannel.trySend(AddEvent.Message(result.reason.withCurlyQuotes()))
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: IllegalArgumentException) {
                eventChannel.trySend(AddEvent.Message(error.message?.withCurlyQuotes() ?: STORE_FAILED))
            } finally {
                model.update { it.copy(busy = false) }
            }
        }
    }

    // Finding and loading the listing.

    private fun start(
        text: String,
        fromShare: Boolean,
    ) {
        when (val link = services.parse(text)) {
            ParsedLink.NotEbay -> {
                if (fromShare) {
                    model.update { it.copy(stage = Stage.Failed(NOT_A_LISTING, retry = null)) }
                } else {
                    eventChannel.trySend(AddEvent.Message(NOT_A_LISTING))
                }
            }

            is ParsedLink.NotAListing -> {
                model.update { it.copy(stage = Stage.Failed(NOT_A_LISTING, retry = null)) }
            }

            is ParsedLink.Item -> {
                fetch(link)
            }

            is ParsedLink.ShortLink -> {
                fetch(link)
            }
        }
    }

    /** The stage before the first load, so a shared link opens straight into "Getting the listing". */
    private fun stageFor(text: String): Stage =
        when (val link = services.parse(text)) {
            is ParsedLink.Item, is ParsedLink.ShortLink -> Stage.Fetching(addressOf(link))
            else -> Stage.LinkEntry
        }

    private fun fetch(link: ParsedLink) {
        fetchJob?.cancel()
        model.update { it.copy(stage = Stage.Fetching(addressOf(link))) }
        fetchJob =
            viewModelScope.launch {
                // A listing already on the list opens as it is, without loading eBay's page again.
                if (link is ParsedLink.Item) {
                    val known = openSnipeOn(link.ref)
                    if (known != null) {
                        model.update { it.copy(stage = alreadySniped(link.ref.url, known)) }
                        return@launch
                    }
                }
                val result =
                    try {
                        services.fetch(link)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: RuntimeException) {
                        FetchResult.Failed(error.message ?: "")
                    }
                model.update { it.copy(stage = stageOf(result, link)) }
            }
    }

    private suspend fun stageOf(
        result: FetchResult,
        link: ParsedLink,
    ): Stage =
        when (result) {
            is FetchResult.Loaded -> {
                loaded(result.listing)
            }

            is FetchResult.NotAnAuction -> {
                result.listing?.let { Stage.CannotSnipe(it, CannotSnipeReason.BuyItNow) }
                    ?: Stage.Failed(NOT_A_LISTING, retry = null)
            }

            is FetchResult.Blocked -> {
                blocked(result.kind, link, result.url)
            }

            is FetchResult.Failed -> {
                Stage.Failed(reasonFor(result.reason), retry = link.takeIf { retryable(result.reason) })
            }
        }

    private suspend fun loaded(listing: Listing): Stage {
        val refs = listOfNotNull(listing.ref, listing.relistedItemId?.let { ItemRef(listing.ref.site, it) })
        refs.firstNotNullOfOrNull { openSnipeOn(it) }?.let { return alreadySniped(listing.ref.url, it) }
        if (listing.status == ListingStatus.ENDED || listing.endTimeUtcMillis <= services.nowUtcMillis()) {
            return Stage.CannotSnipe(listing, CannotSnipeReason.Ended)
        }
        maxField.clearText()
        val signedIn = listing.signedIn ?: services.isSignedIn(listing.ref.site)
        model.update { it.copy(signedIn = signedIn) }
        return Stage.Loaded(listing, AddPlanner.minimum(listing))
    }

    private fun blocked(
        kind: PageKind,
        link: ParsedLink,
        url: String,
    ): Stage =
        when (kind) {
            PageKind.SIGN_IN -> {
                Stage.Failed("eBay wants you to sign in before it shows this listing.", retry = link, signIn = true)
            }

            PageKind.CAPTCHA, PageKind.CHALLENGE -> {
                Stage.Failed(
                    "eBay wants a quick check first. Solve it in View listing, then try again.",
                    link,
                    url = url,
                )
            }

            PageKind.RATE_WALL -> {
                Stage.Failed("eBay is busy right now. Try again in a minute.", retry = link)
            }

            PageKind.NOT_FOUND -> {
                Stage.Failed(NO_LISTING, retry = null)
            }

            else -> {
                Stage.Failed("eBay showed a page Kestrel doesn’t recognise.", retry = link, url = url)
            }
        }

    private suspend fun openSnipeOn(ref: ItemRef): Snipe? =
        services.snipes().first().firstOrNull { it.ref == ref && !it.status.isFinal }

    private fun alreadySniped(
        url: String,
        snipe: Snipe,
    ): Stage = Stage.AlreadySniped(snipe, url)

    // The state.

    private fun stateOf(
        model: Model,
        now: Instant,
        maxText: String,
        groups: List<GroupOption>,
    ): AddUiState {
        val page =
            when (val stage = model.stage) {
                Stage.LinkEntry -> {
                    AddPage.LinkEntry(linkField)
                }

                is Stage.Fetching -> {
                    AddPage.Fetching(stage.address)
                }

                is Stage.Failed -> {
                    AddPage.Failed(
                        stage.reason,
                        canRetry = stage.retry != null,
                        signIn = stage.signIn,
                        failedUrl = stage.url,
                    )
                }

                is Stage.CannotSnipe -> {
                    AddPage.CannotSnipe(AddPlanner.header(stage.listing), stage.reason, stage.listing.ref.url)
                }

                is Stage.AlreadySniped -> {
                    val status = LotStatusMapper.statusOf(stage.snipe, emptyList(), now, Connectivity.Online)
                    AddPage.AlreadySniped(AddPlanner.header(stage.snipe), stage.snipe.id, status, stage.url)
                }

                is Stage.Loaded -> {
                    ready(stage, model, now, maxText, groups)
                }
            }
        val picker =
            model.picking?.let { pick -> GroupPickerState(options = groups, selected = pick, newName = newGroupName) }
        return AddUiState(now = now, page = page, groups = picker)
    }

    private fun ready(
        stage: Stage.Loaded,
        model: Model,
        now: Instant,
        maxText: String,
        groups: List<GroupOption>,
    ): AddPage.Ready {
        val typed = MaxRules.read(maxText, stage.listing.currentPrice.currency, stage.minimum)
        val openInGroup =
            (model.group as? GroupChoice.Existing)?.let { group ->
                groups.firstOrNull {
                    it.id ==
                        group.group.id
                }
            }
        return AddPlanner.ready(
            listing = stage.listing,
            minimum = stage.minimum,
            maxField = maxField,
            typed = typed,
            leadSeconds = model.leadSeconds,
            group = AddPlanner.groupRow(model.group, openInGroup?.openSnipes ?: 0),
            signedIn = model.signedIn,
            checkingWords = model.checkingWords,
            busy = model.busy,
            now = now,
            suggestOddPence = model.suggestOddPence,
        )
    }

    /** The parts of S2 the ViewModel owns, apart from the fields. */
    private data class Model(
        val stage: Stage,
        val leadSeconds: Int = Settings.DEFAULT_LEAD_SECONDS,
        val group: GroupChoice = GroupChoice.None,
        /** Non-null while the group picker is open: what it has selected. */
        val picking: GroupPick? = null,
        val signedIn: Boolean = true,
        val checkingWords: Boolean = false,
        val busy: Boolean = false,
        /** Settings "Suggest odd pence". */
        val suggestOddPence: Boolean = true,
    )

    private sealed interface Stage {
        data object LinkEntry : Stage

        data class Fetching(
            val address: String,
        ) : Stage

        data class Failed(
            val reason: String,
            val retry: ParsedLink?,
            val signIn: Boolean = false,
            val url: String? = null,
        ) : Stage

        data class CannotSnipe(
            val listing: Listing,
            val reason: CannotSnipeReason,
        ) : Stage

        data class AlreadySniped(
            val snipe: Snipe,
            val url: String,
        ) : Stage

        data class Loaded(
            val listing: Listing,
            val minimum: Money,
        ) : Stage
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        const val NOT_A_LISTING = "That link isn’t an eBay listing."
        const val NO_LISTING = "eBay has no listing at this link."
        const val NO_RESPONSE = "eBay didn’t respond."
        const val WATCHING_MESSAGE = "Watching: no bid until you set a max"
        private const val STORE_FAILED = "Kestrel couldn’t save this snipe."

        /**
         * "ebay.co.uk/itm/…", the address S2 shows while it loads (DESIGN S2 "Fetching"): the host and the kind of
         * page, without the scheme, the item number or the query.
         */
        fun addressOf(link: ParsedLink): String =
            when (link) {
                is ParsedLink.Item -> "${link.ref.site.host.removePrefix("www.")}/itm/…"
                is ParsedLink.ShortLink -> "${hostOf(link.url)}/…"
                is ParsedLink.NotAListing -> "${hostOf(link.url)}/…"
                ParsedLink.NotEbay -> ""
            }

        /** "ebay.us" from "https://ebay.us/m/abc?x=1". */
        private fun hostOf(url: String): String =
            url
                .substringAfter("://")
                .substringBefore('/')
                .substringBefore('?')
                .removePrefix("www.")

        /**
         * The web layer explains a failed load in its own words; S2 says it plainly. Unknown failures are a page that
         * did not load: eBay didn't respond.
         */
        fun reasonFor(webReason: String): String =
            when {
                webReason.contains("not an eBay link", ignoreCase = true) -> NOT_A_LISTING

                webReason.contains("did not lead to an eBay listing", ignoreCase = true) -> NOT_A_LISTING

                webReason.contains("no listing", ignoreCase = true) -> NO_LISTING

                webReason.contains(
                    "could not read the listing",
                    ignoreCase = true,
                ) -> "Kestrel couldn’t read the listing page."

                else -> NO_RESPONSE
            }

        private fun retryable(webReason: String): Boolean =
            reasonFor(webReason).let {
                it != NOT_A_LISTING &&
                    it != NO_LISTING
            }
    }
}
