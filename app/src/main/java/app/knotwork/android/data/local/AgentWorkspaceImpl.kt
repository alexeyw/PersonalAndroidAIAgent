package app.knotwork.android.data.local

import android.content.Context
import app.knotwork.android.domain.models.WorkspaceError
import app.knotwork.android.domain.models.WorkspaceFile
import app.knotwork.android.domain.models.WorkspaceResult
import app.knotwork.android.domain.repositories.SettingsRepository
import app.knotwork.android.domain.services.AgentWorkspace
import app.knotwork.android.domain.services.WorkspaceTextEdit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Filesystem-backed [AgentWorkspace] rooted at `files/agent_workspace/` inside
 * the app's private storage ([Context.filesDir]).
 *
 * The directory is created lazily on first access. All blocking I/O runs on
 * [Dispatchers.IO]. Quota limits are read fresh from [SettingsRepository] on
 * each write so a settings change takes effect immediately.
 *
 * **Containment** is enforced in exactly one place — [canonicalResolve] — which
 * every public method funnels through. Path canonicalisation collapses `..`
 * segments and resolves symlinks, and the result is accepted only if it stays
 * at or under the canonicalised root (compared with a trailing [File.separator]
 * so a sibling directory such as `agent_workspace_evil` cannot pass the prefix
 * check). This is the project's path-traversal mitigation, per the official
 * Android guidance.
 *
 * **Concurrency.** Mutating operations serialise on [mutex], which also guards
 * the cached total-size counter ([cachedTotalBytes]). The cache is valid because
 * the workspace is the only writer of its directory; it is recomputed by walking
 * the tree whenever it is unset and updated in place after each write.
 *
 * @property context Application context, used solely to locate [Context.filesDir].
 * @property settingsRepository Source of the per-file and total-size quotas.
 */
@Singleton
class AgentWorkspaceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) : AgentWorkspace {

    private val mutex = Mutex()

    @Volatile
    private var cachedTotalBytes: Long? = null

    override suspend fun resolve(relativePath: String): WorkspaceResult<WorkspaceFile> = withContext(Dispatchers.IO) {
        when (val resolved = canonicalResolve(relativePath)) {
            is WorkspaceResult.Failure -> resolved
            is WorkspaceResult.Success -> WorkspaceResult.Success(toWorkspaceFile(resolved.value))
        }
    }

    override suspend fun readText(relativePath: String): WorkspaceResult<String> = withContext(Dispatchers.IO) {
        when (val resolved = canonicalResolve(relativePath)) {
            is WorkspaceResult.Failure -> resolved
            is WorkspaceResult.Success -> readTextResolved(resolved.value)
        }
    }

    override suspend fun writeText(
        relativePath: String,
        content: String,
        overwrite: Boolean,
    ): WorkspaceResult<WorkspaceFile> = withContext(Dispatchers.IO) {
        when (val resolved = canonicalResolve(relativePath)) {
            is WorkspaceResult.Failure -> resolved
            is WorkspaceResult.Success -> mutex.withLock { writeTextLocked(resolved.value, content, overwrite) }
        }
    }

    override suspend fun editText(
        relativePath: String,
        oldText: String,
        newText: String,
    ): WorkspaceResult<WorkspaceFile> = withContext(Dispatchers.IO) {
        when (val resolved = canonicalResolve(relativePath)) {
            is WorkspaceResult.Failure -> resolved
            is WorkspaceResult.Success -> mutex.withLock { editTextLocked(resolved.value, oldText, newText) }
        }
    }

    override suspend fun delete(relativePath: String): WorkspaceResult<Unit> = withContext(Dispatchers.IO) {
        when (val resolved = canonicalResolve(relativePath)) {
            is WorkspaceResult.Failure -> resolved
            is WorkspaceResult.Success -> mutex.withLock { deleteLocked(resolved.value) }
        }
    }

