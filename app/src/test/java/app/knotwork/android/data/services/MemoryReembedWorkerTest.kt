package app.knotwork.android.data.services

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import app.knotwork.android.domain.usecases.RecomputePendingEmbeddingsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Robolectric coverage for the `@HiltWorker`-annotated [MemoryReembedWorker].
 *
 * Mirrors [MemoryCompactionWorkerTest]: Hilt's assisted-inject only fires in a
 * real runtime, so the mocked use case is handed to the worker via a manual
 * [WorkerFactory] and `doWork()` is driven through [TestListenableWorkerBuilder].
 */
@RunWith(RobolectricTestRunner::class)
class MemoryReembedWorkerTest {

    private lateinit var context: Context
    private lateinit var recomputePendingEmbeddings: RecomputePendingEmbeddingsUseCase

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        recomputePendingEmbeddings = mockk()
    }

    private fun workerFactory(): WorkerFactory = object : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker = MemoryReembedWorker(appContext, workerParameters, recomputePendingEmbeddings)
    }

    private fun buildWorker(): MemoryReembedWorker = TestListenableWorkerBuilder<MemoryReembedWorker>(context)
        .setWorkerFactory(workerFactory())
        .build()

    @Test
    fun `doWork returns success after running the re-embed pass`() = runTest {
        coEvery { recomputePendingEmbeddings() } returns 3

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { recomputePendingEmbeddings() }
    }

    @Test
    fun `doWork returns retry when the re-embed pass throws`() = runTest {
        coEvery { recomputePendingEmbeddings() } throws RuntimeException("offline")

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `unique work name matches the public contract`() {
        assertEquals("memory-reembed", MemoryReembedWorker.UNIQUE_NAME)
    }
}
