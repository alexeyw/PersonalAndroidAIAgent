package ai.agent.android.presentation.ui.about

import ai.agent.android.BuildConfig
import ai.agent.android.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/**
 * About surface — Phase 22 / Task 9 expansion.
 *
 * Owns the license link that used to live in Settings (per task 9 brief
 * the Settings screen no longer carries license metadata). The full
 * Knotwork-look pass arrives in task 15; this file already ships
 * everything users need before the v0.1 release: version, git commit,
 * license name, acknowledgments, privacy summary.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.about_title),
                        style = KnotworkTextStyles.TitleLg,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(KnotworkTheme.spacing.sp4),
            verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp4),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = KnotworkTextStyles.TitleLg.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            SectionCard(title = stringResource(R.string.about_section_app)) {
                LabeledRow(
                    label = stringResource(R.string.about_section_app),
                    value = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                )
                LabeledRow(
                    label = stringResource(R.string.about_section_commit),
                    value = BuildConfig.GIT_SHA,
                )
                LabeledRow(
                    label = stringResource(R.string.about_section_license),
                    value = stringResource(R.string.license_name),
                )
            }
            SectionCard(title = stringResource(R.string.about_section_acknowledgments)) {
                Text(
                    text = stringResource(R.string.about_acknowledgments_body),
                    style = KnotworkTextStyles.BodyBase,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            SectionCard(title = stringResource(R.string.about_section_privacy_policy)) {
                Text(
                    text = stringResource(R.string.about_privacy_policy_body),
                    style = KnotworkTextStyles.BodyBase,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = KnotworkTheme.shapes.md,
        color = KnotworkTheme.extended.surface1,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(KnotworkTheme.spacing.sp4),
            verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        ) {
            Text(
                text = title.uppercase(),
                style = KnotworkTextStyles.LabelSm.copy(fontWeight = FontWeight.SemiBold),
                color = KnotworkTheme.extended.onSurfaceMuted,
            )
            content()
        }
    }
}

@Composable
private fun LabeledRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1)) {
        Text(
            text = label,
            style = KnotworkTextStyles.BodySm,
            color = KnotworkTheme.extended.onSurfaceMuted,
        )
        Text(
            text = value,
            style = KnotworkTextStyles.MonoSm,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
