package app.knotwork.android.domain.usecases.workspace

import app.knotwork.android.domain.models.WorkspaceResult
import app.knotwork.android.domain.services.AgentWorkspace
import java.io.OutputStream
import javax.inject.Inject

/**
 * Streams a workspace file out to a caller-provided sink, backing both the
 * "Save as…" (a `CreateDocument` destination) and "Share" (a `FileProvider`
 * staging file) actions on the Files screen.
 *
 * Unlike a preview or a text read, an export must reproduce the file in full and
 * verbatim, so it delegates to [AgentWorkspace.exportTo] which copies the raw
 * bytes with no size cap or text constraint. The caller owns [sink] and closes
 * it once this returns.
 *
 * @property workspace The agent's jailed file sandbox.
 */
class ExportWorkspaceFileUseCase @Inject constructor(private val workspace: AgentWorkspace) {
    /**
     * Copies the file at [relativePath] to [sink].
     *
     * @param relativePath Path of the file to export, relative to the workspace
     *   root.
     * @param sink Destination stream; written but not closed here.
     * @return [WorkspaceResult.Success] on completion, or a typed failure
     *   (missing file, or a path that escapes the sandbox).
     */
    suspend operator fun invoke(relativePath: String, sink: OutputStream): WorkspaceResult<Unit> =
        workspace.exportTo(relativePath = relativePath, sink = sink)
}
