package ai.agent.android.presentation.ui

import ai.agent.android.data.services.AgentForegroundService
import ai.agent.android.presentation.theme.AndroidAIAgentTheme
import ai.agent.android.presentation.ui.chat.ChatScreen
import ai.agent.android.presentation.ui.chat.ChatViewModel
import ai.agent.android.presentation.ui.memory.MemoryScreen
import ai.agent.android.presentation.ui.models.ModelsScreen
import ai.agent.android.presentation.ui.monitoring.MonitoringScreen
import ai.agent.android.presentation.ui.monitoring.MonitoringViewModel
import ai.agent.android.presentation.ui.orchestrator.OrchestratorViewModel
import ai.agent.android.presentation.ui.orchestrator.PipelineLibraryScreen
import ai.agent.android.presentation.ui.orchestrator.VisualOrchestratorScreen
import ai.agent.android.presentation.ui.prompts.PromptLibraryScreen
import ai.agent.android.presentation.ui.settings.SettingsScreen
import ai.agent.android.presentation.ui.settings.SettingsViewModel
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
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Handle permission result if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
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
                        startDestination = "splash",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("splash") {
                            SplashScreen(
                                onInitialized = {
                                    navController.navigate("home") {
                                        // Drop the splash route so Back from
                                        // home exits the app instead of
                                        // re-entering the loading screen.
                                        popUpTo("splash") { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        composable("home") {
                            HomeScreen(
                                onNavigateToModels = {
                                    navController.navigate("models") {
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateToMemory = {
                                    navController.navigate("memory") {
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateToTools = {
                                    navController.navigate("tools") {
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateToChat = {
                                    navController.navigate("chat") {
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateToMonitoring = {
                                    navController.navigate("monitoring") {
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateToTaskMonitor = {
                                    navController.navigate("taskmonitor") {
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateToSettings = {
                                    navController.navigate("settings") {
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateToOrchestrator = {
                                    navController.navigate("pipelines") {
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateToPrompts = {
                                    navController.navigate("prompts") {
                                        launchSingleTop = true
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        composable("chat") {
                            val chatViewModel: ChatViewModel = hiltViewModel()
                            ChatScreen(
                                viewModel = chatViewModel,
                                onBack = { navController.popBackStack() })
                        }
                        composable("models") {
                            ModelsScreen(
                                modifier = Modifier.fillMaxSize(),
                                onBack = { navController.popBackStack() })
                        }
                        composable("memory") {
                            MemoryScreen(onBack = { navController.popBackStack() })
                        }
                        composable("tools") {
                            ToolsScreen(
                                modifier = Modifier.fillMaxSize(),
                                onBack = { navController.popBackStack() })
                        }
                        composable("monitoring") {
                            val monitoringViewModel: MonitoringViewModel = hiltViewModel()
                            MonitoringScreen(
                                viewModel = monitoringViewModel,
                                onBack = { navController.popBackStack() })
                        }
                        composable("taskmonitor") {
                            val taskMonitorViewModel: TaskMonitorViewModel = hiltViewModel()
                            TaskMonitorScreen(
                                viewModel = taskMonitorViewModel,
                                onNavigateToChat = { sessionId -> 
                                    navController.navigate("chat") { launchSingleTop = true }
                                },
                                onBack = { navController.popBackStack() })
                        }
                        composable("settings") {
                            SettingsScreen(
                                modifier = Modifier.fillMaxSize(),
                                onBack = { navController.popBackStack() })
                        }
                        composable("prompts") {
                            PromptLibraryScreen(
                                modifier = Modifier.fillMaxSize(),
                                onBack = { navController.popBackStack() })
                        }
                        // Pipelines feature: library + editor share one
                        // OrchestratorViewModel scoped to the nested nav graph,
                        // so creating / renaming / duplicating in the library is
                        // immediately reflected when the editor opens.
                        navigation(
                            startDestination = "pipeline-library",
                            route = "pipelines",
                        ) {
                            composable("pipeline-library") { entry ->
                                val parentEntry = remember(entry) {
                                    navController.getBackStackEntry("pipelines")
                                }
                                val orchestratorViewModel: OrchestratorViewModel =
                                    hiltViewModel(parentEntry)
                                PipelineLibraryScreen(
                                    viewModel = orchestratorViewModel,
                                    onOpenEditor = {
                                        navController.navigate("pipeline-editor") {
                                            launchSingleTop = true
                                        }
                                    },
                                    onBack = { navController.popBackStack() },
                                )
                            }
                            composable("pipeline-editor") { entry ->
                                val parentEntry = remember(entry) {
                                    navController.getBackStackEntry("pipelines")
                                }
                                val orchestratorViewModel: OrchestratorViewModel =
                                    hiltViewModel(parentEntry)
                                VisualOrchestratorScreen(
                                    viewModel = orchestratorViewModel,
                                    onNavigateToPrompts = {
                                        navController.navigate("prompts") {
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            // Home doesn't ship with its own Scaffold, so it owns the
            // status- / navigation-bar insets directly to keep the buttons
            // clear of system surfaces.
            .systemBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(text = "Android AI Agent Home")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateToChat) {
            Text("Open Chat")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateToModels) {
            Text("Manage Models")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateToMemory) {
            Text("Memory Management")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateToTools) {
            Text("Manage Tools & MCP")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateToMonitoring) {
            Text("Monitor Tasks & Logs")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateToTaskMonitor) {
            Text("Manage Active Tasks")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateToSettings) {
            Text("Settings")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateToOrchestrator) {
            Text("Visual Orchestrator")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateToPrompts) {
            Text("Prompt Library")
        }
    }
}