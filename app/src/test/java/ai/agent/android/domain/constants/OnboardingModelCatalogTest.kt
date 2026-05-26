package ai.agent.android.domain.constants

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [OnboardingModelCatalog].
 *
 * The catalog itself is two preset entries plus a small URL-parsing
 * helper for the `Custom URL…` row. The tests here pin down the
 * helper's edge cases — the presets are exercised end-to-end via
 * [OnboardingViewModelTest].
 */
class OnboardingModelCatalogTest {

    @Test
    fun `fileNameForCustomUrl returns last path segment with extension preserved`() {
        val result = OnboardingModelCatalog.fileNameForCustomUrl(
            url = "https://example.com/models/foo-1.litertlm",
        )

        assertEquals("foo-1.litertlm", result)
    }

    @Test
    fun `fileNameForCustomUrl appends litertlm suffix when missing`() {
        val result = OnboardingModelCatalog.fileNameForCustomUrl(
            url = "https://example.com/models/foo-1",
        )

        assertEquals("foo-1.litertlm", result)
    }

    @Test
    fun `fileNameForCustomUrl preserves a slashless naked filename`() {
        // Regression for the PR-review feedback: prior behaviour passed
        // missingDelimiterValue = "" which collapsed slashless input
        // into the generic fallback.
        val result = OnboardingModelCatalog.fileNameForCustomUrl(url = "my-model.litertlm")

        assertEquals("my-model.litertlm", result)
    }

    @Test
    fun `fileNameForCustomUrl falls back when the segment carries query params`() {
        val result = OnboardingModelCatalog.fileNameForCustomUrl(
            url = "https://example.com/download?file=abc&token=xyz",
        )

        assertEquals("custom-model.litertlm", result)
    }

    @Test
    fun `fileNameForCustomUrl falls back on blank input`() {
        assertEquals("custom-model.litertlm", OnboardingModelCatalog.fileNameForCustomUrl(url = ""))
        assertEquals("custom-model.litertlm", OnboardingModelCatalog.fileNameForCustomUrl(url = "   "))
    }

    @Test
    fun `entryById resolves bundled preset ids`() {
        val gemma2 = OnboardingModelCatalog.entryById(OnboardingModelCatalog.ID_GEMMA_4_E2B)

        assertEquals("gemma-4-E2B-it.litertlm", gemma2?.fileName)
    }

    @Test
    fun `entryById returns null for the custom URL row`() {
        // The Custom URL path has no preset URL — the VM falls back to
        // the user-supplied `customDownloadUrl` field instead.
        assertEquals(null, OnboardingModelCatalog.entryById(OnboardingModelCatalog.ID_CUSTOM_URL))
    }
}
