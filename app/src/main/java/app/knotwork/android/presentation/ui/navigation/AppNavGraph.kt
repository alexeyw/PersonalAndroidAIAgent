package app.knotwork.android.presentation.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import app.knotwork.android.domain.models.ProviderId
import app.knotwork.android.presentation.ui.about.AboutScreen
import app.knotwork.android.presentation.ui.chat.home.ChatHomeScreen
import app.knotwork.android.presentation.ui.chat.home.ChatHomeViewModel
import app.knotwork.android.presentation.ui.discover.DiscoverDetailScreen
import app.knotwork.android.presentation.ui.discover.DiscoverScreen
import app.knotwork.android.presentation.ui.files.FilesScreen
import app.knotwork.android.presentation.ui.memory.MemoryScreen
import app.knotwork.android.presentation.ui.models.ModelsScreen
import app.knotwork.android.presentation.ui.monitoring.MonitoringScreen
import app.knotwork.android.presentation.ui.monitoring.MonitoringViewModel
import app.knotwork.android.presentation.ui.more.MoreScreen
import app.knotwork.android.presentation.ui.onboarding.OnboardingScreen
import app.knotwork.android.presentation.ui.orchestrator.OrchestratorViewModel
import app.knotwork.android.presentation.ui.orchestrator.PipelineLibraryScreen
import app.knotwork.android.presentation.ui.orchestrator.presets.PipelinePresetsManagerScreen
import app.knotwork.android.presentation.ui.pipeline.editor.PipelineEditorScreen
import app.knotwork.android.presentation.ui.prompts.PromptLibraryScreen
import app.knotwork.android.presentation.ui.settings.AboutSettingsScreen
import app.knotwork.android.presentation.ui.settings.BackgroundSettingsScreen
import app.knotwork.android.presentation.ui.settings.GenerationSettingsScreen
import app.knotwork.android.presentation.ui.settings.MemorySettingsScreen
import app.knotwork.android.presentation.ui.settings.ModelsSettingsScreen
import app.knotwork.android.presentation.ui.settings.PipelinesSettingsScreen
import app.knotwork.android.presentation.ui.settings.PrivacySettingsScreen
import app.knotwork.android.presentation.ui.settings.SettingsHubScreen
import app.knotwork.android.presentation.ui.settings.SettingsNavActions
import app.knotwork.android.presentation.ui.settings.SettingsViewModel
import app.knotwork.android.presentation.ui.settings.ToolsSettingsScreen
import app.knotwork.android.presentation.ui.settings.provider.ProviderDetailScreen
import app.knotwork.android.presentation.ui.settings.provider.ProviderPickerScreen
import app.knotwork.android.presentation.ui.skills.SkillLibraryScreen
import app.knotwork.android.presentation.ui.splash.SplashScreen
import app.knotwork.android.presentation.ui.taskmonitor.TaskMonitorScreen
import app.knotwork.android.presentation.ui.taskmonitor.TaskMonitorViewModel
import app.knotwork.android.presentation.ui.tools.AllowedDomainsScreen
import app.knotwork.android.presentation.ui.tools.McpServerConfigScreen
import app.knotwork.android.presentation.ui.tools.ToolDetailScreen
import app.knotwork.android.presentation.ui.tools.ToolsScreen
import app.knotwork.design.screens.settings.SettingsCategoryId
import timber.log.Timber

/**
 * The single [NavHost] for the whole app.
 *
 * Navigation map:
 *  - Splash and Onboarding are top-level "shell-less" destinations — the
 *    bottom-nav is hidden for them (see [shouldShowBottomNav]). After
 *    onboarding completes, the user lands on the Chat tab.
 *  - The four bottom-nav tabs (Chat / Pipelines / Tools / More) each own a
 *    `composable(...)` start-destination; deeper screens (`memory`,
 *    `settings`, `tools/{id}`, `pipeline/{id}/edit`, ...) live as
 *    additional entries reachable from inside a tab.
 *  - The Pipelines tab is a nested `navigation { ... }` graph so its
 *    library and editor share a single [OrchestratorViewModel] scoped to
 *    the graph entry — exactly as before this task, just with a different
 *    parent route.
 *
 * Modal bottom-sheet routes (`sheet/...`) are registered with the shared
 * [KnotworkModalRoute] wrapper; their bodies arrive in Tasks 6/7/10.
 *
 * @param navController Activity-owned controller observed by the parent
 *        [AppShellScaffold] for bottom-nav visibility / highlight.
 * @param showOnboarding Read once at composition. When `true`, the splash
 *        completion handler routes to onboarding; otherwise it goes to
 *        Chat. Sourced from `SettingsRepository.hasCompletedOnboarding`
 *        (inverted) — a flag that survives `InitializeAppUseCase` and so
 *        is the right gate for the UI surface, unlike `isFirstLaunch`
 *        which is cleared during cold-start init.
 * @param launchedFromDeepLink `true` when the host activity was started by a
 *        `knotwork://` deep link (launcher shortcut, share target, or a
 *        notification tap). Navigation Compose has already placed the deep-link
 *        destination on the back stack, so the splash handler must only **drop
 *        the splash entry** rather than navigate to [NavRoutes.CHAT_TAB] — the
 *        latter buries the deep-link target under the last-active chat, so Back
 *        reveals the target instead of closing the app.
 * @param modifier Inset-padding passthrough from [AppShellScaffold].
 */
