package app.kestrel.ebay

import kotlinx.serialization.json.JsonObject

/**
 * Finds the JSON modules eBay inlines into item pages. A module is located by its key text (`"SEMANTIC_DATA":{`)
 * and cut out with a string-aware brace scanner, because the script global that carries the model changes its
 * name between builds (`$MC`, `$M_96613636_C`) and the page is too large to parse as a whole.
 */
object PageModelExtractor {
    /** Every parseable object that follows `"key":{` in [text], in page order. */
    fun objectsAfterKey(
        text: String,
        key: String,
    ): List<JsonObject> =
        occurrencesOfKey(text, key).mapNotNull { (_, objectText) ->
            parseJsonOrNull(objectText).asObject()
        }

    /**
     * The module stored under [key]. When [expectedType] is given, the first object whose `_type` matches wins, so a
     * same-named layout entry is never mistaken for the module; otherwise (or when no `_type` matches) the first
     * parseable object.
     */
    fun module(
        text: String,
        key: String,
        expectedType: String? = null,
    ): JsonObject? {
        val candidates = objectsAfterKey(text, key)
        if (expectedType != null) {
            candidates.firstOrNull { it["_type"].asText() == expectedType }?.let { return it }
        }
        return candidates.firstOrNull()
    }

    /** Positions (index of the key's opening quote) and raw text of every balanced object after `"key":{`. */
    internal fun occurrencesOfKey(
        text: String,
        key: String,
    ): List<Pair<Int, String>> {
        val needle = "\"$key\""
        val found = mutableListOf<Pair<Int, String>>()
        var searchFrom = 0
        while (found.size < MAXIMUM_OCCURRENCES) {
            val keyAt = text.indexOf(needle, searchFrom)
            if (keyAt < 0) break
            searchFrom = keyAt + needle.length
            val braceAt = openingBraceAfterColon(text, searchFrom) ?: continue
            balancedObjectAt(text, braceAt)?.let { found += keyAt to it }
        }
        return found
    }

    /** The balanced `{...}` starting at [openBrace], honouring JSON strings and escapes; null when unbalanced. */
    internal fun balancedObjectAt(
        text: String,
        openBrace: Int,
    ): String? {
        if (openBrace !in text.indices || text[openBrace] != '{') return null
        var depth = 0
        var inString = false
        var index = openBrace
        while (index < text.length) {
            val character = text[index]
            if (inString) {
                when (character) {
                    '\\' -> index++
                    '"' -> inString = false
                }
            } else {
                when (character) {
                    '"' -> {
                        inString = true
                    }

                    '{' -> {
                        depth++
                    }

                    '}' -> {
                        depth--
                        if (depth == 0) return text.substring(openBrace, index + 1)
                    }
                }
            }
            index++
        }
        return null
    }

    private fun openingBraceAfterColon(
        text: String,
        from: Int,
    ): Int? {
        var index = skipWhitespace(text, from)
        if (index >= text.length || text[index] != ':') return null
        index = skipWhitespace(text, index + 1)
        return if (index < text.length && text[index] == '{') index else null
    }

    private fun skipWhitespace(
        text: String,
        from: Int,
    ): Int {
        var index = from
        while (index < text.length && text[index].isWhitespace()) index++
        return index
    }

    // A real page carries each module once or twice; the cap only bounds the work on a hostile or broken page.
    private const val MAXIMUM_OCCURRENCES = 64
}
