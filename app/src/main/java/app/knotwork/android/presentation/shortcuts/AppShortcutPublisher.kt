package app.knotwork.android.presentation.shortcuts

import android.content.Context
import android.content.Intent
import androidx.annotation.WorkerThread
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import app.knotwork.android.R
import app.knotwork.android.domain.models.DynamicShortcutSpec
import app.knotwork.android.domain.repositories.ChatRepository
import app.knotwork.android.domain.usecases.BuildDynamicShortcutsUseCase
import app.knotwork.android.presentation.ui.MainActivity
import app.knotwork.android.presentation.ui.navigation.NavRoutes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publishes the dynamic launcher shortcuts (recent chat sessions) from the
 * latest session list.
 *
 * The *what* (which sessions, labels, ordering, caps) is decided by the pure
 * [BuildDynamicShortcutsUseCase]; this class only performs the framework-bound
 * conversion to `ShortcutInfoCompat` and the publish, which `ShortcutManagerCompat`
 * documents as a `@WorkerThread` call. Each shortcut deep-links into its session
 * through the `knotwork://chat/{threadId}` pattern, so a tap reopens the exact
 * conversation. Static shortcuts (New chat / Pipelines) are declared in
 * `res/xml/shortcuts.xml` and are not touched here.
 */
@Singleton
class AppShortcutPublisher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatRepository: ChatRepository,
    private val buildDynamicShortcuts: BuildDynamicShortcutsUseCase,
) {

    /**
     * Recomputes and publishes the dynamic shortcut set from the current
     * sessions. Best-effort: any failure is logged and swallowed so a launcher
     * quirk never crashes the app. Must run off the main thread.
     */
    @WorkerThread
    suspend fun refresh() {
        try {
            val sessions = chatRepository.getSessionsFlow().first()
            val specs = buildDynamicShortcuts(sessions)
            ShortcutManagerCompat.setDynamicShortcuts(context, specs.map { it.toShortcutInfo() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to publish dynamic shortcuts.")
        }
    }

    /** Converts a [DynamicShortcutSpec] into a launcher [ShortcutInfoCompat]. */
    private fun DynamicShortcutSpec.toShortcutInfo(): ShortcutInfoCompat {
        val intent = Intent(
            Intent.ACTION_VIEW,
            "${NavRoutes.DEEP_LINK_SCHEME}://${NavRoutes.chatRoute(sessionId)}".toUri(),
            context,
            MainActivity::class.java,
        )
        return ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(shortLabel)
            .setLongLabel(longLabel)
            .setRank(rank)
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_new_chat))
            .setIntent(intent)
            .build()
    }
}