@Composable
fun AppNavGraph(
    navController: NavHostController,
    showOnboarding: Boolean,
    launchedFromDeepLink: Boolean,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.SPLASH,
        modifier = modifier.fillMaxSize(),
    ) {
        composable(NavRoutes.SPLASH) {
            SplashScreen(
                onInitialized = {
                    // `previousBackStackEntry` is non-null only when a deep link
                    // (shortcut / share / notification) already placed its target
                    // below the splash during graph creation. In that case just
                    // drop the splash entry — navigating to CHAT_TAB here would
                    // stack the last-active chat ON TOP of the deep-link target,
                    // so Back would surface the target instead of closing the app.
                    // The `previousBackStackEntry` guard also keeps the NavHost
                    // non-empty if a `knotwork://` intent ever fails to match a
                    // destination (splash alone → fall through to normal routing).
                    if (launchedFromDeepLink && navController.previousBackStackEntry != null) {
                        navController.popBackStack(NavRoutes.SPLASH, inclusive = true)
                    } else {
                        val next = if (showOnboarding) NavRoutes.ONBOARDING else NavRoutes.CHAT_TAB
                        navController.navigate(next) {
                            popUpTo(NavRoutes.SPLASH) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(NavRoutes.ONBOARDING) {
            OnboardingScreen(
                onCompleted = {
                    navController.navigate(NavRoutes.CHAT_TAB) {
                        popUpTo(NavRoutes.ONBOARDING) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onConfigureProvider = { providerWireId ->
                    // Step 3 cloud-provider tap — navigate to the same
                    // per-provider API-key editor that Settings uses, so
                    // the user can actually paste a key. The provider
                    // detail screen pops back to onboarding on `onBack`;
                    // the VM observes ApiKeyRepository so the
                    // "Configured" pill on the row flips on its own
                    // once a key is persisted.
                    val target = NavRoutes.PROVIDER_DETAIL.replace(
                        oldValue = "{${NavRoutes.PROVIDER_DETAIL_ID_ARG}}",
                        newValue = providerWireId,
                    )
                    Timber.d("Onboarding step 3: navigating to provider detail '$target'")
                    navController.navigate(target)
                },
            )
        }

        // ─── Chat tab ──────────────────────────────────────────────────────
        composable(
            route = NavRoutes.CHAT_TAB,
            deepLinks = listOf(
                navDeepLink { uriPattern = NavRoutes.CHAT_TAB_DEEP_LINK_PATTERN },
            ),
        ) {
            val chatHomeViewModel: ChatHomeViewModel = hiltViewModel()
            ChatHomeScreen(
                viewModel = chatHomeViewModel,
                onOpenSettings = { navController.navigate(NavRoutes.SETTINGS) },
                onOpenModels = { navController.navigate(NavRoutes.MODELS) },
            )
        }
        composable(
            route = NavRoutes.CHAT_WITH_THREAD,
            arguments = listOf(
                navArgument(NavRoutes.CHAT_THREAD_ARG) {
                    type = NavType.StringType
                    nullable = false
                },
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = NavRoutes.CHAT_DEEP_LINK_PATTERN },
            ),
        ) { entry ->
            val threadId = entry.arguments?.getString(NavRoutes.CHAT_THREAD_ARG)
            val chatHomeViewModel: ChatHomeViewModel = hiltViewModel()
            LaunchedEffect(threadId) {
                if (!threadId.isNullOrBlank()) {
                    chatHomeViewModel.selectThread(threadId)
                }
            }
            ChatHomeScreen(
                viewModel = chatHomeViewModel,
                onOpenSettings = { navController.navigate(NavRoutes.SETTINGS) },
                onOpenModels = { navController.navigate(NavRoutes.MODELS) },
            )
        }

        // ─── Pipelines tab (nested graph) ──────────────────────────────────
        navigation(
            startDestination = NavRoutes.PIPELINE_LIBRARY,
            route = NavRoutes.PIPELINES_GRAPH,
        ) {
            composable(
                route = NavRoutes.PIPELINE_LIBRARY,
                deepLinks = listOf(
                    navDeepLink { uriPattern = NavRoutes.PIPELINES_DEEP_LINK_PATTERN },
                ),
            ) { entry ->
                val parentEntry = remember(entry) {
                    navController.getBackStackEntry(NavRoutes.PIPELINES_GRAPH)
                }
                val orchestratorViewModel: OrchestratorViewModel = hiltViewModel(parentEntry)
                PipelineLibraryScreen(
                    viewModel = orchestratorViewModel,
                    onOpenEditor = {
                        navController.navigate(NavRoutes.PIPELINE_EDITOR) {
                            launchSingleTop = true
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(NavRoutes.PIPELINE_EDITOR) { entry ->
                val parentEntry = remember(entry) {
                    navController.getBackStackEntry(NavRoutes.PIPELINES_GRAPH)
                }
                val orchestratorViewModel: OrchestratorViewModel = hiltViewModel(parentEntry)
                PipelineEditorScreen(
                    viewModel = orchestratorViewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = NavRoutes.PIPELINE_EDIT_WITH_ID,
                arguments = listOf(
                    navArgument(NavRoutes.PIPELINE_EDIT_ID_ARG) {
                        type = NavType.StringType
                        nullable = false
                    },
                ),
            ) { entry ->
                val parentEntry = remember(entry) {
                    navController.getBackStackEntry(NavRoutes.PIPELINES_GRAPH)
                }
                val orchestratorViewModel: OrchestratorViewModel = hiltViewModel(parentEntry)
                val pipelineId = entry.arguments?.getString(NavRoutes.PIPELINE_EDIT_ID_ARG)
                LaunchedEffect(pipelineId) {
                    if (!pipelineId.isNullOrBlank()) orchestratorViewModel.loadPipeline(pipelineId)
                }
                PipelineEditorScreen(
                    viewModel = orchestratorViewModel,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        // ─── Tools tab ─────────────────────────────────────────────────────
        composable(NavRoutes.TOOLS) {
            ToolsScreen(
                modifier = Modifier.fillMaxSize(),
                onBack = { navController.popBackStack() },
                onAddMcpServer = { navController.navigate(NavRoutes.MCP_SERVER_CONFIG_ADD) },
                onEditMcpServer = { originalUrl ->
                    navController.navigate(NavRoutes.mcpServerConfigEditRoute(originalUrl = originalUrl))
                },
                onOpenToolDetail = { toolId ->
                    // AppFunction-shaped tool ids embed `/` and `#` (e.g.
                    // `<pkg>/<FQN>#invoke`). Percent-encode them via
                    // `Uri.encode` so they fit a single `{toolId}`
                    // segment; Navigation's internal `Uri.decode` is the
                    // inverse, so the receiver gets the raw id back.
                    val encoded = android.net.Uri.encode(toolId)
                    navController.navigate(NavRoutes.TOOL_DETAIL.replace(oldValue = "{toolId}", newValue = encoded))
                },
                onOpenAllowedDomains = { navController.navigate(NavRoutes.ALLOWED_DOMAINS) },
            )
        }
        composable(NavRoutes.ALLOWED_DOMAINS) {
            AllowedDomainsScreen(
                onBack = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(
            route = NavRoutes.TOOL_DETAIL,
            arguments = listOf(
                navArgument(NavRoutes.TOOL_DETAIL_ID_ARG) {
                    type = NavType.StringType
                    nullable = false
                },
            ),
        ) { backStackEntry ->
            val toolId = backStackEntry.arguments?.getString(NavRoutes.TOOL_DETAIL_ID_ARG).orEmpty()
            ToolDetailScreen(
                toolId = toolId,
                onBack = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(
            route = NavRoutes.MCP_SERVER_CONFIG,
            arguments = listOf(
                navArgument(NavRoutes.MCP_SERVER_CONFIG_URL_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            McpServerConfigScreen(
                onDone = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize(),
            )
        }
        // ─── More tab and its secondary screens ────────────────────────────
        composable(NavRoutes.MORE) {
            MoreScreen(
                onNavigateToMemory = { navController.navigate(NavRoutes.MEMORY) },
                onNavigateToModels = { navController.navigate(NavRoutes.MODELS) },
                onNavigateToMonitoring = { navController.navigate(NavRoutes.MONITORING) },
                onNavigateToTaskMonitor = { navController.navigate(NavRoutes.TASK_MONITOR) },
                onNavigateToSettings = { navController.navigate(NavRoutes.SETTINGS) },
                onNavigateToPrompts = { navController.navigate(NavRoutes.PROMPTS) },
                onNavigateToSkills = { navController.navigate(NavRoutes.SKILLS) },
                onNavigateToAbout = { navController.navigate(NavRoutes.ABOUT) },
                onNavigateToLibrary = { navController.navigate(NavRoutes.PIPELINE_PRESETS) },
                onNavigateToFiles = { navController.navigate(NavRoutes.FILES) },
            )
        }
        composable(NavRoutes.PIPELINE_PRESETS) {
            PipelinePresetsManagerScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.MEMORY) {
            MemoryScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.FILES) {
            FilesScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.MODELS) {
            ModelsScreen(
                modifier = Modifier.fillMaxSize(),
                onBack = { navController.popBackStack() },
                onOpenDiscover = { navController.navigate(NavRoutes.DISCOVER) },
            )
        }
        composable(NavRoutes.DISCOVER) {
            DiscoverScreen(
                modifier = Modifier.fillMaxSize(),
                onBack = { navController.popBackStack() },
                onOpenModel = { repoId -> navController.navigate(NavRoutes.discoverDetailRoute(repoId)) },
            )
        }
        composable(
            route = NavRoutes.DISCOVER_DETAIL,
            arguments = listOf(
                navArgument(NavRoutes.DISCOVER_DETAIL_REPO_ID_ARG) {
                    type = NavType.StringType
                    nullable = false
                    defaultValue = ""
                },
            ),
        ) { entry ->
            val repoId = entry.arguments?.getString(NavRoutes.DISCOVER_DETAIL_REPO_ID_ARG).orEmpty()
            DiscoverDetailScreen(
                repoId = repoId,
                onBack = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(NavRoutes.MONITORING) {
            val monitoringViewModel: MonitoringViewModel = hiltViewModel()
            MonitoringScreen(
                viewModel = monitoringViewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(NavRoutes.TASK_MONITOR) {
            val taskMonitorViewModel: TaskMonitorViewModel = hiltViewModel()
            TaskMonitorScreen(
                viewModel = taskMonitorViewModel,
                onNavigateToChat = { _ ->
                    navController.navigate(NavRoutes.CHAT_TAB) {
                        applyTabSwitchOptions()
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        navigation(startDestination = NavRoutes.SETTINGS_HUB, route = NavRoutes.SETTINGS) {
            val nav = settingsNavActions(navController)
            composable(NavRoutes.SETTINGS_HUB) { entry ->
                SettingsHubScreen(viewModel = settingsGraphViewModel(navController, entry), nav = nav)
            }
            composable(NavRoutes.SETTINGS_GENERATION) { entry ->
                GenerationSettingsScreen(viewModel = settingsGraphViewModel(navController, entry), nav = nav)
            }
            composable(NavRoutes.SETTINGS_MODELS) { entry ->
                ModelsSettingsScreen(viewModel = settingsGraphViewModel(navController, entry), nav = nav)
            }
            composable(NavRoutes.SETTINGS_MEMORY) { entry ->
                MemorySettingsScreen(viewModel = settingsGraphViewModel(navController, entry), nav = nav)
            }
            composable(NavRoutes.SETTINGS_PIPELINES) { entry ->
                PipelinesSettingsScreen(viewModel = settingsGraphViewModel(navController, entry), nav = nav)
            }
            composable(NavRoutes.SETTINGS_TOOLS) { entry ->
                ToolsSettingsScreen(viewModel = settingsGraphViewModel(navController, entry), nav = nav)
            }
            composable(NavRoutes.SETTINGS_BACKGROUND) { entry ->
                BackgroundSettingsScreen(viewModel = settingsGraphViewModel(navController, entry), nav = nav)
            }
            composable(NavRoutes.SETTINGS_PRIVACY) { entry ->
                PrivacySettingsScreen(viewModel = settingsGraphViewModel(navController, entry), nav = nav)
            }
            composable(NavRoutes.SETTINGS_ABOUT) { entry ->
                AboutSettingsScreen(viewModel = settingsGraphViewModel(navController, entry), nav = nav)
            }
        }
        composable(
            route = NavRoutes.PROVIDER_DETAIL,
            arguments = listOf(
                navArgument(NavRoutes.PROVIDER_DETAIL_ID_ARG) {
                    type = NavType.StringType
                    nullable = false
                },
            ),
        ) { entry ->
            val wireId = entry.arguments?.getString(NavRoutes.PROVIDER_DETAIL_ID_ARG).orEmpty()
            val providerId = ProviderId.entries.firstOrNull { it.cloudProvider.id == wireId }
                ?: ProviderId.OpenAi
            ProviderDetailScreen(
                providerId = providerId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(NavRoutes.ADD_PROVIDER) {
            // v0.1: Add provider goes through the same picker as the
            // detail screen — surface a minimal list-style picker that
            // forwards to the per-provider detail route. The richer
            // bottom-sheet picker is a follow-up.
            ProviderPickerScreen(
                onPick = { providerId ->
                    val wireId = providerId.cloudProvider.id
                    navController.navigate(
                        NavRoutes.PROVIDER_DETAIL.replace(
                            oldValue = "{${NavRoutes.PROVIDER_DETAIL_ID_ARG}}",
                            newValue = wireId,
                        ),
                    ) {
                        popUpTo(NavRoutes.SETTINGS)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(NavRoutes.PROMPTS) {
            PromptLibraryScreen(
                modifier = Modifier.fillMaxSize(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(NavRoutes.SKILLS) {
            SkillLibraryScreen(
                modifier = Modifier.fillMaxSize(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(NavRoutes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }

        // ─── Modal bottom-sheet placeholders (Tasks 6 / 7) ─────────────────
        composable(NavRoutes.SHEET_NODE_CONFIG) {
            KnotworkModalRoute(onDismiss = { navController.popBackStack() }) { _ -> }
        }
        composable(NavRoutes.SHEET_CONSOLE) {
            KnotworkModalRoute(onDismiss = { navController.popBackStack() }) { _ -> }
        }
    }
}

/**
 * Resolves the shared [SettingsViewModel] scoped to the settings navigation
 * graph so the hub and every category sub-screen observe the same instance (the
 * Pipelines-graph pattern).
 */
@Composable
private fun settingsGraphViewModel(navController: NavHostController, entry: NavBackStackEntry): SettingsViewModel {
    val parentEntry = remember(entry) { navController.getBackStackEntry(NavRoutes.SETTINGS) }
    return hiltViewModel(parentEntry)
}

/** Builds the settings navigation actions over [navController]. */
private fun settingsNavActions(navController: NavHostController): SettingsNavActions = SettingsNavActions(
    onBack = { navController.popBackStack() },
    onOpenCategory = { category -> navController.navigate(settingsCategoryRoute(category)) },
    onOpenModels = { navController.navigate(NavRoutes.MODELS) },
    onOpenProvider = { providerId ->
        navController.navigate(
            NavRoutes.PROVIDER_DETAIL.replace(
                oldValue = "{${NavRoutes.PROVIDER_DETAIL_ID_ARG}}",
                newValue = providerId.cloudProvider.id,
            ),
        )
    },
    onOpenAddProvider = { navController.navigate(NavRoutes.ADD_PROVIDER) },
    onOpenManageTools = { navController.navigate(NavRoutes.TOOLS) },
    onOpenAllowedDomains = { navController.navigate(NavRoutes.ALLOWED_DOMAINS) },
    onOpenLicenses = { navController.navigate(NavRoutes.ABOUT) },
)

/** Maps a settings category id to its sub-screen route. */
private fun settingsCategoryRoute(category: SettingsCategoryId): String = when (category) {
    SettingsCategoryId.Generation -> NavRoutes.SETTINGS_GENERATION
    SettingsCategoryId.Models -> NavRoutes.SETTINGS_MODELS
    SettingsCategoryId.Memory -> NavRoutes.SETTINGS_MEMORY
    SettingsCategoryId.Pipelines -> NavRoutes.SETTINGS_PIPELINES
    SettingsCategoryId.Tools -> NavRoutes.SETTINGS_TOOLS
    SettingsCategoryId.Background -> NavRoutes.SETTINGS_BACKGROUND
    SettingsCategoryId.Privacy -> NavRoutes.SETTINGS_PRIVACY
    SettingsCategoryId.About -> NavRoutes.SETTINGS_ABOUT
}
