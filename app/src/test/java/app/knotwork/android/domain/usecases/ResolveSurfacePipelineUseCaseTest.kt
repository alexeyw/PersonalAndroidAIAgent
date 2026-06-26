package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.EntrySurface
import app.knotwork.android.domain.repositories.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [ResolveSurfacePipelineUseCase], confirming each surface reads
 * its own binding flow and passes through `null` (the inert default).
 */
class ResolveSurfacePipelineUseCaseTest {

    private val settingsRepository = mockk<SettingsRepository>()
    private val useCase = ResolveSurfacePipelineUseCase(settingsRepository)

    @Test
    fun `given share binding when resolving SHARE then returns it`() = runTest {
        every { settingsRepository.shareTargetPipelineId } returns flowOf("share-pipe")

        assertEquals("share-pipe", useCase(EntrySurface.SHARE))
    }

    @Test
    fun `given tile binding when resolving QUICK_TILE then returns it`() = runTest {
        every { settingsRepository.quickSettingsTilePipelineId } returns flowOf("tile-pipe")

        assertEquals("tile-pipe", useCase(EntrySurface.QUICK_TILE))
    }

    @Test
    fun `given no share binding when resolving SHARE then returns null`() = runTest {
        every { settingsRepository.shareTargetPipelineId } returns flowOf(null)

        assertNull(useCase(EntrySurface.SHARE))
    }
}
