package app.knotwork.android.domain.models

/**
 * Immutable metadata snapshot of a single entry inside the agent workspace.
 *
 * Deliberately carries no filesystem handle: the workspace root is an
 * implementation detail of the data layer, and exposing a `java.io.File` here
 * would let callers bypass the single canonicalisation gate
 * ([app.knotwork.android.domain.services.AgentWorkspace.resolve]). Consumers
 * address an entry exclusively by its [relativePath].
 *
 * @property relativePath Path of the entry relative to the workspace root,
 *   using `/` as the separator (e.g. `reports/summary.md`). Never contains a
 *   leading slash and never escapes the root.
 * @property sizeBytes Size of the file in bytes; `0` for a directory.
 * @property lastModified Last-modified timestamp in epoch milliseconds.
 * @property isDirectory `true` when the entry is a directory.
 * @property isText `true` when the file's bytes decode as valid UTF-8 text and
 *   can therefore be read via
 *   [app.knotwork.android.domain.services.AgentWorkspace.readText]; `false` for
 *   binary files (which remain listable but not text-readable) and for
 *   directories.
 */
data class WorkspaceFile(
    val relativePath: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val isText: Boolean,
)
