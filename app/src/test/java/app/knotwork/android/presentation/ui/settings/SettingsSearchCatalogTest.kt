package app.knotwork.android.presentation.ui.settings

import app.knotwork.android.domain.settings.SettingTier
import app.knotwork.android.domain.settings.SettingsCategoryId
import app.knotwork.android.domain.settings.SettingsRegistry
import app.knotwork.android.domain.settings.SettingsSearchField
import app.knotwork.android.domain.settings.SettingsSearchResult
import app.knotwork.android.domain.settings.SettingsSearchableEntry
import app.knotwork.android.domain.settings.anchorKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import app.knotwork.design.screens.settings.SettingsCategoryId as CatalogCategoryId

/**
 * Drift guard for the settings-search index bridge.
 *
 * The completeness test makes the registry the single source of truth: every row
 * it exposes must have search copy in [SettingsSearchCatalog.SEARCH_STRINGS], so
 * a setting added to the registry cannot silently fall out of search. It is paired
 * with `SettingsRegistryTest`, which guards the registry's own contents.
 */
class SettingsSearchCatalogTest {

    @Test
    fun `every registry anchor has search copy and there is no orphan copy`() {
        val anchors = SettingsRegistry.allEntries().map { it.anchorKey() }.toSet()
        assertEquals(
            "SEARCH_STRINGS must cover exactly the registry anchors",
            anchors,
            SettingsSearchCatalog.SEARCH_STRINGS.keys,
        )
    }

    @Test
    fun `every search-copy entry carries a non-zero name resource`() {
        SettingsSearchCatalog.SEARCH_STRINGS.forEach { (anchor, copy) ->
            assertTrue("blank name resource for $anchor", copy.nameRes != 0)
        }
    }

    @Test
    fun `toCatalog maps every domain category to a distinct catalog category`() {
        val mapped = SettingsCategoryId.entries.map { it.toCatalog() }
        assertEquals(SettingsCategoryId.entries.size, mapped.toSet().size)
        assertEquals(CatalogCategoryId.Generation, SettingsCategoryId.GENERATION.toCatalog())
        assertEquals(CatalogCategoryId.About, SettingsCategoryId.ABOUT.toCatalog())
    }

    @Test
    fun `toHubRow carries the name match range and tier`() {
        val entry = SettingsSearchableEntry(
            anchorKey = "TEMPERATURE",
            categoryId = SettingsCategoryId.GENERATION,
            tier = SettingTier.ADVANCED,
            name = "Temperature",
            description = "",
            categoryTitle = "Generation",
            synonyms = emptyList(),
        )
        val row = SettingsSearchResult(
            entry = entry,
            matchedField = SettingsSearchField.NAME,
            nameMatchRange = 0 until 4,
            synonymHit = null,
        ).toHubRow()

        assertEquals("TEMPERATURE", row.anchorKey)
        assertEquals(CatalogCategoryId.Generation, row.categoryId)
        assertEquals(0, row.nameMatchStart)
        assertEquals(4, row.nameMatchLength)
        assertFalse(row.isBasic)
        assertEquals(null, row.synonymHit)
    }

    @Test
    fun `toHubRow reports no name match and surfaces the synonym for a synonym hit`() {
        val entry = SettingsSearchableEntry(
            anchorKey = "PIPELINE_MAX_STEPS",
            categoryId = SettingsCategoryId.PIPELINES,
            tier = SettingTier.BASIC,
            name = "Cap autonomous steps",
            description = "",
            categoryTitle = "Pipelines & structured output",
            synonyms = listOf("max"),
        )
        val row = SettingsSearchResult(
            entry = entry,
            matchedField = SettingsSearchField.SYNONYM,
            nameMatchRange = null,
            synonymHit = "max",
        ).toHubRow()

        assertEquals(-1, row.nameMatchStart)
        assertEquals(0, row.nameMatchLength)
        assertTrue(row.isBasic)
        assertEquals("max", row.synonymHit)
    }
}
