package app.knotwork.design.components.topbar

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.knotwork.design.theme.KnotworkTheme

/**
 * Wraps an arbitrary `TopAppBar`-style [bar] composable in a [Column]
 * and appends a 1-dp `HorizontalDivider` tinted with
 * [KnotworkTheme.extended.divider] underneath.
 *
 * The Knotwork TopAppBar has no tonal elevation by design — it sits on
 * the same surface as the screen body — so without this explicit hairline
 * the bar bleeds into the content on scroll. Every screen Scaffold's
 * `topBar` slot should wire through this helper instead of nesting
 * `TopAppBar(…)` directly.
 *
 * @param modifier optional layout modifier applied to the wrapper column.
 * @param bar the actual `TopAppBar` (or any custom toolbar composable)
 * to render above the divider.
 */
@Composable
fun KnotworkTopAppBarShell(modifier: Modifier = Modifier, bar: @Composable () -> Unit) {
    Column(modifier = modifier) {
        bar()
        HorizontalDivider(color = KnotworkTheme.extended.divider)
    }
}
