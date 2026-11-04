package app.kestrel.ebay

import app.kestrel.model.Money
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

// Null-safe reading of eBay's JSON. eBay adds, drops and renames fields between builds, so every accessor
// returns null instead of throwing when a value is missing or has an unexpected type.

private val strictJson = Json
private val lenientJson = Json { isLenient = true }

internal fun parseJsonOrNull(text: String): JsonElement? =
    try {
        strictJson.parseToJsonElement(text)
    } catch (_: Exception) {
        try {
            lenientJson.parseToJsonElement(text)
        } catch (_: Exception) {
            null
        }
    }

internal fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

internal fun JsonElement?.asArray(): JsonArray? = this as? JsonArray

internal fun JsonElement?.asText(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

/** A JSON number, or a string that holds one ("2.0" in JSON-LD). */
internal fun JsonElement?.asDecimal(): BigDecimal? {
    val primitive = this as? JsonPrimitive ?: return null
    return try {
        BigDecimal(primitive.content.trim())
    } catch (_: NumberFormatException) {
        null
    }
}

internal fun JsonElement?.asWholeNumber(): Long? {
    val decimal = asDecimal() ?: return null
    return try {
        decimal.longValueExact()
    } catch (_: ArithmeticException) {
        null
    }
}

internal fun JsonElement?.asBoolean(): Boolean? {
    val primitive = this as? JsonPrimitive ?: return null
    return when (primitive.content.lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }
}

internal fun JsonElement?.isJsonNull(): Boolean = this is JsonNull

/** An ISO 4217 code ("GBP"), upper-cased; null for anything that is not three letters. */
internal fun JsonElement?.asCurrencyCode(): String? =
    asText()?.trim()?.uppercase()?.takeIf { code -> code.length == 3 && code.all { it in 'A'..'Z' } }

/** The elements of an array, or the element itself when eBay sent a single object where a list may appear. */
internal fun JsonElement?.asList(): List<JsonElement> =
    (this as? JsonArray)?.toList() ?: listOfNotNull(this?.takeUnless { it is JsonNull })

internal fun JsonElement?.path(vararg keys: String): JsonElement? {
    var node: JsonElement? = this
    for (key in keys) node = node.asObject()?.get(key) ?: return null
    return node
}

/** Every "text" string under this node, in document order (TextSpan texts of eBay's display models). */
internal fun JsonElement?.collectTexts(): List<String> {
    val texts = mutableListOf<String>()

    fun visit(node: JsonElement?) {
        if (node is JsonObject) {
            node.forEach { (key, value) ->
                val text = if (key == "text") value.asText() else null
                if (text != null) texts += text else visit(value)
            }
        } else if (node is JsonArray) {
            node.forEach { visit(it) }
        }
    }
    visit(this)
    return texts
}

/** Depth-first search for the first object (this one included) that satisfies [predicate]. */
internal fun JsonElement?.firstObject(predicate: (JsonObject) -> Boolean): JsonObject? {
    if (this is JsonObject) {
        if (predicate(this)) return this
        for (value in values) value.firstObject(predicate)?.let { return it }
    } else if (this is JsonArray) {
        for (value in this) value.firstObject(predicate)?.let { return it }
    }
    return null
}

/** Parses eBay's ISO instants ("2026-04-27T11:13:22.000Z", "2026-04-27T11:13:22Z") to epoch milliseconds. */
internal fun parseUtcMillis(text: String?): Long? {
    val trimmed = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return try {
        Instant.parse(trimmed).toEpochMilli()
    } catch (_: DateTimeParseException) {
        try {
            OffsetDateTime.parse(trimmed).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}

/**
 * eBay's price object: {value, currency} plus {convertedFromValue, convertedFromCurrency} when the listing is from
 * another site. [listing] is in the listing's own currency (the bid currency); [site] is the viewing site's
 * conversion, present only for converted prices.
 */
internal data class PriceReading(
    val listing: Money,
    val site: Money?,
)

internal fun JsonElement?.asPriceReading(): PriceReading? {
    val price = asObject() ?: return null
    val value = price["value"].asDecimal()
    val currency = price["currency"].asCurrencyCode()
    val originalValue = price["convertedFromValue"].asDecimal()
    val originalCurrency = price["convertedFromCurrency"].asCurrencyCode()
    if (originalValue != null && originalCurrency != null && originalCurrency != currency) {
        val site = if (value != null && currency != null) moneyOrNull(value, currency) else null
        return moneyOrNull(originalValue, originalCurrency)?.let { PriceReading(it, site) }
    }
    if (value == null || currency == null) return null
    return moneyOrNull(value, currency)?.let { PriceReading(it, null) }
}

/** Money from a decimal amount, or null when it is negative or too large to be a real price. */
internal fun moneyOrNull(
    amount: BigDecimal,
    currency: String,
): Money? {
    if (amount.signum() < 0 || amount > MAXIMUM_PLAUSIBLE_AMOUNT) return null
    return Money.of(amount, currency)
}

private val MAXIMUM_PLAUSIBLE_AMOUNT = BigDecimal("1000000000")
