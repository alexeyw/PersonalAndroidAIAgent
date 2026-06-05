package ai.agent.android.di

import ai.agent.android.data.local.AppDatabase
import ai.agent.android.data.local.Converters
import ai.agent.android.data.local.EncryptedDbPassphraseProvider
import ai.agent.android.data.local.dao.ChatDao
import ai.agent.android.data.local.dao.LocalModelDao
import ai.agent.android.data.local.dao.MemoryDao
import ai.agent.android.data.local.dao.PipelineDao
import ai.agent.android.data.local.dao.PipelinePresetDao
import ai.agent.android.data.local.dao.PromptPresetDao
import ai.agent.android.data.local.dao.PromptTemplateDao
import ai.agent.android.data.local.dao.TraceStepDao
import ai.agent.android.data.tools.local.AppFunctionDataCodec
import ai.agent.android.data.tools.local.LocalAppFunctionManager
import ai.agent.android.domain.services.ApprovalNotifier
import ai.agent.android.presentation.notifications.ApprovalNotificationManager
import ai.agent.android.presentation.state.ActiveSessionTracker
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.work.WorkManager
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
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
    fun providePreferencesDataStore(@ApplicationContext appContext: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { appContext.preferencesDataStoreFile(USER_PREFERENCES_NAME) },
        )

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
            DATABASE_NAME,
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
                AppDatabase.MIGRATION_17_18,
                AppDatabase.MIGRATION_18_19,
                AppDatabase.MIGRATION_19_20,
                AppDatabase.MIGRATION_20_21,
                AppDatabase.MIGRATION_21_22,
                AppDatabase.MIGRATION_22_23,
                AppDatabase.MIGRATION_23_24,
                AppDatabase.MIGRATION_24_25,
                AppDatabase.MIGRATION_25_26,
                AppDatabase.MIGRATION_26_27,
                AppDatabase.MIGRATION_27_28,
            )
            .fallbackToDestructiveMigration(true)
            .build()
    }

    /**
     * Provides the [LocalModelDao] from the database.
     */
    @Provides
    fun provideLocalModelDao(database: AppDatabase): LocalModelDao = database.localModelDao()

    /**
     * Provides the [ChatDao] from the database.
     */
    @Provides
    fun provideChatDao(database: AppDatabase): ChatDao = database.chatDao()

    /**
     * Provides the [MemoryDao] from the database.
     */
    @Provides
    fun provideMemoryDao(database: AppDatabase): MemoryDao = database.memoryDao()

    /**
     * Provides the [PipelineDao] from the database.
     */
    @Provides
    fun providePipelineDao(database: AppDatabase): PipelineDao = database.pipelineDao()

    /**
     * Provides the [PromptTemplateDao] from the database.
     */
    @Provides
    fun providePromptTemplateDao(database: AppDatabase): PromptTemplateDao = database.promptTemplateDao()

    /**
     * Provides the [TraceStepDao] from the database.
     */
    @Provides
    fun provideTraceStepDao(database: AppDatabase): TraceStepDao = database.traceStepDao()

    /**
     * Provides the [PipelinePresetDao] backing the user-saved
     * pipeline-preset catalogue.
     */
    @Provides
    fun providePipelinePresetDao(database: AppDatabase): PipelinePresetDao = database.pipelinePresetDao()

    /**
     * Provides the [PromptPresetDao] backing the user-saved
     * prompt-preset catalogue.
     */
    @Provides
    fun providePromptPresetDao(database: AppDatabase): PromptPresetDao = database.promptPresetDao()

    /**
     * Provides the singleton instance of Converters for Room mapping.
     */
    @Provides
    @Singleton
    fun provideConverters(): Converters = Converters()

    /**
     * Provides the singleton instance of OkHttpClient.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

    /**
     * Provides the singleton instance of LocalAppFunctionManager.
     */
    @Provides
    @Singleton
    fun provideLocalAppFunctionManager(
        @ApplicationContext appContext: Context,
        codec: AppFunctionDataCodec,
    ): LocalAppFunctionManager = LocalAppFunctionManager(appContext, codec)

    /**
     * Provides the singleton instance of WorkManager.
     */
    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext appContext: Context): WorkManager = WorkManager.getInstance(appContext)

    /**
     * Provides the Firebase Crashlytics singleton.
     *
     * Wired through Hilt so unit tests can substitute a mock instead of
     * touching the static `getInstance()` entry point. Crashlytics
     * auto-collection is disabled by manifest meta-data — actual
     * upload only happens once the user opts in via
     * [ai.agent.android.domain.repositories.CrashReportingRepository.setEnabled].
     *
     * Defensively initialises [FirebaseApp] if it hasn't been by
     * `FirebaseInitProvider` yet. The `ProcessPhoenix.triggerRebirth`
     * restart spawns a transient `:phoenix` sub-process whose Hilt
     * graph is constructed before the Firebase ContentProvider has had
     * a chance to fire — without this guard the process crashes with
     * `IllegalStateException: Default FirebaseApp is not initialized`
     * and the restart silently fails.
     */
    @Provides
    @Singleton
    fun provideFirebaseCrashlytics(@ApplicationContext appContext: Context): FirebaseCrashlytics {
        ensureFirebaseInitialised(context = appContext)
        return FirebaseCrashlytics.getInstance()
    }

    /**
     * Provides the Firebase Analytics singleton. Analytics is required as
     * a transitive dependency of Crashlytics; both opt-in flags toggle
     * together inside `FirebaseCrashReportingRepositoryImpl`.
     *
     * Shares the same `:phoenix`-process resilience as
     * [provideFirebaseCrashlytics].
     */
    @Provides
    @Singleton
    fun provideFirebaseAnalytics(@ApplicationContext appContext: Context): FirebaseAnalytics {
        ensureFirebaseInitialised(context = appContext)
        return FirebaseAnalytics.getInstance(appContext)
    }

    /**
     * Idempotent helper that calls [FirebaseApp.initializeApp] when no
     * default app has been registered yet. `initializeApp(Context)` is a
     * no-op the second time around, so this is safe to call from every
     * Firebase provider without churn.
     */
    private fun ensureFirebaseInitialised(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            runCatching { FirebaseApp.initializeApp(context) }
        }
    }

    /**
     * Provides the singleton instance of ApprovalNotifier.
     */
    @Provides
    @Singleton
    fun provideApprovalNotifier(
        @ApplicationContext appContext: Context,
        activeSessionTracker: ActiveSessionTracker,
    ): ApprovalNotifier = ApprovalNotificationManager(appContext, activeSessionTracker)
}
