package app.kestrel.ui.screens.add

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.view.textclassifier.TextClassifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * The clipboard as S2 uses it. Whether it holds a link comes from the clip's description only, which carries no
 * text, so Android shows no "pasted from your clipboard" notice; the text itself is read only when the user taps
 * Paste.
 */
class ClipboardLinks(
    private val context: Context,
) {
    private val manager: ClipboardManager? get() = context.getSystemService(ClipboardManager::class.java)

    /** True when the clip is a link: a URI list, or plain text the system classified as a URL. */
    fun holdsLink(): Boolean {
        val description = manager?.primaryClipDescription ?: return false
        if (description.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST)) return true
        if (!description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) return false
        return description.classificationStatus == ClipDescription.CLASSIFICATION_COMPLETE &&
            description.getConfidenceScore(TextClassifier.TYPE_URL) >= URL_CONFIDENCE
    }

    /** The clip's text. Call it only from the Paste tap. */
    fun read(): String? {
        val clip = manager?.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip
            .getItemAt(0)
            .coerceToText(context)
            ?.toString()
            ?.takeIf { it.isNotBlank() }
    }

    internal fun addListener(listener: ClipboardManager.OnPrimaryClipChangedListener) {
        manager?.addPrimaryClipChangedListener(listener)
    }

    internal fun removeListener(listener: ClipboardManager.OnPrimaryClipChangedListener) {
        manager?.removePrimaryClipChangedListener(listener)
    }

    private companion object {
        /** The classifier's score from which a clip counts as a link. */
        const val URL_CONFIDENCE = 0.5f
    }
}

/** Whether [clipboard] holds a link, checked again when the screen comes back and whenever the clip changes. */
@Composable
internal fun rememberClipboardHoldsLink(clipboard: ClipboardLinks): Boolean {
    var holdsLink by remember(clipboard) { mutableStateOf(false) }
    LifecycleResumeEffect(clipboard) {
        holdsLink = clipboard.holdsLink()
        onPauseOrDispose {}
    }
    DisposableEffect(clipboard) {
        val listener = ClipboardManager.OnPrimaryClipChangedListener { holdsLink = clipboard.holdsLink() }
        clipboard.addListener(listener)
        onDispose { clipboard.removeListener(listener) }
    }
    return holdsLink
}
