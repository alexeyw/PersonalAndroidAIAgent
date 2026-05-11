package ai.agent.android.data.repositories

import ai.agent.android.domain.repositories.SettingsRepository
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import io.mockk.verify as mockkVerify

/**
 * Unit tests for [FirebaseCrashReportingRepositoryImpl].
 *
 * Cover: every public method must be a strict no-op while the user has not
 * opted in (the on-device privacy contract), the Firebase SDK is invoked
 * with the expected payload once the opt-in flag flips to `true`, and the
 * combined Crashlytics/Analytics toggle behaviour of [setEnabled].
 */
class FirebaseCrashReportingRepositoryImplTest {

    private val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
    private val analytics = mockk<FirebaseAnalytics>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>()

    private fun repositoryWithFlag(enabled: Boolean): FirebaseCrashReportingRepositoryImpl {
        every { settingsRepository.crashReportingEnabled } returns flowOf(enabled)
        return FirebaseCrashReportingRepositoryImpl(settingsRepository, crashlytics, analytics)
    }

    @Test
    fun `given disabled when recordException then crashlytics is not touched`() = runTest {
        val repository = repositoryWithFlag(enabled = false)

        repository.recordException(IllegalStateException("boom"))

        verify(exactly = 0) { crashlytics.recordException(any()) }
        verify(exactly = 0) { crashlytics.setCustomKey(any<String>(), any<String>()) }
    }

    @Test
    fun `given disabled when setCustomKey then crashlytics is not touched`() = runTest {
        val repository = repositoryWithFlag(enabled = false)

        repository.setCustomKey("active_pipeline_id", "abc")

        verify(exactly = 0) { crashlytics.setCustomKey(any<String>(), any<String>()) }
    }

    @Test
    fun `given enabled when recordException then forwards throwable and extras`() = runTest {
        val repository = repositoryWithFlag(enabled = true)
        val throwable = RuntimeException("kaboom")
        justRun { crashlytics.recordException(throwable) }
        justRun { crashlytics.setCustomKey("active_pipeline_id", "p-1") }
        justRun { crashlytics.setCustomKey("active_model", "gemma-2b") }

        repository.recordException(
            throwable,
            mapOf("active_pipeline_id" to "p-1", "active_model" to "gemma-2b"),
        )

        verify(exactly = 1) { crashlytics.setCustomKey("active_pipeline_id", "p-1") }
        verify(exactly = 1) { crashlytics.setCustomKey("active_model", "gemma-2b") }
        verify(exactly = 1) { crashlytics.recordException(throwable) }
    }

    @Test
    fun `given enabled when setCustomKey then forwards key and value`() = runTest {
        val repository = repositoryWithFlag(enabled = true)
        justRun { crashlytics.setCustomKey("k", "v") }

        repository.setCustomKey("k", "v")

        verify(exactly = 1) { crashlytics.setCustomKey("k", "v") }
    }

    @Test
    fun `setEnabled true toggles both Crashlytics and Analytics collection on`() = runTest {
        val repository = repositoryWithFlag(enabled = false)
        every { crashlytics.isCrashlyticsCollectionEnabled = true } returns Unit
        justRun { analytics.setAnalyticsCollectionEnabled(true) }

        repository.setEnabled(true)

        mockkVerify(exactly = 1) { crashlytics.isCrashlyticsCollectionEnabled = true }
        mockkVerify(exactly = 1) { analytics.setAnalyticsCollectionEnabled(true) }
    }

    @Test
    fun `setEnabled false toggles both Crashlytics and Analytics collection off`() = runTest {
        val repository = repositoryWithFlag(enabled = true)
        every { crashlytics.isCrashlyticsCollectionEnabled = false } returns Unit
        justRun { analytics.setAnalyticsCollectionEnabled(false) }

        repository.setEnabled(false)

        mockkVerify(exactly = 1) { crashlytics.isCrashlyticsCollectionEnabled = false }
        mockkVerify(exactly = 1) { analytics.setAnalyticsCollectionEnabled(false) }
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
