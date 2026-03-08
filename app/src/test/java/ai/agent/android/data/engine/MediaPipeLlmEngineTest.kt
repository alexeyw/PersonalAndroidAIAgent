package ai.agent.android.data.engine

import android.content.Context
import ai.agent.android.domain.models.Result
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit tests for [MediaPipeLlmEngine].
 */
class MediaPipeLlmEngineTest {

    private lateinit var context: Context
    private lateinit var engine: MediaPipeLlmEngine

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        engine = MediaPipeLlmEngine(context)
    }

    @After
    fun tearDown() {
        engine.close()
        unmockkAll()
    }

    @Test
    fun `initialize returns Error when model file does not exist`() = runTest {
        // Arrange
        val missingPath = "/path/that/does/not/exist/model.bin"

        // Act
        val result = engine.initialize(missingPath)

        // Assert
        assertTrue("Expected Result.Error but was ${result::class.simpleName}", result is Result.Error)
    }

    @Test
    fun `initialize returns Success when model file exists`() = runTest {
        // Arrange
        val tempFile = File.createTempFile("test_model", ".bin")
        tempFile.deleteOnExit()
        
        // Mock MediaPipe LlmInference creation
        mockkStatic(LlmInference::class)
        val mockLlmInference = mockk<LlmInference>(relaxed = true)
        every { LlmInference.createFromOptions(any(), any()) } returns mockLlmInference

        // Act
        val result = engine.initialize(tempFile.absolutePath)

        // Assert
        assertTrue("Expected Result.Success but was ${result::class.simpleName}", result is Result.Success)
    }

    @Test
    fun `generateResponseStream returns flow error if not initialized`() = runTest {
        // Arrange & Act
        try {
            engine.generateResponseStream("Hello").toList()
            assert(false) { "Expected an exception to be thrown" }
        } catch (e: Exception) {
            // Assert
            assertTrue(e is IllegalStateException)
        }
    }
}