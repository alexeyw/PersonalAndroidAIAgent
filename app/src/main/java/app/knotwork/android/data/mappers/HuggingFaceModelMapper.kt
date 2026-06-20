package app.knotwork.android.data.mappers

import app.knotwork.android.data.network.huggingface.HfModelDto
import app.knotwork.android.domain.constants.ModelDiscoveryConstants
import app.knotwork.android.domain.models.DiscoverableModelDetail
import app.knotwork.android.domain.models.DiscoverableModelFile
import app.knotwork.android.domain.models.DiscoverableModelSummary
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Pure mapping from the Hugging Face wire DTOs to the domain discovery models.
 *
 * Kept free of any I/O so the tag/gated/license parsing and the
 * `.litertlm`-file filtering are unit-testable in isolation; the repository
 * supplies the side-channel data the Hub does not (which files are already
 * installed locally).
 */
object HuggingFaceModelMapper {

    /** Tag prefix carrying the SPDX-ish license id (e.g. `"license:apache-2.0"`). */
    private const val LICENSE_TAG_PREFIX = "license:"

    /** String gated markers the Hub uses for an access-gated repository. */
    private val GATED_STRINGS = setOf("auto", "manual", "true")

    /**
     * Projects a list-endpoint DTO onto a [DiscoverableModelSummary]. The
     * `.litertlm` file count drives the upstream compatibility filter.
     */
    fun HfModelDto.toSummary(): DiscoverableModelSummary = DiscoverableModelSummary(
        repoId = id,
        displayName = displayNameOf(id),
        author = author ?: id.substringBefore(delimiter = '/'),
        downloads = downloads,
        likes = likes,
        license = parseLicense(),
        gated = parseGated(gated),
        litertFileCount = litertFiles().size,
        lastModifiedIso = lastModified,
    )

    /**
     * Projects a detail-endpoint DTO onto a [DiscoverableModelDetail],
     * resolving each `.litertlm` file's download URL and stamping the
     * caller-supplied installed flags.
     *
     * @param installedFileNames on-disk names already present locally.
     */
    fun HfModelDto.toDetail(installedFileNames: Set<String>): DiscoverableModelDetail = DiscoverableModelDetail(
        repoId = id,
        displayName = displayNameOf(id),
        author = author ?: id.substringBefore(delimiter = '/'),
        downloads = downloads,
        likes = likes,
        license = parseLicense(),
        gated = parseGated(gated),
        lastModifiedIso = lastModified,
        modelCardUrl = ModelDiscoveryConstants.modelCardUrl(id),
        files = litertFiles().map { sibling ->
            DiscoverableModelFile(
                fileName = sibling.rfilename,
                sizeBytes = sibling.size ?: 0L,
                resolveUrl = ModelDiscoveryConstants.resolveUrl(repoId = id, fileName = sibling.rfilename),
                isInstalled = sibling.rfilename in installedFileNames,
            )
        },
    )

    /** The repo's engine-compatible `.litertlm` files. */
    private fun HfModelDto.litertFiles() = siblings.filter {
        it.rfilename.endsWith(suffix = ModelDiscoveryConstants.LITERTLM_EXTENSION, ignoreCase = true)
    }

    /** Short display name: the repo id with its `author/` prefix stripped. */
    private fun displayNameOf(id: String): String = id.substringAfterLast(delimiter = '/')

    /**
     * Resolves the license id, preferring the model-card front-matter and
     * falling back to the `"license:"` tag.
     */
    private fun HfModelDto.parseLicense(): String? = cardData?.license?.takeIf { it.isNotBlank() }
        ?: tags.firstOrNull { it.startsWith(prefix = LICENSE_TAG_PREFIX) }
            ?.removePrefix(prefix = LICENSE_TAG_PREFIX)
            ?.takeIf { it.isNotBlank() }

    /**
     * Interprets the polymorphic `gated` field: boolean `false` ⇒ open;
     * string `"auto"`/`"manual"` (or boolean `true`) ⇒ gated.
     */
    private fun parseGated(element: JsonElement?): Boolean {
        val primitive = element as? JsonPrimitive ?: return false
        return if (primitive.isString) {
            primitive.content.lowercase() in GATED_STRINGS
        } else {
            primitive.content.toBooleanStrictOrNull() ?: false
        }
    }
}
