package ai.agent.android.domain.models

/**
 * How an imported set of memory chunks is reconciled with the chunks already
 * stored on the device.
 *
 * Chosen by the user in the import dialog and consumed by
 * [ai.agent.android.domain.usecases.MemoryImportUseCase].
 */
enum class MemoryImportStrategy {

    /**
     * Add the imported chunks alongside the existing ones, skipping any whose
     * id already exists locally. Non-destructive — nothing currently stored is
     * removed or overwritten.
     */
    Merge,

    /**
     * Wipe every existing chunk (pinned included) before loading the imported
     * set, so the local store becomes an exact copy of the file. Destructive —
     * the UI must confirm before invoking it.
     */
    Replace,
}
