package app.knotwork.android.di

import app.knotwork.android.data.repositories.NoOpCrashReportingRepository
import app.knotwork.android.domain.repositories.CrashReportingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * `foss`-flavour crash-reporting wiring.
 *
 * This module lives in the `foss` source set only and binds the
 * [NoOpCrashReportingRepository], which has zero Firebase/Google dependencies.
 * Its `full`-flavour counterpart (same fully-qualified name, under `src/full`)
 * binds the Firebase-backed implementation and provides the Crashlytics /
 * Analytics singletons. Exactly one of the two is compiled into any given
 * build, so Hilt always sees a single binding for [CrashReportingRepository].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CrashReportingModule {

    /**
     * Binds the no-op [NoOpCrashReportingRepository] to the domain-level
     * [CrashReportingRepository] for the F-Droid build.
     */
    @Binds
    @Singleton
    abstract fun bindCrashReportingRepository(repository: NoOpCrashReportingRepository): CrashReportingRepository
}
