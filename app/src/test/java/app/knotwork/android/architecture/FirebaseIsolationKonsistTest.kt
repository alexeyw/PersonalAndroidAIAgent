package app.knotwork.android.architecture

import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.Test

/**
 * Konsist guard keeping the Firebase SDK out of the shared `main` source set.
 *
 * Crash reporting is split across distribution flavours: the Firebase-backed
 * implementation, its Hilt module, and the Crashlytics/Analytics meta-data all
 * live in the `full` source set (`app/src/full`), while the `foss` source set
 * binds a no-op with no Firebase on the classpath. For that split to hold —
 * and for the F-Droid (`foss`) build to be provably free of
 * `com.google.firebase` — nothing in the shared `main` tree may import the
 * Firebase SDK; every reference must go through the flavour-agnostic
 * [app.knotwork.android.domain.repositories.CrashReportingRepository] interface.
 *
 * The Gradle-level guarantee (Firebase is a `fullImplementation` dependency, so
 * it is absent from the `foss` classpath entirely) is the primary line of
 * defence; this structural guard is belt-and-braces and fails fast in code
 * review if a `main` file ever reaches for `com.google.firebase.*` directly.
 */
class FirebaseIsolationKonsistTest {
    @Test
    fun `main source set has no Firebase imports`() {
        ArchitectureScope.production
            .files
            .assertFalse(additionalMessage = FIREBASE_IMPORT_FAILURE) { file ->
                file.imports.any { import -> import.name.startsWith("com.google.firebase.") }
            }
    }

    private companion object {
        const val FIREBASE_IMPORT_FAILURE =
            "shared `main` source set must not import com.google.firebase.*; the Firebase-backed crash " +
                "reporting lives in the `full` flavour source set (app/src/full) and the rest of the code " +
                "must depend only on the flavour-agnostic CrashReportingRepository interface"
    }
}
