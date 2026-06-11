package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.constants.DefaultPrompts
import app.knotwork.android.domain.engine.DefaultPipelineFactory
import app.knotwork.android.domain.models.PipelineRunStatus
import app.knotwork.android.domain.repositories.PipelineRepository
import app.knotwork.android.domain.repositories.PipelineRunRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Use case executed when the application is launched.
 * It checks if this is the first launch, and if so, initializes default settings,
 * such as saving the default system prompts to the settings repository,
 * and materialises the bundled showcase pipeline as the application default.
 *
 * On **every** launch (not just the first) it additionally sweeps orphaned
 * pipeline-run records: any run still in QUEUED or RUNNING status at process
 * start necessarily belonged to a process that died mid-execution — an
 * in-process run cannot predate the process — and is finalised as
 * [PipelineRunStatus.INTERRUPTED]. WAITING_* runs are deliberately left
 * untouched: a pending approval or clarification survives process death by
 * design, and its fate is decided by the background-HITL flow.
 */
class InitializeAppUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val pipelineRepository: PipelineRepository,
    private val loadPipelineFromPresetUseCase: LoadPipelineFromPresetUseCase,
    private val pipelineRunRepository: PipelineRunRepository,
) {
    /**
     * Executes the initialization logic.
     *
     * On the very first launch the seed pipeline is materialised from the
     * bundled [SHOWCASE_PRESET_ID] preset via [LoadPipelineFromPresetUseCase]
     * (fresh node / connection ids, validated, persisted). This gives the new
     * user a pipeline that exercises the full breadth of the agent — intent
     * routing, a local/cloud fork, task decomposition, a tool call, a
     * human-in-the-loop clarification, summarisation and evaluation — instead
     * of a minimal stub. If the preset cannot be loaded (e.g. a corrupt or
     * missing asset) the seed falls back to the code-level
     * [DefaultPipelineFactory] so first launch never leaves the library empty.
     */
    suspend operator fun invoke() {
        sweepOrphanedRuns()

        val isFirstLaunch = settingsRepository.isFirstLaunch.first()

        if (isFirstLaunch) {
            // Save default prompts to settings so they can be modified later by the user
            settingsRepository.setSystemPromptPrefix(DefaultPrompts.SYSTEM_PROMPT_PREFIX)
            settingsRepository.setToolUsageInstruction(DefaultPrompts.TOOL_USAGE_INSTRUCTION)

            // Materialise the bundled showcase pipeline as the seed; fall back
            // to the code-level factory if the preset asset is unavailable.
            val seededPipelineId = loadPipelineFromPresetUseCase(SHOWCASE_PRESET_ID)
                .getOrNull()
                ?: seedFallbackPipeline()

            // Mark the seeded pipeline as the application default so the
            // chat surfaces ("Use default pipeline (…)" label, TopAppBar
            // subtitle) show a concrete name from the very first launch
            // instead of relying on the implicit "first in library" fallback.
            settingsRepository.setDefaultPipelineId(seededPipelineId)

            // Mark first launch as complete
            settingsRepository.setFirstLaunch(false)
        }
    }

    /**
     * Finalises every run record orphaned by a previous process death:
     * QUEUED / RUNNING records are moved to [PipelineRunStatus.INTERRUPTED]
     * with [ORPHANED_RUN_MESSAGE] as the reason. The repository excludes
     * WAITING_* statuses from the orphan query, so suspended HITL runs are
     * never touched here.
     */
    private suspend fun sweepOrphanedRuns() {
        pipelineRunRepository.getOrphanedRunning().forEach { run ->
            pipelineRunRepository.finishRun(run.id, PipelineRunStatus.INTERRUPTED, ORPHANED_RUN_MESSAGE)
        }
    }

    /**
     * Builds and persists the code-level default pipeline, returning its id.
     * Used only when the bundled showcase preset fails to materialise.
     */
    private suspend fun seedFallbackPipeline(): String {
        val defaultPipeline = DefaultPipelineFactory.create("Default System Pipeline")
        pipelineRepository.savePipeline(defaultPipeline)
        return defaultPipeline.id
    }

    private companion object {
        /**
         * Stable id of the bundled showcase preset
         * (`assets/presets/pipelines/showcase_full_agent.json`) materialised as
         * the first-launch seed pipeline.
         */
        const val SHOWCASE_PRESET_ID = "showcase_full_agent"

        /**
         * Reason written into run records finalised by the orphan sweep —
         * their owning process died before the run could finish.
         */
        const val ORPHANED_RUN_MESSAGE = "Process terminated during execution"
    }
}
