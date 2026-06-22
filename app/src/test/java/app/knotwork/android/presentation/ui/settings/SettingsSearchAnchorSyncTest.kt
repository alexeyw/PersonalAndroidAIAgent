package app.knotwork.android.presentation.ui.settings

import app.knotwork.android.domain.settings.SettingsRegistry
import app.knotwork.android.domain.settings.anchorKey
import app.knotwork.design.screens.settings.SettingsRowAnchors
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Drift guard tying the deep-link highlight anchors back to the settings
 * registry. The highlight matches a search result's anchor against the anchor a
 * category row declares; those row anchors are hand-maintained in two places —
 * [SettingsRowAnchors] (non-slider rows) and [SLIDER_TO_ANCHOR] (slider rows) —
 * so without this guard a registry key rename would silently orphan a literal and
 * the row would stop flashing with no test failure.
 */
class SettingsSearchAnchorSyncTest {

    private val registryAnchors: Set<String> = SettingsRegistry.allEntries().map { it.anchorKey() }.toSet()

    @Test
    fun `every non-slider row anchor resolves to a registry anchor`() {
        val orphans = SettingsRowAnchors.ALL - registryAnchors
        assertEquals("SettingsRowAnchors not present in the registry", emptySet<String>(), orphans)
    }

    @Test
    fun `every slider anchor resolves to a registry anchor`() {
        val orphans = SLIDER_TO_ANCHOR.values.toSet() - registryAnchors
        assertEquals("SLIDER_TO_ANCHOR values not present in the registry", emptySet<String>(), orphans)
    }

    @Test
    fun `row and slider anchors cover every registry row except the known UI-less rows`() {
        val rendered = SettingsRowAnchors.ALL + SLIDER_TO_ANCHOR.values
        // Registry rows that are deliberately searchable but have no on-screen
        // control to highlight (links shown elsewhere, workspace/HTTP caps not yet
        // surfaced on the Tools sub-screen). Adding a row here is a conscious act.
        val knownUiLess = setOf(
            "DEFAULT_PIPELINE_ID",
            "LINK_PROVIDER_DETAIL",
            "TOOL_CALL_TIMEOUT_MS",
            "WORKSPACE_MAX_FILE_SIZE_BYTES",
            "WORKSPACE_MAX_TOTAL_BYTES",
            "WORKSPACE_READ_TOKEN_BUDGET",
            "HTTP_TOOL_MAX_RESPONSE_BYTES",
        )
        assertEquals(
            "registry rows with no highlight anchor must be exactly the known UI-less set",
            knownUiLess,
            registryAnchors - rendered,
        )
    }
}
