package app.kestrel.ui.harness

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import app.kestrel.ui.components.LotPhotoLoader
import java.util.concurrent.ConcurrentHashMap

/**
 * Decodes fixture photographs from the test resources (`photos/`), synchronously, so a screenshot never waits for the
 * network or a loader. A fixture photo URL is `fixture://<name>`, for example `fixture://canon_ae1`.
 */
object FixturePhotos : LotPhotoLoader {
    const val SCHEME = "fixture://"

    private val decoded = ConcurrentHashMap<String, ImageBitmap>()

    fun url(name: String): String = "$SCHEME$name"

    @Composable
    override fun painterFor(url: String): Painter? {
        val bitmap = remember(url) { bitmapFor(url) } ?: return null
        return remember(bitmap) { BitmapPainter(bitmap) }
    }

    private fun bitmapFor(url: String): ImageBitmap? {
        if (!url.startsWith(SCHEME)) return null
        val name = url.removePrefix(SCHEME)
        decoded[name]?.let { return it }
        val stream =
            listOf("jpg", "png").firstNotNullOfOrNull { extension ->
                FixturePhotos::class.java.getResourceAsStream("/photos/$name.$extension")
            } ?: error("No fixture photo called $name in src/test/resources/photos")
        val bitmap = stream.use { BitmapFactory.decodeStream(it) }.asImageBitmap()
        decoded[name] = bitmap
        return bitmap
    }
}
