package ai.agent.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Base Application class for the Android AI Agent project.
 * 
 * This class is annotated with @HiltAndroidApp to trigger Hilt's code generation,
 * including a base class for the application that serves as the application-level
 * dependency container.
 * 
 * It acts as the primary entry point for setting up global application state,
 * integrating Dagger-Hilt for dependency injection across the presentation,
 * domain, and data layers of our Clean Architecture.
 */
@HiltAndroidApp
class App : Application()