package ai.agent.android.presentation.ui.navigation

import ai.agent.android.domain.models.ProviderId
import ai.agent.android.presentation.ui.about.AboutScreen
import ai.agent.android.presentation.ui.chat.home.ChatHomeScreen
import ai.agent.android.presentation.ui.chat.home.ChatHomeViewModel
import ai.agent.android.presentation.ui.memory.MemoryScreen
import ai.agent.android.presentation.ui.models.ModelsScreen
import ai.agent.android.presentation.ui.monitoring.MonitoringScreen
import ai.agent.android.presentation.ui.monitoring.MonitoringViewModel
import ai.agent.android.presentation.ui.more.MoreScreen
import ai.agent.android.presentation.ui.onboarding.OnboardingScreen
import ai.agent.android.presentation.ui.orchestrator.OrchestratorViewModel
import ai.agent.android.presentation.ui.orchestrator.PipelineLibraryScreen
import ai.agent.android.presentation.ui.orchestrator.presets.PipelinePresetsManagerScreen
import ai.agent.android.presentation.ui.pipeline.editor.PipelineEditorScreen
import ai.agent.android.presentation.ui.prompts.PromptLibraryScreen
import ai.agent.android.presentation.ui.settings.SettingsScreen
import ai.agent.android.presentation.ui.settings.provider.ProviderDetailScreen
import ai.agent.android.presentation.ui.settings.provider.ProviderPickerScreen
import ai.agent.android.presentation.ui.splash.SplashScreen
import ai.agent.android.presentation.ui.taskmonitor.TaskMonitorScreen
import ai.agent.android.presentation.ui.taskmonitor.TaskMonitorViewModel
import ai.agent.android.presentation.ui.tools.McpServerConfigScreen
import ai.agent.android.presentation.ui.tools.ToolDetailScreen
import ai.agent.android.presentation.ui.tools.ToolsScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
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
 * @param modifier Inset-padding passthrough from [AppShellScaffold].
 */
@Composable
fun AppNavGraph(navController: NavHostController, showOnboarding: Boolean, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.SPLASH,
        modifier = modifier.fillMaxSize(),
    ) {
        composable(NavRoutes.SPLASH) {
            SplashScreen(
                onInitialized = {
                    val next = if (showOnboarding) NavRoutes.ONBOARDING else NavRoutes.CHAT_TAB
                    navController.navigate(next) {
                        popUpTo(NavRoutes.SPLASH) { inclusive = true }
                        launchSingleTop = true
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
        composable(NavRoutes.CHAT_TAB) {
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
            composable(NavRoutes.PIPELINE_LIBRARY) { entry ->
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
                onNavigateToAbout = { navController.navigate(NavRoutes.ABOUT) },
                onNavigateToLibrary = { navController.navigate(NavRoutes.PIPELINE_PRESETS) },
            )
        }
        composable(NavRoutes.PIPELINE_PRESETS) {
            PipelinePresetsManagerScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.MEMORY) {
            MemoryScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.MODELS) {
            ModelsScreen(
                modifier = Modifier.fillMaxSize(),
                onBack = { navController.popBackStack() },
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
        composable(NavRoutes.SETTINGS) {
            SettingsScreen(
                modifier = Modifier.fillMaxSize(),
                onBack = { navController.popBackStack() },
                onOpenModels = { navController.navigate(NavRoutes.MODELS) },
                onOpenProvider = { providerId ->
                    val wireId = providerId.cloudProvider.id
                    navController.navigate(
                        NavRoutes.PROVIDER_DETAIL.replace(
                            oldValue = "{${NavRoutes.PROVIDER_DETAIL_ID_ARG}}",
                            newValue = wireId,
                        ),
                    )
                },
                onOpenAddProvider = { navController.navigate(NavRoutes.ADD_PROVIDER) },
            )
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
