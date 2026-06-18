package app.knotwork.android.domain.models

/**
 * Cached prose summary of the older portion of a chat session's history.
 *
 * Long sessions outgrow a small on-device model's context window: the verbatim
 * `--- Chat History ---` block keeps growing until it either hits the token
 * limit or crowds out long-term memory and tool results. To bound it, the
 * background [app.knotwork.android.domain.usecases.CompressChatHistoryUseCase]
 * summarises the tail of the conversation that sits *older* than the live
 * window of recent messages into one dense paragraph, persisted here and
 * rendered as `--- Earlier conversation (summarized) ---` ahead of the live
 * window.
 *
 * The summary is **incremental**: a later pass folds the previous summary plus
 * the messages that have since aged out of the live window into a new summary,
 * rather than re-summarising the whole history every time. [coveredMessageCount]
 * is the cursor that makes this work — it records how many leading messages of
 * the session (in [app.knotwork.android.domain.repositories.ChatRepository.getMessagesForSession]
 * order, the same unfiltered order the engine reads for context) the [summary]
 * already represents, so a pass only ever has to digest the messages beyond it.
 *
 * @property sessionId Id of the chat session this summary belongs to. One
 *   summary per session (1:1 with `chat_sessions`); the row is cascade-deleted
 *   with the session.
 * @property summary The dense prose summary of the covered prefix of the
 *   conversation. Plain text, no markup.
 * @property coveredMessageCount Number of leading messages of the session that
 *   [summary] represents. The render path keeps everything *after* this prefix
 *   verbatim, so the cursor is also the boundary between the summarised tail and
 *   the live window.
 * @property updatedAt Wall-clock time the summary was last (re)computed, in
 *   `System.currentTimeMillis()` units.
 */
data class ChatHistorySummary(
    val sessionId: String,
    val summary: String,
    val coveredMessageCount: Int,
    val updatedAt: Long,
)
