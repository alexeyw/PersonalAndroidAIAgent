package ai.agent.android.presentation.ui.about

import ai.agent.android.BuildConfig
import ai.agent.android.R
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import app.knotwork.design.screens.about.AboutCallbacks
import app.knotwork.design.screens.about.AboutContent
import app.knotwork.design.screens.about.AboutStrings
import app.knotwork.design.screens.about.AboutViewState
import app.knotwork.design.screens.about.AcknowledgmentEntry

/**
 * App-side About surface. Renders the Knotwork [AboutContent] with brand
 * metadata pulled from [BuildConfig] and a hand-maintained acknowledgments
 * list (`PHASE 22 — Task 15`: the auto-discovery alternative would pull in
 * a heavy dependency and is deferred to a follow-up).
 */
@Composable
fun AboutScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val state = AboutViewState(
        appName = stringResource(R.string.app_name),
        tagline = stringResource(R.string.about_tagline),
        versionLine = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
        buildLine = stringResource(R.string.about_build_format, BuildConfig.VERSION_CODE, BuildConfig.BUILD_TYPE),
        commitSha = BuildConfig.GIT_SHA,
        licenseName = stringResource(R.string.license_name),
        acknowledgments = AboutAcknowledgments.entries,
        privacyBody = stringResource(R.string.about_privacy_policy_body),
    )
    val strings = AboutStrings(
        title = stringResource(R.string.about_title),
        backCd = stringResource(R.string.common_back),
        sectionVersion = stringResource(R.string.about_section_app),
        sectionLicense = stringResource(R.string.about_section_license),
        sectionAcknowledgments = stringResource(R.string.about_section_acknowledgments),
        sectionPrivacy = stringResource(R.string.about_section_privacy_policy),
        licenseCta = stringResource(R.string.about_open_license_cta),
        privacyCta = stringResource(R.string.about_open_privacy_cta),
    )
    AboutContent(
        state = state,
        modifier = modifier,
        strings = strings,
        callbacks = AboutCallbacks(
            onBack = onBack,
            onOpenLicense = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, LICENSE_URL.toUri()))
                }
            },
            onOpenPrivacyPolicy = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_URL.toUri()))
                }
            },
        ),
    )
}

/** Public Apache 2.0 license URL — same the manifest already declares via `license_url`. */
private const val LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0"

/**
 * Privacy policy URL — points at the repo README until a dedicated page
 * ships. Replace once the privacy.html page is published.
 */
private const val PRIVACY_URL = "https://github.com/alexeyw/PersonalAndroidAIAgent#privacy"

/** Hand-maintained acknowledgments list (15 key dependencies). */
private object AboutAcknowledgments {
    val entries: List<AcknowledgmentEntry> = listOf(
        AcknowledgmentEntry(name = "Jetpack Compose", license = "Apache 2.0"),
        AcknowledgmentEntry(name = "Kotlin Coroutines", license = "Apache 2.0"),
        AcknowledgmentEntry(name = "Hilt", license = "Apache 2.0"),
        AcknowledgmentEntry(name = "Room", license = "Apache 2.0"),
        AcknowledgmentEntry(name = "Koog", license = "Apache 2.0"),
        AcknowledgmentEntry(name = "LiteRT-LM", license = "Apache 2.0"),
        AcknowledgmentEntry(name = "Retrofit", license = "Apache 2.0"),
        AcknowledgmentEntry(name = "Coil", license = "Apache 2.0"),
        AcknowledgmentEntry(name = "Roborazzi", license = "Apache 2.0"),
        AcknowledgmentEntry(name = "MockK", license = "Apache 2.0"),
        AcknowledgmentEntry(name = "SQLCipher", license = "BSD-3-Clause"),
        AcknowledgmentEntry(name = "Firebase Crashlytics", license = "Apache 2.0"),
        AcknowledgmentEntry(name = "MediaPipe", license = "Apache 2.0"),
        AcknowledgmentEntry(name = "Timber", license = "Apache 2.0"),
        AcknowledgmentEntry(name = "ProcessPhoenix", license = "Apache 2.0"),
    )
}
