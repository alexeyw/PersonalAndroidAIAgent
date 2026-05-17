package ai.agent.android.presentation.ui.navigation

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
import ai.agent.android.presentation.ui.pipeline.editor.PipelineEditorScreen
import ai.agent.android.presentation.ui.prompts.PromptLibraryScreen
import ai.agent.android.presentation.ui.settings.SettingsScreen
import ai.agent.android.presentation.ui.splash.SplashScreen
import ai.agent.android.presentation.ui.taskmonitor.TaskMonitorScreen
import ai.agent.android.presentation.ui.taskmonitor.TaskMonitorViewModel
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

/**
 * The single [NavHost] for the whole app.
 *
 * Layout follows `screens/README.md §Navigation map`:
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
            )
        }

        // ─── Chat tab ──────────────────────────────────────────────────────
        // Phase 21 / Task 8: the user-facing surface is now `ChatHomeScreen`
        // backed by a stub `ChatHomeViewModel` driving the 9-state matrix
        // from `compose/screens/README.md §C1`. The orchestrator/runtime
        // wiring (real chat backend) is captured in the legacy
        // `chat.legacy.*` files and re-attaches to this screen in a
        // follow-up task after v0.1.
        composable(NavRoutes.CHAT_TAB) {
            val chatHomeViewModel: ChatHomeViewModel = hiltViewModel()
            ChatHomeScreen(viewModel = chatHomeViewModel)
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
            ChatHomeScreen(viewModel = chatHomeViewModel)
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
            )
        }
        // `tools/{toolId}` and `tools/add-mcp` arrive with Task 10; we
        // register them as not-yet-implemented so deep-link / nav-arg
        // contracts are stable from Task 4. The body is a placeholder
        // ToolsScreen view so any accidental link does not crash the app.
        composable(
            route = NavRoutes.TOOL_DETAIL,
            arguments = listOf(
                navArgument(NavRoutes.TOOL_DETAIL_ID_ARG) {
                    type = NavType.StringType
                    nullable = false
                },
            ),
        ) {
            ToolsScreen(
                modifier = Modifier.fillMaxSize(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(NavRoutes.ADD_MCP_SERVER) {
            ToolsScreen(
                modifier = Modifier.fillMaxSize(),
                onBack = { navController.popBackStack() },
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
            )
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
