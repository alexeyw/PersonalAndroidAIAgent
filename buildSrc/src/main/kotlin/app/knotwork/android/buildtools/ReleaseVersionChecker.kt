package app.knotwork.android.buildtools

/**
 * Pure checker that ties a release git tag to the version the build actually
 * declares.
 *
 * A release is cut by pushing a `v<x>.<y>.<z>` tag, and everything downstream —
 * the artefact file names, the GitHub Release title, the version users quote in
 * bug reports — is derived from that tag. The version compiled *into* the APK,
 * however, comes from `versionName` in `app/build.gradle.kts`. Nothing forces
 * the two to agree, and the failure is silent in both directions: a tag ahead of
 * `versionName` ships an artefact called `0.7.0` whose About screen says
 * `0.6.0`, and a tag behind it overwrites a published version number with
 * different bytes.
 *
 * `versionName` stays the single source of truth for the build itself — the
 * F-Droid recipe builds a tag with no Gradle properties injected and must still
 * get the right number — so this checker does not rewrite it. It asserts the
 * agreement and fails the release when it does not hold.
 *
 * The checker is a pure `(String, String) -> String?` transform with no Gradle
 * or file-system access, so it is trivially unit-testable; the
 * `verifyReleaseVersion` task in `app/build.gradle.kts` supplies both values.
 */
object ReleaseVersionChecker {

    /**
     * Release tag shape: a `v`-prefixed three-component version with an optional
     * pre-release suffix (`v0.7.0`, `v1.2.3-rc1`).
     *
     * Deliberately strict. A lenient pattern would accept `v0.7` or `0.7.0` and
     * then quietly compare a value the rest of the pipeline cannot name a file
     * after; the tag is the release's identity, so a malformed one is a failure,
     * not something to normalise.
     */
    private val TAG_PATTERN = Regex("""^v(\d+\.\d+\.\d+(?:-[0-9A-Za-z][0-9A-Za-z.-]*)?)$""")

    /**
     * Strips the `v` prefix from a well-formed release tag.
     *
     * @param tag The git tag, e.g. `v0.7.0`.
     * @return The bare version name (`0.7.0`), or `null` when [tag] does not
     *   match the release-tag shape.
     */
    fun versionNameFromTag(tag: String): String? = TAG_PATTERN.matchEntire(tag.trim())?.groupValues?.get(1)

    /**
     * Verifies that [tag] is a well-formed release tag naming exactly the
     * version the build declares.
     *
     * @param tag The git tag the release is being cut from, e.g. `v0.7.0`.
     * @param declaredVersionName The `versionName` from the app's
     *   `defaultConfig`, e.g. `0.7.0`.
     * @return `null` when the tag is well-formed and the two versions agree;
     *   otherwise a complete, actionable failure message naming both values and
     *   the file to edit.
     */
    fun verify(tag: String, declaredVersionName: String): String? {
        val expected = versionNameFromTag(tag)
            ?: return "Release tag `$tag` is not a valid release tag. " +
                "Expected `v<major>.<minor>.<patch>` with an optional pre-release suffix, e.g. `v0.7.0` or `v0.7.0-rc1`."
        val declared = declaredVersionName.trim()
        if (declared.isEmpty()) {
            return "The build declares no `versionName`, so tag `$tag` cannot be verified against it. " +
                "Set `android.defaultConfig.versionName` in `app/build.gradle.kts`."
        }
        if (declared != expected) {
            return "Release tag `$tag` expects versionName `$expected`, but the build declares `$declared`. " +
                "Bump `android.defaultConfig.versionName` (and `versionCode`) in `app/build.gradle.kts` " +
                "before tagging, or re-tag to match the declared version."
        }
        return null
    }
}
