package app.knotwork.android.presentation.ui.settings.provider

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import app.knotwork.android.R
import app.knotwork.android.domain.models.ProviderId
import app.knotwork.design.icons.AppIcons
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/**
 * Minimal provider picker shown when the user taps Settings →
 * "+ Add provider". Lists the 5 known providers; tapping a row routes
 * to [ProviderDetailScreen] for that provider.
 *
 * v0.1 keeps the picker as a full screen rather than a bottom-sheet so
 * the predictive-back gesture works without bottom-sheet anchored-
 * draggable plumbing. The richer bottom-sheet picker is a follow-up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderPickerScreen(onPick: (ProviderId) -> Unit, onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(left = 0, top = 0, right = 0, bottom = 0),
        topBar = {
            app.knotwork.design.components.topbar.KnotworkTopAppBarShell {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.settings_provider_picker_title),
                            style = KnotworkTextStyles.TitleMd,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = AppIcons.Back,
                                contentDescription = stringResource(R.string.common_back),
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
            ProviderId.entries.forEach { providerId ->
                ProviderPickerRow(
                    title = providerTitle(providerId),
                    onClick = { onPick(providerId) },
                )
            }
        }
    }
}

@Composable
private fun ProviderPickerRow(title: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = KnotworkTheme.shapes.md,
        color = KnotworkTheme.extended.surface1,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(KnotworkTheme.spacing.sp4),
        ) {
            Text(
                text = title,
                style = KnotworkTextStyles.BodyBase.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = AppIcons.ArrowR,
                contentDescription = null,
                tint = KnotworkTheme.extended.onSurfaceMuted,
            )
        }
    }
}

private fun providerTitle(id: ProviderId): String = when (id) {
    ProviderId.OpenAi -> "OpenAI"
    ProviderId.Anthropic -> "Anthropic"
    ProviderId.Google -> "Google"
    ProviderId.DeepSeek -> "DeepSeek"
    ProviderId.Ollama -> "Ollama"
}
