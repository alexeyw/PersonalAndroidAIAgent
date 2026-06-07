package app.knotwork.android.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [Result] sealed interface to ensure Success and Error states
 * hold data and exceptions correctly.
 */
class ResultTest {

    private class DummyNetworkError : AppError.Network

    @Test
    fun `Success holds data correctly`() {
        val data = "Test Data"
        val result: Result<String, AppError> = Result.Success(data)

        assertTrue(result is Result.Success)
        assertEquals(data, (result as Result.Success).data)
    }

    @Test
    fun `Error holds error type and optional details correctly`() {
        val errorType = DummyNetworkError()
        val exception = RuntimeException("Test Exception")
        val message = "Something went wrong"

        val result: Result<String, AppError> = Result.Error(
            error = errorType,
            message = message,
            throwable = exception,
        )

        assertTrue(result is Result.Error)
        val errorResult = result as Result.Error
        assertEquals(errorType, errorResult.error)
        assertEquals(message, errorResult.message)
        assertEquals(exception, errorResult.throwable)
    }

    @Test
    fun `Error holds only error type when details are omitted`() {
        val errorType = DummyNetworkError()

        val result: Result<String, AppError> = Result.Error(error = errorType)

        assertTrue(result is Result.Error)
        val errorResult = result as Result.Error
        assertEquals(errorType, errorResult.error)
        assertNull(errorResult.message)
        assertNull(errorResult.throwable)
    }
}