    override suspend fun list(): WorkspaceResult<List<WorkspaceFile>> = withContext(Dispatchers.IO) {
        val root = rootDir()
        // `walkTopDown` follows directory symlinks, so canonicalise each entry and drop
        // any whose real path escapes the workspace — the same containment rule as the
        // resolve gate, so a symlink pointing out can never appear in a listing. Transient
        // atomic-write scratch files are filtered too so a crashed write's leftover never
        // surfaces to the agent.
        val files = root.walkTopDown()
            .filter { it.isFile && !isScratchFile(it) }
            .map { it.canonicalFile }
            .filter { isInsideRoot(it, root) }
            .map { toWorkspaceFile(it, root) }
            .sortedBy { it.relativePath }
            .toList()
        WorkspaceResult.Success(files)
    }

    /**
     * The single canonicalisation gate. Rejects absolute paths outright, then
     * canonicalises `root/relativePath` and accepts it only if it stays inside
     * the canonical root.
     *
     * @param relativePath The caller-supplied workspace-relative path.
     * @return [WorkspaceResult.Success] with the canonical [File] (which may not
     *   yet exist), or [WorkspaceResult.Failure] with
     *   [WorkspaceError.PathOutsideWorkspace].
     */
    private fun canonicalResolve(relativePath: String): WorkspaceResult<File> {
        if (File(relativePath).isAbsolute) {
            return WorkspaceResult.Failure(WorkspaceError.PathOutsideWorkspace)
        }
        val root = rootDir()
        val target = File(root, relativePath).canonicalFile
        if (!isInsideRoot(target, root)) {
            return WorkspaceResult.Failure(WorkspaceError.PathOutsideWorkspace)
        }
        // Reject the reserved atomic-write scratch suffix: such files are hidden from
        // listings and excluded from quota accounting, so letting the agent address one
        // would let it write storage the quota never counts (and that it could never see
        // or clean up). Reported as NotFound so the artifact stays invisible.
        if (isScratchFile(target)) {
            return WorkspaceResult.Failure(WorkspaceError.NotFound)
        }
        return WorkspaceResult.Success(target)
    }

    /**
     * Containment predicate shared by [canonicalResolve] and [list]: a
     * canonicalised path is in-bounds when it is the root itself or sits under
     * it. The trailing [File.separator] stops a sibling directory such as
     * `agent_workspace_evil` from passing the prefix check.
     *
     * @param canonical An already-canonicalised candidate path.
     * @param root The canonicalised workspace root.
     */
    private fun isInsideRoot(canonical: File, root: File): Boolean {
        val rootPath = root.path
        return canonical.path == rootPath || canonical.path.startsWith(rootPath + File.separator)
    }

    /**
     * Reads an already-resolved (in-bounds) [target] as UTF-8 text, enforcing
     * existence, the per-file size cap and text-ness.
     */
    private suspend fun readTextResolved(target: File): WorkspaceResult<String> = when {
        !target.isFile -> WorkspaceResult.Failure(WorkspaceError.NotFound)
        target.length() > maxFileSizeBytes() -> WorkspaceResult.Failure(WorkspaceError.TooLarge)
        else -> {
            val bytes = target.readBytes()
            if (isUtf8Text(bytes)) {
                WorkspaceResult.Success(bytes.toString(Charsets.UTF_8))
            } else {
                WorkspaceResult.Failure(WorkspaceError.NotAText)
            }
        }
    }

