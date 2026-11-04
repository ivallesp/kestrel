package app.kestrel.ui.screens.signinbrowser

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kestrel.data.AddResult
import app.kestrel.data.MINUTE
import app.kestrel.data.TEST_NOW
import app.kestrel.data.gbp
import app.kestrel.data.testListing
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeStatus
import app.kestrel.model.TimelineKind
import app.kestrel.platform.SnipeAction
import app.kestrel.platform.SnipeRequest
import app.kestrel.platform.TestKestrelApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Signing in again looks at the lots that met eBay's sign-in page at once, so they stop reading as paused. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestKestrelApp::class)
class SignInRecheckTest {
    private val app = ApplicationProvider.getApplicationContext<TestKestrelApp>()
    private val repository = app.testContainer.repository

    private suspend fun add(itemId: String): Snipe =
        (
            repository.addFromListing(
                testListing(itemId = itemId),
                gbp("48.00"),
                leadSeconds = 6,
            ) as AddResult.Added
        ).snipe

    private suspend fun dryRunFailed(
        snipe: Snipe,
        reason: String,
    ) {
        repository.update(
            snipe.copy(
                status = SnipeStatus.DRY_RUN_FAILED,
                dryRunAtUtcMillis = TEST_NOW - MINUTE,
                dryRunPassed = false,
            ),
        )
        repository.addEvent(snipe.id, TimelineKind.DRY_RUN, "Dry run failed.", """{"reason":"$reason"}""")
    }

    /** The jobs started within [timeoutMillis], as (action, snipe id). */
    private fun startedJobs(
        expected: Int,
        timeoutMillis: Long = 5_000,
    ): List<Pair<SnipeAction, Long>> {
        val started = mutableListOf<Intent>()
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (started.size < expected && System.currentTimeMillis() < deadline) {
            shadowOf(app).nextStartedService?.let { started += it } ?: Thread.sleep(20)
        }
        Thread.sleep(200)
        generateSequence { shadowOf(app).nextStartedService }.forEach { started += it }
        return started.mapNotNull { SnipeRequest.from(it) }.map { it.action to it.snipeId }
    }

    @Test
    fun signingInChecksSignedOutLotsAndRerunsDryRunsThatMetTheSignInPage() {
        val (signedOut, dryRunOnSignIn) =
            runBlocking {
                val signedOut = add("1")
                repository.update(signedOut.copy(status = SnipeStatus.NEEDS_SIGN_IN))
                val dryRunOnSignIn = add("2")
                dryRunFailed(dryRunOnSignIn, reason = "SIGN_IN")
                dryRunFailed(add("3"), reason = "DRY_RUN_FAILED")
                add("4")
                signedOut to dryRunOnSignIn
            }

        ContainerSignInSession(app.testContainer, app).keep("oakshelf")

        assertEquals(
            setOf(SnipeAction.CHECK to signedOut.id, SnipeAction.DRY_RUN to dryRunOnSignIn.id),
            startedJobs(expected = 2).toSet(),
        )
    }
}
