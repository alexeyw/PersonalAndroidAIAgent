package app.knotwork.design.components.chat

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Asymmetric chat-bubble shapes consumed by [ChatMessage].
 *
 * Visual contract:
 *  - User bubble: trailing-bottom corner tightens to 4 dp, the other three
 *    stay at 16 dp — giving the bubble a clear "anchored to the user side"
 *    silhouette.
 *  - Assistant bubble: mirror image — leading-bottom corner tightens.
 *
 * These are chat-specific shape primitives; they intentionally do not flow
 * through `MaterialTheme.shapes` / `KnotworkTheme.shapes` so the regular
 * shape scale stays a generic 4-step ramp.
 */
object ChatBubbleShapes {

    /** Tight-corner radius (`4 dp`) on the assistant's leading bottom corner. */
    private val TightCornerRadius = 4.dp

    /** Default radius (`16 dp`) on the three open corners of the assistant bubble. */
    private val OpenCornerRadius = 16.dp

    /** Full pill radius applied to every corner of the user bubble. */
    private val UserCornerRadius = 22.dp

    /**
     * User-side bubble shape: uniformly rounded pill so the user's
     * contribution reads as a quick spoken-style chip in the thread.
     */
    val User = RoundedCornerShape(size = UserCornerRadius)

    /**
     * Assistant-side bubble shape: 16 dp on top-start, top-end, bottom-end;
     * 4 dp on bottom-start (the leading corner closest to the assistant).
     */
    val Assistant = RoundedCornerShape(
        topStart = OpenCornerRadius,
        topEnd = OpenCornerRadius,
        bottomEnd = OpenCornerRadius,
        bottomStart = TightCornerRadius,
    )
}
