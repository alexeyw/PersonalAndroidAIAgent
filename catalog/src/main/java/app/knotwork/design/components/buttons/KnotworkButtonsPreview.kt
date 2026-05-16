package app.knotwork.design.components.buttons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.knotwork.design.theme.KnotworkTheme

/**
 * Composable harness exercising every [KnotworkPrimaryButton],
 * [KnotworkSecondaryButton], [KnotworkTextButton], and [KnotworkIconButton]
 * variant in a single column. Used by the Android Studio preview pane and by
 * the `:catalog` Roborazzi snapshot baseline so a regression in any button
 * state surfaces in the same diff.
 *
 * Renders inside the parent [KnotworkTheme]; callers (preview / test) pin
 * `darkTheme` deterministically.
 */
@Composable
fun KnotworkButtonsCatalogContent() {
    Surface(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(KnotworkTheme.spacing.sp4),
            verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp4),
        ) {
            // Primary button — idle / loading / disabled / leading icon.
            KnotworkPrimaryButton(text = "Save changes", onClick = {})
            KnotworkPrimaryButton(text = "Sending…", onClick = {}, loading = true)
            KnotworkPrimaryButton(text = "Save changes", onClick = {}, enabled = false)
            KnotworkPrimaryButton(
                text = "Send",
                onClick = {},
                leadingIcon = Icons.Filled.ArrowUpward,
            )

            // Secondary button — default / destructive / loading / disabled.
            KnotworkSecondaryButton(text = "Cancel", onClick = {})
            KnotworkSecondaryButton(text = "Reject", onClick = {}, destructive = true)
            KnotworkSecondaryButton(text = "Loading", onClick = {}, loading = true)
            KnotworkSecondaryButton(text = "Disabled", onClick = {}, enabled = false)

            // Text button — default / destructive / disabled.
            KnotworkTextButton(text = "Always allow", onClick = {})
            KnotworkTextButton(text = "Delete pipeline", onClick = {}, destructive = true)
            KnotworkTextButton(text = "Disabled", onClick = {}, enabled = false)

            // Icon button row — bare / with badge / overflow badge / disabled.
            Row(horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2)) {
                KnotworkIconButton(
                    onClick = {},
                    contentDescription = "Open console",
                    icon = Icons.Outlined.Terminal,
                )
                KnotworkIconButton(
                    onClick = {},
                    contentDescription = "Search messages",
                    icon = Icons.Outlined.Search,
                    badge = 3,
                )
                KnotworkIconButton(
                    onClick = {},
                    contentDescription = "Search messages",
                    icon = Icons.Outlined.Search,
                    badge = 42,
                )
                KnotworkIconButton(
                    onClick = {},
                    contentDescription = "Open console",
                    icon = Icons.Outlined.Terminal,
                    enabled = false,
                )
            }
        }
    }
}

/** Light-theme preview pinning `darkTheme = false` for deterministic rendering. */
@Preview(name = "Buttons — Light", showBackground = true, heightDp = 720)
@Composable
private fun KnotworkButtonsLightPreview() {
    KnotworkTheme(darkTheme = false) { KnotworkButtonsCatalogContent() }
}

/** Dark-theme preview pinning `darkTheme = true` for deterministic rendering. */
@Preview(name = "Buttons — Dark", showBackground = true, heightDp = 720)
@Composable
private fun KnotworkButtonsDarkPreview() {
    KnotworkTheme(darkTheme = true) { KnotworkButtonsCatalogContent() }
}
