package app.kestrel.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ArmedSnipesCopyTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun newFile() = File(context.noBackupFilesDir, "armed-${System.nanoTime()}.json")

    @Test
    fun writtenSnipesReadBack() {
        val file = newFile()
        val snipes =
            listOf(
                ArmedSnipe(1, "Canon AE-1", TEST_NOW + HOUR),
                ArmedSnipe(
                    2,
                    "Leica \"M3\" ✓",
                    TEST_NOW + 2 * HOUR,
                ),
            )

        ArmedSnipesCopy(file).write(snipes)

        assertEquals(snipes, ArmedSnipesCopy(file).read())
    }

    @Test
    fun aMissingFileMeansNoArmedSnipes() {
        assertTrue(ArmedSnipesCopy(newFile()).read().isEmpty())
    }

    @Test
    fun aDamagedFileMeansNoArmedSnipesRatherThanACrash() {
        val file = newFile().apply { writeText("{not json") }

        assertTrue(ArmedSnipesCopy(file).read().isEmpty())
    }

    @Test
    fun anEmptyListReplacesTheOldCopy() {
        val file = newFile()
        val copy = ArmedSnipesCopy(file)
        copy.write(listOf(ArmedSnipe(1, "Canon AE-1", TEST_NOW + HOUR)))

        copy.write(emptyList())

        assertTrue(ArmedSnipesCopy(file).read().isEmpty())
    }

    @Test
    fun theDeviceProtectedCopyIsReadableThroughItsOwnFactory() {
        val snipes = listOf(ArmedSnipe(7, "Nikon FM2", TEST_NOW + HOUR))
        ArmedSnipesCopy.inDeviceProtectedStorage(context).write(snipes)

        assertEquals(snipes, ArmedSnipesCopy.inDeviceProtectedStorage(context).read())
    }
}
