package ai.agent.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Main Room Database for the Android AI Agent.
 * 
 * Currently initialized without any entities as a foundation.
 * Future entities (e.g., ChatHistory, PromptTemplates) will be registered here.
 */
@Database(
    entities = [], // Add entities here when created
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    // Define abstract methods for DAOs here in the future
}
