package app.knotwork.android.presentation.ui.orchestrator

import app.knotwork.android.domain.models.AgentTool
import app.knotwork.android.domain.models.CloudProvider
import app.knotwork.android.domain.models.ConnectionModel
import app.knotwork.android.domain.models.LocalModel
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.PipelineBundleImportOutcome
import app.knotwork.android.domain.models.PipelineGraph
import app.knotwork.android.domain.models.PipelineImportOutcome
import app.knotwork.android.domain.models.PipelineValidationError
import app.knotwork.android.domain.models.PromptTemplate
import app.knotwork.android.domain.prompt.PromptSegment
import app.knotwork.android.presentation.ui.common.UiText

/**
 * Represents the UI state for the Visual Orchestrator screen.
 *
 * @property currentPipeline The pipeline graph currently being edited.
 * @property savedPipelines List of all saved pipelines available to load.
 * @property isLoading Whether a loading operation is currently in progress.
 * @property errorMessage An error message if an operation fails.
 * @property availableTools List of all available tools in the system.
 * @property providerKeys Map indicating whether an API key is set for specific provider node types.
 * @property promptTemplates List of saved prompt templates.
 * @property availableVariables Tokens (`$KEY`) of every prompt variable currently
 * registered in the DI graph. Drives the chip row in the prompt editor.
 * @property previewState Current state of the prompt-preview bottom sheet.
 * @property pendingImport A schema-mismatch outcome awaiting user
 * confirmation before being persisted. `null` when no import is pending.
 * @property pendingCollision A cleanly-parsed single-import graph whose id
 * already names a saved pipeline, awaiting the user's collision choice
 * (replace / import as copy). `null` when no single-import collision is pending.
 * @property pendingBundleImport A prepared bundle awaiting the user's collision
 * / schema-mismatch decision before the closure is written atomically. `null`
 * when no bundle import is pending.
 * @property pendingBundleExport A produced bundle document awaiting the user to
 * pick a destination file. Observed by the library screen to launch the
 * create-document picker. `null` when no export is in flight.
 * @property feedbackMessage One-shot success-flavoured message for the
 * library Snackbar (e.g. "Pipeline duplicated"). Distinct from
 * [errorMessage] so the UI can style green/blue toast vs. red error.
 * Cleared via `clearFeedback()` after display.
 * @property pendingEditorNavigation One-shot flag the library screen
 * observes to navigate to the editor. Set by the ViewModel only when an
 * action that should open the editor (e.g. `createNewPipeline`) actually
 * succeeds — so a failed create stays on the library screen instead of
 * dragging the user into the editor with the previously active pipeline.
 * Cleared via `consumePendingEditorNavigation()` once acted upon.
 * @property defaultPipelineId Id of the pipeline the user has marked as
 * default in the library, observed from `SettingsRepository.defaultPipelineId`.
 * `null` means no explicit choice — unbound chats then refuse to run
 * until a default is marked or the chat is bound explicitly. Drives the
 * "Default" badge and the menu item state in `PipelineLibraryScreen`.
 * @property shareTargetPipelineId Id of the pipeline bound to the OS Share
 * target, observed from `SettingsRepository.shareTargetPipelineId`. `null` =
 * the surface is inert. Drives the outlined "SHARE" pill on the matching row.
 * @property quickSettingsTilePipelineId Id of the pipeline bound to the Quick
 * Settings tile, observed from `SettingsRepository.quickSettingsTilePipelineId`.
 * `null` = the surface is inert. Drives the outlined "TILE" pill on the row.
 */
