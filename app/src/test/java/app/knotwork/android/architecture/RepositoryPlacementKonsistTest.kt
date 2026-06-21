package app.knotwork.android.architecture

import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

/**
 * Konsist guard enforcing the repository placement convention from the
 * api-conventions rule: the abstraction (`<Noun>Repository` interface) is
 * owned by the `domain` layer, and its implementation (`<Noun>RepositoryImpl`)
 * lives in the `data` layer. This keeps the dependency inversion intact —
 * `domain` declares the contract, `data` fulfils it.
 */
class RepositoryPlacementKonsistTest {
    @Test
    fun `repository interfaces reside in the domain layer`() {
        ArchitectureScope.production
            .interfaces()
            .withNameEndingWith("Repository")
            .assertTrue(additionalMessage = INTERFACE_PLACEMENT_FAILURE) { declaration ->
                declaration.resideInPackage("app.knotwork.android.domain..")
            }
    }

    @Test
    fun `repository implementations reside in the data layer`() {
        ArchitectureScope.production
            .classes()
            .withNameEndingWith("RepositoryImpl")
            .assertTrue(additionalMessage = IMPL_PLACEMENT_FAILURE) { declaration ->
                declaration.resideInPackage("app.knotwork.android.data..")
            }
    }

    private companion object {
        const val INTERFACE_PLACEMENT_FAILURE =
            "a *Repository interface must live in the domain layer (app.knotwork.android.domain..)"
        const val IMPL_PLACEMENT_FAILURE =
            "a *RepositoryImpl class must live in the data layer (app.knotwork.android.data..)"
    }
}
