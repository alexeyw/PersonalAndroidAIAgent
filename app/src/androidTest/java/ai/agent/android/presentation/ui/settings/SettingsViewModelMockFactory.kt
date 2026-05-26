@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")
// File hosts both the `mockSettingsViewModel` factory function (primary
// export) and its sibling `SettingsMockHandles` data class.

package ai.agent.android.presentation.ui.settings

import ai.agent.android.domain.models.Identity
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Mutable mirror of every [MutableStateFlow] backing [SettingsViewModel].
 * Tests mutate the flows directly to drive the screen through state
 * transitions, then call `composeTestRule.waitForIdle()` to recompose.
 */
internal class SettingsMockHandles(val uiStateFlow: MutableStateFlow<SettingsUiState>)

/**
 * Builds a relaxed [SettingsViewModel] mock with [SettingsUiState] stubbed
 * to a deterministic starting value, plus a sibling [SettingsMockHandles]
 * bundle that lets the test mutate the flow without re-stubbing.
 *
 * Defaults seed a non-null [Identity] so the screen short-circuits past
 * the Loading visual state and renders the actual surface.
 */
internal fun mockSettingsViewModel(
    initialUiState: SettingsUiState = SettingsUiState(
        identity = Identity(
            displayName = "Anonymous · this device",
            deviceId = "1234-5678",
            keystoreAvailable = true,
        ),
    ),
): Pair<SettingsViewModel, SettingsMockHandles> {
    val uiStateFlow = MutableStateFlow(initialUiState)
    val vm = mockk<SettingsViewModel>(relaxed = true)
    every { vm.uiState } returns uiStateFlow
    val handles = SettingsMockHandles(uiStateFlow = uiStateFlow)
    return vm to handles
}