data class OrchestratorUiState(
    val currentPipeline: PipelineGraph = PipelineGraph(
        id = java.util.UUID.randomUUID().toString(),
        name = DEFAULT_PIPELINE_NAME,
    ),
    val savedPipelines: List<PipelineGraph> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: UiText? = null,
    val availableTools: List<AgentTool> = emptyList(),
    val availableLocalModels: List<LocalModel> = emptyList(),
    val providerKeys: Map<CloudProvider, Boolean> = emptyMap(),
    val promptTemplates: List<PromptTemplate> = emptyList(),
    val availableVariables: List<String> = emptyList(),
    val previewState: PromptPreviewState = PromptPreviewState.Hidden,
    val pendingImport: PipelineImportOutcome.SchemaMismatch? = null,
    val pendingCollision: PipelineGraph? = null,
    val pendingBundleImport: PendingBundleImport? = null,
    val pendingBundleExport: PendingBundleExport? = null,
    val feedbackMessage: UiText? = null,
    val pendingEditorNavigation: Boolean = false,
    val defaultPipelineId: String? = null,
    val shareTargetPipelineId: String? = null,
    val quickSettingsTilePipelineId: String? = null,
) {
    /**
     * Helper to get nodes easily.
     */
    val nodes: List<NodeModel> get() = currentPipeline.nodes

    /**
     * Helper to get connections easily.
     */
    val connections: List<ConnectionModel> get() = currentPipeline.connections

    /**
     * Dynamically computed list of validation errors for the current pipeline.
     */
    val validationErrors: List<PipelineValidationError> get() = currentPipeline.validate()

    /**
     * The id of the pipeline currently loaded into the editor — i.e. the "active"
     * pipeline for highlight and delete-block purposes in the library screen.
     *
     * Returns `null` when [currentPipeline] is the unsaved scratch graph (no nodes
     * and not present in [savedPipelines]); under that condition there is nothing
     * to highlight in the library and no delete to block.
     */
    val activePipelineId: String?
        get() = currentPipeline.id.takeIf { id ->
            savedPipelines.any { it.id == id } || currentPipeline.nodes.isNotEmpty()
        }

    companion object {
        /**
         * Display name applied to a freshly-instantiated scratch pipeline
         * (the in-memory placeholder shown before the user creates or loads
         * anything). Kept here so the editor and any tests creating a
         * scratch state agree on the same baseline label.
         */
        const val DEFAULT_PIPELINE_NAME = "New Pipeline"
    }
}

/**
 * A prepared pipeline bundle held in UI state between the parse/validate step
 * and the atomic write, so the library screen can prompt the user for a
 * collision / schema-mismatch decision first.
 *
 * @property pipelines The closure of pipelines to persist, in file order.
 * @property collidingIds The subset of [pipelines] ids that already exist in
 * the library. Empty means no collision dialog is required.
 * @property schemaMismatches Per-pipeline schema-version divergences to warn
 * about, if any.
 */
data class PendingBundleImport(
    val pipelines: List<PipelineGraph>,
    val collidingIds: List<String>,
    val schemaMismatches: List<PipelineBundleImportOutcome.SchemaMismatch>,
)

/**
 * A produced bundle document awaiting a destination file, driving the
 * create-document picker in the library screen.
 *
 * @property fileName Suggested file name (e.g. `knotwork-bundle-2026-07-07.json`).
 * @property content The full bundle JSON to write once the user picks a target.
 */
data class PendingBundleExport(val fileName: String, val content: String)

/**
 * State of the prompt-preview bottom sheet shared by every editor that supports the
 * `$VARIABLE` chip row.
 *
 * The state is hoisted to the ViewModel so the segments survive configuration changes
 * (rotation) and so prompt resolution — which may suspend on I/O — is performed off the
 * main thread.
 */
sealed interface PromptPreviewState {

    /** Sheet is closed, no preview is being computed. */
    data object Hidden : PromptPreviewState

    /** A preview was requested and the engine is currently rendering segments. */
    data object Loading : PromptPreviewState

    /**
     * Segments have been produced and the sheet should be shown. [segments] is the
     * ordered output of `PromptTemplateEngine.renderSegments`.
     */
    data class Ready(val segments: List<PromptSegment>) : PromptPreviewState
}

/**
 * Live pipeline-run state surfaced by the editor's run-trace bar and node-running pulse.
 *
 * Placeholder while the orchestrator-runtime wiring is finalised: the editor
 * exercises both fields through a debug toggle so the bar can be reviewed end-to-end
 * before the agent loop wires them up post-v0.1.
 *
 * @property isRunning whether a pipeline run is currently in progress.
 * @property activeNodeId id of the node the run is currently executing, or `null` when
 * the run has not yet emitted a step (or after the run completes).
 */
data class PipelineRunState(val isRunning: Boolean = false, val activeNodeId: String? = null)
