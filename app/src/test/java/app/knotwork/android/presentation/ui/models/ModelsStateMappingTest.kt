package app.knotwork.android.presentation.ui.models

import app.knotwork.android.domain.models.LocalModel
import app.knotwork.design.screens.models.ModelsVisualState
import app.knotwork.design.screens.models.PresetStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies `ModelsUiState.toViewState(...)` — the mapper bridging the
 * data layer projection onto the catalog `ModelsViewState`.
 */
class ModelsStateMappingTest {

    private val subtitleFormat = "%1\$d active · %2\$d on disk · %3\$s"

    @Test
    fun `given empty state when mapped then subtitle reads 0 active 0 on disk 0 GB`() {
        val result = ModelsUiState().toViewState(subtitleFormat)

        assertEquals("0 active · 0 on disk · 0 GB", result.subtitle)
        assertNull(result.active)
    }

    @Test
    fun `given active model when mapped then ActiveModelRow populated`() {
        val model = LocalModel(id = 7, name = "gemma.litertlm", path = "/x", size = 1_400_000_000L, isActive = true)
        val state = ModelsUiState(downloadedModels = listOf(model), activeModel = model)

        val result = state.toViewState(subtitleFormat)

        assertNotNull(result.active)
        assertEquals(7L, result.active?.id)
        assertEquals("gemma.litertlm", result.active?.displayName)
        assertTrue(result.subtitle.startsWith("1 active · 1 on disk · 1.3 GB"))
    }

    @Test
    fun `given downloading preset when mapped then preset status is Downloading with progress`() {
        val state = ModelsUiState(
            isDownloading = true,
            downloadProgress = 42,
            activeDownloadFileName = "gemma-4-E4B-it.litertlm",
        )

        val result = state.toViewState(subtitleFormat)

        val target = result.presets.first { it.id == "gemma-4-E4B-it" }
        assertTrue(target.status is PresetStatus.Downloading)
        assertEquals(42, (target.status as PresetStatus.Downloading).progress)
    }

    @Test
    fun `given preset already on disk when mapped then preset status is OnDisk`() {
        val onDisk = LocalModel(
            id = 1,
            name = "gemma-4-E2B-it.litertlm",
            path = "/x",
            size = 1_400_000_000L,
            isActive = false,
        )
        val state = ModelsUiState(downloadedModels = listOf(onDisk))

        val result = state.toViewState(subtitleFormat)

        val target = result.presets.first { it.id == "gemma-4-E2B-it" }
        assertTrue(target.status is PresetStatus.OnDisk)
    }

    @Test
    fun `given pristine state when mapped then visualState is Default`() {
        val result = ModelsUiState().toViewState(subtitleFormat)
        assertEquals(ModelsVisualState.Default, result.visualState)
    }
}
