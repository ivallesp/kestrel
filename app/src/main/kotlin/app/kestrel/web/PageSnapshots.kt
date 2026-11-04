package app.kestrel.web

import kotlinx.serialization.Serializable

// What kestrel.js functions answer. Every field has a default so a missing field never breaks decoding.

@Serializable
internal data class ProbeSnapshot(
    val ok: Boolean = false,
    val error: String? = null,
    val url: String = "",
    val title: String = "",
    val readyState: String? = null,
    val visibilityState: String? = null,
    val signedIn: Boolean? = null,
    val greetingName: String? = null,
    val hasItemModel: Boolean = false,
    val bidButton: BidButtonSnapshot = BidButtonSnapshot(),
    val layerOpen: Boolean = false,
    val devicePixelRatio: Double = 1.0,
    val viewportWidth: Double? = null,
    val viewportHeight: Double? = null,
)

@Serializable
internal data class BidButtonSnapshot(
    val present: Boolean = false,
    val selector: String? = null,
    val tag: String? = null,
    val label: String? = null,
    val disabled: Boolean = false,
    val loading: Boolean = false,
    val signInHref: String? = null,
    val visible: Boolean = false,
)

@Serializable
internal data class ModelTextSnapshot(
    val ok: Boolean = false,
    val error: String? = null,
    val url: String = "",
    val title: String = "",
    val text: String = "",
    val modelScripts: Int = 0,
    val jsonLdScripts: Int = 0,
)

@Serializable
internal data class LayerSnapshot(
    val ok: Boolean = false,
    val error: String? = null,
    val url: String = "",
    val title: String = "",
    val open: Boolean = false,
    val ready: Boolean = false,
    val container: ContainerSnapshot? = null,
    val input: InputSnapshot? = null,
    val submit: SubmitSnapshot? = null,
    val minimumText: String? = null,
    val minimumAmountText: String? = null,
    val powerBids: List<String> = emptyList(),
    val lowestPowerBidAmountText: String? = null,
    val notices: List<String> = emptyList(),
    val inlineError: String? = null,
    val interstitial: InterstitialSnapshot? = null,
    val pendingDialogText: String? = null,
    val layerError: String? = null,
    val signInRequired: Boolean = false,
)

@Serializable
internal data class ContainerSnapshot(
    val selector: String? = null,
    val label: String? = null,
)

@Serializable
internal data class InputSnapshot(
    val selector: String? = null,
    val id: String? = null,
    val type: String? = null,
    val inputMode: String? = null,
    val value: String = "",
    val disabled: Boolean = false,
    val invalid: Boolean = false,
)

@Serializable
internal data class SubmitSnapshot(
    val selector: String? = null,
    val label: String? = null,
    val enabled: Boolean = false,
    val found: Boolean = false,
    val rejected: List<RejectedLabel> = emptyList(),
)

@Serializable
internal data class RejectedLabel(
    val label: String? = null,
    val problem: String? = null,
)

@Serializable
internal data class InterstitialSnapshot(
    val kind: String = "unknown",
    val text: String = "",
)

@Serializable
internal data class OpenSnapshot(
    val ok: Boolean = false,
    val error: String? = null,
    val reason: String? = null,
    val alreadyOpen: Boolean = false,
    val selector: String? = null,
    val label: String? = null,
    /** The text of a dialog in front of the bid button, when that is why nothing was pressed. */
    val text: String? = null,
)

@Serializable
internal data class TypedSnapshot(
    val ok: Boolean = false,
    val error: String? = null,
    val reason: String? = null,
    val value: String = "",
    val inlineError: String? = null,
    val minimumText: String? = null,
    val minimumAmountText: String? = null,
    val lowestPowerBidAmountText: String? = null,
    val submitEnabled: Boolean = false,
    val submitLabel: String? = null,
    val documentToken: String? = null,
)

@Serializable
internal data class ObserverSnapshot(
    val ok: Boolean = false,
    val error: String? = null,
    val fetchWrapped: Boolean = false,
    val xhrWrapped: Boolean = false,
    val resourceObserver: Boolean = false,
    val ceilingMinor: Long? = null,
    val hold: Boolean = false,
    val holdReason: String? = null,
)