    /**
     * Performs the quota-checked write of an already-resolved (in-bounds)
     * [target]. Must be called while holding [mutex].
     */
    private suspend fun writeTextLocked(
        target: File,
        content: String,
        overwrite: Boolean,
    ): WorkspaceResult<WorkspaceFile> {
        // A directory can never be replaced by a file, even with `overwrite`. Report it
        // distinctly so the tool does not hand back a "retry with overwrite" hint that
        // would loop forever (the overwrite flag is checked only for an existing file).
        if (target.isDirectory) return WorkspaceResult.Failure(WorkspaceError.IsDirectory)
        val exists = target.isFile
        if (exists && !overwrite) return WorkspaceResult.Failure(WorkspaceError.AlreadyExists)

        val newBytes = content.toByteArray(Charsets.UTF_8)
        if (newBytes.size.toLong() > maxFileSizeBytes()) return WorkspaceResult.Failure(WorkspaceError.TooLarge)

        val existingSize = if (exists) target.length() else 0L
        val projectedTotal = currentTotalBytesLocked() - existingSize + newBytes.size
        if (projectedTotal > settingsRepository.workspaceMaxTotalBytes.first()) {
            return WorkspaceResult.Failure(WorkspaceError.QuotaExceeded)
        }

        // Invalidate the cache before the risky write: if the write throws partway
        // (disk full, parent turned into a file) the next read recomputes from disk
        // instead of trusting a stale total. The cache is set only on full success.
        cachedTotalBytes = null
        target.parentFile?.mkdirs()
        writeAtomically(target, newBytes)
        cachedTotalBytes = projectedTotal
        return WorkspaceResult.Success(toWorkspaceFile(target))
    }

    /**
     * Writes [bytes] to [target] atomically: the content is staged into a
     * sibling scratch file and then [renamed][File.renameTo] onto [target].
     * Because both paths sit in the same directory (one filesystem), the rename
     * is a single atomic replace — a crash at any point leaves [target] either
     * fully old or fully new, never half-written, and the `finally` clause drops
     * any partial scratch file so no garbage is left behind.
     *
     * @param target The destination file (may or may not already exist).
     * @param bytes The full content to write.
     * @throws IOException When staging or the rename fails.
     */
    private fun writeAtomically(target: File, bytes: ByteArray) {
        val scratch = File(target.parentFile, target.name + RESERVED_TMP_SUFFIX)
        try {
            scratch.writeBytes(bytes)
            if (!scratch.renameTo(target)) {
                throw IOException("Atomic rename failed for ${target.path}")
            }
        } finally {
            // On success the scratch no longer exists (it became [target]); on any
            // failure this removes the partial stage so a crash never leaves a stray file.
            if (scratch.exists()) scratch.delete()
        }
    }

    /**
     * Performs the anchored find-replace edit of an already-resolved (in-bounds)
     * [target]. Must be called while holding [mutex] so the read and the rewrite
     * cannot be interleaved with another mutation.
     */
    private suspend fun editTextLocked(
        target: File,
        oldText: String,
        newText: String,
    ): WorkspaceResult<WorkspaceFile> {
        val content = when (val read = readTextResolved(target)) {
            is WorkspaceResult.Failure -> return read
            is WorkspaceResult.Success -> read.value
        }
        return when (val outcome = WorkspaceTextEdit.apply(content, oldText, newText)) {
            WorkspaceTextEdit.Outcome.AnchorNotFound -> WorkspaceResult.Failure(WorkspaceError.AnchorNotFound)
            is WorkspaceTextEdit.Outcome.AnchorNotUnique ->
                WorkspaceResult.Failure(WorkspaceError.AnchorNotUnique(outcome.count))
            // Re-route through the quota-checked atomic write so an edit cannot
            // grow the workspace past its limits and is applied in one replace.
            is WorkspaceTextEdit.Outcome.Replaced ->
                writeTextLocked(target, outcome.newContent, overwrite = true)
        }
    }

    /**
     * Deletes an already-resolved (in-bounds) [target] if it is a regular file,
     * keeping the cached total-size counter in step. Must be called while
     * holding [mutex].
     */
    private fun deleteLocked(target: File): WorkspaceResult<Unit> {
        if (!target.isFile) return WorkspaceResult.Failure(WorkspaceError.NotFound)
        val size = target.length()
        val previousTotal = currentTotalBytesLocked()
        // Invalidate before the mutation: if delete reports failure the next read
        // recomputes from disk rather than trusting a counter that may be wrong.
        cachedTotalBytes = null
        if (!target.delete() || target.exists()) {
            return WorkspaceResult.Failure(WorkspaceError.NotFound)
        }
        cachedTotalBytes = previousTotal - size
        return WorkspaceResult.Success(Unit)
    }

