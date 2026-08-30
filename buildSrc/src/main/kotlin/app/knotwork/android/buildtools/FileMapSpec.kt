package app.knotwork.android.buildtools

import java.io.Serializable

/**
 * One generated block of one `FILE_MAP.md`.
 *
 * A map may own more than one block — the application map carries both its main
 * package and the non-`main` source sets — so the block, not the file, is the
 * unit of generation.
 *
 * Paths are stored relative to the repository root rather than absolute, so the
 * value is a stable task input: an absolute path would make the up-to-date
 * check depend on where the checkout happens to live.
 *
 * @property mapPath Path of the `FILE_MAP.md`, relative to the repository root.
 * @property blockId Identifier of the `AUTO-GEN` block inside that file.
 * @property roots Source roots whose Kotlin files this block renders.
 * @property baselineKey Key this block's counts are ratcheted under in the
 *   baseline file.
 */
data class FileMapSpec(
    val mapPath: String,
    val blockId: String,
    val roots: List<Root>,
    val baselineKey: String,
) : Serializable {

    /**
     * One source root, and the path prefix its files are rendered under.
     *
     * The prefix exists because a source set's directory layout and its
     * *meaning* differ: every flavour keeps its sources under
     * `<flavour>/java/app/knotwork/android/…`, and rendering that literally
     * would bury two files under seven levels of package directory that carry
     * no information. The prefix names the source set and the rest of the path
     * is the package-relative one a reader recognises.
     *
     * @property dir Directory to walk, relative to the repository root.
     * @property prefix Rendered ahead of each file's path relative to [dir];
     *   empty for a block with a single root.
     */
    data class Root(val dir: String, val prefix: String = "") : Serializable {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
