package app.knotwork.android.data.local.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity persisting one parked HITL interaction in the
 * `pending_interactions` table.
 *
 * Mirrors the domain model `PendingInteraction`; enum-typed fields ([kind],
 * [risk], [decision]) are stored as their `name` strings and the answer-choice
 * list as a JSON array string, following the `pipeline_runs` precedent of
 * forward-compatible, human-debuggable rows. There is deliberately **no**
 * foreign key on [runId] or [sessionId]: the record's lifecycle is owned by
 * the two-phase waiting protocol (created on park, consumed on resume,
 * expired by maintenance), not by row cascades.
 *
 * @property runId Id of the parked pipeline run; primary key — one pending
 *   interaction per run at most.
 * @property sessionId Id of the owning chat session (indexed — the reattach
 *   protocol looks records up by session).
 * @property kind `PendingInteractionKind` name (`APPROVAL` / `CLARIFICATION`).
 * @property toolName Name of the tool awaiting approval; `null` for clarifications.
 * @property toolArgs JSON argument string of the staged tool call; `null` for
 *   clarifications.
 * @property risk `ToolRisk` name of the staged tool call; `null` for clarifications.
 * @property question Persisted clarifying question; `null` for approvals.
 * @property optionsJson JSON array of answer choices; `null` for approvals or
 *   free-form questions.
 * @property decision `PendingDecision` name recorded after the park; `null`
 *   while unanswered.
 * @property answer The user's clarification answer; `null` while unanswered.
 * @property requestedAt Epoch millis when the persistent waiting phase began.
 */
@Entity(
    tableName = "pending_interactions",
    indices = [
        Index(value = ["sessionId"]),
    ],
)
data class PendingInteractionEntity(
    @PrimaryKey
    val runId: String,
    val sessionId: String,
    val kind: String,
    val toolName: String?,
    val toolArgs: String?,
    val risk: String?,
    val question: String?,
    val optionsJson: String?,
    val decision: String?,
    val answer: String?,
    val requestedAt: Long,
)
