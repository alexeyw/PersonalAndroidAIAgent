package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.constants.DefaultPrompts
import app.knotwork.android.domain.engine.LlmInferenceEngine
import app.knotwork.android.domain.engine.StreamInferenceMeter
import app.knotwork.android.domain.engine.TaskQueueManager
import app.knotwork.android.domain.models.ModelPerformanceSample
import app.knotwork.android.domain.models.Result
import app.knotwork.android.domain.models.isBusy
import app.knotwork.android.domain.repositories.LocalModelRepository
import app.knotwork.android.domain.repositories.ModelPerformanceRepository
import app.knotwork.android.domain.services.NativeMemorySampler
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject

/**
 * Runs a controlled, one-shot performance benchmark of the active local model.
 *
 * The benchmark exists to give the user a comparable speed/memory figure on
 * demand, independent of whatever prompt their pipelines happen to send. It runs
 * a fixed prompt ([DefaultPrompts.BENCHMARK_PROMPT]) twice: a **warm-up** run
 * whose timings are discarded (so paging the weights in and JIT/first-token
 * costs don't skew the number), then a **measured** run that is timed and
 * sampled exactly like an ordinary pipeline generation. The measured sample is
 * persisted (with `isBenchmark = true`) so it both feeds the rolling average and
 * is returned for the one-shot report.
 *
 * **Engine contract.** The benchmark is foreground-only and refuses to run while
 * a pipeline holds the engine ([TaskQueueManager.globalState] is busy): the
 * engine serves one conversation at a time, so starting now would tear down the
 * in-flight run. It does not acquire the `AgentForegroundService` wake lock —
 * it is a user-initiated foreground action, not background work. Cancellation
 * (the user tapping "Cancel") propagates as coroutine cancellation; no sample is
 * recorded for an interrupted run.
 *
 * @property taskQueueManager Source of the agent-busy signal.
 * @property localModelRepository Source of the active model (name + path).
 * @property loadModelUseCase Ensures the active model is loaded before timing.
 * @property llmInferenceEngine The engine the benchmark drives directly.
 * @property modelPerformanceRepository Sink for the measured sample.
 * @property nativeMemorySampler Source of native-heap readings for the peak.
 */
class RunBenchmarkUseCase @Inject constructor(
    private val taskQueueManager: TaskQueueManager,
    private val localModelRepository: LocalModelRepository,
    private val loadModelUseCase: LoadModelUseCase,
    private val llmInferenceEngine: LlmInferenceEngine,
    private val modelPerformanceRepository: ModelPerformanceRepository,
    private val nativeMemorySampler: NativeMemorySampler,
) {
    /**
     * Executes the benchmark against the active model.
     *
     * @param onPhase Invoked as the run moves through its phases so the UI can
     *   show "Warming up…" then "Measuring…". Called before each phase begins.
     * @return The [BenchmarkOutcome]: [BenchmarkOutcome.Success] with the report,
     *   or one of the refusal reasons. Throws [CancellationException] if the
     *   caller cancels mid-run.
     */
    suspend operator fun invoke(onPhase: suspend (BenchmarkRunPhase) -> Unit = {}): BenchmarkOutcome {
        if (taskQueueManager.globalState.value.isBusy) {
            return BenchmarkOutcome.EngineBusy
        }
        val activeModel = localModelRepository.getActiveModel() ?: return BenchmarkOutcome.NoActiveModel

        return try {
            when (val load = loadModelUseCase()) {
                is Result.Error -> BenchmarkOutcome.Failed(
                    load.message ?: "Failed to load the model for the benchmark",
                )
                is Result.Success -> {
                    // The concrete path the engine actually loaded — the sample
                    // key, robust to the "Active model" sentinel.
                    val modelPath = llmInferenceEngine.currentModelPath ?: activeModel.path

                    // Warm-up: page weights in and absorb first-token cost; timings discarded.
                    onPhase(BenchmarkRunPhase.WARMING_UP)
                    llmInferenceEngine.generateResponseStream(DefaultPrompts.BENCHMARK_PROMPT).collect { }

                    // Re-check the engine-busy gate after the (multi-second)
                    // warm-up: a background/scheduled run may have started in the
                    // meantime. Measuring now would block on the engine's
                    // generation mutex and inflate the timings, so refuse rather
                    // than report a corrupted figure.
                    if (taskQueueManager.globalState.value.isBusy) {
                        BenchmarkOutcome.EngineBusy
                    } else {
                        onPhase(BenchmarkRunPhase.MEASURING)
                        val sample = measureRun(modelPath)
                        modelPerformanceRepository.record(sample)
                        BenchmarkOutcome.Success(
                            BenchmarkReport(
                                modelName = activeModel.name,
                                ttftMs = sample.ttftMs,
                                decodeTokensPerSec = sample.decodeTokensPerSec,
                                totalMs = sample.totalMs,
                                peakNativeHeapBytes = sample.peakNativeHeapBytes,
                            ),
                        )
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Model benchmark failed")
            BenchmarkOutcome.Failed(e.localizedMessage ?: "Benchmark failed")
        }
    }

    /**
     * Runs the measured generation, timing TTFT/decode and sampling peak native
     * heap exactly as the LITE_RT executor does for a real run.
     *
     * @param modelPath Concrete path of the loaded model (sample key).
     * @return The computed [ModelPerformanceSample] (`isBenchmark = true`).
     */
    private suspend fun measureRun(modelPath: String): ModelPerformanceSample {
        val meter = StreamInferenceMeter(nativeMemorySampler)
        llmInferenceEngine.generateResponseStream(DefaultPrompts.BENCHMARK_PROMPT).collect {
            meter.onToken(System.currentTimeMillis())
        }
        val endMs = System.currentTimeMillis()
        return meter.toSample(modelPath, endMs = endMs, isBenchmark = true, createdAt = endMs)
    }
}

/**
 * The two timed phases of a benchmark run, reported to the caller for progress
 * display.
 */
enum class BenchmarkRunPhase {
    /** Warm-up generation whose timings are discarded. */
    WARMING_UP,

    /** Measured generation that is timed and sampled. */
    MEASURING,
}

/**
 * Outcome of a [RunBenchmarkUseCase] invocation.
 */
sealed interface BenchmarkOutcome {
    /**
     * The benchmark completed.
     *
     * @property report The measured figures for the one-shot report.
     */
    data class Success(val report: BenchmarkReport) : BenchmarkOutcome

    /** A pipeline run is active; the benchmark is refused to avoid seizing the engine. */
    data object EngineBusy : BenchmarkOutcome

    /** No model is active, so there is nothing to benchmark. */
    data object NoActiveModel : BenchmarkOutcome

    /**
     * The benchmark failed (model load or inference error).
     *
     * @property message Human-readable failure reason for the UI.
     */
    data class Failed(val message: String) : BenchmarkOutcome
}

/**
 * The measured result of one benchmark run, surfaced as the inline one-shot
 * report on the Performance card and as the shareable text summary.
 *
 * @property modelName Display name of the benchmarked model.
 * @property ttftMs Time-to-first-token of the measured run, in milliseconds.
 * @property decodeTokensPerSec Decode throughput of the measured run.
 * @property totalMs Total inference wall-clock of the measured run, in
 *   milliseconds (model-load excluded).
 * @property peakNativeHeapBytes Peak process-wide native-heap allocation during
 *   the measured run, in bytes (approximate).
 */
data class BenchmarkReport(
    val modelName: String,
    val ttftMs: Long,
    val decodeTokensPerSec: Float,
    val totalMs: Long,
    val peakNativeHeapBytes: Long,
)
