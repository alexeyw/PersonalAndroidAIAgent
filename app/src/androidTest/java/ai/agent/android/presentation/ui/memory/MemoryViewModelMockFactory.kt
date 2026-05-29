@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")
// File hosts both the `mockMemoryViewModel` factory function (primary
// export) and its sibling `MemoryMockHandles` data class. Naming after
// the factory is preferred since tests reach for it by name.

package ai.agent.android.presentation.ui.memory

import ai.agent.android.domain.models.MemoryChunk
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Mutable mirror of every [MutableStateFlow] that backs [MemoryViewModel].
 * Tests mutate the flows directly to drive the screen through state
 * transitions, then call `composeTestRule.waitForIdle()` to recompose.
 *
 * Mirrors the pattern established by `ChatHomeMockHandles` /
 * `OrchestratorMockHandles` in Phase 23-6 / 23-7: exposing the mutable
 * handles keeps tests free of MockK re-stubbing between phases of a
 * single scenario.
 */
internal class MemoryMockHandles(val uiStateFlow: MutableStateFlow<MemoryUiState>)

/**
 * Builds a relaxed [MemoryViewModel] mock with [uiState] stubbed to a
 * deterministic starting value, plus a sibling [MemoryMockHandles] bundle
 * that lets the test mutate the flow without re-stubbing.
 *
 * Defaults render a non-empty surface populated with two unpinned memory
 * rows so the search-debounce and interaction tests have something to act
 * against.
 */
internal fun mockMemoryViewModel(
    initialUiState: MemoryUiState = MemoryUiState(
        vectorMemories = listOf(
            sampleMemoryChunk(id = 1L, text = "alpha note about coffee"),
            sampleMemoryChunk(id = 2L, text = "beta note about tea"),
        ),
        isLoading = false,
    ),
): Pair<MemoryViewModel, MemoryMockHandles> {
    val uiStateFlow = MutableStateFlow(initialUiState)
    val vm = mockk<MemoryViewModel>(relaxed = true)
    every { vm.uiState } returns uiStateFlow
    every { vm.exportRequests } returns MutableSharedFlow()
    val handles = MemoryMockHandles(uiStateFlow = uiStateFlow)
    return vm to handles
}

/** Builds a synthetic [MemoryChunk] with a deterministic embedding payload. */
internal fun sampleMemoryChunk(
    id: Long,
    text: String,
    timestamp: Long = id * SAMPLE_TIMESTAMP_STEP_MS,
    isPinned: Boolean = false,
): MemoryChunk = MemoryChunk(
    id = id,
    text = text,
    embedding = FloatArray(size = SAMPLE_EMBEDDING_SIZE) { 0f },
    timestamp = timestamp,
    isPinned = isPinned,
)

private const val SAMPLE_EMBEDDING_SIZE = 4
private const val SAMPLE_TIMESTAMP_STEP_MS = 1_000L
