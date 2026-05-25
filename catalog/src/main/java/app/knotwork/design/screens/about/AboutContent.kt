@file:Suppress("MatchingDeclarationName") // Hosts AboutContent + helpers.

package app.knotwork.design.screens.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import app.knotwork.design.components.brand.KnotworkLogo
import app.knotwork.design.components.brand.KnotworkLogoSize
import app.knotwork.design.components.buttons.KnotworkSecondaryButton
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/**
 * Stateless Knotwork About surface — hero brand mark + version / license /
 * acknowledgments / privacy cards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutContent(
    state: AboutViewState,
    modifier: Modifier = Modifier,
    strings: AboutStrings = AboutStrings(),
    callbacks: AboutCallbacks = noopAboutCallbacks(),
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { TopBar(strings = strings, callbacks = callbacks) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = KnotworkTheme.spacing.sp4,
                end = KnotworkTheme.spacing.sp4,
                top = KnotworkTheme.spacing.sp4,
                bottom = KnotworkTheme.spacing.sp6,
            ),
            verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp4),
        ) {
            item(key = "hero") { Hero(state = state) }
            item(key = "version") { VersionCard(state = state, strings = strings) }
            item(key = "license") {
                LicenseCard(state = state, strings = strings, onOpen = callbacks.onOpenLicense)
            }
            item(key = "ack-header") {
                SectionHeader(label = strings.sectionAcknowledgments)
            }
            items(items = state.acknowledgments, key = { it.name }) { entry ->
                AcknowledgmentRow(entry = entry)
            }
            item(key = "privacy") {
                PrivacyCard(state = state, strings = strings, onOpen = callbacks.onOpenPrivacyPolicy)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(strings: AboutStrings, callbacks: AboutCallbacks) {
    TopAppBar(
        title = {
            Text(text = strings.title, style = KnotworkTextStyles.TitleLg, color = MaterialTheme.colorScheme.onSurface)
        },
        navigationIcon = {
            IconButton(onClick = callbacks.onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = strings.backCd,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
private fun Hero(state: AboutViewState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
        modifier = Modifier.fillMaxWidth().padding(vertical = KnotworkTheme.spacing.sp4),
    ) {
        KnotworkLogo(size = KnotworkLogoSize.Lg)
        Text(
            text = state.appName,
            style = KnotworkTextStyles.Display2xl,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = state.tagline,
            style = KnotworkTextStyles.BodyBase,
            color = KnotworkTheme.extended.onSurfaceMuted,
        )
    }
}

@Composable
private fun VersionCard(state: AboutViewState, strings: AboutStrings) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(KnotworkTheme.shapes.md)
            .background(color = KnotworkTheme.extended.surface1)
            .padding(KnotworkTheme.spacing.sp4),
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
    ) {
        Text(
            text = strings.sectionVersion,
            style = KnotworkTextStyles.LabelSm,
            color = KnotworkTheme.extended.onSurfaceMuted,
        )
        Text(
            text = state.versionLine,
            style = KnotworkTextStyles.MonoBase,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = state.buildLine,
            style = KnotworkTextStyles.MonoSm,
            color = KnotworkTheme.extended.onSurfaceMuted,
        )
        Text(
            text = state.commitSha,
            style = KnotworkTextStyles.MonoSm,
            color = KnotworkTheme.extended.onSurfaceMuted,
        )
    }
}

@Composable
private fun LicenseCard(state: AboutViewState, strings: AboutStrings, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(KnotworkTheme.shapes.md)
            .background(color = KnotworkTheme.extended.surface1)
            .padding(KnotworkTheme.spacing.sp4),
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
    ) {
        Text(
            text = strings.sectionLicense,
            style = KnotworkTextStyles.LabelSm,
            color = KnotworkTheme.extended.onSurfaceMuted,
        )
        Text(
            text = state.licenseName,
            style = KnotworkTextStyles.TitleMd,
            color = MaterialTheme.colorScheme.onSurface,
        )
        KnotworkSecondaryButton(text = strings.licenseCta, onClick = onOpen)
    }
}

@Composable
private fun PrivacyCard(state: AboutViewState, strings: AboutStrings, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(KnotworkTheme.shapes.md)
            .background(color = KnotworkTheme.extended.surface1)
            .padding(KnotworkTheme.spacing.sp4),
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
    ) {
        Text(
            text = strings.sectionPrivacy,
            style = KnotworkTextStyles.LabelSm,
            color = KnotworkTheme.extended.onSurfaceMuted,
        )
        Text(
            text = state.privacyBody,
            style = KnotworkTextStyles.BodyBase,
            color = MaterialTheme.colorScheme.onSurface,
        )
        KnotworkSecondaryButton(text = strings.privacyCta, onClick = onOpen)
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label,
        style = KnotworkTextStyles.LabelSm,
        color = KnotworkTheme.extended.onSurfaceMuted,
        modifier = Modifier.fillMaxWidth().padding(top = KnotworkTheme.spacing.sp2),
    )
}

@Composable
private fun AcknowledgmentRow(entry: AcknowledgmentEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(KnotworkTheme.shapes.sm)
            .background(color = KnotworkTheme.extended.surface1)
            .padding(horizontal = KnotworkTheme.spacing.sp3, vertical = KnotworkTheme.spacing.sp3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.name,
            style = KnotworkTextStyles.BodyBase,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = entry.license,
            style = KnotworkTextStyles.MonoSm,
            color = KnotworkTheme.extended.onSurfaceMuted,
        )
    }
}
