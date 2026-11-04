package app.kestrel.ebay

import app.kestrel.model.AutoRefreshState
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Reads the item page's AUTO_REFRESH reply. Implements [AutoRefreshParser].
 *
 * The reply is `{"states":[{"eventName":..., "state":{"model":{...}}}, ...]}`. Its wrappers changed over time
 * (an extra x-hero state in early 2024, see bidflow AR3) while the bid-price-section payload stayed the same, so
 * states are found by eventName fragment anywhere in the tree and their payload by the fields it must carry.
 */
internal object AutoRefreshReader {
    fun read(json: String): AutoRefreshState? {
        val root = parseJsonOrNull(json.trim()) ?: return null
        val priceSection = stateModel(root, "bid-price-section", ::isPriceModel)
        val refreshModule = stateModel(root, "auto-refresh", ::isRefreshModel)
        if (priceSection == null && refreshModule == null) return null
        val timer = priceSection.path("bidInfo", "endTime").asObject()
        val countdownShown = timer?.get("formattedText").collectTexts().any { it.isNotBlank() }
        val requestedDelay = refreshModule?.get("refreshInSeconds").asWholeNumber()
        val refreshInSeconds = requestedDelay?.takeIf { it in 1..MAXIMUM_REFRESH_SECONDS }?.toInt()
        // At the end eBay drops the auto-refresh state and the countdown text (bidflow AR2). A missing countdown alone
        // is not enough while eBay still asks for a refresh: a false "ended" would stop a live snipe.
        val ended =
            refreshModule == null ||
                timerLabelSaysExpired(timer) ||
                (timer != null && !countdownShown && refreshInSeconds == null)
        val bidCount = priceSection.path("bidInfo", "bidCount", "value").asWholeNumber()
        return AutoRefreshState(
            currentPrice = priceSection.path("bidPrice", "value").asPriceReading()?.listing,
            bidCount = bidCount?.takeIf { it in 0L..Int.MAX_VALUE.toLong() }?.toInt(),
            endTimeUtcMillis = parseUtcMillis(timer.path("endTime", "value").asText()),
            refreshInSeconds = if (ended) null else refreshInSeconds,
            ended = ended,
        )
    }

    /**
     * AUTOREFRESH.autoRefreshAction.URL from the item page model. Only a path on the page's own origin or an https
     * URL on an eBay host is returned, since the page fetches it with the user's cookies.
     */
    fun urlFromPage(pageText: String): String? {
        val module = PageModelExtractor.module(pageText, "AUTOREFRESH", "AutoRefreshModule") ?: return null
        val url = module.path("autoRefreshAction", "URL").asText()?.trim() ?: return null
        if (url.isEmpty() || url.any { it.isWhitespace() }) return null
        val isOwnOriginPath = url.startsWith("/") && !url.startsWith("//") && !url.startsWith("/\\")
        val isEbayUrl = url.startsWith("https://", ignoreCase = true) && isEbayHost(url)
        return url.takeIf { isOwnOriginPath || isEbayUrl }
    }

    private fun isEbayHost(url: String): Boolean = UrlParts.parse(url)?.host?.let(EbayDomains::isEbayHost) == true

    /**
     * The payload of the state whose eventName contains [eventFragment]. When no state is named that way (a renamed
     * event), the first object anywhere in the reply that carries the payload's fields.
     */
    private fun stateModel(
        root: JsonElement,
        eventFragment: String,
        isModel: (JsonObject) -> Boolean,
    ): JsonObject? {
        val state = root.firstObject { it["eventName"].asText().orEmpty().contains(eventFragment, ignoreCase = true) }
        return state.firstObject(isModel) ?: root.firstObject(isModel)
    }

    private fun isPriceModel(candidate: JsonObject): Boolean =
        candidate["bidPrice"].asObject()?.get("value") != null ||
            candidate["bidInfo"].asObject()?.get("bidCount") != null

    private fun isRefreshModel(candidate: JsonObject): Boolean =
        candidate["refreshInSeconds"] != null || candidate["_type"].asText() == "AutoRefreshModule"

    /** True when the timer's label reads the same as its expired text ("Ended", "Beendet"). */
    private fun timerLabelSaysExpired(timer: JsonObject?): Boolean {
        val label =
            timer
                ?.get("label")
                .collectTexts()
                .joinToString(" ")
                .trim()
        val expired =
            timer
                ?.get("expiredText")
                .collectTexts()
                .joinToString(" ")
                .trim()
        return label.isNotEmpty() && label.equals(expired, ignoreCase = true)
    }

    // A week; eBay sent 60458 s six days out. Anything larger is not a delay worth honouring.
    private const val MAXIMUM_REFRESH_SECONDS = 7L * 24 * 60 * 60
}
