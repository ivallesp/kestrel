package app.kestrel.ebay

/**
 * The few HTML pieces Kestrel reads outside the JSON model: the document title, Open Graph meta tags, the canonical
 * link and inline script bodies. Works on the full page and on the reduced page built by kestrel.js `modelText()`,
 * whose tags may differ in attribute case and quoting from eBay's own markup.
 */
internal object HtmlText {
    /** The first `<title>` element's text (the document title; later ones belong to inline SVG icons). */
    fun documentTitle(html: String): String? {
        val open = indexOfTag(html, "title", 0) ?: return null
        val contentStart = html.indexOf('>', open)
        if (contentStart < 0) return null
        val close = indexOfIgnoreCase(html, "</title", contentStart + 1)
        if (close < 0) return null
        return cleanText(html.substring(contentStart + 1, close)).takeIf { it.isNotEmpty() }
    }

    /** `content` of the first `<meta property="[property]">` (or `name=`), entity-decoded. */
    fun metaContent(
        html: String,
        property: String,
    ): String? =
        tags(html, "meta")
            .filter { attributes -> (attributes["property"] ?: attributes["name"]).equals(property, ignoreCase = true) }
            .firstNotNullOfOrNull { attributes -> attributes["content"]?.trim()?.takeIf { it.isNotEmpty() } }

    /** `href` of the first `<link rel="canonical">`. */
    fun canonicalHref(html: String): String? =
        tags(html, "link")
            .filter { attributes -> "canonical" in attributes["rel"].orEmpty().lowercase().split(' ', '\t', '\n') }
            .firstNotNullOfOrNull { attributes -> attributes["href"]?.trim()?.takeIf { it.isNotEmpty() } }

    /** Bodies of `<script>` elements whose `type` attribute contains [typeFragment]. */
    fun scriptBodies(
        html: String,
        typeFragment: String,
    ): List<String> {
        val bodies = mutableListOf<String>()
        var searchFrom = 0
        while (bodies.size < MAXIMUM_SCRIPTS) {
            val open = indexOfTag(html, "script", searchFrom) ?: break
            val openEnd = html.indexOf('>', open)
            if (openEnd < 0) break
            val close = indexOfIgnoreCase(html, "</script", openEnd + 1)
            if (close < 0) break
            val type = parseAttributes(html.substring(open, openEnd))["type"].orEmpty()
            if (type.contains(typeFragment, ignoreCase = true)) bodies += html.substring(openEnd + 1, close)
            searchFrom = close + 1
        }
        return bodies
    }

    /** Removes tags (`<wbr/>` sits inside eBay titles), decodes entities and collapses whitespace. */
    fun cleanText(raw: String): String = decodeEntities(raw.replace(TAG, "")).replace(WHITESPACE_RUN, " ").trim()

    fun decodeEntities(text: String): String {
        if ('&' !in text) return text
        return ENTITY.replace(text) { match ->
            val body = match.groupValues[1]
            val decoded =
                when {
                    body.startsWith(
                        "#x",
                    ) || body.startsWith("#X") -> body.substring(2).toIntOrNull(16)?.let(::codePointText)

                    body.startsWith("#") -> body.substring(1).toIntOrNull()?.let(::codePointText)

                    else -> NAMED_ENTITIES[body]
                }
            decoded ?: match.value
        }
    }

    private fun codePointText(codePoint: Int): String? =
        if (Character.isValidCodePoint(codePoint) && codePoint != 0) String(Character.toChars(codePoint)) else null

    /** Attribute maps of every `<name ...>` start tag, in page order. */
    private fun tags(
        html: String,
        name: String,
    ): Sequence<Map<String, String>> =
        sequence {
            var searchFrom = 0
            var count = 0
            while (count < MAXIMUM_TAGS) {
                val open = indexOfTag(html, name, searchFrom) ?: break
                val end = html.indexOf('>', open)
                if (end < 0) break
                yield(parseAttributes(html.substring(open, end)))
                searchFrom = end + 1
                count++
            }
        }

    /** Attributes of one start tag. Values are entity-decoded; unquoted values are accepted (eBay emits them). */
    private fun parseAttributes(tag: String): Map<String, String> {
        val attributes = mutableMapOf<String, String>()
        val afterName = tag.indexOfFirst { it.isWhitespace() }.takeIf { it >= 0 } ?: return attributes
        for (match in ATTRIBUTE.findAll(tag.substring(afterName))) {
            val name = match.groupValues[1].lowercase()
            val value = match.groups[2]?.value ?: match.groups[3]?.value ?: match.groups[4]?.value ?: ""
            attributes.putIfAbsent(name, decodeEntities(value))
        }
        return attributes
    }

    /** Index of `<name` followed by whitespace, `>` or `/`, so `<title` never matches `<titlebar`. */
    private fun indexOfTag(
        html: String,
        name: String,
        from: Int,
    ): Int? {
        var searchFrom = from
        while (true) {
            val at = indexOfIgnoreCase(html, "<$name", searchFrom)
            if (at < 0) return null
            val next = html.getOrNull(at + name.length + 1)
            if (next == null || next == '>' || next == '/' || next.isWhitespace()) return at
            searchFrom = at + 1
        }
    }

    fun indexOfIgnoreCase(
        text: String,
        needle: String,
        from: Int,
    ): Int {
        if (needle.isEmpty()) return from
        val first = needle[0]
        var index = from.coerceAtLeast(0)
        val last = text.length - needle.length
        while (index <= last) {
            val character = text[index]
            if (character.equals(first, ignoreCase = true) &&
                text.regionMatches(index, needle, 0, needle.length, ignoreCase = true)
            ) {
                return index
            }
            index++
        }
        return -1
    }

    private val TAG = Regex("<[^<>]*>")
    private val WHITESPACE_RUN = Regex("[\\s\\u00A0\\u2007\\u202F]+")
    private val ENTITY = Regex("&(#[0-9]{1,7}|#[xX][0-9A-Fa-f]{1,6}|[A-Za-z][A-Za-z0-9]{1,31});")

    // eBay also embeds escaped copies of its head tags inside JavaScript strings (`Property=\"og:title\"`). A value
    // may not start with a backslash, so those copies never yield attributes.
    private val ATTRIBUTE =
        Regex("([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'=<>`\\\\]+))")

    private val NAMED_ENTITIES =
        mapOf(
            "amp" to "&",
            "lt" to "<",
            "gt" to ">",
            "quot" to "\"",
            "apos" to "'",
            "nbsp" to "\u00A0",
            "pound" to "£",
            "euro" to "€",
            "dollar" to "$",
            "cent" to "¢",
            "yen" to "¥",
            "copy" to "©",
            "reg" to "®",
            "trade" to "™",
            "deg" to "°",
            "times" to "×",
            "frac12" to "½",
            "frac14" to "¼",
            "frac34" to "¾",
            "ndash" to "–",
            "mdash" to "—",
            "hellip" to "…",
            "lsquo" to "‘",
            "rsquo" to "’",
            "ldquo" to "“",
            "rdquo" to "”",
            "laquo" to "«",
            "raquo" to "»",
            "middot" to "·",
            "bull" to "•",
        )

    private const val MAXIMUM_SCRIPTS = 2_000
    private const val MAXIMUM_TAGS = 2_000
}
