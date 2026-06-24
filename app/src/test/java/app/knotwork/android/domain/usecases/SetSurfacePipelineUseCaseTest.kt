package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.EntrySurface
import app.knotwork.android.domain.repositories.SettingsRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Unit tests for [SetSurfacePipelineUseCase], confirming each surface writes to
 * its own binding and that `null` clears it.
 */
class SetSurfacePipelineUseCaseTest {

    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val useCase = SetSurfacePipelineUseCase(settingsRepository)

    @Test
    fun `given SHARE when invoked then writes the share binding`() = runTest {
        useCase(EntrySurface.SHARE, "share-pipe")

        coVerify { settingsRepository.setShareTargetPipelineId("share-pipe") }
    }

    @Test
    fun `given QUICK_TILE when invoked then writes the tile binding`() = runTest {
        useCase(EntrySurface.QUICK_TILE, "tile-pipe")

        coVerify { settingsRepository.setQuickSettingsTilePipelineId("tile-pipe") }
    }

    @Test
    fun `given null pipeline when invoked then clears the binding`() = runTest {
        useCase(EntrySurface.SHARE, null)

        coVerify { settingsRepository.setShareTargetPipelineId(null) }
    }
}
