package app.knotwork.design.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.knotwork.design.components.misc.KnotworkWarningBanner
import app.knotwork.design.components.topbar.KnotworkTopAppBarShell
import app.knotwork.design.icons.AppIcons
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/**
 * Everything the provider detail screen renders, with every string already
 * resolved by `:app`.
 *
 * Split out of `ProviderDetailScreen` because that screen was composed entirely
 * in `:app` and therefore had no visual baseline at all — which is how it grew a
 * look of its own (its own title style, a grey explanatory paragraph, bare
 * Material sliders) without anything noticing. There was nothing to compare it
 * against.
 *
 * The provider itself is not a parameter. `:app` resolves which inputs a
 * provider has — Ollama has a base URL and no API key, the rest have a key and a
 * model picker — into [ProviderDetailViewState], so this module never learns the
 * provider vocabulary and a new provider needs no change here.
 *
 * @param state Resolved copy and values.
 * @param modifier Layout modifier from the caller.
 * @param callbacks Edits and navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderDetailContent(
    state: ProviderDetailViewState,
    modifier: Modifier = Modifier,
    callbacks: ProviderDetailCallbacks = noopProviderDetailCallbacks(),
) {
    Scaffold(
        modifier = modifier.testTag(PROVIDER_DETAIL_ROOT_TEST_TAG),
        containerColor = MaterialTheme.colorScheme.surface,
        // The outer app shell already absorbs the system bars; defaulting to
        // safeDrawing here would double-count the insets.
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
                        IconButton(onClick = callbacks.onBack) {
                            Icon(
                                imageVector = AppIcons.Back,
                                contentDescription = state.backContentDescription,
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
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(KnotworkTheme.spacing.sp4),
            ) {
                KnotworkProviderRow(
                    title = state.providerLabel,
                    keyValue = state.apiKey.orEmpty(),
                    onKeyChange = callbacks.onApiKeyChange,
                    keyLabel = state.apiKeyLabel,
                    modelValue = state.model,
                    onModelChange = callbacks.onModelChange,
                    modelLabel = state.modelLabel,
                    availableModels = state.availableModels,
                    ollama = state.ollama,
                    onOllamaBaseUrlChange = callbacks.onOllamaBaseUrlChange,
                    onOllamaContextWindowChange = callbacks.onOllamaContextWindowChange,
                    // `null` is "this provider has no key", not "the key is
                    // empty" — Ollama runs LAN-local without authentication, and
                    // an empty key field would read as one the user forgot.
                    showApiKey = state.apiKey != null,
                )
                // Unencrypted LAN traffic is refused until the user says
                // otherwise for this exact address. A banner rather than a
                // dialog because the base URL persists on every keystroke — a
                // dialog would open mid-typing.
                state.cleartextConsent?.let { consent ->
                    KnotworkWarningBanner(
                        text = consent.body,
                        actionLabel = consent.actionLabel,
                        onAction = callbacks.onApproveCleartextOrigin,
                        testTag = CLEARTEXT_CONSENT_BANNER_TEST_TAG,
                    )
                }
                CloudRetrySection(state = state.retry, callbacks = callbacks)
            }
        }
    }
}

/**
 * The cloud-retry policy, shown on every provider because it applies to all of
 * them uniformly.
 *
 * Built from the settings components rather than raw Material ones. This section
 * had grown its own look — a title in `TitleMd`, a grey paragraph, `Text` + bare
 * `Slider` pairs — so a reader arriving from any other settings screen met a
 * different visual language and none of the help affordance the rest of Settings
 * had gained.
 *
 * @param state Resolved slider values, labels and bounds.
 * @param callbacks Slider edits.
 */
@Composable
private fun CloudRetrySection(state: CloudRetryViewState, callbacks: ProviderDetailCallbacks) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = KnotworkTheme.spacing.sp4),
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
    ) {
        CompositionLocalProvider(LocalSettingsRowAnchor provides state.sectionAnchor) {
            SettingsSectionLabel(text = state.sectionTitle) {
                SettingsHintGlyph(settingName = state.sectionTitle)
            }
            // The paragraph that used to sit here explained the policy in muted
            // body text — the slot this app reserves for machine state, and the
            // reason such text went unread. It is the section's hint now.
            SettingsHintBody()
        }
        CompositionLocalProvider(LocalSettingsRowAnchor provides state.attemptsAnchor) {
            KnotworkParamSlider(
                label = state.attemptsLabel,
                valueLabel = state.attemptsValueLabel,
                value = state.attempts.toFloat(),
                onValueChange = { callbacks.onRetryAttemptsChange(it.toInt()) },
                valueRange = state.attemptsRange,
                steps = state.attemptsSteps,
            )
        }
        CompositionLocalProvider(LocalSettingsRowAnchor provides state.delayAnchor) {
            KnotworkParamSlider(
                label = state.delayLabel,
                valueLabel = state.delayValueLabel,
                value = state.delayMs.toFloat(),
                onValueChange = { callbacks.onRetryDelayChange(it.toLong()) },
                valueRange = state.delayRange,
            )
        }
    }
}

/** Root test tag of the provider detail surface. */
const val PROVIDER_DETAIL_ROOT_TEST_TAG: String = "provider_detail_root"

/** Test tag of the cleartext-consent banner. */
const val CLEARTEXT_CONSENT_BANNER_TEST_TAG: String = "cleartext_consent_banner"
