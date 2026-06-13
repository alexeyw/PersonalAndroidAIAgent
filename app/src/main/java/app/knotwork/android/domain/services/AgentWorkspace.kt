package app.knotwork.android.domain.services

import app.knotwork.android.domain.models.WorkspaceError
import app.knotwork.android.domain.models.WorkspaceFile
import app.knotwork.android.domain.models.WorkspaceResult

/**
 * The agent's jailed file sandbox: a single, size-bounded directory the agent
 * may read from and write to, and from which it can never escape.
 *
 * This is the foundation every file tool builds on. The agent has no general
 * filesystem access — only this contract — so the entire trust boundary for
 * agent-driven file I/O is enforced here:
 *
 *  - **Containment.** Every path is resolved through [resolve], the single
 *    canonicalisation gate. A relative path that canonicalises outside the
 *    workspace root (`../` traversal, an absolute path, a symlink escaping the
 *    sandbox) is refused with [WorkspaceError.PathOutsideWorkspace] before any
 *    I/O reaches the target.
 *  - **Quotas.** Writes are checked against a per-file size limit
 *    ([WorkspaceError.TooLarge]) and a workspace-wide total-size limit
 *    ([WorkspaceError.QuotaExceeded]) so a looping pipeline cannot exhaust
 *    device storage.
 *  - **Text-only surface (for now).** Only UTF-8 text is read and written.
 *    Binary files remain visible in [list] but cannot be text-read
 *    ([WorkspaceError.NotAText]).
 *
 * All operations are `suspend` (the implementation performs blocking I/O on a
 * background dispatcher) and never throw for an expected, refusable condition:
 * they return a [WorkspaceResult.Failure] carrying a typed [WorkspaceError].
 *
 * Implementations are expected to be safe for concurrent use.
 */
interface AgentWorkspace {
    /**
     * Resolves a workspace-relative path to its metadata, enforcing the
     * containment boundary.
     *
     * This is the **single point of path validation**: every other method
     * funnels through the same canonicalisation. The target need not exist —
     * resolving a not-yet-created path (e.g. a future write target) succeeds as
     * long as it stays inside the workspace; a non-existent in-bounds path is
     * reported with [WorkspaceFile.sizeBytes] `0` and [WorkspaceFile.lastModified]
     * `0`.
     *
     * @param relativePath Path relative to the workspace root. A leading slash,
     *   an absolute path, or any `../` sequence that escapes the root after
     *   canonicalisation is refused.
     * @return [WorkspaceResult.Success] with the resolved [WorkspaceFile], or
     *   [WorkspaceResult.Failure] with [WorkspaceError.PathOutsideWorkspace].
     */
    suspend fun resolve(relativePath: String): WorkspaceResult<WorkspaceFile>

    /**
     * Reads a workspace file as UTF-8 text.
     *
     * @param relativePath Path of the file to read, relative to the workspace
     *   root.
     * @return [WorkspaceResult.Success] with the file's full text, or
     *   [WorkspaceResult.Failure] with [WorkspaceError.PathOutsideWorkspace]
     *   (escapes the sandbox), [WorkspaceError.NotFound] (no such file),
     *   [WorkspaceError.NotAText] (binary content) or [WorkspaceError.TooLarge]
     *   (exceeds the per-file size limit).
     */
    suspend fun readText(relativePath: String): WorkspaceResult<String>

    /**
     * Writes UTF-8 [content] to a workspace file, creating parent directories as
     * needed.
     *
     * Quotas are checked **before** any bytes are written: the new content must
     * fit the per-file size limit, and the resulting workspace total (accounting
     * for the size of any file being replaced) must fit the workspace-wide
     * limit.
     *
     * @param relativePath Path of the file to write, relative to the workspace
     *   root.
     * @param content Text to write, encoded as UTF-8.
     * @param overwrite When `false` (the default), writing over an existing file
     *   is refused with [WorkspaceError.AlreadyExists]; when `true`, the existing
     *   file is replaced.
     * @return [WorkspaceResult.Success] with the resulting [WorkspaceFile]
     *   metadata, or [WorkspaceResult.Failure] with
     *   [WorkspaceError.PathOutsideWorkspace], [WorkspaceError.AlreadyExists]
     *   (a file is already there and `overwrite` is `false`),
     *   [WorkspaceError.IsDirectory] (the path is a directory),
     *   [WorkspaceError.TooLarge] or [WorkspaceError.QuotaExceeded].
     */
    suspend fun writeText(
        relativePath: String,
        content: String,
        overwrite: Boolean = false,
    ): WorkspaceResult<WorkspaceFile>

    /**
     * Replaces the single occurrence of [oldText] with [newText] in an existing
     * workspace file, anchoring the edit on a uniquely-matching fragment.
     *
     * The whole read-modify-write is performed atomically under the workspace's
     * own lock so a concurrent operation can never observe (or interleave with)
     * a half-applied edit. The anchor must address exactly one fragment:
     *
     *  - It must occur **exactly once** in the file — zero matches is
     *    [WorkspaceError.AnchorNotFound], more than one is
     *    [WorkspaceError.AnchorNotUnique] (carrying the count so the caller can
     *    request a longer anchor). Matching is literal and non-overlapping.
     *  - An empty [newText] deletes the matched fragment.
     *
     * The rewritten content is subject to the same per-file and total-size
     * quotas as [writeText].
     *
     * @param relativePath Path of the file to edit, relative to the workspace
     *   root.
     * @param oldText The unique anchor fragment to replace. Must be non-empty.
     * @param newText The replacement text; may be empty to delete the fragment.
     * @return [WorkspaceResult.Success] with the resulting [WorkspaceFile]
     *   metadata, or [WorkspaceResult.Failure] with
     *   [WorkspaceError.PathOutsideWorkspace], [WorkspaceError.NotFound],
     *   [WorkspaceError.NotAText], [WorkspaceError.AnchorNotFound],
     *   [WorkspaceError.AnchorNotUnique], [WorkspaceError.TooLarge] or
     *   [WorkspaceError.QuotaExceeded].
     */
    suspend fun editText(relativePath: String, oldText: String, newText: String): WorkspaceResult<WorkspaceFile>

    /**
     * Deletes a regular file from the workspace.
     *
     * Deletion is irreversible, so this is the workspace's most destructive
     * operation; the file tool layered on top routes it through the strictest
     * Human-in-the-Loop confirmation path. Only regular files are deletable: a
     * path that resolves to a directory (or to nothing) is reported as
     * [WorkspaceError.NotFound], never silently traversed.
     *
     * @param relativePath Path of the file to delete, relative to the workspace
     *   root.
     * @return [WorkspaceResult.Success] with [Unit] when the file was removed,
     *   or [WorkspaceResult.Failure] with [WorkspaceError.PathOutsideWorkspace]
     *   (escapes the sandbox) or [WorkspaceError.NotFound] (no such file).
     */
    suspend fun delete(relativePath: String): WorkspaceResult<Unit>

    /**
     * Lists every file in the workspace, recursively.
     *
     * Returns a stable, path-sorted list of [WorkspaceFile] entries for the
     * regular files in the tree (directories are traversed but not emitted as
     * entries). Binary files are included with [WorkspaceFile.isText] `false`.
     * An empty (or not-yet-created) workspace yields an empty list.
     *
     * @return [WorkspaceResult.Success] with the listing. Listing the root never
     *   fails the containment check, so a [WorkspaceResult.Failure] only arises
     *   from an unexpected I/O fault.
     */
    suspend fun list(): WorkspaceResult<List<WorkspaceFile>>
}
