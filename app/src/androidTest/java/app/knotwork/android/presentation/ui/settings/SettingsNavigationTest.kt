package app.knotwork.android.presentation.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.knotwork.android.presentation.ui.navigation.NavRoutes
import app.knotwork.design.screens.settings.HUB_CATEGORY_ROW_TAG_PREFIX
import app.knotwork.design.screens.settings.HubSearchResultRow
import app.knotwork.design.screens.settings.SETTINGS_CATEGORY_BODY_TEST_TAG
import app.knotwork.design.screens.settings.SETTINGS_HUB_BODY_TEST_TAG
import app.knotwork.design.screens.settings.SETTINGS_SEARCH_FIELD_TAG
import app.knotwork.design.screens.settings.SETTINGS_SEARCH_RESULT_TAG_PREFIX
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import app.knotwork.design.screens.settings.SettingsCategoryId as CatalogCategoryId

/**
 * Integration tests for the redesigned Settings navigation graph: the
 * category hub deep-links into focused sub-screens, and the in-settings
 * search routes a result tap to its owning category.
 *
 * Both tests drive the *real* screen wrappers ([SettingsHubScreen] and the
 * category sub-screens) inside a minimal [NavHost] that mirrors the production
 * settings nested-graph wiring (route constants + [SettingsNavActions] over the
 * `NavController`), with a mocked [SettingsViewModel] so no Hilt graph is
 * needed. This exercises the hub → sub-screen transition end-to-end rather than
 * a single screen in isolation.
 */
class SettingsNavigationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * Builds the same [SettingsNavActions] the production graph wires
     * ([app.knotwork.android.presentation.ui.navigation] settings graph), so a
     * category tap navigates through the real route mapping. Non-settings
     * destinations are no-ops — the tests only trigger `onOpenCategory`.
     */
    private fun navActions(navController: NavHostController): SettingsNavActions = SettingsNavActions(
        onBack = { navController.popBackStack() },
        onOpenCategory = { category -> navController.navigate(categoryRoute(category)) },
        onOpenModels = {},
        onOpenProvider = {},
        onOpenAddProvider = {},
        onOpenManageTools = {},
        onOpenAllowedDomains = {},
        onOpenLicenses = {},
        onOpenUsageStatistics = {},
        onOpenExternalAutomationJournal = {},
    )

    /** Mirror of the production category → sub-screen route mapping. */
    private fun categoryRoute(category: CatalogCategoryId): String = when (category) {
        CatalogCategoryId.Generation -> NavRoutes.SETTINGS_GENERATION
        CatalogCategoryId.Models -> NavRoutes.SETTINGS_MODELS
        CatalogCategoryId.Memory -> NavRoutes.SETTINGS_MEMORY
        CatalogCategoryId.Pipelines -> NavRoutes.SETTINGS_PIPELINES
        CatalogCategoryId.Tools -> NavRoutes.SETTINGS_TOOLS
        CatalogCategoryId.Background -> NavRoutes.SETTINGS_BACKGROUND
        CatalogCategoryId.Privacy -> NavRoutes.SETTINGS_PRIVACY
        CatalogCategoryId.About -> NavRoutes.SETTINGS_ABOUT
    }

    /** Renders the settings nested graph starting at the hub. */
    private fun setSettingsGraph(vm: SettingsViewModel): NavHostController {
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            val nav = navActions(navController)
            MaterialTheme {
                NavHost(navController = navController, startDestination = NavRoutes.SETTINGS_HUB) {
                    composable(NavRoutes.SETTINGS_HUB) { SettingsHubScreen(viewModel = vm, nav = nav) }
                    composable(NavRoutes.SETTINGS_GENERATION) { GenerationSettingsScreen(viewModel = vm, nav = nav) }
                    composable(NavRoutes.SETTINGS_MODELS) { ModelsSettingsScreen(viewModel = vm, nav = nav) }
                    composable(NavRoutes.SETTINGS_MEMORY) { MemorySettingsScreen(viewModel = vm, nav = nav) }
                    composable(NavRoutes.SETTINGS_PIPELINES) { PipelinesSettingsScreen(viewModel = vm, nav = nav) }
                    composable(NavRoutes.SETTINGS_TOOLS) { ToolsSettingsScreen(viewModel = vm, nav = nav) }
                    composable(NavRoutes.SETTINGS_BACKGROUND) { BackgroundSettingsScreen(viewModel = vm, nav = nav) }
                    composable(NavRoutes.SETTINGS_PRIVACY) { PrivacySettingsScreen(viewModel = vm, nav = nav) }
                    composable(NavRoutes.SETTINGS_ABOUT) { AboutSettingsScreen(viewModel = vm, nav = nav) }
                }
            }
        }
        return navController
    }

    @Test
    fun hub_tapCategoryRow_navigatesToThatSubScreen() {
        val (vm, _) = mockSettingsViewModel()
        val navController = setSettingsGraph(vm)

        // Start on the hub.
        composeTestRule.onNodeWithTag(SETTINGS_HUB_BODY_TEST_TAG).assertIsDisplayed()

        // Tap the Memory category row.
        composeTestRule.onNodeWithTag(HUB_CATEGORY_ROW_TAG_PREFIX + CatalogCategoryId.Memory.name)
            .performClick()
        composeTestRule.waitForIdle()

        // The Memory sub-screen is now showing.
        composeTestRule.onNodeWithTag(SETTINGS_CATEGORY_BODY_TEST_TAG).assertIsDisplayed()
        assertEquals(NavRoutes.SETTINGS_MEMORY, navController.currentDestination?.route)

        // Back returns to the hub.
        composeTestRule.runOnUiThread { navController.popBackStack() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(SETTINGS_HUB_BODY_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun search_typingQuery_showsResult_tapNavigatesAndRequestsHighlight() {
        val (vm, handles) = mockSettingsViewModel()
        // A query populates a single result; the engine itself is unit-tested
        // separately (SettingsSearchEngineTest), so the stub stands in for it.
        val result = HubSearchResultRow(
            anchorKey = "MAX_CONTEXT_LENGTH",
            categoryId = CatalogCategoryId.Generation,
            name = "Max context length",
            nameMatchStart = 0,
            nameMatchLength = 3,
            isBasic = false,
            synonymHit = null,
        )
        every { vm.onSearchQueryChange(any()) } answers {
            val query = firstArg<String>()
            handles.uiStateFlow.update { it.copy(searchQuery = query, searchResults = listOf(result)) }
        }

        val navController = setSettingsGraph(vm)

        // Type a query into the hub search field. The test tag sits on the
        // field's outer container, so target the editable node beneath it
        // (the hub also renders the inline System-instructions field).
        composeTestRule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(SETTINGS_SEARCH_FIELD_TAG)))
            .performTextInput("max")
        composeTestRule.waitForIdle()

        // The result row appears; tap it.
        composeTestRule.onNodeWithTag(SETTINGS_SEARCH_RESULT_TAG_PREFIX + result.anchorKey)
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        // Tapping the result requests the deep-link highlight and navigates to
        // the owning category sub-screen.
        verify(exactly = 1) { vm.requestHighlight(result.anchorKey) }
        composeTestRule.onNodeWithTag(SETTINGS_CATEGORY_BODY_TEST_TAG).assertIsDisplayed()
        assertEquals(NavRoutes.SETTINGS_GENERATION, navController.currentDestination?.route)
    }
}
