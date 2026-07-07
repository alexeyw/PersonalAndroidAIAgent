package app.knotwork.android.presentation.ui.triggers

import app.knotwork.android.domain.models.PipelineGraph
import app.knotwork.android.domain.models.Trigger
import app.knotwork.android.domain.models.TriggerCondition
import app.knotwork.android.presentation.ui.common.UiText
import app.knotwork.android.presentation.ui.triggers.TriggerConditionFormatter.MIN_INTERVAL_MINUTES
import app.knotwork.design.screens.triggers.TRIGGER_INTERVAL_PRESETS
import app.knotwork.design.screens.triggers.TriggerConditionType

/** Minutes per hour — the divisor when interpreting a custom interval in hours. */
private const val MINUTES_PER_HOUR = 60L

/**
 * In-progress editor draft. Holds the editable trigger fields plus the
 * per-condition parameters; the ViewModel projects this to the catalog
 * `TriggerEditorUi` and, on save, into a [TriggerDraftIntent] for
 * `SaveTriggerUseCase` (which derives the runtime lifecycle fields).
 *
 * Note this draft carries only **user-edited** fields — the `armed`,
 * `lastFiredAt` and `createdAt` lifecycle values are owned by the domain save
 * use case, not by the editor.
 *
 * @property id `null` for a new draft, non-null when editing an existing trigger.
 * @property name current Name value.
 * @property type the selected condition type.
 * @property intervalCustom `true` when the Custom interval panel is shown.
 * @property intervalPresetMinutes the selected preset interval (when not custom).
 * @property intervalCustomAmount raw text in the Custom amount field (digits).
 * @property intervalCustomUnitHours `true` interprets the amount as hours.
 * @property dailyHour selected hour `0..23` for the Daily condition.
 * @property dailyMinute selected minute `0..59` for the Daily condition.
 * @property wifiOnly `true` restricts the Network condition to Wi-Fi.
 * @property ssids network names the Wi-Fi condition is scoped to; empty means
 *   "any network / any Wi-Fi" (the original behaviour).
 * @property pipelineId the bound pipeline id, or `null` (unbound, inert).
 * @property prompt the input message handed to the pipeline on fire.
 * @property enabled the on/off value edited inside the editor.
 */
