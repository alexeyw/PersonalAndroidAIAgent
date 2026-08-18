package app.knotwork.android.domain.engine

import app.knotwork.android.domain.models.RunOrigin

/**
 * Where the long-term-memory retrieval key of a run came from.
 *
 * Surfaced in the `MemoryAccess` console line so a background run's retrieval
 * is explainable after the fact ("why did it search for *that*?") instead of
 * silently keying off a prompt the pipeline author wrote months ago.
 */
enum class RetrievalQuerySource {
    /** The pipeline declared the query itself (`PipelineGraph.memoryRetrievalQuery`). */
    DECLARED,

    /** The text the first memory-aware node actually executes on. */
    NODE_INPUT,

    /** The message that started the run — the interactive default and last-resort fallback. */
    USER_PROMPT,
}

/**
 * The retrieval key chosen for a run, paired with the rule that produced it.
 *
 * @property text The query string handed to
 *   `RetrieveRelevantMemoryUseCase.retrieveScored`. Never blank: every rule in
 *   [MemoryRetrievalQueryResolver] skips blank candidates, and the final
 *   fallback is the run's own prompt.
 * @property source Which rule won. Purely diagnostic — it changes no behaviour,
 *   only the console line.
 */
data class ResolvedRetrievalQuery(val text: String, val source: RetrievalQuerySource)

/**
 * Pure decision component that answers one question: **what text should a run
 * embed when it searches long-term memory?**
 *
 * For an interactive run the answer is trivial — the user's message *is* the
 * question, so it is also the best semantic key. A background run has no such
 * luxury: its prompt was written once by the pipeline author ("write the evening
 * journal entry") and says nothing about what *this* particular firing is about,
 * so keying retrieval off it returns whatever happens to sit near that generic
 * sentence in embedding space. Since automation runs became a proven live path,
 * that blindness is a real defect rather than a theoretical one.
 *
 * The resolution contract (canonical: `DESCRIPTION.md` §6.10.1):
 *
 * | Origin                               | Key                                        |
 * |--------------------------------------|--------------------------------------------|
 * | `CHAT`, `SHARE`                      | the run's prompt ([RetrievalQuerySource.USER_PROMPT]) |
 * | `SCHEDULER`, `QUICK_TILE`, `TRIGGER` | declared query → node input → run prompt   |
 *
 * `SHARE` counts as interactive on purpose: the shared text *is* the user's
 * query. `QUICK_TILE` counts as background: the tile launches a duty pipeline
 * under a fixed prompt and suffers exactly the trigger's genericity.
 *
 * The object is deterministic, framework-free and clock-free — the caller
 * supplies every input, including the already-template-rendered declared query.
 */
object MemoryRetrievalQueryResolver {

    /**
     * Picks the retrieval key for one run.
     *
     * @param origin What started the run. Decides which of the two contracts
     *   applies (see the table above).
     * @param declaredQuery The pipeline's own `memoryRetrievalQuery`, **already
     *   rendered** through `PromptTemplateEngine` by the caller (so a declared
     *   `"journal entries around $DATE"` arrives with today's date substituted).
     *   `null` or blank means "not declared" and falls through to the next rule.
     * @param nodeInput The text the first memory-aware node is about to execute
     *   on — the upstream node's output, or the run prompt when that node sits
     *   directly behind `INPUT`. Blank falls through.
     * @param userPrompt The message that started the run. Used verbatim for
     *   interactive runs and as the terminal fallback for background ones; it is
     *   the only candidate that is never skipped, so the result is well-defined
     *   even when everything else is empty.
     * @return The chosen query plus the rule that chose it.
     */
    fun resolve(
        origin: RunOrigin,
        declaredQuery: String?,
        nodeInput: String,
        userPrompt: String,
    ): ResolvedRetrievalQuery {
        if (origin.isInteractive) {
            return ResolvedRetrievalQuery(userPrompt, RetrievalQuerySource.USER_PROMPT)
        }
        declaredQuery?.takeIf { it.isNotBlank() }?.let {
            return ResolvedRetrievalQuery(it, RetrievalQuerySource.DECLARED)
        }
        nodeInput.takeIf { it.isNotBlank() }?.let {
            return ResolvedRetrievalQuery(it, RetrievalQuerySource.NODE_INPUT)
        }
        return ResolvedRetrievalQuery(userPrompt, RetrievalQuerySource.USER_PROMPT)
    }
}

/**
 * `true` when the run was started by a person acting on the app right now, so
 * its prompt carries the user's actual intent.
 *
 * The distinction drives the retrieval-key contract above and is deliberately
 * exhaustive (`when` over the enum): a new [RunOrigin] will not compile until it
 * is classified, which is the point — silently defaulting a new background
 * surface to "interactive" would reintroduce the very blindness this resolver
 * exists to fix.
 */
val RunOrigin.isInteractive: Boolean
    get() = when (this) {
        RunOrigin.CHAT, RunOrigin.SHARE -> true
        RunOrigin.SCHEDULER, RunOrigin.QUICK_TILE, RunOrigin.TRIGGER, RunOrigin.EXTERNAL -> false
    }
