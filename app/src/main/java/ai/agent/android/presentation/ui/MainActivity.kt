package ai.agent.android.presentation.ui

import ai.agent.android.data.services.AgentForegroundService
import ai.agent.android.data.services.MemoryCompactionScheduler
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.services.MemoryReembedScheduler
import ai.agent.android.presentation.state.TransientMessageRelay
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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The main activity of the application, serving as the entry point.
 * It sets up the platform splash, edge-to-edge insets, foreground service,
 * and the Compose nav graph hosted inside [AppShellScaffold].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var transientMessageRelay: TransientMessageRelay

    @Inject lateinit var memoryCompactionScheduler: MemoryCompactionScheduler

    @Inject lateinit var memoryReembedScheduler: MemoryReembedScheduler

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

        // Schedule background long-term-memory maintenance off the main thread:
        // a daily charging + idle compaction pass plus an out-of-schedule watch
        // that drains the table when it grows past the configured hard limit.
        // Both calls are idempotent, so re-running them on activity recreation
        // is harmless.
        lifecycleScope.launch(Dispatchers.Default) {
            memoryCompactionScheduler.schedulePeriodic()
            memoryCompactionScheduler.startHardLimitWatch()
            // Self-heal: re-arm the import re-embed pass if a prior one-off was
            // lost or exhausted its retries. The check lives in the scheduler so
            // recovery isn't tied to this one entry point (the foreground service
            // re-arms too).
            memoryReembedScheduler.rearmIfPending()
        }

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
                // Onboarding gate: invert `hasCompletedOnboarding` instead
                // of reusing `isFirstLaunch` — the latter is cleared by
                // `InitializeAppUseCase` during cold-start init (which
                // runs while the splash screen is still in front), so by
                // the time `SplashScreen.onInitialized` fires it has
                // already been flipped to `false`. The dedicated
                // `hasCompletedOnboarding` flag survives initialization
                // and is the right gate for the UI surface. Re-emits if
                // the user resets onboarding from Settings (wired in
                // Task 10). Default `initial = false` (i.e. "treat as
                // returning user until DataStore confirms otherwise") so
                // we never flash onboarding for a returning user during
                // the brief read window; on a fresh install, the splash
                // screen blocks long enough for DataStore to emit the
                // real `false` value before the navigation decision.
                val hasCompletedOnboarding by settingsRepository.hasCompletedOnboarding
                    .collectAsState(initial = false)

                AppShellScaffold(
                    navController = navController,
                    transientMessageRelay = transientMessageRelay,
                ) { innerPadding ->
                    AppNavGraph(
                        navController = navController,
                        showOnboarding = !hasCompletedOnboarding,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}
