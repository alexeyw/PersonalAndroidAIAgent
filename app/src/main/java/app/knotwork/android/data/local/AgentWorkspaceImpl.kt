package app.knotwork.android.data.local

import android.content.Context
import app.knotwork.android.domain.models.WorkspaceError
import app.knotwork.android.domain.models.WorkspaceFile
import app.knotwork.android.domain.models.WorkspaceResult
import app.knotwork.android.domain.repositories.SettingsRepository
import app.knotwork.android.domain.services.AgentWorkspace
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

    override suspend fun list(): WorkspaceResult<List<WorkspaceFile>> = withContext(Dispatchers.IO) {
        val root = rootDir()
        val files = root.walkTopDown()
            .filter { it.isFile }
            .map { toWorkspaceFile(it.canonicalFile) }
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
        val rootPath = root.path
        val inside = target.path == rootPath || target.path.startsWith(rootPath + File.separator)
        return if (inside) {
            WorkspaceResult.Success(target)
        } else {
            WorkspaceResult.Failure(WorkspaceError.PathOutsideWorkspace)
        }
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
        if (target.isDirectory) return WorkspaceResult.Failure(WorkspaceError.AlreadyExists)
        val exists = target.isFile
        if (exists && !overwrite) return WorkspaceResult.Failure(WorkspaceError.AlreadyExists)

        val newBytes = content.toByteArray(Charsets.UTF_8)
        if (newBytes.size.toLong() > maxFileSizeBytes()) return WorkspaceResult.Failure(WorkspaceError.TooLarge)

        val existingSize = if (exists) target.length() else 0L
        val projectedTotal = currentTotalBytesLocked() - existingSize + newBytes.size
        if (projectedTotal > settingsRepository.workspaceMaxTotalBytes.first()) {
            return WorkspaceResult.Failure(WorkspaceError.QuotaExceeded)
        }

        target.parentFile?.mkdirs()
        target.writeBytes(newBytes)
        cachedTotalBytes = projectedTotal
        return WorkspaceResult.Success(toWorkspaceFile(target))
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
        val total = rootDir().walkTopDown().filter { it.isFile }.sumOf { it.length() }
        cachedTotalBytes = total
        return total
    }

    /** Locates the workspace root, creating it on first use, and canonicalises it. */
    private fun rootDir(): File {
        val dir = File(context.filesDir, WORKSPACE_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir.canonicalFile
    }

    /** Maps a canonical workspace [File] to its [WorkspaceFile] metadata snapshot. */
    private fun toWorkspaceFile(file: File): WorkspaceFile {
        val relativePath = file.toRelativeString(rootDir()).replace(File.separatorChar, '/')
        val isFile = file.isFile
        return WorkspaceFile(
            relativePath = relativePath,
            sizeBytes = if (isFile) file.length() else 0L,
            lastModified = file.lastModified(),
            isDirectory = file.isDirectory,
            isText = isFile && looksLikeText(file),
        )
    }

    /** Reads [file]'s bytes and reports whether they decode as UTF-8 text. */
    private fun looksLikeText(file: File): Boolean = try {
        isUtf8Text(file.readBytes())
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
    }
}
