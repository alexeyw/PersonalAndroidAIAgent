package app.knotwork.android.data.repositories

import app.knotwork.android.domain.repositories.SettingsRepository
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Unit tests for [FirebaseCrashReportingRepositoryImpl].
 *
 * Cover: every public method must be a strict no-op while the user has not
 * opted in (the on-device privacy contract), the Firebase SDK is invoked with
 * the expected payload once the opt-in flag flips to `true`, and [setEnabled]
 * drives **Crashlytics collection only**.
 *
 * That last one is a boundary, not a detail: the toggle used to flip Firebase
 * Analytics collection too, which the consent copy never mentioned. The
 * Analytics SDK is no longer on this build's classpath — so the guarantee is
 * enforced by the compiler, and this suite would not even build if the call
 * came back.
 */
class FirebaseCrashReportingRepositoryImplTest {

    private val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>()

    private fun repositoryWithFlag(enabled: Boolean): FirebaseCrashReportingRepositoryImpl {
        every { settingsRepository.crashReportingEnabled } returns flowOf(enabled)
        return FirebaseCrashReportingRepositoryImpl(settingsRepository, crashlytics)
    }

    @Test
    fun `given disabled when recordException then crashlytics is not touched`() = runTest {
        val repository = repositoryWithFlag(enabled = false)

        repository.recordException(IllegalStateException("boom"), mapOf("k" to "v"))

        verify(exactly = 0) { crashlytics.recordException(any()) }
        verify(exactly = 0) { crashlytics.setCustomKey(any<String>(), any<String>()) }
        verify(exactly = 0) { crashlytics.log(any()) }
    }

    @Test
    fun `given disabled when setCustomKey then crashlytics is not touched`() = runTest {
        val repository = repositoryWithFlag(enabled = false)

        repository.setCustomKey("active_pipeline_id", "abc")

        verify(exactly = 0) { crashlytics.setCustomKey(any<String>(), any<String>()) }
    }

    @Test
    fun `given enabled when recordException then forwards throwable with extras as transient log breadcrumbs`() =
        runTest {
            val repository = repositoryWithFlag(enabled = true)
            val throwable = RuntimeException("kaboom")
            justRun { crashlytics.recordException(throwable) }
            justRun { crashlytics.log(any()) }

            repository.recordException(
                throwable,
                mapOf("timber_message" to "node failed", "timber_tag" to "Engine"),
            )

            verify(exactly = 1) { crashlytics.log("timber_message=node failed") }
            verify(exactly = 1) { crashlytics.log("timber_tag=Engine") }
            verify(exactly = 1) { crashlytics.recordException(throwable) }
            // Extras must NOT leak into the session-wide custom-key namespace.
            verify(exactly = 0) { crashlytics.setCustomKey(any<String>(), any<String>()) }
        }

    @Test
    fun `given enabled when setCustomKey then forwards key and value`() = runTest {
        val repository = repositoryWithFlag(enabled = true)
        justRun { crashlytics.setCustomKey("k", "v") }

        repository.setCustomKey("k", "v")

        verify(exactly = 1) { crashlytics.setCustomKey("k", "v") }
    }

    @Test
    fun `setEnabled true enables Crashlytics collection and nothing else`() = runTest {
        val repository = repositoryWithFlag(enabled = false)
        every { crashlytics.isCrashlyticsCollectionEnabled = true } returns Unit

        repository.setEnabled(true)

        verify(exactly = 1) { crashlytics.isCrashlyticsCollectionEnabled = true }
        confirmVerified(crashlytics)
    }

    @Test
    fun `setEnabled false disables Crashlytics collection and nothing else`() = runTest {
        val repository = repositoryWithFlag(enabled = true)
        every { crashlytics.isCrashlyticsCollectionEnabled = false } returns Unit

        repository.setEnabled(false)

        verify(exactly = 1) { crashlytics.isCrashlyticsCollectionEnabled = false }
        confirmVerified(crashlytics)
    }

    @Test
    fun `setEnabled swallows underlying SDK failure`() = runTest {
        val repository = repositoryWithFlag(enabled = false)
        every { crashlytics.isCrashlyticsCollectionEnabled = true } throws IllegalStateException("nope")

        repository.setEnabled(true)
    }

    @Test
    fun `recordException swallows underlying SDK failure when enabled`() = runTest {
        val repository = repositoryWithFlag(enabled = true)
        every { crashlytics.recordException(any()) } throws IllegalStateException("nope")

        repository.recordException(RuntimeException("x"))
    }

    @Test
    fun `setCustomKey swallows underlying SDK failure when enabled`() = runTest {
        val repository = repositoryWithFlag(enabled = true)
        every { crashlytics.setCustomKey(any<String>(), any<String>()) } throws IllegalStateException("nope")

        repository.setCustomKey("k", "v")
    }
}
