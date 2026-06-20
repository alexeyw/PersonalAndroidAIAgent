package app.knotwork.android.architecture

import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.Test

/**
 * Konsist guard enforcing the strictest project rule for the `domain` layer:
 * it is pure Kotlin with **zero** Android/framework imports, so it can be
 * compiled and unit-tested off-device.
 *
 * This is the import-level mirror of [LayerDependencyKonsistTest]. The layer
 * rule there forbids `domain -> data`/`domain -> presentation`; this rule
 * additionally forbids `domain` reaching into the Android SDK
 * (`android.*` / `androidx.*`).
 *
 * The single sanctioned exception is `androidx.annotation` — annotation-only,
 * runtime-free helpers such as `@VisibleForTesting` are already used inside
 * `domain` (e.g. the node executors) and carry no Android runtime dependency.
 * Any other `androidx.*` import (let alone `android.*`) is a layer violation.
 */
class DomainPurityKonsistTest {
    @Test
    fun `domain layer has no Android SDK imports except androidx annotation`() {
        ArchitectureScope.production
            .files
            .withPackage("app.knotwork.android.domain..")
            .assertFalse(additionalMessage = ANDROID_IMPORT_FAILURE) { file ->
                file.imports.any { import ->
                    import.name.startsWith("android.") ||
                        (
                            import.name.startsWith("androidx.") &&
                                !import.name.startsWith("androidx.annotation.")
                            )
                }
            }
    }

    private companion object {
        const val ANDROID_IMPORT_FAILURE =
            "domain layer must not import android.*/androidx.* (only androidx.annotation.* is allowed); " +
                "move framework-dependent logic to the data or presentation layer"
    }
}
