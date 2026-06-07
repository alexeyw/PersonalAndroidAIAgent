package app.knotwork.android.domain.models

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AppError] sealed interface to ensure all expected sub-types can be instantiated
 * and correctly implement the base interface.
 */
class AppErrorTest {

    private class TestNetworkError : AppError.Network
    private class TestDatabaseError : AppError.Database
    private class TestSystemError : AppError.System
    private class TestUnknownError : AppError.Unknown

    @Test
    fun `Network error implements AppError`() {
        val error = TestNetworkError()
        assertTrue(error is AppError)
    }

    @Test
    fun `Database error implements AppError`() {
        val error = TestDatabaseError()
        assertTrue(error is AppError)
    }

    @Test
    fun `System error implements AppError`() {
        val error = TestSystemError()
        assertTrue(error is AppError)
    }

    @Test
    fun `Unknown error implements AppError`() {
        val error = TestUnknownError()
        assertTrue(error is AppError)
    }
}
