package ai.agent.android.presentation.ui

import ai.agent.android.R
import ai.agent.android.data.services.AgentForegroundService
import ai.agent.android.presentation.theme.AndroidAIAgentTheme
import ai.agent.android.presentation.ui.chat.ChatScreen
import ai.agent.android.presentation.ui.chat.ChatViewModel
import ai.agent.android.presentation.ui.memory.MemoryScreen
import ai.agent.android.presentation.ui.models.ModelsScreen
import ai.agent.android.presentation.ui.monitoring.MonitoringScreen
import ai.agent.android.presentation.ui.monitoring.MonitoringViewModel
import ai.agent.android.presentation.ui.navigation.NavRoutes
import ai.agent.android.presentation.ui.orchestrator.OrchestratorViewModel
import ai.agent.android.presentation.ui.orchestrator.PipelineLibraryScreen
import ai.agent.android.presentation.ui.orchestrator.VisualOrchestratorScreen
import ai.agent.android.presentation.ui.prompts.PromptLibraryScreen
import ai.agent.android.presentation.ui.settings.SettingsScreen
import ai.agent.android.presentation.ui.splash.SplashScreen
import ai.agent.android.presentation.ui.taskmonitor.TaskMonitorScreen
import ai.agent.android.presentation.ui.taskmonitor.TaskMonitorViewModel
import ai.agent.android.presentation.ui.tools.ToolsScreen
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint

/**
 * The main activity of the application, serving as the entry point.
 * It sets up the navigation graph and handles initial app setup.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        // Handle permission result if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Start the background agent service. Application init (incl. the
        // first-launch defaults that used to live here) is now driven by the
        // splash screen via `AppInitializationUseCase`.
        val serviceIntent = Intent(this, AgentForegroundService::class.java)
        startForegroundService(serviceIntent)

        enableEdgeToEdge()
        setContent {
            AndroidAIAgentTheme {
                val navController = rememberNavController()

                // The outer Scaffold previously applied `WindowInsets.systemBars`
                // via `innerPadding`; every feature screen also has its own
                // Scaffold whose default `contentWindowInsets = systemBars` kicks
                // in independently — that produced doubled status- and
                // navigation-bar insets visible inside the work area. Force the
                // outer Scaffold to surface zero insets so each screen's own
                // Scaffold is the single authority on inset padding. Splash
                // and Home don't have their own Scaffold, so they apply
                // `systemBarsPadding()` directly inside their composables.
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = NavRoutes.SPLASH,
                        modifier = Modifier.padding(innerPadding),
                    ) {
                        composable(NavRoutes.SPLASH) {
                            SplashScreen(
                                onInitialized = {
                                    navController.navigate(NavRoutes.HOME) {
                                        // Drop the splash route so Back from
                                        // home exits the app instead of
                                        // re-entering the loading screen.
                                        popUpTo(NavRoutes.SPLASH) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        composable(NavRoutes.HOME) {
                            HomeScreen(
                                onNavigateToModels = {
                                    navController.navigate(NavRoutes.MODELS) {
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateToMemory = {
                                    navController.navigate(NavRoutes.MEMORY) {
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateToTools = {
                                    navController.navigate(NavRoutes.TOOLS) {
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateToChat = {
                                    navController.navigate(NavRoutes.CHAT) {
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateToMonitoring = {
                                    navController.navigate(NavRoutes.MONITORING) {
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateToTaskMonitor = {
                                    navController.navigate(NavRoutes.TASK_MONITOR) {
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateToSettings = {
                                    navController.navigate(NavRoutes.SETTINGS) {
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateToOrchestrator = {
                                    navController.navigate(NavRoutes.PIPELINES_GRAPH) {
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateToPrompts = {
                                    navController.navigate(NavRoutes.PROMPTS) {
                                        launchSingleTop = true
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        composable(NavRoutes.CHAT) {
                            val chatViewModel: ChatViewModel = hiltViewModel()
                            ChatScreen(
                                viewModel = chatViewModel,
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(NavRoutes.MODELS) {
                            ModelsScreen(
                                modifier = Modifier.fillMaxSize(),
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(NavRoutes.MEMORY) {
                            MemoryScreen(onBack = { navController.popBackStack() })
                        }
                        composable(NavRoutes.TOOLS) {
                            ToolsScreen(
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
                                onNavigateToChat = { sessionId ->
                                    navController.navigate(NavRoutes.CHAT) { launchSingleTop = true }
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
                        // Pipelines feature: library + editor share one
                        // OrchestratorViewModel scoped to the nested nav graph,
                        // so creating / renaming / duplicating in the library is
                        // immediately reflected when the editor opens.
                        navigation(
                            startDestination = NavRoutes.PIPELINE_LIBRARY,
                            route = NavRoutes.PIPELINES_GRAPH,
                        ) {
                            composable(NavRoutes.PIPELINE_LIBRARY) { entry ->
                                val parentEntry = remember(entry) {
                                    navController.getBackStackEntry(NavRoutes.PIPELINES_GRAPH)
                                }
                                val orchestratorViewModel: OrchestratorViewModel =
                                    hiltViewModel(parentEntry)
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
                                val orchestratorViewModel: OrchestratorViewModel =
                                    hiltViewModel(parentEntry)
                                VisualOrchestratorScreen(
                                    viewModel = orchestratorViewModel,
                                    onNavigateToPrompts = {
                                        navController.navigate(NavRoutes.PROMPTS) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onBack = { navController.popBackStack() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Composable screen for the home destination, displaying navigation options.
 *
 * @param onNavigateToModels Callback to navigate to the Models screen.
 * @param onNavigateToMemory Callback to navigate to the Memory screen.
 * @param onNavigateToTools Callback to navigate to the Tools screen.
 * @param onNavigateToChat Callback to navigate to the Chat screen.
 * @param onNavigateToMonitoring Callback to navigate to the Monitoring screen.
 * @param onNavigateToSettings Callback to navigate to the Settings screen.
 * @param onNavigateToOrchestrator Callback to navigate to the Orchestrator screen.
 * @param modifier The modifier to be applied to the layout.
 */
@Composable
fun HomeScreen(
    onNavigateToModels: () -> Unit,
    onNavigateToMemory: () -> Unit,
    onNavigateToTools: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToMonitoring: () -> Unit,
    onNavigateToTaskMonitor: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToOrchestrator: () -> Unit,
    onNavigateToPrompts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            // Home doesn't ship with its own Scaffold, so it owns the
            // status- / navigation-bar insets directly to keep the buttons
            // clear of system surfaces.
            .systemBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(text = stringResource(R.string.home_title))
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateToChat) {
            Text(stringResource(R.string.home_open_chat))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateToModels) {
            Text(stringResource(R.string.home_manage_models))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateToMemory) {
            Text(stringResource(R.string.home_memory_management))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateToTools) {
            Text(stringResource(R.string.home_manage_tools))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateToMonitoring) {
            Text(stringResource(R.string.home_monitor_tasks))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateToTaskMonitor) {
            Text(stringResource(R.string.home_manage_active_tasks))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateToSettings) {
            Text(stringResource(R.string.home_settings))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateToOrchestrator) {
            Text(stringResource(R.string.home_visual_orchestrator))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateToPrompts) {
            Text(stringResource(R.string.home_prompt_library))
        }
    }
}
