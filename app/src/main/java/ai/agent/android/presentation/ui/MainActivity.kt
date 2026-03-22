package ai.agent.android.presentation.ui

import ai.agent.android.domain.usecases.InitializeAppUseCase
import ai.agent.android.data.services.AgentForegroundService
import ai.agent.android.presentation.theme.AndroidAIAgentTheme
import ai.agent.android.presentation.ui.chat.ChatScreen
import ai.agent.android.presentation.ui.chat.ChatViewModel
import ai.agent.android.presentation.ui.memory.MemoryScreen
import ai.agent.android.presentation.ui.models.ModelsScreen
import ai.agent.android.presentation.ui.monitoring.MonitoringScreen
import ai.agent.android.presentation.ui.monitoring.MonitoringViewModel
import ai.agent.android.presentation.ui.settings.SettingsScreen
import ai.agent.android.presentation.ui.tools.ToolsScreen
import ai.agent.android.presentation.ui.orchestrator.VisualOrchestratorScreen
import ai.agent.android.presentation.ui.orchestrator.OrchestratorViewModel
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var initializeAppUseCase: InitializeAppUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            initializeAppUseCase()
        }

        // Start the background agent service
        val serviceIntent = Intent(this, AgentForegroundService::class.java)
        startForegroundService(serviceIntent)

        enableEdgeToEdge()
        setContent {
            AndroidAIAgentTheme {
                val navController = rememberNavController()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("home") {
                            HomeScreen(
                                onNavigateToModels = { navController.navigate("models") },
                                onNavigateToMemory = { navController.navigate("memory") },
                                onNavigateToTools = { navController.navigate("tools") },
                                onNavigateToChat = { navController.navigate("chat") },
                                onNavigateToMonitoring = { navController.navigate("monitoring") },
                                onNavigateToSettings = { navController.navigate("settings") },
                                onNavigateToOrchestrator = { navController.navigate("orchestrator") },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        composable("chat") {
                            val chatViewModel: ChatViewModel = hiltViewModel()
                            ChatScreen(viewModel = chatViewModel)
                        }
                        composable("models") {
                            ModelsScreen(modifier = Modifier.fillMaxSize())
                        }
                        composable("memory") {
                            MemoryScreen()
                        }
                        composable("tools") {
                            ToolsScreen(modifier = Modifier.fillMaxSize())
                        }
                        composable("monitoring") {
                            val monitoringViewModel: MonitoringViewModel = hiltViewModel()
                            MonitoringScreen(viewModel = monitoringViewModel)
                        }
                        composable("settings") {
                            SettingsScreen(modifier = Modifier.fillMaxSize())
                        }
                        composable("orchestrator") {
                            val orchestratorViewModel: OrchestratorViewModel = hiltViewModel()
                            VisualOrchestratorScreen(viewModel = orchestratorViewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    onNavigateToModels: () -> Unit,
    onNavigateToMemory: () -> Unit,
    onNavigateToTools: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToMonitoring: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToOrchestrator: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
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
        Button(onClick = onNavigateToSettings) {
            Text("Settings")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateToOrchestrator) {
            Text("Visual Orchestrator")
        }
    }
}