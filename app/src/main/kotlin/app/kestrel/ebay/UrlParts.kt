package app.kestrel.ebay

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * A lenient http(s) URL split into parts. java.net.URI rejects much of what people paste (spaces, `|`, raw
 * Unicode), so this parser only needs the scheme, host, path and query to be recognisable.
 */
internal data class UrlParts(
    val scheme: String,
    val host: String,
    /** Percent-decoded path, "/" when empty. */
    val path: String,
    /** Decoded query parameters in their original order. */
    val query: List<QueryParameter>,
) {
    data class QueryParameter(
        val name: String,
        val value: String?,
    )

    fun parameter(name: String): String? = query.firstOrNull { it.name.equals(name, ignoreCase = true) }?.value

    /** The URL rebuilt with https, a lower-case host and without the parameters [drop] selects. */
    fun rebuilt(drop: (QueryParameter) -> Boolean = { false }): String {
        val queryText =
            query.filterNot(drop).joinToString("&") { parameter ->
                val name = URLEncoder.encode(parameter.name, Charsets.UTF_8)
                parameter.value?.let { "$name=${URLEncoder.encode(it, Charsets.UTF_8)}" } ?: name
            }
        return "https://$host$path" + if (queryText.isEmpty()) "" else "?$queryText"
    }

    companion object {
        fun parse(text: String): UrlParts? {
            val match = URL.matchEntire(text.trim()) ?: return null
            // Anything before '@' is user information, so "https://www.ebay.co.uk@example.com" is example.com.
            val hostAndPort = match.groupValues[2].substringAfterLast('@')
            val host = EbayDomains.normalizeHost(hostAndPort.substringBefore(':')) ?: return null
            val path = decodePath(match.groupValues[3]).ifEmpty { "/" }
            val query =
                match.groups[4]
                    ?.value
                    ?.let(::parseQuery)
                    .orEmpty()
            return UrlParts(match.groupValues[1].lowercase(), host, path, query)
        }

        private fun parseQuery(text: String): List<QueryParameter> =
            text
                .split('&', ';')
                .filter { it.isNotEmpty() }
                .map { pair ->
                    val separator = pair.indexOf('=')
                    if (separator < 0) {
                        QueryParameter(decodeQueryComponent(pair), null)
                    } else {
                        QueryParameter(
                            decodeQueryComponent(pair.substring(0, separator)),
                            decodeQueryComponent(pair.substring(separator + 1)),
                        )
                    }
                }

        /** Form decoding ('+' is a space) that keeps the raw text when it is malformed ("100%" in a pasted link). */
        private fun decodeQueryComponent(text: String): String =
            if ('%' !in text && '+' !in text) text else decodeOrNull(text) ?: text

        /** In a path '+' is a literal plus, so only percent escapes are decoded. */
        private fun decodePath(text: String): String =
            if ('%' !in text) text else decodeOrNull(text.replace("+", "%2B")) ?: text

        private fun decodeOrNull(text: String): String? =
            try {
                URLDecoder.decode(text, Charsets.UTF_8)
            } catch (_: IllegalArgumentException) {
                null
            }

        private val URL = Regex("(?i)(https?)://([^/?#\\s]+)([^?#\\s]*)(?:\\?([^#\\s]*))?(?:#\\S*)?")
    }
}
