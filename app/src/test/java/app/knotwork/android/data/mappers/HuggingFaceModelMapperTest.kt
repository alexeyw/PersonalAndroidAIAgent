package app.knotwork.android.data.mappers

import app.knotwork.android.data.mappers.HuggingFaceModelMapper.toDetail
import app.knotwork.android.data.mappers.HuggingFaceModelMapper.toSummary
import app.knotwork.android.data.network.huggingface.HfCardDataDto
import app.knotwork.android.data.network.huggingface.HfModelDto
import app.knotwork.android.data.network.huggingface.HfSiblingDto
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [HuggingFaceModelMapper] — the pure DTO → domain projection
 * covering tag/license/gated parsing, `.litertlm` filtering, resolve-URL
 * construction and installed-flag stamping.
 */
class HuggingFaceModelMapperTest {

    private fun dto(
        id: String = "litert-community/gemma-4-E2B-it-litert-lm",
        author: String? = "litert-community",
        downloads: Int = 1200,
        likes: Int = 34,
        gated: Any? = false,
        tags: List<String> = listOf("litert-lm", "license:apache-2.0", "base_model:google/gemma"),
        siblings: List<HfSiblingDto> = listOf(
            HfSiblingDto(rfilename = ".gitattributes"),
            HfSiblingDto(rfilename = "gemma-4-E2B-it.litertlm", size = 2_588_147_712L),
            HfSiblingDto(rfilename = "gemma-4-E2B-it-gpu.litertlm", size = 3_000_000_000L),
        ),
        cardData: HfCardDataDto? = null,
        lastModified: String? = "2026-05-01T12:00:00.000Z",
    ): HfModelDto = HfModelDto(
        id = id,
        author = author,
        downloads = downloads,
        likes = likes,
        gated = when (gated) {
            null -> null
            is Boolean -> JsonPrimitive(gated)
            else -> JsonPrimitive(gated.toString())
        },
        tags = tags,
        lastModified = lastModified,
        siblings = siblings,
        cardData = cardData,
    )

    @Test
    fun `given list dto when toSummary then maps fields and counts litertlm files`() {
        val summary = dto().toSummary()

        assertEquals("litert-community/gemma-4-E2B-it-litert-lm", summary.repoId)
        assertEquals("gemma-4-E2B-it-litert-lm", summary.displayName)
        assertEquals("litert-community", summary.author)
        assertEquals(1200, summary.downloads)
        assertEquals(34, summary.likes)
        assertEquals("apache-2.0", summary.license)
        assertFalse(summary.gated)
        assertEquals(2, summary.litertFileCount)
        assertEquals("2026-05-01T12:00:00.000Z", summary.lastModifiedIso)
    }

    @Test
    fun `given card data license when toSummary then prefers card data over tag`() {
        val summary = dto(
            tags = listOf("license:mit"),
            cardData = HfCardDataDto(license = "apache-2.0"),
        ).toSummary()

        assertEquals("apache-2.0", summary.license)
    }

    @Test
    fun `given no license metadata when toSummary then license is null`() {
        val summary = dto(tags = listOf("litert-lm"), cardData = null).toSummary()

        assertNull(summary.license)
    }

    @Test
    fun `given string gated marker when toSummary then gated is true`() {
        assertTrue(dto(gated = "auto").toSummary().gated)
        assertTrue(dto(gated = "manual").toSummary().gated)
    }

    @Test
    fun `given boolean true gated when toSummary then gated is true`() {
        assertTrue(dto(gated = true).toSummary().gated)
    }

    @Test
    fun `given null gated when toSummary then gated is false`() {
        assertFalse(dto(gated = null).toSummary().gated)
    }

    @Test
    fun `given missing author when toSummary then derives author from repo id`() {
        val summary = dto(author = null).toSummary()

        assertEquals("litert-community", summary.author)
    }

    @Test
    fun `given detail dto when toDetail then builds files with resolve urls and installed flags`() {
        val detail = dto().toDetail(installedFileNames = setOf("gemma-4-E2B-it-gpu.litertlm"))

        assertEquals(2, detail.files.size)
        assertEquals("https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm", detail.modelCardUrl)

        val cpu = detail.files.first { it.fileName == "gemma-4-E2B-it.litertlm" }
        assertEquals(2_588_147_712L, cpu.sizeBytes)
        assertEquals(
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            cpu.resolveUrl,
        )
        assertFalse(cpu.isInstalled)

        val gpu = detail.files.first { it.fileName == "gemma-4-E2B-it-gpu.litertlm" }
        assertTrue(gpu.isInstalled)
    }

    @Test
    fun `given sibling without size when toDetail then size defaults to zero`() {
        val detail = dto(
            siblings = listOf(HfSiblingDto(rfilename = "model.litertlm", size = null)),
        ).toDetail(installedFileNames = emptySet())

        assertEquals(0L, detail.files.single().sizeBytes)
    }
}