    /** Returns the per-file size ceiling from settings. */
    private suspend fun maxFileSizeBytes(): Long = settingsRepository.workspaceMaxFileSizeBytes.first()

    /**
     * Returns the workspace's total occupied bytes, using the cached value when
     * present and otherwise walking the tree. Must be called while holding
     * [mutex].
     */
    private fun currentTotalBytesLocked(): Long {
        cachedTotalBytes?.let { return it }
        val total = rootDir().walkTopDown()
            .filter { it.isFile && !isScratchFile(it) }
            .sumOf { it.length() }
        cachedTotalBytes = total
        return total
    }

    /**
     * Reports whether [file] is a transient atomic-write scratch file. Such a
     * file is excluded from listings and quota accounting because it only ever
     * exists for the brief window between staging and rename (or as the residue
     * of a crashed write, which the next write cleans up).
     */
    private fun isScratchFile(file: File): Boolean = file.name.endsWith(RESERVED_TMP_SUFFIX)

    /** Locates the workspace root, creating it on first use, and canonicalises it. */
    private fun rootDir(): File {
        val dir = File(context.filesDir, WORKSPACE_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir.canonicalFile
    }

    /** Maps a canonical workspace [File] to its [WorkspaceFile] metadata snapshot. */
    private fun toWorkspaceFile(file: File, root: File = rootDir()): WorkspaceFile {
        val relativePath = file.toRelativeString(root).replace(File.separatorChar, '/')
        val isFile = file.isFile
        return WorkspaceFile(
            relativePath = relativePath,
            sizeBytes = if (isFile) file.length() else 0L,
            lastModified = file.lastModified(),
            isDirectory = file.isDirectory,
            isText = isFile && looksLikeText(file),
        )
    }

    /**
     * Reports whether [file] is text by sniffing only a bounded prefix — listing
     * and resolving must not pull whole files into memory just to set the
     * advisory `isText` flag (a full UTF-8 validation still happens in [readText],
     * which is the authoritative path). If the sniff buffer fills, the last few
     * bytes are dropped so a multi-byte UTF-8 sequence straddling the boundary is
     * not misread as malformed.
     */
    private fun looksLikeText(file: File): Boolean = try {
        file.inputStream().use { input ->
            val buffer = ByteArray(TEXT_SNIFF_BYTES)
            val read = input.read(buffer)
            if (read <= 0) {
                true // empty file counts as text
            } else {
                val usable = if (read == TEXT_SNIFF_BYTES) read - MAX_UTF8_TAIL_BYTES else read
                isUtf8Text(buffer.copyOf(usable))
            }
        }
    } catch (e: IOException) {
        // An unreadable file is treated as non-text rather than crashing a listing.
        Timber.w(e, "Failed to sample file for text detection: %s", file.path)
        false
    }

    /**
     * Decides whether [bytes] is UTF-8 text: empty content counts as text, a NUL
     * byte marks it binary, and otherwise a strict (report-on-error) UTF-8 decode
     * must succeed.
     */
    private fun isUtf8Text(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return true
        if (bytes.any { it == 0.toByte() }) return false
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes))
            true
        } catch (e: CharacterCodingException) {
            false
        }
    }

    private companion object {
        /** Name of the workspace directory inside [Context.filesDir]. */
        const val WORKSPACE_DIR_NAME = "agent_workspace"

        /**
         * Suffix of the sibling scratch file used to stage an atomic write before
         * the rename. Reserved: files ending in it are hidden from listings and
         * quota accounting, so the agent is never expected to create one itself.
         */
        const val RESERVED_TMP_SUFFIX = ".knotwork-tmp"

        /** Number of leading bytes sampled to classify a file as text or binary. */
        const val TEXT_SNIFF_BYTES = 8 * 1024

        /**
         * Maximum length of a UTF-8 code point. Dropped from the tail of a filled
         * sniff buffer so a sequence split across the boundary is not misjudged.
         */
        const val MAX_UTF8_TAIL_BYTES = 3
    }
}
