package ai.agent.android.data.logging

import ai.agent.android.domain.repositories.CrashReportingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import timber.log.Timber

/**
 * Unit tests for [CrashlyticsTimberTree].
 *
 * Cover: severity filtering (only `WARN` / `ERROR` reach the repository)
 * and routing semantics — explicit throwables are forwarded verbatim while
 * message-only records are wrapped in a synthetic exception whose message
 * preserves the original tag/body so Crashlytics still has something to
 * group on.
 *
 * The tree is exercised through Timber's public API because
 * `Timber.Tree.log` and `isLoggable` are protected by design.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CrashlyticsTimberTreeTest {

    private val crashReportingRepository = mockk<CrashReportingRepository>(relaxed = true)
    private lateinit var dispatcher: TestDispatcher
    private lateinit var scope: TestScope
    private lateinit var tree: CrashlyticsTimberTree

    @Before
    fun setup() {
        dispatcher = StandardTestDispatcher()
        scope = TestScope(dispatcher)
        tree = CrashlyticsTimberTree(crashReportingRepository, scope)
        Timber.plant(tree)
    }

    @After
    fun tearDown() {
        Timber.uproot(tree)
    }

    @Test
    fun `verbose info and debug records do not reach the repository`() = runTest {
        coEvery { crashReportingRepository.recordException(any(), any()) } returns Unit

        Timber.v("verbose message")
        Timber.d("debug message")
        Timber.i("info message")
        scope.advanceUntilIdle()

        coVerify(exactly = 0) { crashReportingRepository.recordException(any(), any()) }
    }

    @Test
    fun `error with explicit throwable forwards throwable plus message and tag as extras`() = runTest {
        val throwable = IllegalStateException("boom")
        coEvery { crashReportingRepository.recordException(any(), any()) } returns Unit

        Timber.tag("Engine").e(throwable, "node crashed")
        scope.advanceUntilIdle()

        coVerify(exactly = 1) {
            crashReportingRepository.recordException(
                throwable,
                mapOf("timber_message" to "node crashed", "timber_tag" to "Engine"),
            )
        }
    }

    @Test
    fun `error with throwable but no tag still forwards message extra`() = runTest {
        val throwable = IllegalStateException("boom")
        coEvery { crashReportingRepository.recordException(any(), any()) } returns Unit

        Timber.e(throwable, "untagged failure")
        scope.advanceUntilIdle()

        coVerify(exactly = 1) {
            crashReportingRepository.recordException(
                throwable,
                mapOf("timber_message" to "untagged failure"),
            )
        }
    }

    @Test
    fun `warn without throwable wraps message in synthetic exception with tag`() = runTest {
        val captured = slot<Throwable>()
        coEvery { crashReportingRepository.recordException(capture(captured), any()) } returns Unit

        Timber.tag("TestTag").w("something looks off")
        scope.advanceUntilIdle()

        coVerify(exactly = 1) { crashReportingRepository.recordException(any(), emptyMap()) }
        assertEquals("[TestTag] something looks off", captured.captured.message)
    }

    @Test
    fun `error without tag still produces readable synthetic message`() = runTest {
        val captured = slot<Throwable>()
        coEvery { crashReportingRepository.recordException(capture(captured), any()) } returns Unit

        Timber.e("untagged failure")
        scope.advanceUntilIdle()

        assertEquals("untagged failure", captured.captured.message)
    }
}
