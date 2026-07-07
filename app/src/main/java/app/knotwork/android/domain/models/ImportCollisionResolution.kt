package app.knotwork.android.domain.models

/**
 * How an import should resolve pipeline **id collisions** — the case where an
 * imported pipeline's id already names a pipeline in the local library.
 *
 * The choice is surfaced to the user through a confirmation dialog (single
 * import) or an aggregated dialog (bundle import) and drives whether existing
 * pipelines are overwritten or the incoming graphs are re-identified.
 *
 * @see app.knotwork.android.domain.usecases.ImportPipelineUseCase
 * @see app.knotwork.android.domain.usecases.ImportPipelineBundleUseCase
 */
enum class ImportCollisionResolution {

    /**
     * Keep the incoming ids and overwrite the colliding library pipelines in
     * place. Treats the import as a *synchronisation*: existing
     * `ChatSession.pipelineId` bindings and cross-pipeline references survive
     * because the ids are unchanged. Local edits to the overwritten pipelines
     * are lost.
     */
    REPLACE,

    /**
     * Regenerate ids for the incoming graphs (and remap every intra-bundle
     * `targetPipelineId` reference accordingly) so they are persisted as fresh
     * copies alongside the existing pipelines. Treats the import as a *copy*:
     * nothing existing is touched, at the cost of duplicating the composition.
     */
    IMPORT_AS_COPY,
}
