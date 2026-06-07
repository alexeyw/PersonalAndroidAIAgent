package app.knotwork.android.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Tests for [PipelinePreset] and [PresetCategory].
 *
 * Pure data + enum logic — no Android framework touch points, runs on the
 * JVM unit-test source set.
 */
class PipelinePresetTest {

    @Test
    fun `given equivalent presets when compared then data-class equality holds`() {
        val graph = PipelineGraph(id = "g1", name = "g")
        val first = PipelinePreset(
            id = "id",
            name = "name",
            description = "desc",
            category = PresetCategory.LOCAL,
            graph = graph,
            tags = listOf("a", "b"),
            isBundled = true,
        )
        val second = first.copy()

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun `given preset when isBundled flipped then equality breaks`() {
        val graph = PipelineGraph(id = "g1", name = "g")
        val bundled = PipelinePreset(
            id = "id",
            name = "name",
            description = "desc",
            category = PresetCategory.LOCAL,
            graph = graph,
            isBundled = true,
        )

        assertNotEquals(bundled, bundled.copy(isBundled = false))
    }

    @Test
    fun `given known wire key when fromKey then resolves matching category`() {
        assertEquals(PresetCategory.LOCAL, PresetCategory.fromKey("local"))
        assertEquals(PresetCategory.CLOUD, PresetCategory.fromKey("cloud"))
        assertEquals(PresetCategory.HYBRID, PresetCategory.fromKey("hybrid"))
        assertEquals(PresetCategory.TOOL, PresetCategory.fromKey("tool"))
        assertEquals(PresetCategory.RESEARCH, PresetCategory.fromKey("research"))
        assertEquals(PresetCategory.OTHER, PresetCategory.fromKey("other"))
    }

    @Test
    fun `given mixed case wire key when fromKey then matches case-insensitively`() {
        assertEquals(PresetCategory.LOCAL, PresetCategory.fromKey("LOCAL"))
        assertEquals(PresetCategory.HYBRID, PresetCategory.fromKey(" Hybrid "))
    }

    @Test
    fun `given unknown wire key when fromKey then falls back to OTHER`() {
        assertEquals(PresetCategory.OTHER, PresetCategory.fromKey("future_bucket"))
    }

    @Test
    fun `given null or blank wire key when fromKey then falls back to OTHER`() {
        assertEquals(PresetCategory.OTHER, PresetCategory.fromKey(null))
        assertEquals(PresetCategory.OTHER, PresetCategory.fromKey(""))
        assertEquals(PresetCategory.OTHER, PresetCategory.fromKey("   "))
    }

    @Test
    fun `given every enum value when key then matches the documented wire form`() {
        // Pins the wire keys so a future rename of the enum constants
        // cannot silently invalidate previously-saved Room rows or
        // bundled JSON files that reference the old strings.
        val expected = mapOf(
            PresetCategory.LOCAL to "local",
            PresetCategory.CLOUD to "cloud",
            PresetCategory.HYBRID to "hybrid",
            PresetCategory.TOOL to "tool",
            PresetCategory.RESEARCH to "research",
            PresetCategory.OTHER to "other",
        )
        expected.forEach { (category, wireKey) -> assertEquals(wireKey, category.key) }
    }
}
