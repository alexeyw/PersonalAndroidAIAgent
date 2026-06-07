package app.knotwork.android.presentation.ui.about

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import app.knotwork.android.BuildConfig
import app.knotwork.android.R
import app.knotwork.design.screens.about.AboutCallbacks
import app.knotwork.design.screens.about.AboutContent
import app.knotwork.design.screens.about.AboutStrings
import app.knotwork.design.screens.about.AboutViewState
import app.knotwork.design.screens.about.AcknowledgmentEntry

/**
 * App-side About surface. Renders the Knotwork [AboutContent] with brand
 * metadata pulled from [BuildConfig] and a hand-maintained acknowledgments
 * list (the auto-discovery alternative would pull in
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
        acknowledgments = AboutAcknowledgments.ENTRIES,
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

/**
 * Hand-maintained acknowledgments for the third-party components bundled in the
 * released APK/AAB, plus the bundled brand fonts.
 *
 * This list is the user-facing companion of the repository `NOTICE` file and is
 * reconciled against the actual `gradle/libs.versions.toml` runtime dependency
 * set. It intentionally excludes test-only artifacts
 * (MockK, Roborazzi, Robolectric, JUnit, org.json) since they are not
 * distributed with the application. `internal` so the
 * `AboutAcknowledgmentsTest` drift guard can read it.
 */
internal object AboutAcknowledgments {
    /** SPDX-style label for components under the Apache License 2.0. */
    private const val APACHE_2_0 = "Apache 2.0"

    val ENTRIES: List<AcknowledgmentEntry> = listOf(
        AcknowledgmentEntry(name = "Jetpack Compose", license = APACHE_2_0),
        AcknowledgmentEntry(name = "AndroidX Jetpack", license = APACHE_2_0),
        AcknowledgmentEntry(name = "Kotlin Coroutines", license = APACHE_2_0),
        AcknowledgmentEntry(name = "Hilt", license = APACHE_2_0),
        AcknowledgmentEntry(name = "Room", license = APACHE_2_0),
        AcknowledgmentEntry(name = "AppFunctions", license = APACHE_2_0),
        AcknowledgmentEntry(name = "LiteRT-LM", license = APACHE_2_0),
        AcknowledgmentEntry(name = "MediaPipe", license = APACHE_2_0),
        AcknowledgmentEntry(name = "Universal Sentence Encoder", license = APACHE_2_0),
        AcknowledgmentEntry(name = "Koog", license = APACHE_2_0),
        AcknowledgmentEntry(name = "Ktor", license = APACHE_2_0),
        AcknowledgmentEntry(name = "OkHttp", license = APACHE_2_0),
        AcknowledgmentEntry(name = "Gson", license = APACHE_2_0),
        AcknowledgmentEntry(name = "Multiplatform Markdown Renderer", license = APACHE_2_0),
        AcknowledgmentEntry(name = "Timber", license = APACHE_2_0),
        AcknowledgmentEntry(name = "ProcessPhoenix", license = APACHE_2_0),
        AcknowledgmentEntry(name = "Firebase Crashlytics", license = APACHE_2_0),
        AcknowledgmentEntry(name = "SQLCipher", license = "BSD-3-Clause"),
        AcknowledgmentEntry(name = "Inter", license = "SIL OFL 1.1"),
        AcknowledgmentEntry(name = "JetBrains Mono", license = "SIL OFL 1.1"),
    )
}
