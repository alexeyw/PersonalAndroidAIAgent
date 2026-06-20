package app.knotwork.android.domain.models

/**
 * A model repository discovered on the Hugging Face Hub, projected for the
 * Discover **list** surface. Carries only the lightweight metadata the list
 * endpoint returns — the per-file breakdown (with sizes) is fetched on demand
 * into a [DiscoverableModelDetail] when the user opens the card.
 *
 * @property repoId fully-qualified repository id (`"litert-community/gemma-…"`);
 *   stable list key and the handle used to fetch the detail.
 * @property displayName short name shown on the card (the repo id with the
 *   `author/` prefix stripped).
 * @property author owning organisation (`"litert-community"`).
 * @property downloads all-time download count reported by the Hub (0 when the
 *   field is absent).
 * @property likes like count reported by the Hub.
 * @property license SPDX-ish license id parsed from the repo metadata
 *   (e.g. `"apache-2.0"`), or `null` when the Hub exposes none.
 * @property gated `true` when the repo is access-gated (the Hub reports
 *   `"auto"`/`"manual"`); installing it needs an accepted licence on the Hub
 *   plus a personal access token.
 * @property litertFileCount number of engine-compatible `.litertlm` files in
 *   the repo. A repo with zero such files is filtered out before it reaches
 *   this model, so this is always `>= 1` in practice.
 * @property lastModifiedIso raw ISO-8601 last-modified timestamp from the Hub,
 *   or `null`; the presentation layer formats it for display.
 */
data class DiscoverableModelSummary(
    val repoId: String,
    val displayName: String,
    val author: String,
    val downloads: Int,
    val likes: Int,
    val license: String?,
    val gated: Boolean,
    val litertFileCount: Int,
    val lastModifiedIso: String?,
)

/**
 * One installable `.litertlm` file inside a discovered repository.
 *
 * @property fileName on-disk file name written by the download manager and
 *   persisted as [LocalModel.name] (e.g. `"gemma-4-E2B-it.litertlm"`).
 * @property sizeBytes file size in bytes reported by the Hub blob metadata
 *   (0 when unknown); stored verbatim onto the [LocalModel] so the Models
 *   screen can show a real size without a follow-up disk stat.
 * @property resolveUrl direct download URL streamed by the download manager.
 * @property isInstalled `true` when a model with this [fileName] already
 *   exists locally — the detail screen renders it as "Installed" and disables
 *   the install action.
 */
data class DiscoverableModelFile(
    val fileName: String,
    val sizeBytes: Long,
    val resolveUrl: String,
    val isInstalled: Boolean,
)

/**
 * Full detail of a discovered repository, fetched when the user opens a card.
 * Adds the per-file breakdown (with sizes and installed flags) and the
 * model-card link to the lightweight [DiscoverableModelSummary] fields.
 *
 * @property repoId fully-qualified repository id (`"author/name"`).
 * @property displayName short name (repo id without the `author/` prefix).
 * @property author owning organisation.
 * @property downloads all-time download count.
 * @property likes like count.
 * @property license parsed license id, or `null`.
 * @property gated `true` when the repo is access-gated.
 * @property lastModifiedIso raw ISO-8601 last-modified timestamp, or `null`.
 * @property modelCardUrl human-facing model-card URL on the Hub.
 * @property files the repo's engine-compatible `.litertlm` files (never empty;
 *   a repo with no such files is filtered out upstream).
 */
data class DiscoverableModelDetail(
    val repoId: String,
    val displayName: String,
    val author: String,
    val downloads: Int,
    val likes: Int,
    val license: String?,
    val gated: Boolean,
    val lastModifiedIso: String?,
    val modelCardUrl: String,
    val files: List<DiscoverableModelFile>,
)
