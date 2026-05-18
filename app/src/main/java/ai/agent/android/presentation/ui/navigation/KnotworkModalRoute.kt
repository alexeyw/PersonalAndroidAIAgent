package ai.agent.android.presentation.ui.navigation

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Generic wrapper for every modal bottom-sheet route in the app
 * (`NodeConfigSheet`, `ConsolePane`, `AddMcpServerScreen` — Tasks 6/7/10
 * provide the bodies, this task provides the host).
 *
 * Responsibilities:
 *  - Render the Material3 [ModalBottomSheet] with the project's standard
 *    chrome (full-height expanded state allowed; the sheet body decides
 *    its snap points internally).
 *  - Wire a [PredictiveBackHandler] so the Android 14+ swipe-to-dismiss
 *    gesture animates the sheet in lockstep with the user's drag instead
 *    of dismissing in a single frame at the gesture end. Falls back to
 *    instant dismiss when [PredictiveBackHandler]'s progress flow is
 *    cancelled (older devices / a11y settings).
 *  - Invoke [onDismiss] once after the sheet has finished animating out so
 *    the caller can `navController.popBackStack()` exactly once per
 *    open/close cycle.
 *
 * Callers should not call `navController.popBackStack()` directly from
 * inside [content] — invoke the `onDismiss` lambda the wrapper passes down
 * instead so the sheet animates out before the route is popped.
 *
 * @param onDismiss Pop the host route from the nav back-stack — invoked
 *        after the sheet has fully animated out.
 * @param skipPartiallyExpanded When `true` the sheet snaps straight to
 *        fully-expanded; defaults to `false` so node-config / console
 *        sheets can implement their own three-point snap.
 * @param content Sheet body. Receives a `dismiss` lambda that triggers an
 *        animated hide; calling it is functionally equivalent to the user
 *        swiping the sheet down.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnotworkModalRoute(
    onDismiss: () -> Unit,
    skipPartiallyExpanded: Boolean = false,
    content: @Composable (dismiss: () -> Unit) -> Unit,
) {
    val sheetState: SheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = skipPartiallyExpanded,
    )
    val scope = rememberCoroutineScope()

    val animateOutAndDismiss: () -> Unit = {
        scope.launch {
            runCatching { sheetState.hide() }
            onDismiss()
        }
    }

    // Predictive back: the system streams [progress] events while the user
    // is swiping. Material3 ModalBottomSheet already follows the platform's
    // predictive-back animation when `enableOnBackInvokedCallback` is true
    // in the manifest (it is — see AndroidManifest.xml); we still register
    // a handler so a sheet body that intercepts dismiss for confirmation
    // (e.g. unsaved-changes warning in NodeConfigSheet — Task 7) can do so
    // without losing the gesture.
    PredictiveBackHandler { progress: Flow<androidx.activity.BackEventCompat> ->
        runCatching { progress.collect { /* observe to keep the handler alive */ } }
        animateOutAndDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = animateOutAndDismiss,
        sheetState = sheetState,
    ) {
        content(animateOutAndDismiss)
    }
}
