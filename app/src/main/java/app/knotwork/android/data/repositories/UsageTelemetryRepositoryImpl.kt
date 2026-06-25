package app.knotwork.android.data.repositories

import androidx.annotation.VisibleForTesting
import app.knotwork.android.data.local.dao.ActiveDayStats
import app.knotwork.android.data.local.dao.UsageTelemetryCategories
import app.knotwork.android.data.local.dao.UsageTelemetryDao
import app.knotwork.android.data.local.models.UsageCounterEntity
import app.knotwork.android.domain.models.PipelineRunStatus
import app.knotwork.android.domain.models.PipelineRunTally
import app.knotwork.android.domain.models.UsageTelemetrySummary
import app.knotwork.android.domain.repositories.SettingsRepository
import app.knotwork.android.domain.repositories.UsageTelemetryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [UsageTelemetryRepository].
 *
 * Aggregates the two on-device counter tables (`usage_counter`,
 * `usage_active_day`) into a live [UsageTelemetrySummary] and records terminal
 * run outcomes / trigger firings behind the opt-in flag. **No method here ever
 * touches the network** — the whole class reads and writes only the local
 * (SQLCipher-encrypted) database, which is the privacy guarantee the feature is
 * built around (enforced structurally by `UsageTelemetryNoNetworkKonsistTest`).
 *
 * **Best-effort contract.** Recording absorbs storage failures (logged, no-op)
 * so it can never take down the run or trigger it observes; `CancellationException`
 * is always re-thrown. Reads degrade to [UsageTelemetrySummary.EMPTY] only via
 * the DAO's own empty results — a malformed counter row is skipped, not fatal.
 *
 * @property dao The telemetry DAO.
 * @property settingsRepository Source of the [SettingsRepository.usageTelemetryEnabled]
 *   opt-in flag that gates every write.
 * @property clockProvider Supplies the [Clock] (carrying the device zone) used to
 *   derive the device-local active day from an event's epoch-millis. A supplier
 *   (not a snapshot) so a runtime time-zone change is picked up live, mirroring
 *   [app.knotwork.android.data.prompt.DateVariableProvider].
 */
@Singleton
class UsageTelemetryRepositoryImpl internal constructor(
    private val dao: UsageTelemetryDao,
    private val settingsRepository: SettingsRepository,
    private val clockProvider: () -> Clock,
) : UsageTelemetryRepository {

    /**
     * Hilt-visible constructor wiring the production clock supplier (a fresh
     * system-default-zone [Clock] per call). Secondary because Hilt rejects a
     * default-valued parameter on an `@Inject` primary constructor.
     */
    @Inject
    constructor(dao: UsageTelemetryDao, settingsRepository: SettingsRepository) : this(
        dao = dao,
        settingsRepository = settingsRepository,
        clockProvider = { Clock.systemDefaultZone() },
    )

    /** Dispatcher carrying every DAO call. Swapped in unit tests. */
    @VisibleForTesting
    internal var dispatcher: CoroutineDispatcher = Dispatchers.IO

    override val summary: Flow<UsageTelemetrySummary>
        get() = combine(
            dao.observeCounters(),
            dao.observeActiveDayStats(),
        ) { counters, dayStats -> aggregate(counters, dayStats) }.flowOn(dispatcher)

    override suspend fun isEnabled(): Boolean = try {
        settingsRepository.usageTelemetryEnabled.first()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.tag(TAG).w(e, "Failed to read the usage-telemetry opt-in flag; treating as disabled.")
        false
    }

    override suspend fun recordPipelineRunOutcome(pipelineId: String?, status: PipelineRunStatus, atMillis: Long) {
        if (!status.isTerminal) return
        if (!isEnabled()) return
        val pipelineKey = pipelineId ?: UsageTelemetryCategories.NULL_PIPELINE_KEY
        absorbing("recordPipelineRunOutcome") {
            withContext(dispatcher) { dao.recordRun(pipelineKey, status.name, localDay(atMillis)) }
        }
    }

    override suspend fun recordTriggerFired(kind: String, atMillis: Long) {
        if (!isEnabled()) return
        absorbing("recordTriggerFired") {
            withContext(dispatcher) { dao.recordTriggerFire(kind, localDay(atMillis)) }
        }
    }

    override suspend fun reset() {
        absorbing("reset") {
            withContext(dispatcher) { dao.clearAll() }
        }
    }

    /** Folds the raw counter rows + active-day aggregate into the domain summary. */
    private fun aggregate(counters: List<UsageCounterEntity>, dayStats: ActiveDayStats): UsageTelemetrySummary {
        val runsByPipeline = counters
            .filter { it.category == UsageTelemetryCategories.PIPELINE_RUN }
            .sortedByDescending { it.count }
            .map { row ->
                PipelineRunTally(
                    pipelineId = row.counterKey.ifEmpty { null },
                    runCount = row.count,
                )
            }
        val runsByOutcome = counters
            .filter { it.category == UsageTelemetryCategories.RUN_OUTCOME }
            .mapNotNull { row -> terminalStatusOrNull(row.counterKey)?.let { it to row.count } }
            .toMap()
        val triggerFiresByKind = counters
            .filter { it.category == UsageTelemetryCategories.TRIGGER_FIRE }
            .associate { it.counterKey to it.count }
        return UsageTelemetrySummary(
            runsByPipeline = runsByPipeline,
            runsByOutcome = runsByOutcome,
            triggerFiresByKind = triggerFiresByKind,
            activeDays = dayStats.dayCount,
            firstActiveDay = dayStats.firstDay,
            lastActiveDay = dayStats.lastDay,
        )
    }

    /** Resolves a stored status name to a terminal [PipelineRunStatus], or `null` if unrecognised. */
    private fun terminalStatusOrNull(name: String): PipelineRunStatus? =
        PipelineRunStatus.entries.firstOrNull { it.name == name && it.isTerminal }

    /** Device-local calendar day of [atMillis] as an ISO `yyyy-MM-dd` string. */
    private fun localDay(atMillis: Long): String {
        val zone = clockProvider().zone
        return Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    /**
     * Runs [block], absorbing any non-cancellation failure (logged, no-op) so a
     * telemetry write can never disturb the run/trigger it observes.
     */
    private suspend inline fun absorbing(operation: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Usage-telemetry %s failed; ignored.", operation)
        }
    }

    private companion object {
        const val TAG = "UsageTelemetry"
    }
}
