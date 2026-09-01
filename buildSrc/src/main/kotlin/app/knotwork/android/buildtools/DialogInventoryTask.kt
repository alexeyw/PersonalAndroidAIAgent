package app.knotwork.android.buildtools

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.VerificationException

/**
 * Fails the build when a dialog or sheet is composed in `:app` without an entry
 * saying why.
 *
 * A typed task rather than an ad-hoc `doLast`, for the reasons the file-map pair
 * records: a script lambda capturing build-script state cannot be stored in the
 * configuration cache, and a verification task with no declared output re-runs
 * on every `check`. The detection lives in [DialogInventoryChecker], which is
 * pure and unit-tested.
 *
 * @property repositoryRoot Root the reported paths are made relative to, so a
 *   failure message reads the same on every machine.
 * @property sources Kotlin files to scan.
 * @property allowed Repository-relative path to the reason it is exempt. The
 *   reason is an input, so editing one busts the cache — a wrong reason should
 *   not survive because the paths happened not to change.
 * @property stampFile Written on success, so the task can be skipped while
 *   nothing it reads has changed.
 */
@CacheableTask
abstract class VerifyDialogInventoryTask : DefaultTask() {

    @get:Internal
    abstract val repositoryRoot: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Input
    abstract val allowed: MapProperty<String, String>

    @get:OutputFile
    abstract val stampFile: RegularFileProperty

    /** Scans the sources and fails on anything the allowlist does not answer. */
    @TaskAction
    fun verify() {
        val root = repositoryRoot.get().asFile
        val files = sources.files
            .filter { it.isFile }
            .associate { it.relativeTo(root).invariantSeparatorsPath to it.readText() }
        val exemptions = allowed.get()

        val violations = DialogInventoryChecker.scan(files, exemptions)
        if (violations.isNotEmpty()) {
            throw VerificationException(
                "Dialogs and sheets composed in :app, where no baseline can reach them:\n" +
                    violations.joinToString("\n") { "  $it" } + "\n" +
                    "Move the body into :catalog and keep only the host here — or, if this file is " +
                    "already a host, add it to `dialogInventoryAllowlist` with the reason.",
            )
        }

        val stale = DialogInventoryChecker.staleEntries(files, exemptions)
        if (stale.isNotEmpty()) {
            throw VerificationException(
                "These `dialogInventoryAllowlist` entries no longer hold a dialog:\n" +
                    stale.joinToString("\n") { "  $it" } + "\n" +
                    "Remove them. An allowlist that outlives what it excused stops being a record " +
                    "of decisions and becomes a list nobody reads.",
            )
        }

        val stamp = stampFile.get().asFile
        stamp.parentFile.mkdirs()
        stamp.writeText("${files.size} files scanned, ${exemptions.size} hosts allowed\n")
    }
}
