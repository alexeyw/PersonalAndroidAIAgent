package app.knotwork.android.domain.models

/**
 * A user-defined automation rule: when its [condition] is met, the bound
 * [pipelineId] is run in the background with [prompt] as the input message.
 *
 * Triggers are the product-level payoff of the persisted background-run
 * infrastructure: a [condition] (a schedule, a charging event, a network
 * connection) fires a normal background pipeline run through the existing
 * scheduler → worker path, attributed to [RunOrigin.TRIGGER].
 *
 * **Inert until bound.** A trigger with a `null` [pipelineId] never fires — it
 * is created/edited freely but does nothing until the user binds a pipeline,
 * mirroring the privacy-default contract of the OS entry surfaces. The
 * [enabled] flag is the user's on/off switch independent of binding.
 *
 * @property id Stable unique identifier (UUID).
 * @property name Human-readable label shown in the trigger list.
 * @property condition The activation condition (the "when").
 * @property pipelineId Id of the pipeline run when the trigger fires, or `null`
 *   when the trigger is not yet bound (inert).
 * @property prompt The input message fed to the bound pipeline as the user
 *   prompt on every fire.
 * @property enabled Whether the trigger is active. A disabled trigger is not
 *   registered with the background runtime and never fires.
 * @property armed Edge-detection latch for [event][TriggerCondition.isEventTriggered]
 *   conditions (charging / network): `true` means the trigger is ready to fire
 *   the next time its condition becomes satisfied. It is cleared on fire and set
 *   again once the condition drops, so a sustained state (e.g. an overnight
 *   charge) fires exactly once, not once per poll. Unused by time-scheduled
 *   conditions (which fire on the clock).
 * @property createdAt Epoch-millis the trigger was created.
 * @property lastFiredAt Epoch-millis of the most recent fire, or `null` if it
 *   has never fired. Used for the interval-schedule debounce and for display.
 */
data class Trigger(
    val id: String,
    val name: String,
    val condition: TriggerCondition,
    val pipelineId: String?,
    val prompt: String,
    val enabled: Boolean,
    val armed: Boolean = true,
    val createdAt: Long,
    val lastFiredAt: Long? = null,
) {
    /**
     * Whether the trigger is eligible to be registered with the background
     * runtime: it must be [enabled] **and** bound to a pipeline. An unbound or
     * disabled trigger is never scheduled, so it consumes no background wakeups.
     */
    val isActive: Boolean get() = enabled && pipelineId != null
}
