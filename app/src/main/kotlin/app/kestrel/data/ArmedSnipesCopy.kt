package app.kestrel.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/** What the locked-after-reboot notice needs to know about one armed snipe. */
@Serializable
data class ArmedSnipe(
    val id: Long,
    val title: String,
    val endTimeUtcMillis: Long,
)

/**
 * A small copy of the armed snipes in device-protected storage. After a reboot the database and the eBay session stay
 * locked until the first unlock, but this file can be read before it, so the phone can say how many snipes wait.
 */
class ArmedSnipesCopy(
    file: File,
) {
    private val atomicFile = AtomicFile(file)
    private val serializer = ListSerializer(ArmedSnipe.serializer())
    private val json = Json { ignoreUnknownKeys = true }
    private var lastWritten: String? = null

    @Synchronized
    fun write(snipes: List<ArmedSnipe>) {
        val text = json.encodeToString(serializer, snipes)
        if (text == lastWritten) return
        try {
            atomicFile.baseFile.parentFile?.mkdirs()
            val stream = atomicFile.startWrite()
            try {
                stream.write(text.toByteArray(Charsets.UTF_8))
                atomicFile.finishWrite(stream)
                lastWritten = text
            } catch (error: IOException) {
                atomicFile.failWrite(stream)
                throw error
            }
        } catch (error: IOException) {
            Log.w(TAG, "Could not write the armed snipes copy", error)
        }
    }

    @Synchronized
    fun read(): List<ArmedSnipe> =
        try {
            if (!atomicFile.baseFile.exists()) {
                emptyList()
            } else {
                json.decodeFromString(serializer, atomicFile.readFully().toString(Charsets.UTF_8))
            }
        } catch (error: IOException) {
            Log.w(TAG, "Could not read the armed snipes copy", error)
            emptyList()
        } catch (error: IllegalArgumentException) {
            // kotlinx.serialization reports a damaged file as SerializationException, a subclass.
            Log.w(TAG, "The armed snipes copy is damaged", error)
            emptyList()
        }

    companion object {
        private const val TAG = "ArmedSnipesCopy"
        private const val FILE_NAME = "armed_snipes.json"

        fun inDeviceProtectedStorage(context: Context): ArmedSnipesCopy {
            val storage = context.createDeviceProtectedStorageContext()
            return ArmedSnipesCopy(File(storage.noBackupFilesDir, FILE_NAME))
        }
    }
}
