package app.knotwork.android.presentation.tile

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import androidx.core.content.getSystemService
import app.knotwork.android.R
import timber.log.Timber

/**
 * Prompts the user to add the [DutyPipelineTileService] to their Quick Settings
 * via `StatusBarManager.requestAddTileService` (available on every supported
 * device — `minSdk` is well above Android 13).
 *
 * Called *in context* — right after the user binds a pipeline to the tile in
 * Settings — which is exactly the moment the platform recommends asking. The app
 * must be in the foreground for the request to show; the system may also
 * auto-deny after repeated dismissals, so the result is best-effort and silent.
 */
fun requestAddDutyTile(context: Context) {
    val statusBar = context.getSystemService<StatusBarManager>() ?: return
    try {
        statusBar.requestAddTileService(
            ComponentName(context, DutyPipelineTileService::class.java),
            context.getString(R.string.tile_label),
            Icon.createWithResource(context, R.drawable.ic_tile_duty),
            { runnable -> runnable.run() },
            { /* result code ignored — purely an offer */ },
        )
    } catch (e: IllegalStateException) {
        // Thrown when the app is not in the foreground; the user can still add
        // the tile manually from the Quick Settings editor.
        Timber.w(e, "Could not request adding the Quick Settings tile.")
    }
}
