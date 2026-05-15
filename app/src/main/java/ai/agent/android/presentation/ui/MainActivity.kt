package ai.agent.android.presentation.ui

import ai.agent.android.data.services.AgentForegroundService
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.presentation.theme.AndroidAIAgentTheme
import ai.agent.android.presentation.ui.navigation.AppNavGraph
import ai.agent.android.presentation.ui.navigation.AppShellScaffold
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The main activity of the application, serving as the entry point.
 * It sets up the platform splash, edge-to-edge insets, foreground service,
 * and the Compose nav graph hosted inside [AppShellScaffold].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _: Boolean ->
        // Permission outcome is observed lazily by features that need it.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Platform splash (Android 12+) — must be installed BEFORE
        // `super.onCreate` so the system swaps the activity-theme splash
        // window for the SplashScreen-managed window before any content
        // attempts to draw. `Theme.App.Splash` supplies the accent-500
        // background and the brand-mark foreground; the post-splash theme
        // declared there takes over as soon as the first Compose frame
        // commits, so the platform splash hides exactly when the in-app
        // [SplashScreen][ai.agent.android.presentation.ui.splash.SplashScreen]
        // composable becomes visible — no blank-frame flash.
        installSplashScreen()
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Start the background agent service. Application init (incl. the
        // first-launch defaults that used to live here) is now driven by
        // the splash screen via `AppInitializationUseCase`.
        val serviceIntent = Intent(this, AgentForegroundService::class.java)
        startForegroundService(serviceIntent)

        // Phase 21 / Task 1/11: pin transparent status- and navigation-bar
        // scrims so the Knotwork design system can paint surfaces all the
        // way to the device edges. `SystemBarStyle.auto(...)` flips between
        // the light- and dark-content variants based on the current theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            AndroidAIAgentTheme {
                val navController = rememberNavController()
                // The `isFirstLaunch` flag is observed once at composition
                // and re-emits if the user resets onboarding from Settings
                // (wired in Task 10). The default `initial = false`
                // matches the not-first-launch case so we don't flash the
                // onboarding screen while DataStore is still loading on
                // cold start — the splash screen blocks until init
                // finishes, then SplashScreen.onInitialized re-reads the
                // (now-cached) flag through the captured value.
                val isFirstLaunch by settingsRepository.isFirstLaunch
                    .collectAsState(initial = true)

                AppShellScaffold(navController = navController) { innerPadding ->
                    AppNavGraph(
                        navController = navController,
                        isFirstLaunch = isFirstLaunch,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}
