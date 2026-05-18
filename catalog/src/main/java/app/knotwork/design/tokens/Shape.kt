@file:Suppress(
    "MagicNumber", // Token file — corner radii ARE the shape scale.
    "MatchingDeclarationName", // File hosts `KnotworkShapes`, `MaterialKnotworkShapes`, `LocalKnotworkShapes`.
)

package app.knotwork.design.tokens

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * Knotwork shape scale — `xs / sm / md / lg / xl / full`.
 *
 * Mapped onto Material3 [Shapes] so callers that consume the standard
 * `MaterialTheme.shapes.{extraSmall…extraLarge}` slots get the Knotwork
 * radii without any per-call ceremony:
 *
 *  - `extraSmall` → [xs] (4 dp)
 *  - `small`      → [sm] (8 dp)
 *  - `medium`     → [md] (12 dp)
 *  - `large`      → [lg] (16 dp)
 *  - `extraLarge` → [xl] (24 dp)
 *
 * Use [full] (~999 dp) for pill-style chips and circular avatars.
 *
 * @property xs 4 dp rounded shape.
 * @property sm 8 dp rounded shape.
 * @property md 12 dp rounded shape.
 * @property lg 16 dp rounded shape.
 * @property xl 24 dp rounded shape.
 * @property full effectively-circular shape for pills.
 */
@Immutable
data class KnotworkShapes(
    val xs: CornerBasedShape = RoundedCornerShape(4.dp),
    val sm: CornerBasedShape = RoundedCornerShape(8.dp),
    val md: CornerBasedShape = RoundedCornerShape(12.dp),
    val lg: CornerBasedShape = RoundedCornerShape(16.dp),
    val xl: CornerBasedShape = RoundedCornerShape(24.dp),
    val full: CornerBasedShape = RoundedCornerShape(999.dp),
)

/** Singleton instance of the default [KnotworkShapes]. */
internal val DefaultKnotworkShapes = KnotworkShapes()

/**
 * Material3 [Shapes] populated from [DefaultKnotworkShapes].
 * Exposed for the `KnotworkTheme` composable to wire into [androidx.compose.material3.MaterialTheme].
 */
val MaterialKnotworkShapes: Shapes = Shapes(
    extraSmall = DefaultKnotworkShapes.xs,
    small = DefaultKnotworkShapes.sm,
    medium = DefaultKnotworkShapes.md,
    large = DefaultKnotworkShapes.lg,
    extraLarge = DefaultKnotworkShapes.xl,
)

/**
 * Composition-local provider for [KnotworkShapes].
 * Always set by the `KnotworkTheme` wrapper — read via `KnotworkTheme.shapes`.
 */
val LocalKnotworkShapes = staticCompositionLocalOf { DefaultKnotworkShapes }
