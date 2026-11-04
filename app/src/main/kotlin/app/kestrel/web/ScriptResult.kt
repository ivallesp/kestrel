package app.kestrel.web

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/** Decodes the value WebView.evaluateJavascript hands its callback: the script's result, serialised as JSON. */
object ScriptResult {
    /**
     * A string result arrives as a JSON string literal, for example `"{\"ok\":true}"`, and is returned unquoted.
     * Any other value comes back as its JSON text ("null" for null and undefined, "{}" for a Promise).
     */
    fun decode(rawValue: String?): String {
        val trimmed = rawValue?.trim() ?: return "null"
        if (!trimmed.startsWith('"')) return trimmed.ifEmpty { "null" }
        return try {
            val element = Json.parseToJsonElement(trimmed)
            if (element is JsonPrimitive && element.isString) element.content else trimmed
        } catch (malformed: SerializationException) {
            trimmed
        } catch (malformed: IllegalArgumentException) {
            trimmed
        }
    }
}
