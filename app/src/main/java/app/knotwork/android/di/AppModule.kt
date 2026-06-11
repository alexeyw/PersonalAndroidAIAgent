package app.knotwork.android.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.work.WorkManager
import app.knotwork.android.data.local.AppDatabase
import app.knotwork.android.data.local.Converters
import app.knotwork.android.data.local.DeferredPassphraseOpenHelperFactory
import app.knotwork.android.data.local.EncryptedDbPassphraseProvider
import app.knotwork.android.data.local.dao.ChatDao
import app.knotwork.android.data.local.dao.LocalModelDao
import app.knotwork.android.data.local.dao.MemoryDao
import app.knotwork.android.data.local.dao.PipelineDao
import app.knotwork.android.data.local.dao.PipelinePresetDao
import app.knotwork.android.data.local.dao.PipelineRunDao
import app.knotwork.android.data.local.dao.PromptPresetDao
import app.knotwork.android.data.local.dao.PromptTemplateDao
import app.knotwork.android.data.local.dao.TraceStepDao
import app.knotwork.android.data.tools.local.AppFunctionDataCodec
import app.knotwork.android.data.tools.local.LocalAppFunctionManager
import app.knotwork.android.domain.services.ApprovalNotifier
import app.knotwork.android.domain.services.ScheduledTaskNotifier
import app.knotwork.android.presentation.notifications.ApprovalNotificationManager
import app.knotwork.android.presentation.notifications.ScheduledTaskNotifierImpl
import app.knotwork.android.presentation.state.ActiveSessionTracker
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
     * in a [app.knotwork.android.data.local.crypto.KeystoreBackedPrefsStore] (AES-GCM under a
     * dedicated Android Keystore key).
     *
     * **Migration policy.** Every schema-version bump is backed by an explicit
     * [androidx.room.migration.Migration] registered through [addMigrations]; the full chain is
     * declared on [AppDatabase]. Destructive recreation on **upgrade** has been removed, so a
     * version bump preserves all user data (chats, long-term memory, run traces, custom
     * pipelines, saved presets and prompt templates) instead of dropping the tables. A missing
     * upgrade path is therefore a hard failure surfaced in development rather than silent data
     * loss in the field.
     *
     * Destructive recreation is retained **only on downgrade**
     * ([fallbackToDestructiveMigrationOnDowngrade]) — forward migrations cannot reverse a schema,
     * so installing an older build over a newer database recreates it empty rather than crashing.
     *
     * Legacy plaintext databases from pre-SQLCipher development builds (which predate the public
     * release) are not supported: SQLCipher cannot open them and there is no downgrade path to
     * recreate them. This affects only such dev installs, never a released version.
     *
     * **Passphrase deferral.** The passphrase is intentionally NOT fetched here: this provider
     * runs synchronously during Hilt injection (often on the main thread while a ViewModel is
     * being constructed), so a passphrase failure here would crash the app before any UI error
     * handling exists. [DeferredPassphraseOpenHelperFactory] postpones the fetch to the first
     * real database open, where `AppInitializationUseCase` catches the failure and routes it to
     * the splash recovery screen.
     */
    /**
     * Provides the deferred SQLCipher open-helper factory as its own singleton so the
     * user-confirmed data wipe ([app.knotwork.android.data.local.DatabaseResetServiceImpl])
     * can run inside [DeferredPassphraseOpenHelperFactory.runExclusive], serialized against
     * every concurrent database open.
     *
     * sqlcipher-android retains the passphrase array for the helper's lifetime (it re-keys
     * every pooled connection from it — unlike the legacy android-database-sqlcipher, it never
     * zeroes the array); the provider hands over a fresh copy, so the retained array never
     * aliases the stored value.
     */
    @Provides
    @Singleton
    fun provideDeferredPassphraseOpenHelperFactory(
        passphraseProvider: EncryptedDbPassphraseProvider,
    ): DeferredPassphraseOpenHelperFactory = DeferredPassphraseOpenHelperFactory(passphraseProvider) { passphrase ->
        SupportOpenHelperFactory(passphrase)
    }

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext appContext: Context,
        factory: DeferredPassphraseOpenHelperFactory,
    ): AppDatabase {
        // net.zetetic:sqlcipher-android does NOT auto-load its native library the way the
        // legacy android-database-sqlcipher did. Without this explicit load, the first call
        // into SupportOpenHelperFactory would crash with UnsatisfiedLinkError. loadLibrary is
        // idempotent, so calling it here (inside the @Singleton provider) is safe.
        System.loadLibrary("sqlcipher")

        return Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME,
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
                AppDatabase.MIGRATION_28_29,
                AppDatabase.MIGRATION_29_30,
                AppDatabase.MIGRATION_30_31,
                AppDatabase.MIGRATION_31_32,
            )
            // No destructive fallback on upgrade: every version bump must supply an explicit
            // migration above so user data survives. Destructive recreation is kept only for the
            // (rare) downgrade case, which forward migrations cannot handle.
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
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
     * Provides the [PipelineRunDao] backing the persistent pipeline-run records.
     */
    @Provides
    fun providePipelineRunDao(database: AppDatabase): PipelineRunDao = database.pipelineRunDao()

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
     * [app.knotwork.android.domain.repositories.CrashReportingRepository.setEnabled].
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

    /**
     * Binds the presentation-layer [ScheduledTaskNotifierImpl] (deep-links into
     * `MainActivity`) to the domain-level [ScheduledTaskNotifier] consumed by
     * the background `AgentWorker`.
     */
    @Provides
    @Singleton
    fun provideScheduledTaskNotifier(impl: ScheduledTaskNotifierImpl): ScheduledTaskNotifier = impl
}
