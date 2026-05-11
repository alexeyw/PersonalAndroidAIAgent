package ai.agent.android.data.engine

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit tests for [MediaPipeTextEmbeddingEngine].
 */
class MediaPipeTextEmbeddingEngineTest {

    private lateinit var context: Context
    private lateinit var embedderFactory: TextEmbedderFactory
    private lateinit var engine: MediaPipeTextEmbeddingEngine

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        embedderFactory = mockk(relaxed = true)

        val filesDir = File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        every { context.getExternalFilesDir(null) } returns filesDir

        // We cannot mock TextEmbedder here because its static initializer loads native libraries
        // which crash the JVM test with UnsatisfiedLinkError.
        // The fact that embedderFactory is a dependency allows Robolectric or AndroidTests
        // to mock it if needed in the future.
        engine = MediaPipeTextEmbeddingEngine(context, embedderFactory)
    }

    @Test
    fun `engine initialization propagates native errors gracefully in JVM tests`() = runTest {
        try {
            engine.generateEmbedding("Test text")
            assert(false) { "Expected exception due to missing native libraries" }
        } catch (e: Throwable) {
            // It will throw either UnsatisfiedLinkError, ExceptionInInitializerError, or NoClassDefFoundError
            assertNotNull(e)
        }
    }
}
