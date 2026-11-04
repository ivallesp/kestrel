package app.kestrel.web

import android.content.Context
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** The functions of window.__kestrel that Kotlin calls. */
enum class PageFunction(
    val jsName: String,
) {
    PROBE("probe"),
    MODEL_TEXT("modelText"),
    AUTO_REFRESH("autoRefresh"),
    AUTO_REFRESH_RESULT("autoRefreshResult"),
    OPEN_BID_LAYER("openBidLayer"),
    LAYER_STATE("layerState"),
    TYPE_MAX("typeMax"),
    ARM_OBSERVER("armObserver"),
    SUBMIT("submit"),
    ELEMENT_CENTER("elementCenter"),
    ARM_TAP("armTap"),
    TAP_RESULT("tapResult"),
    DISARM_TAP("disarmTap"),
    ARM_TOUCH_PROBE("armTouchProbe"),
    TOUCH_PROBE_RESULT("touchProbeResult"),
    REPLY_STATE("replyState"),
    CLOSE_LAYER("closeLayer"),
    REPORT("report"),
}

/** kestrel.js (app/src/main/assets/js/kestrel.js) and the expressions that install and call it. */
class PageScript(
    val source: String,
) {
    val version: String =
        versionPattern.find(source)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("kestrel.js declares no VERSION")

    /** Installs the helper in the current document; a no-op when this version is already there. */
    val installExpression: String get() = "$source\n;true"

    /** Calls one function; the expression evaluates to its JSON string, or to null when the helper is missing. */
    fun callExpression(
        function: PageFunction,
        arguments: List<JsonElement> = emptyList(),
    ): String =
        "(function(){var k=window.__kestrel;if(!k||k.version!=='$version'){return '$HELPER_MISSING';}" +
            "return k.${function.jsName}(${arguments.joinToString(",")});})()"

    companion object {
        const val ASSET_PATH = "js/kestrel.js"

        /**
         * What a call answers when the helper is not in the document, so the function certainly did not run. A null
         * answer means something else: the function may have run and its answer was lost.
         */
        const val HELPER_MISSING = """{"ok":false,"helperMissing":true}"""
        private val versionPattern = Regex("""var VERSION = '([A-Za-z0-9._-]+)'""")

        @Volatile
        private var cached: PageScript? = null

        /** Reads the asset once per process. */
        fun fromAssets(context: Context): PageScript =
            cached ?: synchronized(this) {
                cached ?: context.assets
                    .open(ASSET_PATH)
                    .bufferedReader()
                    .use { PageScript(it.readText()) }
                    .also { cached = it }
            }
    }
}

internal val pageJson =
    Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
    }

/**
 * Calls kestrel.js through a [PageDriver]. Every page navigation drops the helper, so a call that finds it missing
 * installs it and tries once more.
 */
internal class PageHelper(
    private val driver: PageDriver,
    private val script: PageScript,
    private val timeoutMillis: Long = PageDriver.DEFAULT_SCRIPT_TIMEOUT_MILLIS,
) {
    suspend fun call(
        function: PageFunction,
        vararg arguments: JsonElement,
    ): JsonObject {
        val expression = script.callExpression(function, arguments.toList())
        var raw = driver.evaluate(expression, timeoutMillis)
        if (raw == PageScript.HELPER_MISSING) {
            // Nothing ran, so installing the helper and calling again cannot do anything twice.
            driver.evaluate(script.installExpression, timeoutMillis)
            raw = driver.evaluate(expression, timeoutMillis)
            if (raw == PageScript.HELPER_MISSING) {
                throw PageScriptException("kestrel.js could not be installed on ${driver.currentUrl}")
            }
        }
        // Never retried: the function may have run (a press, say) before its answer was lost.
        if (raw == "null") throw ScriptAnswerLostException("kestrel.js ${function.jsName} ran but its answer was lost")
        val element =
            try {
                pageJson.parseToJsonElement(raw)
            } catch (unreadable: SerializationException) {
                throw PageScriptException("kestrel.js ${function.jsName} answered unreadable JSON: ${raw.take(200)}")
            }
        return element as? JsonObject
            ?: throw PageScriptException("kestrel.js ${function.jsName} answered ${raw.take(200)}")
    }

    suspend fun <T> call(
        function: PageFunction,
        strategy: DeserializationStrategy<T>,
        vararg arguments: JsonElement,
    ): T {
        val reply = call(function, *arguments)
        return try {
            pageJson.decodeFromJsonElement(strategy, reply)
        } catch (unexpected: SerializationException) {
            throw PageScriptException(
                "kestrel.js ${function.jsName} answered an unexpected shape: ${unexpected.message}",
            )
        } catch (unexpected: IllegalArgumentException) {
            throw PageScriptException(
                "kestrel.js ${function.jsName} answered an unexpected shape: ${unexpected.message}",
            )
        }
    }
}
