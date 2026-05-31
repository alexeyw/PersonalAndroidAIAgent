@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")
// File hosts both the `mockChatHomeViewModel` factory function (primary
// export) and its sibling `ChatHomeMockHandles` data class. Naming after the
// factory is preferred since tests reach for it by name.

package ai.agent.android.presentation.ui.chat.home

import ai.agent.android.domain.models.ClarificationRequest
import ai.agent.android.domain.models.LocalModel
import app.knotwork.design.components.console.ConsoleFilter
import app.knotwork.design.components.console.ConsoleLine
import app.knotwork.design.components.console.ConsoleSnap
import app.knotwork.design.components.console.ConsoleTab
import app.knotwork.design.components.console.ConsoleTraceSpan
import app.knotwork.design.components.console.ConsoleVarRow
import app.knotwork.design.screens.chat.ChatHomeThreadRow
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Mutable mirror of every [MutableStateFlow] / [MutableSharedFlow] that
 * backs [ChatHomeViewModel]. Tests mutate the flows directly to drive the
 * screen through state transitions, then call
 * `composeTestRule.waitForIdle()` to recompose.
 *
 * Exposing the mutable handles (rather than just the readable mock) keeps
 * the tests free of MockK re-stubbing between phases of a single scenario
 * (e.g. send → Generating → Idle).
 */
internal class ChatHomeMockHandles(
    val stateFlow: MutableStateFlow<ChatHomeUiState>,
    val composerValueFlow: MutableStateFlow<String>,
    val pendingTypedConfirmFlow: MutableStateFlow<String>,
    val pendingToolFlow: MutableStateFlow<HitlPending?>,
    val pendingClarificationFlow: MutableStateFlow<ClarificationRequest?>,
    val consoleLinesFlow: MutableStateFlow<List<ConsoleLine>>,
    val consoleVarsFlow: MutableStateFlow<List<ConsoleVarRow>>,
    val consoleTracesFlow: MutableStateFlow<List<ConsoleTraceSpan>>,
    val consoleTabFlow: MutableStateFlow<ConsoleTab>,
    val consoleSnapFlow: MutableStateFlow<ConsoleSnap?>,
    val consoleSearchQueryFlow: MutableStateFlow<String?>,
    val consoleFilterFlow: MutableStateFlow<ConsoleFilter>,
    val consoleClearConfirmFlow: MutableStateFlow<Boolean>,
    val threadRowsFlow: MutableStateFlow<List<ChatHomeThreadRow>>,
    val availablePipelinesFlow: MutableStateFlow<List<PipelineSummary>>,
    val installedModelsFlow: MutableStateFlow<List<LocalModel>>,
    val pipelineNameFlow: MutableStateFlow<String?>,
)

/**
 * Builds a relaxed [ChatHomeViewModel] mock with every observed flow
 * stubbed to a deterministic starting value, plus a sibling
 * [ChatHomeMockHandles] bundle that lets the test mutate any flow without
 * re-stubbing.
 *
 * Defaults match what [ChatHomeOverflowMenuTest] used before this helper
 * was extracted, so existing assertions continue to pass.
 *
 * Tests pass per-test overrides (`initialState = ChatHomeUiState.Generating`,
 * `initialThreadRows = listOf(...)`) for the values they actually care
 * about; everything else stays on the boring defaults.
 */
