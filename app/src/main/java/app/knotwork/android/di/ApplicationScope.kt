package app.knotwork.android.di

import javax.inject.Qualifier

/**
 * Qualifies the application-lifetime [kotlinx.coroutines.CoroutineScope] provided
 * by [AppModule]. Singletons that own fire-and-forget background work (e.g. the
 * voice recorder's capture loop) inject this scope instead of self-constructing
 * one, per the project's coroutine convention.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
