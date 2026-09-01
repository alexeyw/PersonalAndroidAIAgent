package app.knotwork.android.buildtools

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.VerificationException

/**
 * Fails the build when a KDoc block documents no declaration.
 *
 * A typed task rather than an ad-hoc `doLast`, for the two reasons the file-map
 * pair records: a script lambda capturing build-script state cannot be stored in
 * the configuration cache, and a verification task with no declared output can
 * never be treated as up to date, so it re-runs on every `check` whether or not
 * a single source file changed. The detection itself lives in
 * [OrphanedKdocChecker], which is pure and unit-tested.
 *
 * @property repositoryRoot Root the reported paths are made relative to, so a
 *   failure message is the same on every machine.
 * @property sources Kotlin files to scan.
 * @property stampFile Written on success, so the task can be skipped while
 *   nothing it reads has changed.
 */
@CacheableTask
abstract class VerifyNoOrphanedKdocTask : DefaultTask() {

    @get:Internal
    abstract val repositoryRoot: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:OutputFile
    abstract val stampFile: RegularFileProperty

    /** Scans every source file and fails on the first crop of orphans. */
    @TaskAction
    fun verify() {
        val root = repositoryRoot.get().asFile
        val files = sources.files
            .filter { it.isFile }
            .associate { it.relativeTo(root).invariantSeparatorsPath to it.readText() }
        val violations = OrphanedKdocChecker.scan(files)
        if (violations.isNotEmpty()) {
            throw VerificationException(
                "KDoc blocks that document nothing:\n" +
                    violations.joinToString("\n") { "  $it" } + "\n" +
                    "A declaration was almost certainly inserted between a doc block and the declaration " +
                    "it describes. Move the block down to the declaration it belongs to.",
            )
        }
        val stamp = stampFile.get().asFile
        stamp.parentFile.mkdirs()
        stamp.writeText("${files.size} files scanned, no orphaned KDoc\n")
    }
}