data class TriggerEditorDraft(
    val id: String?,
    val name: String,
    val type: TriggerConditionType,
    val intervalCustom: Boolean,
    val intervalPresetMinutes: Long,
    val intervalCustomAmount: String,
    val intervalCustomUnitHours: Boolean,
    val dailyHour: Int,
    val dailyMinute: Int,
    val wifiOnly: Boolean,
    val ssids: List<String>,
    val pipelineId: String?,
    val prompt: String,
    val enabled: Boolean,
) {
    /** The effective interval in minutes (preset value, or the resolved custom amount). */
    val effectiveIntervalMinutes: Long
        get() = if (intervalCustom) {
            val amount = intervalCustomAmount.toLongOrNull() ?: 0L
            if (intervalCustomUnitHours) amount * MINUTES_PER_HOUR else amount
        } else {
            intervalPresetMinutes
        }

    /** `true` when the Interval condition is below the 15-minute floor. */
    val intervalError: Boolean
        get() = type == TriggerConditionType.Interval && effectiveIntervalMinutes < MIN_INTERVAL_MINUTES

    /** Save is enabled once the name is non-blank and the condition is valid. */
    val canSave: Boolean get() = name.isNotBlank() && !intervalError

    /** Resolves the draft's type + params into a domain [TriggerCondition]. */
    fun resolveCondition(): TriggerCondition = when (type) {
        TriggerConditionType.Interval -> TriggerCondition.IntervalSchedule(effectiveIntervalMinutes)
        TriggerConditionType.Daily -> TriggerCondition.DailySchedule(dailyHour, dailyMinute)
        TriggerConditionType.Charging -> TriggerCondition.Charging
        // Scoping to SSIDs implies Wi-Fi; force wifiOnly so the resolved condition
        // is internally consistent regardless of the toggle's last value.
        TriggerConditionType.Network -> TriggerCondition.NetworkConnected(
            wifiOnly = wifiOnly || ssids.isNotEmpty(),
            ssids = ssids,
        )
    }

    companion object {
        /**
         * The interval preset minute values, derived from the catalog chips
         * ([TRIGGER_INTERVAL_PRESETS]) so the preset-vs-custom decision can never
         * drift from what the editor renders.
         */
        private val INTERVAL_PRESETS = TRIGGER_INTERVAL_PRESETS.toSet()

        /** The minute count seeded into the Custom amount field when it opens. */
        const val CUSTOM_SEED: Long = 45L

        /** Builds a fresh, unbound draft with sensible defaults (daily 08:00). */
        fun newDraft(): TriggerEditorDraft = TriggerEditorDraft(
            id = null,
            name = "",
            type = TriggerConditionType.Daily,
            intervalCustom = false,
            intervalPresetMinutes = 30L,
            intervalCustomAmount = "",
            intervalCustomUnitHours = false,
            dailyHour = 8,
            dailyMinute = 0,
            wifiOnly = false,
            ssids = emptyList(),
            pipelineId = null,
            prompt = "",
            enabled = true,
        )

        /** Builds an editor draft from an existing [Trigger]. */
        fun fromTrigger(trigger: Trigger): TriggerEditorDraft {
            val base = newDraft().copy(
                id = trigger.id,
                name = trigger.name,
                pipelineId = trigger.pipelineId,
                prompt = trigger.prompt,
                enabled = trigger.enabled,
            )
            return when (val condition = trigger.condition) {
                is TriggerCondition.IntervalSchedule -> base.withInterval(condition.intervalMinutes)
                is TriggerCondition.DailySchedule -> base.copy(
                    type = TriggerConditionType.Daily,
                    dailyHour = condition.hour,
                    dailyMinute = condition.minute,
                )
                TriggerCondition.Charging -> base.copy(type = TriggerConditionType.Charging)
                is TriggerCondition.NetworkConnected -> base.copy(
                    type = TriggerConditionType.Network,
                    wifiOnly = condition.wifiOnly,
                    ssids = condition.ssids,
                )
            }
        }

        /**
         * Seeds the interval fields from [minutes]: a preset value selects the
         * matching chip; any other value opens the Custom panel, expressed in
         * whole hours when divisible by 60, otherwise minutes.
         */
        private fun TriggerEditorDraft.withInterval(minutes: Long): TriggerEditorDraft {
            if (minutes in INTERVAL_PRESETS) {
                return copy(
                    type = TriggerConditionType.Interval,
                    intervalCustom = false,
                    intervalPresetMinutes = minutes,
                )
            }
            val hours = minutes % MINUTES_PER_HOUR == 0L && minutes >= MINUTES_PER_HOUR
            return copy(
                type = TriggerConditionType.Interval,
                intervalCustom = true,
                intervalCustomUnitHours = hours,
                intervalCustomAmount = (if (hours) minutes / MINUTES_PER_HOUR else minutes).toString(),
            )
        }
    }
}

/**
 * Delete-confirmation target, surfaced when the user taps Delete on a trigger.
 *
 * @property id the trigger id to delete.
 * @property name the trigger name (shown in the dialog).
 */
data class TriggerDeleteTarget(val id: String, val name: String)

/**
 * Screen state for the Triggers surface. The trigger list, the available
 * pipelines (for the binding picker and the bound-name lookup), and the
 * editor / delete sub-states are modelled together; the editor and delete dialog
 * are nullable.
 *
 * @property isLoading initial fetch in progress.
 * @property triggers all triggers, newest first.
 * @property pipelines pipelines offered in the editor's binding picker.
 * @property openMenuTriggerId id of the row whose overflow menu is open.
 * @property editor non-null when the full-screen editor is open.
 * @property deleteTarget non-null when the delete dialog is open.
 * @property loadError a fatal load error (drives the Error screen when there is
 *   nothing to show); cleared on a successful load.
 * @property transientError a one-shot error from a mutation (save / toggle /
 *   delete) surfaced as a snackbar; cleared once shown.
 */
data class TriggersUiState(
    val isLoading: Boolean = true,
    val triggers: List<Trigger> = emptyList(),
    val pipelines: List<PipelineGraph> = emptyList(),
    val openMenuTriggerId: String? = null,
    val editor: TriggerEditorDraft? = null,
    val deleteTarget: TriggerDeleteTarget? = null,
    val loadError: UiText? = null,
    val transientError: UiText? = null,
) {
    /** Number of triggers eligible to fire (enabled and bound). */
    val activeCount: Int get() = triggers.count { it.isActive }
}
