package ai.agent.android.domain.usecases

import ai.agent.android.domain.prompt.PromptVariableProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Catalog entry describing one prompt variable for the Settings →
 * System instructions chip row and Insert-variable sheet.
 *
 * @property placeholder The raw placeholder including the leading `$`
 *   (e.g. `$DATE`) — what the user inserts into the textarea.
 * @property sample Live resolved value at catalog-build time (e.g.
 *   `20 May 2026`) shown beneath the chip so the user previews what
 *   the variable will expand to.
 */
data class PromptVariableCatalogEntry(val placeholder: String, val sample: String)

/**
 * Materialises the list of [PromptVariableCatalogEntry] available for
 * insertion into the Settings → System instructions textarea. The
 * Insert-variable sheet uses the resolved samples to preview each
 * variable's effect before the user commits it.
 *
 * Sorted by placeholder name for deterministic UI ordering. Resolution
 * errors on individual providers fall back to an empty sample —
 * mirrors `PromptTemplateEngine`'s "swallow + log" policy.
 */
class GetSystemPromptVariableCatalogUseCase @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards PromptVariableProvider>,
) {
    /**
     * Resolves every registered provider once and returns the catalog
     * sorted by placeholder. Errors on individual providers fall back to
     * an empty sample — the chip still renders but with no preview.
     */
    suspend operator fun invoke(): List<PromptVariableCatalogEntry> = withContext(Dispatchers.Default) {
        providers
            .map { provider ->
                val sample = runCatching { provider.resolve() }.getOrDefault("")
                PromptVariableCatalogEntry(
                    placeholder = "\$${provider.key()}",
                    sample = sample,
                )
            }
            .sortedBy { it.placeholder }
    }
}
