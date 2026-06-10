package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.engine.LlmInferenceEngine
import app.knotwork.android.domain.models.Result
import app.knotwork.android.domain.models.TestProbeResult
import app.knotwork.android.domain.repositories.LocalModelRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

/**
 * Runs a fixed prompt-probe against the currently active local model
 * backend and persists the resulting [TestProbeResult] so the Settings →
 * Local model "Test backend" row keeps showing the most recent
 * throughput numbers across navigation.
 *
 * Sequence:
 *  1. Resolve the active [app.knotwork.android.domain.models.LocalModel] via
 *     [LocalModelRepository.getActiveModel]. If `null`, persist a failure
 *     result and return it.
 *  2. Load the model via [LoadModelUseCase]; on error persist + return.
 *  3. Stream the fixed probe prompt through [LlmInferenceEngine],
 *     counting tokens and wall-clock duration.
 *  4. Persist + return the [TestProbeResult].
 *
 * The fixed prompt is intentionally short and self-contained so the
 * probe stays bounded in cost — the goal is a "the backend works"
 * smoke test, not a model-quality benchmark.
 */
class TestBackendUseCase @Inject constructor(
    private val localModelRepository: LocalModelRepository,
    private val loadModelUseCase: LoadModelUseCase,
    private val llmInferenceEngine: LlmInferenceEngine,
    private val settingsRepository: SettingsRepository,
) {

    /**
     * Runs the probe end-to-end and persists the result. Caller may
     * observe [SettingsRepository.lastTestProbeResult] for the live row
     * subtitle.
     */
    suspend operator fun invoke(): TestProbeResult {
        val started = System.currentTimeMillis()
        val activeModel = localModelRepository.getActiveModel()
        if (activeModel == null) {
            return persistAndReturn(
                TestProbeResult(
                    tokensGenerated = 0,
                    durationMs = 0L,
                    timestampMs = started,
                    success = false,
                    errorMessage = "No active model selected.",
                ),
            )
        }

        val loadResult = loadModelUseCase(activeModel.path)
        if (loadResult is Result.Error) {
            return persistAndReturn(
                TestProbeResult(
                    tokensGenerated = 0,
                    durationMs = System.currentTimeMillis() - started,
                    timestampMs = started,
                    success = false,
                    errorMessage = loadResult.message ?: "Failed to load model.",
                ),
            )
        }

        var tokenCount = 0
        return try {
            llmInferenceEngine.generateResponseStream(PROBE_PROMPT).collect { _ -> tokenCount += 1 }
            persistAndReturn(
                TestProbeResult(
                    tokensGenerated = tokenCount,
                    durationMs = System.currentTimeMillis() - started,
                    timestampMs = started,
                    success = true,
                    errorMessage = null,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Test backend probe failed")
            persistAndReturn(
                TestProbeResult(
                    tokensGenerated = tokenCount,
                    durationMs = System.currentTimeMillis() - started,
                    timestampMs = started,
                    success = false,
                    errorMessage = e.message ?: "Unknown error",
                ),
            )
        }
    }

    private suspend fun persistAndReturn(result: TestProbeResult): TestProbeResult {
        settingsRepository.setLastTestProbeResult(result)
        // Force a read so DataStore writes flush before the caller observes the row.
        try {
            settingsRepository.lastTestProbeResult.first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Best-effort flush — the probe result is already persisted.
        }
        return result
    }

    private companion object {
        const val PROBE_PROMPT =
            "Reply with the single word 'ok' to confirm you are reachable. " +
                "Do not generate anything else."
    }
}
