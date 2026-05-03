package ai.agent.android.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import ai.agent.android.data.local.AppDatabase
import ai.agent.android.data.local.EncryptedDbPassphraseProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Global application-level dependency injection module.
 * 
 * This module is installed in the SingletonComponent, meaning the dependencies
 * provided here will live as long as the application itself.
 * Use this module to provide system-wide singletons, such as application context
 * providers, database instances, network clients, etc.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private const val USER_PREFERENCES_NAME = "agent_preferences"
    private const val DATABASE_NAME = "agent_database.db"

    /**
     * Provides the singleton instance of the DataStore preferences.
     */
    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext appContext: Context
    ): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            produceFile = { appContext.preferencesDataStoreFile(USER_PREFERENCES_NAME) }
        )
    }

    /**
     * Provides the singleton instance of the Room Database.
     *
     * The database is encrypted at rest via SQLCipher. A random 32-byte passphrase is stored
     * in [androidx.security.crypto.EncryptedSharedPreferences] (master key in Android Keystore).
     * If a legacy plaintext database from an earlier build exists, SQLCipher will fail to open
     * it and Room will recreate it via [fallbackToDestructiveMigration] — acceptable because
     * the project has no shipped users yet.
     */
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext appContext: Context,
        passphraseProvider: EncryptedDbPassphraseProvider,
    ): AppDatabase {
        // net.zetetic:sqlcipher-android does NOT auto-load its native library the way the
        // legacy android-database-sqlcipher did. Without this explicit load, the first call
        // into SupportOpenHelperFactory would crash with UnsatisfiedLinkError. loadLibrary is
        // idempotent, so calling it here (inside the @Singleton provider) is safe.
        System.loadLibrary("sqlcipher")

        val passphrase = passphraseProvider.getOrCreatePassphrase()

        // SupportOpenHelperFactory zeroes the byte array after consumption.
        val factory = SupportOpenHelperFactory(passphrase)

        return Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            DATABASE_NAME
        )
        .openHelperFactory(factory)
        .addMigrations(
            AppDatabase.MIGRATION_9_10,
            AppDatabase.MIGRATION_10_11,
            AppDatabase.MIGRATION_11_12,
            AppDatabase.MIGRATION_12_13,
            AppDatabase.MIGRATION_13_14,
            AppDatabase.MIGRATION_14_15,
            AppDatabase.MIGRATION_15_16,
            AppDatabase.MIGRATION_16_17,
        )
        .fallbackToDestructiveMigration(true)
        .build()
    }

    /**
     * Provides the [ai.agent.android.data.local.dao.LocalModelDao] from the database.
     */
    @Provides
    fun provideLocalModelDao(database: AppDatabase): ai.agent.android.data.local.dao.LocalModelDao {
        return database.localModelDao()
    }

    /**
     * Provides the [ai.agent.android.data.local.dao.ChatDao] from the database.
     */
    @Provides
    fun provideChatDao(database: AppDatabase): ai.agent.android.data.local.dao.ChatDao {
        return database.chatDao()
    }

    /**
     * Provides the [ai.agent.android.data.local.dao.MemoryDao] from the database.
     */
    @Provides
    fun provideMemoryDao(database: AppDatabase): ai.agent.android.data.local.dao.MemoryDao {
        return database.memoryDao()
    }

    /**
     * Provides the [ai.agent.android.data.local.dao.PipelineDao] from the database.
     */
    @Provides
    fun providePipelineDao(database: AppDatabase): ai.agent.android.data.local.dao.PipelineDao {
        return database.pipelineDao()
    }

    /**
     * Provides the [ai.agent.android.data.local.dao.PromptTemplateDao] from the database.
     */
    @Provides
    fun providePromptTemplateDao(database: AppDatabase): ai.agent.android.data.local.dao.PromptTemplateDao {
        return database.promptTemplateDao()
    }

    /**
     * Provides the [ai.agent.android.data.local.dao.TraceStepDao] from the database.
     */
    @Provides
    fun provideTraceStepDao(database: AppDatabase): ai.agent.android.data.local.dao.TraceStepDao {
        return database.traceStepDao()
    }

    /**
     * Provides the singleton instance of Converters for Room mapping.
     */
    @Provides
    @Singleton
    fun provideConverters(): ai.agent.android.data.local.Converters {
        return ai.agent.android.data.local.Converters()
    }

    /**
     * Provides the singleton instance of OkHttpClient.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().build()
    }

    /**
     * Provides the singleton instance of LocalAppFunctionManager.
     */
    @Provides
    @Singleton
    fun provideLocalAppFunctionManager(
        @ApplicationContext appContext: Context
    ): ai.agent.android.data.tools.local.LocalAppFunctionManager {
        return ai.agent.android.data.tools.local.LocalAppFunctionManager(appContext)
    }

    /**
     * Provides the singleton instance of WorkManager.
     */
    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext appContext: Context
    ): androidx.work.WorkManager {
        return androidx.work.WorkManager.getInstance(appContext)
    }

    /**
     * Provides the singleton instance of ApprovalNotifier.
     */
    @Provides
    @Singleton
    fun provideApprovalNotifier(
        @ApplicationContext appContext: Context,
        activeSessionTracker: ai.agent.android.presentation.state.ActiveSessionTracker
    ): ai.agent.android.domain.services.ApprovalNotifier {
        return ai.agent.android.presentation.notifications.ApprovalNotificationManager(appContext, activeSessionTracker)
    }
}