@Serializable
internal data class TapTargetSnapshot(
    val ok: Boolean = false,
    val error: String? = null,
    val reason: String? = null,
    val x: Double = 0.0,
    val y: Double = 0.0,
    val hit: Boolean? = null,
    val devicePixelRatio: Double = 1.0,
    val label: String? = null,
)

@Serializable
internal data class TapResultSnapshot(
    val ok: Boolean = false,
    val armed: Boolean = false,
    val reached: Boolean = false,
    val swallowed: Int = 0,
    val labelChanged: Boolean = false,
    val pressPerfTime: Double? = null,
    val documentToken: String? = null,
)

@Serializable
internal data class ProbeTapSnapshot(
    val ok: Boolean = false,
    val error: String? = null,
    val x: Double = 0.0,
    val y: Double = 0.0,
    val devicePixelRatio: Double = 1.0,
)

@Serializable
internal data class ProbeOutcomeSnapshot(
    val ok: Boolean = false,
    val trusted: Boolean = false,
    val matched: Boolean = false,
    val expectClientX: Double? = null,
    val expectClientY: Double? = null,
    val events: List<GuardEventSnapshot> = emptyList(),
)

@Serializable
internal data class GuardEventSnapshot(
    val type: String = "",
    val x: Double? = null,
    val y: Double? = null,
)

@Serializable
internal data class PressSnapshot(
    val ok: Boolean = false,
    val error: String? = null,
    val dispatched: Boolean = false,
    /** Some events of the press went out before eBay changed the button; the bid may have been sent. */
    val partial: Boolean = false,
    val reason: String? = null,
    val label: String? = null,
    val selector: String? = null,
    val perfTime: Double? = null,
    val epochTime: Double? = null,
)

@Serializable
internal data class ReplySnapshot(
    val ok: Boolean = false,
    val error: String? = null,
    val url: String = "",
    val title: String = "",
    val layerOpen: Boolean = false,
    val notices: List<String> = emptyList(),
    val newNotices: List<String> = emptyList(),
    val addedLines: List<String> = emptyList(),
    val inlineError: String? = null,
    val interstitial: InterstitialSnapshot? = null,
    val records: List<RequestRecord> = emptyList(),
    val blocked: List<BlockedRequest> = emptyList(),
    val blockedSincePress: List<BlockedRequest> = emptyList(),
    val pressed: Boolean = false,
    val pressMethod: String? = null,
    val pressPerfTime: Double? = null,
    val documentToken: String? = null,
    val nowPerf: Double? = null,
)

@Serializable
internal data class RequestRecord(
    val via: String? = null,
    val method: String? = null,
    val action: String? = null,
    val url: String? = null,
    val requestPrice: RequestPrice? = null,
    val priceVerified: Boolean = false,
    val status: Int? = null,
    val bodyPrefix: String? = null,
    val error: String? = null,
    val startedPerf: Double? = null,
    val finishedPerf: Double? = null,
)

@Serializable
internal data class RequestPrice(
    val value: String? = null,
    val currency: String? = null,
)

@Serializable
internal data class BlockedRequest(
    val via: String? = null,
    val action: String? = null,
    val url: String? = null,
    val reason: String? = null,
    val requestPrice: RequestPrice? = null,
)

@Serializable
internal data class AutoRefreshStartSnapshot(
    val ok: Boolean = false,
    val error: String? = null,
    val reason: String? = null,
    val requestId: Int? = null,
)

@Serializable
internal data class AutoRefreshResultSnapshot(
    val ok: Boolean = false,
    val reason: String? = null,
    val done: Boolean = false,
    val status: Int? = null,
    val text: String? = null,
    val error: String? = null,
    val elapsedMillis: Long? = null,
)

@Serializable
internal data class CloseSnapshot(
    val ok: Boolean = false,
    val closed: Boolean = false,
    val method: String? = null,
)
