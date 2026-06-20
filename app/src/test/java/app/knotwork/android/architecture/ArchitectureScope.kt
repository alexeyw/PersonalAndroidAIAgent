package app.knotwork.android.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope

/**
 * Shared Konsist scope for the architecture guard suite.
 *
 * The scope is pinned to the `app` module's **main** source set
 * ([PRODUCTION_SOURCE_PATH]) rather than [Konsist.scopeFromProject] /
 * [Konsist.scopeFromProduction] on purpose:
 *
 * - It excludes test sources, which legitimately cross layer boundaries
 *   (a `domain` use-case test may construct `data` fakes) and must not be
 *   constrained by these rules.
 * - It excludes anything outside the module's real source tree. The project
 *   root can contain unrelated copies of the codebase — notably stale git
 *   worktrees under `.claude/worktrees/` from parallel sessions — which a
 *   whole-project scope would parse and then flag with spurious, environment
 *   dependent violations. Pinning to a single directory subtree keeps the
 *   guard deterministic across machines and CI.
 */
internal object ArchitectureScope {
    /**
     * Path, relative to the Gradle root project directory, of the `app`
     * module's production source set.
     */
    private const val PRODUCTION_SOURCE_PATH = "app/src/main"

    /**
     * Konsist scope over the `app` module's production code, reused by every
     * test in the suite to avoid re-parsing the source tree per test.
     */
    val production: KoScope = Konsist.scopeFromDirectory(PRODUCTION_SOURCE_PATH)
}
