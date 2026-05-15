@file:Suppress(
    "MatchingDeclarationName", // File hosts `KnotworkA11y` plus its CompositionLocal and helpers.
)

package app.knotwork.design.a11y

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * Knotwork accessibility scaffolding consumed by every component that needs to
 * react to system-wide a11y settings (reduced motion, font scale).
 *
 * Components MUST go through [KnotworkA11y] (re-exported as
 * `KnotworkTheme.a11y`) instead of touching `LocalConfiguration` or
 * `Settings.Global` directly. Centralising the read keeps the rules in
 * `decisions.md §14` enforceable in one place and lets tests inject a
 * deterministic implementation via [LocalKnotworkA11y].
 *
 * The default implementation is [DefaultKnotworkA11y]; tests may install
 * [FixedKnotworkA11y] to pin a particular `reducedMotion` / `fontScale`
 * state for snapshot determinism.
 */
@Stable
interface KnotworkA11y {
    /**
     * Returns `true` when the user has disabled (or near-disabled) animations
     * system-wide. Components MUST collapse every animation longer than
     * `motionSm` (180 ms) to either an instant state change or an alpha-only
     * 80 ms crossfade per `decisions.md §14`.
     *
     * @return `true` if the system reports `TRANSITION_ANIMATION_SCALE == 0f`
     * or `ANIMATOR_DURATION_SCALE == 0f`.
     */
    @Composable
    fun reducedMotion(): Boolean

    /**
     * Returns the user's chosen text-scale factor. Mirrors
     * `LocalConfiguration.current.fontScale`.
     *
     * Components MUST lay out cleanly up to `2.0f`; above that they may clamp.
     *
     * @return scale factor (1.0 = system default, 2.0 = "Largest" preset).
     */
    @Composable
    fun fontScale(): Float
}

/**
 * Production [KnotworkA11y] implementation backed by the system
 * `Settings.Global` flags and `LocalConfiguration`.
 *
 * `reducedMotion()` registers a [ContentObserver] for both the
 * `TRANSITION_ANIMATION_SCALE` and `ANIMATOR_DURATION_SCALE` keys so the
 * surrounding composition recomposes when the user toggles "Remove
 * animations" in system settings while the app is in the foreground.
 */
object DefaultKnotworkA11y : KnotworkA11y {

    @Composable
    override fun reducedMotion(): Boolean {
        val context = LocalContext.current
        val resolver = context.contentResolver
        var disabled by remember { mutableStateOf(readReducedMotion(resolver)) }
        DisposableEffect(resolver) {
            val handler = Handler(Looper.getMainLooper())
            val observer = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    disabled = readReducedMotion(resolver)
                }
            }
            resolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.TRANSITION_ANIMATION_SCALE),
                /* notifyForDescendants = */
                false,
                observer,
            )
            resolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                /* notifyForDescendants = */
                false,
                observer,
            )
            onDispose { resolver.unregisterContentObserver(observer) }
        }
        return disabled
    }

    @Composable
    override fun fontScale(): Float = LocalConfiguration.current.fontScale

    private fun readReducedMotion(resolver: android.content.ContentResolver): Boolean {
        val transition = Settings.Global.getFloat(
            resolver,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            1f,
        )
        val animator = Settings.Global.getFloat(
            resolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        return transition == 0f || animator == 0f
    }
}

/**
 * Test-friendly [KnotworkA11y] that returns the values it was constructed
 * with. Install via `CompositionLocalProvider(LocalKnotworkA11y provides
 * FixedKnotworkA11y(reducedMotion = true)) { ... }` to snapshot the
 * reduced-motion variant of any component without touching `Settings.Global`.
 *
 * @property reducedMotion fixed value returned from [reducedMotion].
 * @property fontScale fixed value returned from [fontScale]; defaults to 1.0
 * (system default).
 */
@Stable
data class FixedKnotworkA11y(val reducedMotion: Boolean = false, val fontScale: Float = 1f) : KnotworkA11y {

    @Composable
    override fun reducedMotion(): Boolean = reducedMotion

    @Composable
    override fun fontScale(): Float = fontScale
}

/**
 * Composition-local provider for [KnotworkA11y].
 *
 * Defaults to [DefaultKnotworkA11y] so previews that forget to wrap in
 * `KnotworkTheme { ... }` still resolve the production implementation. The
 * `KnotworkTheme` composable explicitly re-installs the default to make the
 * dependency obvious in a code search; tests override it via
 * `CompositionLocalProvider(LocalKnotworkA11y provides FixedKnotworkA11y(…))`.
 */
val LocalKnotworkA11y = staticCompositionLocalOf<KnotworkA11y> { DefaultKnotworkA11y }
