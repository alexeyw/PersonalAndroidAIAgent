@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")
// File hosts both the `mockPromptLibraryViewModel` factory function
// (primary export) and its sibling `PromptLibraryMockHandles` data class.

package app.knotwork.android.presentation.ui.prompts

import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.PromptPreset
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

/**
 * Builds a user-saved [PromptPreset] sample with the given identifiers.
 *
 * Defaults to `isBundled = false` so the editor / delete / duplicate
 * affordances render — bundled presets are read-only and the catalog hides
 * those controls. The [category] is the [NodeType] name backing the preset.
 */
internal fun samplePromptPreset(
    id: String,
    name: String,
    category: String = "DECOMPOSITION",
    body: String = "$name body",
    isBundled: Boolean = false,
): PromptPreset = PromptPreset(
    id = id,
    name = name,
    description = "$name description",
    nodeType = NodeType.valueOf(category),
    systemPrompt = body,
    isBundled = isBundled,
)
