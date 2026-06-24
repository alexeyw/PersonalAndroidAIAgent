package app.knotwork.android.presentation.ui.triggers

import app.knotwork.android.domain.models.PipelineGraph
import app.knotwork.android.domain.models.Trigger
import app.knotwork.android.domain.models.TriggerCondition
import app.knotwork.android.presentation.ui.common.UiText
import app.knotwork.android.presentation.ui.triggers.TriggerConditionFormatter.MIN_INTERVAL_MINUTES
import app.knotwork.design.screens.triggers.TriggerConditionType

/** Minutes per hour — the divisor when interpreting a custom interval in hours. */
private const val MINUTES_PER_HOUR = 60L

/** Seed value for a fresh Custom interval reveal (a non-preset minute count). */
private const val CUSTOM_INTERVAL_SEED_MINUTES = 45L

/**
 * In-progress editor draft. Holds the editable trigger fields plus the
 * per-condition parameters; the ViewModel projects this to the catalog
 * `TriggerEditorUi` and, on save, back into a domain [Trigger].
 *
 * @property id `null` for a new draft, non-null when editing an existing trigger.
 * @property createdAt original creation timestamp, preserved across an edit
 *   (0 for a new draft — stamped on first save).
 * @property armed the edge latch carried through an edit (reset on a fresh draft).
 * @property lastFiredAt the last-fired timestamp carried through an edit.
 * @property name current Name value.
 * @property type the selected condition type.
 * @property intervalCustom `true` when the Custom interval panel is shown.
 * @property intervalPresetMinutes the selected preset interval (when not custom).
 * @property intervalCustomAmount raw text in the Custom amount field (digits).
 * @property intervalCustomUnitHours `true` interprets the amount as hours.
 * @property dailyHour selected hour `0..23` for the Daily condition.
 * @property dailyMinute selected minute `0..59` for the Daily condition.
 * @property wifiOnly `true` restricts the Network condition to Wi-Fi.
 * @property pipelineId the bound pipeline id, or `null` (unbound, inert).
 * @property prompt the input message handed to the pipeline on fire.
 * @property enabled the on/off value edited inside the editor.
 */
data class TriggerEditorDraft(
    val id: String?,
    val createdAt: Long,
    val armed: Boolean,
    val lastFiredAt: Long?,
    val name: String,
    val type: TriggerConditionType,
    val intervalCustom: Boolean,
    val intervalPresetMinutes: Long,
    val intervalCustomAmount: String,
    val intervalCustomUnitHours: Boolean,
    val dailyHour: Int,
    val dailyMinute: Int,
    val wifiOnly: Boolean,
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
        TriggerConditionType.Network -> TriggerCondition.NetworkConnected(wifiOnly)
    }

    companion object {
        /** The interval preset minute values mirrored from the catalog chips. */
        private val INTERVAL_PRESETS = setOf(15L, 30L, 60L, 360L, 1440L)

        /** Builds a fresh, unbound draft with sensible defaults (daily 08:00). */
        fun newDraft(): TriggerEditorDraft = TriggerEditorDraft(
            id = null,
            createdAt = 0L,
            armed = true,
            lastFiredAt = null,
            name = "",
            type = TriggerConditionType.Daily,
            intervalCustom = false,
            intervalPresetMinutes = 30L,
            intervalCustomAmount = "",
            intervalCustomUnitHours = false,
            dailyHour = 8,
            dailyMinute = 0,
            wifiOnly = false,
            pipelineId = null,
            prompt = "",
            enabled = true,
        )

        /** The minute count seeded into the Custom amount field when it opens. */
        const val CUSTOM_SEED: Long = CUSTOM_INTERVAL_SEED_MINUTES

        /** Builds an editor draft from an existing [Trigger]. */
        fun fromTrigger(trigger: Trigger): TriggerEditorDraft {
            val base = newDraft().copy(
                id = trigger.id,
                createdAt = trigger.createdAt,
                armed = trigger.armed,
                lastFiredAt = trigger.lastFiredAt,
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
 * @property errorMessage transient error banner / fatal load error.
 */
data class TriggersUiState(
    val isLoading: Boolean = true,
    val triggers: List<Trigger> = emptyList(),
    val pipelines: List<PipelineGraph> = emptyList(),
    val openMenuTriggerId: String? = null,
    val editor: TriggerEditorDraft? = null,
    val deleteTarget: TriggerDeleteTarget? = null,
    val errorMessage: UiText? = null,
) {
    /** Number of triggers eligible to fire (enabled and bound). */
    val activeCount: Int get() = triggers.count { it.isActive }
}
