@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")
// File hosts both the `mockPromptLibraryViewModel` factory function
// (primary export) and its sibling `PromptLibraryMockHandles` data class.

package ai.agent.android.presentation.ui.prompts

import ai.agent.android.domain.models.PromptTemplate
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Mutable mirror of [PromptLibraryViewModel.uiState] used by androidTest
 * scenarios to drive the screen through list / editor phases without
 * re-stubbing the mock.
 */
internal class PromptLibraryMockHandles(val uiStateFlow: MutableStateFlow<PromptLibraryUiState>)

/**
 * Builds a relaxed [PromptLibraryViewModel] mock with [uiState] stubbed
 * to a deterministic starting value.
 */
internal fun mockPromptLibraryViewModel(
    initialUiState: PromptLibraryUiState = PromptLibraryUiState(),
): Pair<PromptLibraryViewModel, PromptLibraryMockHandles> {
    val uiStateFlow = MutableStateFlow(initialUiState)
    val vm = mockk<PromptLibraryViewModel>(relaxed = true)
    every { vm.uiState } returns uiStateFlow
    val handles = PromptLibraryMockHandles(uiStateFlow = uiStateFlow)
    return vm to handles
}

/** Builds a [PromptTemplate] sample with the given identifiers. */
internal fun samplePromptTemplate(
    id: Long,
    name: String,
    category: String = "DECOMPOSITION",
    text: String = "$name body",
): PromptTemplate = PromptTemplate(id = id, name = name, text = text, category = category)
