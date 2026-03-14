package ai.agent.android.presentation.ui

import ai.agent.android.domain.usecases.InitializeAppUseCase
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import ai.agent.android.presentation.theme.AndroidAIAgentTheme
import ai.agent.android.presentation.ui.chat.ChatScreen
import ai.agent.android.presentation.ui.chat.ChatViewModel
import ai.agent.android.presentation.ui.memory.MemoryScreen
import ai.agent.android.presentation.ui.models.ModelsScreen
import ai.agent.android.presentation.ui.tools.ToolsScreen
import ai.agent.android.presentation.ui.monitoring.MonitoringScreen
import ai.agent.android.presentation.ui.monitoring.MonitoringViewModel
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp)
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
    }
}