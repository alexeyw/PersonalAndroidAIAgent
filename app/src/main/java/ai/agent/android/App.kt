package ai.agent.android

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

/**
 * Base Application class for the Android AI Agent project.
 *
 * This class is annotated with @HiltAndroidApp to trigger Hilt's code generation,
 * including a base class for the application that serves as the application-level
 * dependency container.
 *
 * It implements Configuration.Provider to configure WorkManager with HiltWorkerFactory,
 * allowing dependencies to be injected into CoroutineWorkers.
 *
 * It acts as the primary entry point for setting up global application state,
 * integrating Dagger-Hilt for dependency injection across the presentation,
 * domain, and data layers of our Clean Architecture.
 */
@HiltAndroidApp
class App :
    Application(),
    Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
