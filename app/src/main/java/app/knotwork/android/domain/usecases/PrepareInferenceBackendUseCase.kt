package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.engine.HardwareAccelerationProbe
import app.knotwork.android.domain.engine.LlmInferenceEngine
import app.knotwork.android.domain.models.LocalBackend
import app.knotwork.android.domain.models.Result
import app.knotwork.android.domain.repositories.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Picks the on-device execution backend for a first-time install and warms the
 * inference handle on it.
 *
 * Why this exists: the persisted default is [LocalBackend.CPU], so without this
 * step the very first run after onboarding's model download — the moment the
 * product is judged on — takes the slowest available path. On the reference
 * device the GPU backend is roughly five times faster on decode, which is the
 * difference between a snappy first answer and a wait.
 *
 * Flipping the default outright is not an option: LiteRT-LM exposes no
 * availability API, `Backend.GPU()` constructs happily on devices with no
 * OpenCL implementation, and the eventual native failure can abort the process
 * before Kotlin ever sees it. So the decision is made in three guarded steps:
 *
 *  1. **Never override the user.** A stored preference — even one equal to the
 *     default — means the choice has been made; this use case only warms up.
 *  2. **Static probe first** ([HardwareAccelerationProbe]). No OpenCL, no GPU
 *     attempt: the decision is recorded as CPU so the probe never runs again.
 *  3. **Verify by actually generating.** A device can ship OpenCL and still
 *     fail to run the model, and an init that merely *succeeds* proves nothing
 *     (the documented failure mode surfaces on the first generation). The probe
 *     therefore reuses [TestBackendUseCase] — a real load plus a short fixed
 *     prompt — and any failure silently reverts to CPU.
 *
 * The engine is [LlmInferenceEngine.unload]ed before the CPU retry because the
 * engine reuses a loaded handle keyed by model path and modalities alone: a
 * reload after a *successful* GPU init but failed generation would otherwise be
 * a no-op and leave the run on the very backend that just failed.
 *
 * The residual risk — a GPU init that aborts the process natively — is covered
 * by the pre-existing `lastInitBackendAttempt` breadcrumb, which forces CPU on
 * the next start. That machinery is deliberately left in the engine's hands
 * rather than duplicated here.
 *
 * @property settingsRepository Reads the raw backend preference and records the
 *   resolved decision.
 * @property accelerationProbe Static, crash-safe GPU plausibility check.
 * @property loadModelUseCase Plain warm-up path (no measurement).
 * @property testBackendUseCase Load + short generation; doubles as the warm-up
 *   on the GPU path and persists its numbers to the Settings probe row.
 * @property llmInferenceEngine Torn down before a fallback reload so the new
 *   backend is actually applied.
 */
class PrepareInferenceBackendUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val accelerationProbe: HardwareAccelerationProbe,
    private val loadModelUseCase: LoadModelUseCase,
    private val testBackendUseCase: TestBackendUseCase,
    private val llmInferenceEngine: LlmInferenceEngine,
) {

    /**
     * Terminal outcome of the preparation, as far as the caller's UI cares.
     */
    sealed interface Outcome {

        /**
         * The model is loaded and ready to answer.
         *
         * @property backend The backend the handle ended up running on.
         */
        data class Warmed(val backend: LocalBackend) : Outcome

        /**
         * Warm-up failed; the caller surfaces [message] and offers a retry.
         *
         * @property message Human-readable failure text, or `null` when the
         *   underlying error carried none.
         */
        data class Failed(val message: String?) : Outcome
    }

    /**
     * Resolves the backend (when it has never been chosen) and warms the model.
     *
     * @param modelPath Absolute path of the model to warm up.
     * @param onAccelerationCheckStarted Invoked — off the main thread — just
     *   before the GPU verification generation begins, so the caller can show
     *   an honest "checking acceleration" state for the extra second it costs.
     *   Never invoked when no verification happens.
     * @return [Outcome.Warmed] with the backend actually in use, or
     *   [Outcome.Failed] when even the CPU path could not load the model.
     */
    suspend operator fun invoke(modelPath: String, onAccelerationCheckStarted: () -> Unit = {}): Outcome =
        withContext(Dispatchers.IO) {
            val stored = settingsRepository.localModelBackendPreference.first()
            if (stored != null) {
                return@withContext warmUp(modelPath, LocalBackend.fromKey(stored) ?: LocalBackend.CPU)
            }

            if (!accelerationProbe.isGpuAvailable()) {
                settingsRepository.setLocalModelBackend(LocalBackend.CPU.key)
                return@withContext warmUp(modelPath, LocalBackend.CPU)
            }

            onAccelerationCheckStarted()
            settingsRepository.setLocalModelBackend(LocalBackend.GPU.key)
            val probe = testBackendUseCase(modelPath)
            if (probe.success) {
                Timber.i(
                    "GPU backend verified in %d ms (%d tokens) — keeping it as the default.",
                    probe.durationMs,
                    probe.tokensGenerated,
                )
                return@withContext Outcome.Warmed(LocalBackend.GPU)
            }

            Timber.w("GPU backend verification failed (%s) — falling back to CPU.", probe.errorMessage)
            settingsRepository.setLocalModelBackend(LocalBackend.CPU.key)
            // The engine may hold a live GPU handle for this exact model; its reuse
            // check ignores the backend, so only a teardown forces the CPU rebuild.
            llmInferenceEngine.unload()
            warmUp(modelPath, LocalBackend.CPU)
        }

    private suspend fun warmUp(modelPath: String, backend: LocalBackend): Outcome =
        when (val result = loadModelUseCase(modelPath)) {
            is Result.Success -> Outcome.Warmed(backend)
            is Result.Error -> Outcome.Failed(result.message)
        }
}
