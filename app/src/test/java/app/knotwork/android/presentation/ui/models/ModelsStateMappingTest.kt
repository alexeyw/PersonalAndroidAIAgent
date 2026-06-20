package app.knotwork.android.presentation.ui.models

import app.knotwork.android.domain.models.LocalModel
import app.knotwork.android.domain.models.ModelPerformanceSummary
import app.knotwork.android.domain.usecases.BenchmarkReport
import app.knotwork.android.domain.usecases.BenchmarkRunPhase
import app.knotwork.design.screens.models.BenchmarkPhase
import app.knotwork.design.screens.models.ModelsVisualState
import app.knotwork.design.screens.models.PerformanceCardState
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

    @Test
    fun `given no active model when mapped then performance card is NoModel`() {
        val result = ModelsUiState().toViewState(subtitleFormat)
        assertEquals(PerformanceCardState.NoModel, result.performance)
    }

    @Test
    fun `given active model with no history when mapped then performance card is Empty`() {
        val result = activeState().toViewState(subtitleFormat)
        assertEquals(PerformanceCardState.Empty, result.performance)
    }

    @Test
    fun `given active model and a summary when mapped then performance card is Populated and formatted`() {
        val state = activeState().copy(
            performanceSummary = ModelPerformanceSummary(
                sampleCount = 8,
                avgTtftMs = 420,
                avgDecodeTokensPerSec = 12.4f,
                peakNativeHeapBytes = (1.8 * 1024 * 1024 * 1024).toLong(),
            ),
        )

        val card = state.toViewState(subtitleFormat).performance
        assertTrue(card is PerformanceCardState.Populated)
        card as PerformanceCardState.Populated
        assertEquals("420 ms", card.ttft)
        assertEquals("12.4 tok/s", card.decode)
        assertEquals("1.8 GB", card.peakMemory)
        assertEquals("avg · last 8 runs", card.sampleCaption)
    }

    @Test
    fun `given engine busy when mapped then performance card is EngineBusy`() {
        val state = activeState().copy(engineBusy = true)
        assertEquals(PerformanceCardState.EngineBusy, state.toViewState(subtitleFormat).performance)
    }

    @Test
    fun `given a running benchmark when mapped then performance card is Running with the mapped phase`() {
        val state = activeState().copy(benchmark = BenchmarkUiState.Running(BenchmarkRunPhase.MEASURING))
        assertEquals(
            PerformanceCardState.Running(BenchmarkPhase.Measuring),
            state.toViewState(subtitleFormat).performance,
        )
    }

    @Test
    fun `given a benchmark result when mapped then performance card is Result and formatted`() {
        val report = BenchmarkReport("m", 390, 13.1f, 2_600, (1.8 * 1024 * 1024 * 1024).toLong())
        val state = activeState().copy(benchmark = BenchmarkUiState.Result(report))

        val card = state.toViewState(subtitleFormat).performance
        assertTrue(card is PerformanceCardState.Result)
        card as PerformanceCardState.Result
        assertEquals("390 ms", card.ttft)
        assertEquals("2.6 s", card.total)
    }

    private fun activeState(): ModelsUiState {
        val model = LocalModel(id = 7, name = "gemma.litertlm", path = "/x", size = 1_400_000_000L, isActive = true)
        return ModelsUiState(downloadedModels = listOf(model), activeModel = model)
    }
}
