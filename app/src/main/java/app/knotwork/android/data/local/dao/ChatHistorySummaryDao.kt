package app.knotwork.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.knotwork.android.data.local.models.ChatHistorySummaryEntity

/**
 * Data Access Object for [ChatHistorySummaryEntity] (`chat_history_summaries`).
 *
 * One row per session; the background compressor overwrites it wholesale on
 * each pass, so the upsert uses [OnConflictStrategy.REPLACE].
 */
@Dao
interface ChatHistorySummaryDao {

    /**
     * Returns the cached summary for a session, or `null` when none exists.
     *
     * @param sessionId The id of the session.
     * @return The stored entity, or `null`.
     */
    @Query("SELECT * FROM chat_history_summaries WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getForSession(sessionId: String): ChatHistorySummaryEntity?

    /**
     * Inserts or replaces the summary for a session.
     *
     * @param summary The entity to persist.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(summary: ChatHistorySummaryEntity)
}
