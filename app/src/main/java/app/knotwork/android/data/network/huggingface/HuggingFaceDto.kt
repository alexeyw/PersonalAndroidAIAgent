package app.knotwork.android.data.network.huggingface

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Wire model for a single repository returned by the Hugging Face Hub
 * `GET /api/models` (list) and `GET /api/models/{repoId}` (detail) endpoints.
 *
 * The two endpoints return a superset of the same object, so one DTO covers
 * both: list responses omit per-file [HfSiblingDto.size] and [cardData]; the
 * detail response (requested with `blobs=true`) fills them in. Unknown fields
 * are ignored by the decoder, so this lists only what the mapper consumes.
 *
 * @property id fully-qualified repository id (`"author/name"`).
 * @property author owning organisation; may be absent (then derived from [id]).
 * @property downloads all-time download count.
 * @property likes like count.
 * @property gated raw gated marker — the Hub reports a boolean `false` or a
 *   string `"auto"`/`"manual"`, so it is decoded as an opaque [JsonElement]
 *   and interpreted by the mapper.
 * @property tags free-form tag list (carries `"license:…"`, `"litert-lm"`,
 *   `"base_model:…"`).
 * @property lastModified ISO-8601 last-modified timestamp.
 * @property siblings repository files; `size` is populated only on the detail
 *   endpoint.
 * @property cardData parsed model-card front-matter; present only on the
 *   detail endpoint.
 */
@Serializable
data class HfModelDto(
    val id: String,
    val author: String? = null,
    val downloads: Int = 0,
    val likes: Int = 0,
    val gated: JsonElement? = null,
    val tags: List<String> = emptyList(),
    val lastModified: String? = null,
    val siblings: List<HfSiblingDto> = emptyList(),
    val cardData: HfCardDataDto? = null,
)

/**
 * One file inside a repository.
 *
 * @property rfilename repo-relative file name (e.g. `"gemma-4-E2B-it.litertlm"`).
 * @property size file size in bytes; present only when the model is fetched
 *   with `blobs=true`, `null` otherwise.
 */
@Serializable
data class HfSiblingDto(val rfilename: String, val size: Long? = null)

/**
 * Parsed subset of a repository's model-card front-matter.
 *
 * @property license SPDX-ish license id (e.g. `"apache-2.0"`), or `null`.
 */
@Serializable
data class HfCardDataDto(val license: String? = null)