@Suppress("LongParameterList", "LongMethod")
internal fun mockChatHomeViewModel(
    initialState: ChatHomeUiState = ChatHomeUiState.Idle,
    initialThreadTitle: String = "Test chat",
    initialModelName: String = "Test model",
    initialComposerValue: String = "",
    initialPendingTypedConfirm: String = "",
    initialPendingTool: HitlPending? = null,
    initialPendingClarification: ClarificationRequest? = null,
    initialConsoleLines: List<ConsoleLine> = emptyList(),
    initialConsoleVars: List<ConsoleVarRow> = emptyList(),
    initialConsoleTraces: List<ConsoleTraceSpan> = emptyList(),
    initialConsoleTab: ConsoleTab = ConsoleTab.Logs,
    initialConsoleSnap: ConsoleSnap? = null,
    initialConsoleSearchQuery: String? = null,
    initialConsoleFilter: ConsoleFilter = ConsoleFilter.allOn,
    initialConsoleClearConfirm: Boolean = false,
    initialThreadRows: List<ChatHomeThreadRow> = listOf(
        ChatHomeThreadRow(
            id = "active-id",
            title = "Test chat",
            subtitle = "Now",
            selected = true,
            active = true,
        ),
    ),
    initialAvailablePipelines: List<PipelineSummary> = emptyList(),
    initialInstalledModels: List<LocalModel> = emptyList(),
    initialPipelineName: String? = "default",
): Pair<ChatHomeViewModel, ChatHomeMockHandles> {
    val stateFlow = MutableStateFlow(initialState)
    val composerValueFlow = MutableStateFlow(initialComposerValue)
    val pendingTypedConfirmFlow = MutableStateFlow(initialPendingTypedConfirm)
    val pendingToolFlow: MutableStateFlow<HitlPending?> = MutableStateFlow(initialPendingTool)
    val pendingClarificationFlow: MutableStateFlow<ClarificationRequest?> =
        MutableStateFlow(initialPendingClarification)
    val consoleLinesFlow = MutableStateFlow(initialConsoleLines)
    val consoleVarsFlow = MutableStateFlow(initialConsoleVars)
    val consoleTracesFlow = MutableStateFlow(initialConsoleTraces)
    val consoleTabFlow = MutableStateFlow(initialConsoleTab)
    val consoleSnapFlow: MutableStateFlow<ConsoleSnap?> = MutableStateFlow(initialConsoleSnap)
    val consoleSearchQueryFlow: MutableStateFlow<String?> = MutableStateFlow(initialConsoleSearchQuery)
    val consoleFilterFlow = MutableStateFlow(initialConsoleFilter)
    val consoleClearConfirmFlow = MutableStateFlow(initialConsoleClearConfirm)
    val threadRowsFlow = MutableStateFlow(initialThreadRows)
    val availablePipelinesFlow = MutableStateFlow(initialAvailablePipelines)
    val installedModelsFlow = MutableStateFlow(initialInstalledModels)
    val pipelineNameFlow: MutableStateFlow<String?> = MutableStateFlow(initialPipelineName)

    val vm = mockk<ChatHomeViewModel>(relaxed = true)
    every { vm.state } returns stateFlow
    every { vm.threadTitle } returns MutableStateFlow(initialThreadTitle)
    every { vm.modelName } returns MutableStateFlow(initialModelName)
    every { vm.composerValue } returns composerValueFlow
    every { vm.pendingTypedConfirm } returns pendingTypedConfirmFlow
    every { vm.messages } returns MutableStateFlow(emptyList())
    every { vm.consoleSearchQuery } returns consoleSearchQueryFlow
    every { vm.consoleFilter } returns consoleFilterFlow
    every { vm.pipelineName } returns pipelineNameFlow
    every { vm.tokensUsed } returns MutableStateFlow(0)
    every { vm.tokensMax } returns MutableStateFlow(0)
    every { vm.streamingTokens } returns MutableStateFlow(0)
    every { vm.pendingTool } returns pendingToolFlow
    every { vm.pendingClarification } returns pendingClarificationFlow
    every { vm.consoleLines } returns consoleLinesFlow
    every { vm.consoleVars } returns consoleVarsFlow
    every { vm.consoleTraces } returns consoleTracesFlow
    every { vm.consoleTab } returns consoleTabFlow
    every { vm.consoleSnap } returns consoleSnapFlow
    every { vm.consoleClearConfirmRequested } returns consoleClearConfirmFlow
    every { vm.favorite } returns MutableStateFlow(false)
    every { vm.threadRows } returns threadRowsFlow
    every { vm.installedModels } returns installedModelsFlow
    every { vm.activeModelId } returns MutableStateFlow(null)
    every { vm.availablePipelinesFlow } returns availablePipelinesFlow
    every { vm.pipelineFallbackEvents } returns MutableSharedFlow()
    every { vm.consoleSnackbarEvents } returns MutableSharedFlow()
    every { vm.exportEvents } returns MutableSharedFlow()
    every { vm.importErrorEvents } returns MutableSharedFlow()
    every { vm.memorySaveEvents } returns MutableSharedFlow()
    every { vm.currentSessionId } returns MutableStateFlow("active-id")
    every { vm.currentPipelineId() } returns null

    val handles = ChatHomeMockHandles(
        stateFlow = stateFlow,
        composerValueFlow = composerValueFlow,
        pendingTypedConfirmFlow = pendingTypedConfirmFlow,
        pendingToolFlow = pendingToolFlow,
        pendingClarificationFlow = pendingClarificationFlow,
        consoleLinesFlow = consoleLinesFlow,
        consoleVarsFlow = consoleVarsFlow,
        consoleTracesFlow = consoleTracesFlow,
        consoleTabFlow = consoleTabFlow,
        consoleSnapFlow = consoleSnapFlow,
        consoleSearchQueryFlow = consoleSearchQueryFlow,
        consoleFilterFlow = consoleFilterFlow,
        consoleClearConfirmFlow = consoleClearConfirmFlow,
        threadRowsFlow = threadRowsFlow,
        availablePipelinesFlow = availablePipelinesFlow,
        installedModelsFlow = installedModelsFlow,
        pipelineNameFlow = pipelineNameFlow,
    )
    return vm to handles
}
