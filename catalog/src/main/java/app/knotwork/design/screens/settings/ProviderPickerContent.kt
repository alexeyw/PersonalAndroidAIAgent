package app.knotwork.design.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import app.knotwork.design.components.topbar.KnotworkTopAppBarShell
import app.knotwork.design.icons.AppIcons
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/**
 * The list of cloud providers a user can configure.
 *
 * A full screen rather than a bottom sheet so the predictive-back gesture works
 * without anchored-draggable plumbing.
 *
 * Rows arrive as resolved strings rather than as a provider enum: `:app` owns
 * which providers exist, and adding one must not reach this module.
 *
 * @param state Title and the rows to list.
 * @param modifier Layout modifier from the caller.
 * @param onPick A row was tapped, by its id.
 * @param onBack Pop back to Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderPickerContent(
    state: ProviderPickerViewState,
    modifier: Modifier = Modifier,
    onPick: (String) -> Unit = {},
    onBack: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier.testTag(PROVIDER_PICKER_ROOT_TEST_TAG),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(left = 0, top = 0, right = 0, bottom = 0),
        topBar = {
            KnotworkTopAppBarShell {
                TopAppBar(
                    title = {
                        Text(
                            text = state.title,
                            style = KnotworkTextStyles.TitleMd,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = AppIcons.Back,
                                contentDescription = state.backContentDescription,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(KnotworkTheme.spacing.sp4),
            verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        ) {
            state.rows.forEach { row ->
                ProviderPickerRow(title = row.title, onClick = { onPick(row.id) })
            }
        }
    }
}

/**
 * One tappable provider row.
 *
 * @param title The provider's name.
 * @param onClick Row tapped.
 */
@Composable
private fun ProviderPickerRow(title: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = KnotworkTheme.shapes.md,
        color = KnotworkTheme.extended.surface1,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(KnotworkTheme.spacing.sp4),
        ) {
            Text(
                text = title,
                style = KnotworkTextStyles.BodyBase.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Root test tag of the provider picker surface. */
const val PROVIDER_PICKER_ROOT_TEST_TAG: String = "provider_picker_root"
