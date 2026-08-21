import app.knotwork.android.buildtools.BrowserEditorConstantsGenerator
import app.knotwork.android.buildtools.DexInstantiabilityChecker
import app.knotwork.android.buildtools.DocsHygieneChecker
import app.knotwork.android.buildtools.ExternalAutomationDocsGenerator
import app.knotwork.android.buildtools.LintBaselineGuard
import app.knotwork.android.buildtools.R8MappingChecker
import app.knotwork.android.buildtools.ReleaseVersionChecker
import com.android.build.api.artifact.SingleArtifact
import dev.detekt.gradle.Detekt
import java.util.Properties
import java.util.zip.ZipFile

/**
 * Resolves the current short git SHA (e.g. `19b9c8f`) via
 * `providers.exec("git", "rev-parse", "--short", "HEAD")`. Returns
 * `"unknown"` when git is absent, the working tree is not a repository,
 * or the command otherwise fails (e.g. a tarball-based release build on a
 * CI runner that lacks git history).
 */
fun Project.resolveGitSha(): String = runCatching {
    val output = providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        isIgnoreExitValue = true
    }
    val exitCode = output.result.get().exitValue
    if (exitCode == 0) {
        output.standardOutput.asText.get().trim().ifEmpty { "unknown" }
    } else {
        "unknown"
    }
}.getOrDefault("unknown")

/**
 * Resolves the build timestamp (epoch milliseconds) baked into `BuildConfig`.
 *
 * Resolution order, most authoritative first:
 *
 * 1. **`SOURCE_DATE_EPOCH`** (seconds since the epoch) — the cross-ecosystem
 *    convention for reproducible builds. A rebuilder sets it to the commit
 *    timestamp and expects a bit-identical artefact; reading the wall clock
 *    instead is precisely what makes a build unreproducible.
 * 2. **The `HEAD` commit date** — deterministic for a given checkout, and the
 *    honest answer to "when was this source built from".
 * 3. **The wall clock** — last resort for a build with neither (a tarball with
 *    no git history and no environment override).
 *
 * The value is surfaced in the Settings top-app-bar subtitle, so it must stay a
 * real date in all three cases rather than a sentinel.
 *
 * @return Epoch milliseconds for the build stamp.
 */
fun Project.resolveBuildTimestampMs(): Long {
    val millisPerSecond = 1_000L
    val sourceDateEpoch = providers.environmentVariable("SOURCE_DATE_EPOCH").orNull
        ?.trim()
        ?.toLongOrNull()
    if (sourceDateEpoch != null) return sourceDateEpoch * millisPerSecond

    val commitEpochSeconds = runCatching {
        val output = providers.exec {
            commandLine("git", "log", "-1", "--pretty=%ct")
            isIgnoreExitValue = true
        }
        if (output.result.get().exitValue == 0) {
            output.standardOutput.asText.get().trim().toLongOrNull()
        } else {
            null
        }
    }.getOrNull()

    return commitEpochSeconds?.times(millisPerSecond) ?: System.currentTimeMillis()
}

/**
 * Resolved release-signing credentials sourced from `local.properties` or
 * environment variables. Carries the validated keystore file plus its
 * passwords and key alias so the `signingConfigs.release` block can be
 * populated without re-reading any properties.
 *
 * @property storeFile The keystore file (already verified to exist on disk).
 * @property storePassword The keystore (store) password.
 * @property keyAlias The alias of the signing key inside the keystore.
 * @property keyPassword The password protecting the signing key.
 */
private data class ReleaseSigningCredentials(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

/**
 * Resolves release-signing credentials, preferring `local.properties` (the
 * developer machine) and falling back to environment variables (CI). The
 * recognised keys are `RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`,
 * `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD`.
 *
 * Returns `null` — which the build interprets as "fall back to the debug
 * signing identity" — whenever any credential is missing/blank or the
 * resolved keystore file does not exist. This keeps a clean checkout without
 * a provisioned key building release artefacts instead of failing
 * configuration.
 *
 * @return The complete, validated set of credentials, or `null` when release
 *   signing is not provisioned in this environment.
 */
private fun Project.resolveReleaseSigning(): ReleaseSigningCredentials? {
    val localProps = Properties().apply {
        rootProject.file("local.properties")
            .takeIf { it.exists() }
            ?.inputStream()
            ?.use { load(it) }
    }
    fun value(key: String): String? = (localProps.getProperty(key) ?: System.getenv(key))?.trim()?.ifEmpty { null }

    val storePath = value("RELEASE_KEYSTORE_PATH") ?: return null
    val storePassword = value("RELEASE_KEYSTORE_PASSWORD") ?: return null
    val keyAlias = value("RELEASE_KEY_ALIAS") ?: return null
    val keyPassword = value("RELEASE_KEY_PASSWORD") ?: return null

    val storeFile = rootProject.file(storePath).takeIf { it.exists() } ?: return null
    return ReleaseSigningCredentials(storeFile, storePassword, keyAlias, keyPassword)
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
    // The `google-services` and `firebase-crashlytics` Gradle plugins are NOT
    // applied here unconditionally. They are proprietary and pull Google's
    // build-time tooling into the graph, which the F-Droid channel rejects.
    // Instead they are applied further down for every build EXCEPT a foss-only
    // one (see the `fossOnlyBuild` guard after the `android {}` block), so an
    // `assembleFossRelease` build never loads them.
}

// The androidx.appfunctions KSP processor generates the per-class
// `*_AppFunctionInventory.kt` / `*_AppFunctionInvoker.kt` artefacts unconditionally,
// but the leaf-application `app_functions_v2.xml` (and the legacy `app_functions.xml`)
// that the platform's AppSearch indexer actually reads at install time is only produced
// when `appfunctions:aggregateAppFunctions=true`. Without this flag the agent APK ships
// `app_functions_schema.xsd` but no inventory XML, so the system AppFunctionManager has
// no `search_tool` entry to advertise to other apps and the callee-side scenario in
// `AppFunctionsEndToEndTest` comes back empty.
ksp {
    arg("appfunctions:aggregateAppFunctions", "true")
    // Export the Room schema for every version so that
    // `MigrationTestHelper` can validate migrations against frozen JSON
    // snapshots in `app/schemas/`. The corresponding `exportSchema = true`
    // flag is set on the `@Database` annotation. Every future schema bump
    // must commit the newly generated `N.json` alongside the migration.
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "app.knotwork.android"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "app.knotwork.android"
        minSdk = 36
        targetSdk = 37
        versionCode = 10
        versionName = "0.7.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // License metadata exposed as Android string
        // resources so a future "About" dialog can render the license name and
        // link to the canonical text without hardcoding the values in UI code.
        resValue("string", "license_name", "Apache License 2.0")
        resValue("string", "license_url", "https://www.apache.org/licenses/LICENSE-2.0")

        // Surface the build's short git SHA inside the
        // About row of the Knotwork settings screen so users can paste a
        // precise build identifier into bug reports without needing the APK
        // hash. Falls back to "unknown" when `git` is unavailable (e.g. a
        // tarball build).
        buildConfigField("String", "GIT_SHA", "\"${resolveGitSha()}\"")

        // Build date surfaced in the Settings top-app-bar
        // subtitle (`v0.9.2 · alpha · 2026.05.18`). Captured at configuration
        // time as an epoch-millis Long so the formatter on the screen owns
        // the locale-specific rendering. Derived from `SOURCE_DATE_EPOCH` or
        // the HEAD commit date (see `resolveBuildTimestampMs`), so two builds
        // of the same commit agree — reading the wall clock here used to make
        // every rebuild a different binary, which is the one thing a
        // reproducible build may not do.
        buildConfigField(
            "long",
            "GIT_COMMIT_DATE_EPOCH_MS",
            "${resolveBuildTimestampMs()}L",
        )
    }

    // Real release-signing identity. The credentials are resolved from
    // `local.properties` (developer machine) or environment variables (CI
    // repository secrets); `resolveReleaseSigning()` returns null when none are
    // provisioned, in which case the `release` buildType below gracefully falls
    // back to the debug keystore so a clean checkout still builds. Keystore
    // material is never committed (guarded by `.gitignore`).
    val releaseSigning = resolveReleaseSigning()
    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = releaseSigning.storeFile
                storePassword = releaseSigning.storePassword
                keyAlias = releaseSigning.keyAlias
                keyPassword = releaseSigning.keyPassword
            }
        }
    }

    buildTypes {
        debug {
            // A debug build installs next to a release build instead of
            // replacing it: separate applicationId means a separate data
            // directory, so dogfooding a work-in-progress build can no longer
            // migrate, corrupt or wipe the database of the build actually in
            // daily use. The launcher label is overridden in `src/debug/res`
            // and the version name is suffixed so About and bug reports say
            // which of the two is talking.
            //
            // `src/debug/google-services.json` carries the same placeholder
            // values as the module-root file with the suffixed package name.
            // The variant-specific file wins for this build type, so the debug
            // build never resolves to a real Firebase project even when the
            // root file is swapped for the real one at release-build time.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // R8 in full mode + resource shrinking.
            // Keep rules for reflection-heavy code paths (Koog, Ktor,
            // kotlinx.serialization, MediaPipe / LiteRT JNI, SQLCipher,
            // AppFunctions KSP-generated wrappers) live in
            // `proguard-rules.pro`; AGP appends the standard
            // `proguard-android-optimize.txt` from the Android SDK.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Use the dedicated `release` signing config when its credentials
            // are provisioned (see `signingConfigs` above); otherwise fall back
            // to the debug keystore so a clean checkout without a key still
            // produces a (debug-signed) release artefact. Provisioning details
            // and signature verification are documented in `docs/release.md`.
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
            // Strip non-arm64 ABIs from the release APK. With `minSdk = 36`
            // (Android 16) every supported device is 64-bit; shipping
            // `armeabi-v7a` + `x86` + `x86_64` would inflate the artefact
            // by ~65 MB for zero benefit. Emulator-based smoke tests should
            // use the debug variant which keeps every ABI.
            ndk {
                abiFilters += "arm64-v8a"
            }
        }
    }

    // Distribution flavours. `full` ships Firebase Crashlytics and is
    // the default for day-to-day development and the Play/direct-APK channel.
    // `foss` is the F-Droid-compatible build: it carries no Firebase/Google
    // dependency (see the flavour-scoped `fullImplementation(...)` block and
    // the conditional plugin application below) and binds a no-op
    // `CrashReportingRepository`. The two flavours share all `main` sources;
    // only the crash-reporting impl + DI module differ (under `src/full` /
    // `src/foss`).
    flavorDimensions += "distribution"
    productFlavors {
        create("full") {
            dimension = "distribution"
            // Pre-selected variant in Android Studio and the default target for
            // ambiguous instrumentation/test tasks.
            isDefault = true
            // Surfaces to the presentation layer whether the in-app
            // crash-reporting consent toggle has a live collector behind it.
            // `full` does; `foss` hides the toggle entirely.
            buildConfigField("boolean", "CRASH_REPORTING_AVAILABLE", "true")
        }
        create("foss") {
            dimension = "distribution"
            buildConfigField("boolean", "CRASH_REPORTING_AVAILABLE", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    // AGP stamps a Google-encrypted blob of the dependency list into every
    // artefact. It is opaque by construction — only Google can read it — which
    // is a poor fit for an APK distributed as the FOSS build and inspected by
    // people who chose it precisely to see what is inside.
    //
    // The asymmetry is deliberate: the bundle keeps its copy, because that is
    // consumed by Play, which uses the block to warn about known-vulnerable
    // dependencies. Dropping it there would trade a real security signal for
    // nothing, since no user ever receives the .aab.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        // Robolectric needs the merged Android resources
        // and assets on the JVM unit-test classpath (string lookups in
        // `LongRunningTaskNotifierImpl`, drawables resolved by NotificationCompat
        // builders). Without this flag Robolectric falls back to its own minimal
        // resource table and `context.getString(R.string.…)` returns a placeholder.
        unitTests.isIncludeAndroidResources = true

        // Gradle's default test-worker heap is 512 MB, and this suite (≈3800
        // tests, Robolectric loading a merged resource table per class) began
        // exhausting it — as an `OutOfMemoryError` in whichever unrelated class
        // happened to run when the heap ran out, which reads like a flake and
        // is not one. The floor is raised once here rather than chased per
        // class; `forkEvery` is deliberately not used, since restarting the
        // worker every N classes costs far more wall-clock than the heap costs
        // memory.
        unitTests.all { test -> test.maxHeapSize = "2g" }
    }

    // Expose the exported Room schemas to the
    // androidTest classpath so `MigrationTestHelper` (which reads them from
    // `assets/`) can validate every migration step against the frozen
    // snapshots in `app/schemas/`.
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    packaging {
        resources {
            excludes += "META-INF/*"
            // Jansi (transitive via Koog → log adapter)
            // ships pre-built native binaries for Windows / Mac / Linux that
            // are dead weight on Android. Stripping them shaves ~430 KB off
            // the release APK without touching runtime behaviour (the
            // ANSI-escape rendering only runs on JVM hosts).
            excludes += "org/fusesource/jansi/internal/native/Windows/**"
            excludes += "org/fusesource/jansi/internal/native/Mac/**"
            excludes += "org/fusesource/jansi/internal/native/Linux/**"
            excludes += "org/fusesource/jansi/internal/native/FreeBSD/**"
            excludes += "META-INF/native-image/jansi/**"
        }
    }

    lint {
        // Strict mode. `abortOnError` + `warningsAsErrors`
        // turn every lint finding into a build failure; `checkDependencies`
        // extends the analysis to library modules so issues in shared code
        // surface on the PR that introduced them. The baseline file
        // grandfathers existing findings — only newly introduced issues
        // surface as failures.
        baseline = file("lint-baseline.xml")
        abortOnError = true
        warningsAsErrors = true
        checkDependencies = true
        htmlReport = true
        xmlReport = true
        // The release variant ships `arm64-v8a` only
        // (every `minSdk = 36` device is 64-bit). ChromeOS support is not in
        // scope for v0.1 — disable the lint check that demands an x86 binary.
        disable += "ChromeOsAbiSupport"
        // Version-freshness and deadline checks stay ENABLED, but are demoted to
        // INFORMATIONAL so they report instead of gating. Their verdict is a
        // function of an external version index (or of the calendar), not of the
        // contents of this repository: the same commit is green today and red
        // tomorrow without a single edit, and green locally while red in CI,
        // because the two version indexes refresh at different times. A check
        // whose verdict changes without the checked object changing is a report,
        // not a gate — `decisions.md` §35, which generalises the rule to any
        // future check that depends on external state.
        //
        // Demoted, NOT disabled. `disable` maps to `Severity.IGNORE`, which drops
        // the incident before any reporter sees it; the signal has to survive.
        // INFORMATIONAL findings still run, still match the baseline and still
        // appear in the HTML/XML reports, which CI uploads on every run (see
        // `.github/workflows/check.yml`). `warningsAsErrors` cannot undo the
        // demotion: lint promotes `Severity.WARNING` only, and INFORMATIONAL is
        // documented as exempt.
        //
        // Deliberate exclusions — checks that stay gates although their verdict is
        // not purely a function of this repository, because each encodes a store
        // publishing blocker rather than a matter of hygiene:
        // - `ExpiredTargetSdkVersion` (FATAL), which is calendar-driven;
        // - `PlaySdkIndexNonCompliant`, `PlaySdkIndexVulnerability`,
        //   `PlaySdkIndexGenericIssues`, `PlaySdkIndexDeprecated`, `RiskyLibrary`
        //   and `OutdatedLibrary`, decided by the Google Play SDK Index — a
        //   network-refreshed dataset with a bundled offline snapshot fallback.
        // Being interrupted by those is the point. Anything outside that list is
        // expected to hold the rule.
        //
        // - Do NOT add `ignoreWarnings = true` alongside this. Unlike
        //   `warningsAsErrors` it tests `<= WARNING`, so it would swallow
        //   INFORMATIONAL as well and silently delete the drift report.
        //
        // The baseline is the third way to delete the report, and the easiest to
        // trip over: lint records informational findings into a regenerated
        // baseline just like errors, and then filters them out of the reports.
        // `verifyLintBaselineOverrides` (below, wired into `check`) fails the
        // build if a baseline ever suppresses one of these ids.
        informational += LintBaselineGuard.DEMOTED_ISSUE_IDS
    }
}

// Apply the proprietary Google build plugins for every build EXCEPT one that
// builds the `foss` flavour and no `full` flavour. `google-services` (and the
// `firebase-crashlytics` plugin it backs) is what makes the Firebase SDK
// initialise cleanly — it generates the `google_app_id` resource from
// `google-services.json`. The `foss` flavour ships no Firebase SDK, so an
// F-Droid build (`./gradlew clean assembleFossRelease`) skips the plugins
// entirely and produces an APK with zero Google build-time tooling in its graph.
//
// The condition deliberately keys on the presence of a `Foss` task AND the
// absence of any `Full` task, NOT "every requested task is foss". The naive
// "all foss" form fails OPEN on the canonical F-Droid recipe
// `clean assembleFossRelease`: `clean` is not a `Foss` task, so "all foss" is
// false and the proprietary plugins would be (wrongly) applied to the foss
// build. With `any-foss && no-full`, neutral tasks like `clean` no longer flip
// the decision, while any invocation that also builds `full` (e.g. `check`,
// `assemble`, `assembleFullRelease`) keeps the plugins so Firebase's startup
// provider finds `google_app_id` and does not throw under Robolectric.
//
// The `foss` flavour's freedom from the `com.google.firebase` runtime classpath
// is guaranteed independently by the flavour-scoped `fullImplementation(...)`
// dependencies, not by this guard — this guard only keeps the proprietary
// *build-time* plugins out of a pure F-Droid build.
val requestedTaskNames = gradle.startParameter.taskNames
val fossOnlyBuild = requestedTaskNames.any { it.contains("Foss", ignoreCase = true) } &&
    requestedTaskNames.none { it.contains("Full", ignoreCase = true) }
if (!fossOnlyBuild) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
    apply(plugin = libs.plugins.firebase.crashlytics.get().pluginId)
}

detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    // Strict mode. Any finding emitted at
    // `severity: error` (default `failOnSeverity`) now fails the build.
    // The legacy 1.x `failFast` switch was removed in detekt 1.22 and is
    // intentionally not used here.
    ignoreFailures = false
    basePath.set(rootDir)
    source.setFrom("src/main/java", "src/main/kotlin")
}

// Coroutine-cancellation gate. `SuspendFunSwallowedCancellation` requires
// type resolution, which the plain `detekt` task above cannot provide, so the
// rule lives in a second, deliberately narrow run: the plugin-generated
// type-resolution tasks for the debug variant of each distribution flavour
// (`detektFullDebug` / `detektFossDebug`) are rewired to
// `detekt-cancellation.yml`, a config that activates only that single rule.
// Both flavours are wired so the gate also covers the flavour-specific
// crash-reporting sources (`src/full` / `src/foss`). Running the full strict
// config under type resolution instead would surface ~1.1k findings from rules
// that have never been part of the gate — adopting them is a separate effort,
// not a side effect of this wiring. Full-config type-resolution analysis
// remains available via `detektMain`/`detektRelease`.
val cancellationGateDetektTasks = setOf("detektFullDebug", "detektFossDebug")
tasks.matching { it.name in cancellationGateDetektTasks }.configureEach {
    this as Detekt
    config.setFrom(files("$rootDir/config/detekt/detekt-cancellation.yml"))
    buildUponDefaultConfig.set(false)
}
tasks.named("check") { dependsOn(cancellationGateDetektTasks) }

tasks.withType<Detekt>().configureEach {
    reports {
        html.required.set(true)
        checkstyle.required.set(true)
        sarif.required.set(false)
        markdown.required.set(false)
    }
    exclude("**/build/**", "**/generated/**")
}

ktlint {
    version.set("1.5.0")
    android.set(true)
    // Strict mode. Any ktlint violation that survives
    // `ktlintFormat` (i.e. cannot be auto-corrected, or the source set
    // was not formatted) fails the build. Compose-specific rule overrides
    // live in `.editorconfig`.
    ignoreFailures.set(false)
    filter {
        exclude { entry -> entry.file.toString().contains("/build/") }
        exclude { entry -> entry.file.toString().contains("/generated/") }
    }
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.HTML)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Kover — test-coverage measurement & enforcement.
// Aggregate threshold enforced via `koverVerifyDebug`; the aggregate floor was
// raised 70 % → 75 %.
//
// Per-rule filters are a Kover 0.10+ feature; on the 0.9.x line the only
// way to scope verification is via the (global) `reports.filters` block,
// which is also what determines what appears in HTML / XML reports. To
// avoid the verify rule failing on Composables and Android-runtime-bound
// classes that this gate cannot cover (they need androidTest / instrumented
// runs), those classes are removed from the **whole** coverage picture
// here. The trade-off is documented in docs/coverage-baseline.md:
// instrumented coverage for `*Screen.kt` etc. is a separate workstream.
//
// The single rule then enforces a 75 % aggregate floor over the
// remaining "unit-testable" surface — domain + data.repositories +
// presentation ViewModels / UiStates. Today's measurement is ~77.6 %, so
// the floor leaves ~2.6 pp of headroom against silent regression. Per-
// package targets that the build cannot yet enforce (no rule-level filters
// in 0.9.x) live in docs/coverage-baseline.md.
kover {
    reports {
        filters {
            excludes {
                classes(
                    // Hilt-generated factories, modules, and member injectors.
                    "*_HiltModules*",
                    "*_HiltModules_*",
                    "*.Hilt_*",
                    "*_Factory",
                    "*_Factory$*",
                    "*_MembersInjector",
                    "*_Provide*Factory",
                    "*_Provide*Factory$*",
                    "dagger.hilt.internal.*",
                    "hilt_aggregated_deps.*",
                    // Room-generated DAO implementations and the database impl
                    // (the schema migrations themselves are bundled inside it).
                    "*_Impl",
                    "*_Impl$*",
                    "app.knotwork.android.data.local.AppDatabase",
                    "app.knotwork.android.data.local.AppDatabase_Impl*",
                    "*_AutoMigration_*",
                    "app.knotwork.android.data.local.dao.*",
                    // Mic capture is a thin platform-`AudioRecord` wrapper that
                    // cannot run on the JVM; its testable WAV-header logic lives
                    // in `data.audio.WavHeader`, which stays in coverage.
                    "app.knotwork.android.data.audio.AudioRecorderImpl",
                    "app.knotwork.android.data.audio.AudioRecorderImpl$*",
                    // Compose synthetic singletons + project-convention preview files
                    // (every Compose `@Preview` lives in a `*Preview.kt` file).
                    "*ComposableSingletons*",
                    "ComposableSingletons$*",
                    "*Preview",
                    "*PreviewKt",
                    // Hilt DI modules — wiring code, no business logic to cover.
                    "app.knotwork.android.di.*",
                    // Generated build artefacts.
                    "app.knotwork.android.BuildConfig",
                    "*.databinding.*",
                    "*.BR",
                    // Android-runtime-bound presentation classes that need
                    // instrumented (androidTest / Compose UI test) coverage,
                    // out of scope for the JVM-only Kover pipeline.
                    "app.knotwork.android.App",
                    "app.knotwork.android.presentation.ui.MainActivity",
                    "app.knotwork.android.presentation.ui.MainActivity$*",
                    "app.knotwork.android.presentation.ui.*Screen",
                    "app.knotwork.android.presentation.ui.*ScreenKt",
                    "app.knotwork.android.presentation.ui.*Screen$*",
                    // Legacy chat surface (moved under chat/legacy/ pending the
                    // post-v0.1 orchestrator-rewiring task).
                    "app.knotwork.android.presentation.ui.chat.legacy.ConsoleFullLogSheet*",
                    "app.knotwork.android.presentation.ui.chat.legacy.ConsolePanelCollapsed*",
                    "app.knotwork.android.presentation.ui.chat.legacy.AgentThoughtIndicator*",
                    "app.knotwork.android.presentation.ui.chat.legacy.ClarificationCard*",
                    "app.knotwork.android.presentation.ui.chat.legacy.PipelineTraceCard*",
                    "app.knotwork.android.presentation.ui.chat.legacy.ApprovalBanner*",
                    "app.knotwork.android.presentation.ui.chat.legacy.ChatScreen*",
                    // Redesigned chat-home Composables — Compose surfaces are
                    // covered by `:catalog` Roborazzi snapshots; the
                    // testable VM / state-mapping live next to them and are
                    // included in coverage.
                    "app.knotwork.android.presentation.ui.chat.home.ChatHomeScreen*",
                    "app.knotwork.android.presentation.ui.chat.home.ChatHomeDebugStatePicker*",
                    "app.knotwork.android.presentation.ui.chat.home.DebugStateRows*",
                    "app.knotwork.android.presentation.ui.components.*",
                    "app.knotwork.android.presentation.ui.orchestrator.components.*",
                    // Pipeline editor Compose layer. Gestures, animations, and
                    // Bezier draw paths are intentionally outside the JVM Kover
                    // scope; the pure-Kotlin core (CanvasTransform, AutoLayout,
                    // EditorUndoRedo, BezierEdge, NodeConfigCodec) plus the VM
                    // hooks ARE covered. Screen-level visual coverage rides on the
                    // catalog's PipelineEditorCatalogPageSnapshotTest and the
                    // a11y + release-candidate gate.
                    "app.knotwork.android.presentation.ui.pipeline.editor.canvas.*",
                    "app.knotwork.android.presentation.ui.pipeline.editor.bars.*",
                    "app.knotwork.android.presentation.ui.pipeline.editor.sheet.*",
                    "app.knotwork.android.presentation.ui.pipeline.editor.PipelineEditorContent*",
                    "app.knotwork.android.presentation.ui.pipeline.editor.PipelineEditorScreen*",
                    "app.knotwork.android.presentation.ui.splash.SplashScreen*",
                    "app.knotwork.android.presentation.theme.*",
                    "app.knotwork.android.presentation.state.*",
                    // Additional Compose-surface / nav-glue packages. Same
                    // rationale as the
                    // existing presentation.ui.*Screen exclusions: rendering and
                    // navigation code needs Compose UI tests, not JVM unit tests.
                    // The redesigned bottom-nav shell (AppShellScaffold,
                    // AppNavGraph, TabDestination, BottomNavVisibility,
                    // KnotworkModalRoute, NavRoutes) is pure UI wiring; route
                    // constants in NavRoutes are unreachable in JVM tests.
                    "app.knotwork.android.presentation.ui.navigation.*",
                    // Single static About surface (AboutScreen.kt). The
                    // `AboutAcknowledgments` private object lives in the same
                    // file and is pure declarative data feeding the Composable.
                    "app.knotwork.android.presentation.ui.about.AboutScreen*",
                    "app.knotwork.android.presentation.ui.about.AboutAcknowledgments*",
                    // Bottom-nav "More" hub Composable. The MoreViewModel /
                    // MoreUiState in the same package remain inside the gate.
                    "app.knotwork.android.presentation.ui.more.MoreScreen*",
                    // Provider picker and detail Compose screens under
                    // presentation/ui/settings/provider — covered by the
                    // catalog snapshot suite, not JVM unit tests.
                    "app.knotwork.android.presentation.ui.settings.provider.ProviderPickerScreen*",
                    "app.knotwork.android.presentation.ui.settings.provider.ProviderDetailScreen*",
                    // AppFunctions callee-side wrapper (SearchAppFunction). The
                    // KSP-generated `*_AppFunctionInvoker` infrastructure and
                    // the platform `PlatformAppFunctionService` need the Android
                    // runtime plus the AppFunctions service host to execute.
                    "app.knotwork.android.data.tools.local.appfunctions.*",
                    // `data.services.*` is now covered by
                    // Robolectric tests (`AgentForegroundServiceTest`,
                    // `AgentWorkerTest`, `AgentIdleManagerTest`,
                    // `AgentPowerManagerTest`, `LongRunningTaskNotifierImplTest`).
                    // The exclusion that was here while the package waited for
                    // Robolectric coverage has been lifted.
                    // `presentation.notifications.*` and
                    // `presentation.receivers.*` are now covered by Robolectric
                    // tests (`ApprovalNotificationManagerTest`,
                    // `AgentApprovalReceiverTest`). The exclusions that lived
                    // here while those packages waited for ShadowNotificationManager
                    // / BroadcastReceiver coverage have been lifted.
                    // Tool-execution Android glue (AppFunctions service, search
                    // tool HTTP client, delegate-task LLM bridge) needs either
                    // an Android runtime or live LLM/HTTP fixtures.
                    "app.knotwork.android.data.tools.local.AgentAppFunctionService",
                    "app.knotwork.android.data.tools.local.AgentAppFunctionService$*",
                    "app.knotwork.android.data.tools.local.LocalAppFunctionManager",
                    "app.knotwork.android.data.tools.local.SearchTool*",
                    "app.knotwork.android.data.tools.local.DelegateTaskTool*",
                    // NOTE: `data.tools.local.executors.*` are pure JSON-arg
                    // parsers that delegate to the (excluded) Android-runtime
                    // tools above — they are JVM-unit-testable and covered by
                    // `*ExecutorTest`s under `data/tools/local/executors/`.
                    // Firebase Crashlytics glue: the `CrashlyticsTimberTree`
                    // (in `src/main`) and the `full`-flavour
                    // `FirebaseCrashReportingRepositoryImpl` (in `src/full`)
                    // thinly wrap `FirebaseCrashlytics` / `FirebaseAnalytics`
                    // singletons which need the Android runtime and Google Play
                    // services to initialise. Unit-test coverage for the
                    // no-op-when-disabled and dispatch branches lives under
                    // `FirebaseCrashReportingRepositoryImplTest` (`src/testFull`)
                    // / `CrashlyticsTimberTreeTest`, but the production
                    // `getInstance()` paths are not exercised on the JVM. The
                    // `foss` no-op impl carries no logic and is covered by
                    // `NoOpCrashReportingRepositoryTest` (`src/testFoss`).
                    "app.knotwork.android.data.logging.CrashlyticsTimberTree*",
                )
                // Belt-and-braces: also skip any @Preview-annotated function that
                // happens to live outside a *Preview.kt file.
                annotatedBy("androidx.compose.ui.tooling.preview.Preview")
            }
        }

        verify {
            // Aggregate gate — protects against silent regression across the
            // whole unit-testable surface. The current measured value
            // (May 2026) is ~77.5 %; the 75 % floor leaves a
            // ~2.5 pp buffer for in-flight refactors.
            //
            // Per-package thresholds were considered for this task but cannot
            // be expressed on Kover 0.9.x — the rule-level `filters { ... }`
            // block is a 0.10+ feature (not yet released; 0.9.8 is the latest
            // on the Gradle Plugin Portal as of May 2026). Per-package
            // *targets* are documented in docs/coverage-baseline.md as
            // guidance; promote them to enforced rules once Kover 0.10 ships.
            rule("Aggregate unit-testable coverage must stay ≥75%") {
                groupBy = kotlinx.kover.gradle.plugin.dsl.GroupingEntityType.APPLICATION
                bound {
                    minValue = 75
                    coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE
                    aggregationForGroup =
                        kotlinx.kover.gradle.plugin.dsl.AggregationType.COVERED_PERCENTAGE
                }
            }
        }
    }
}

// Wire `koverVerifyFullDebug` into the `check` lifecycle so a single
// `./gradlew check` invocation runs detekt + ktlintCheck + lint + unit tests +
// coverage verification. With the `distribution` flavour dimension the
// per-variant Kover task gains the flavour segment (`koverVerifyDebug` →
// `koverVerifyFullDebug`); the `full` variant is the representative coverage
// target because both flavours share every measured source — they differ only
// in the crash-reporting impl, and the `foss` no-op carries no logic to cover.
tasks.named("check") { dependsOn("koverVerifyFullDebug") }

// AGP wires only the *default* variant's lint (`lintFullDebug`) into `check`.
// With the `distribution` flavour dimension that leaves the `foss`-only sources
// (the no-op crash reporter + its DI module + the absence of the Firebase
// manifest overlay) un-linted. Add `lintFossDebug` so `check` lints BOTH
// flavours — matching the CI artifact upload and the "both flavours gated" claim.
tasks.named("check") { dependsOn("lintFossDebug") }

// Enforces the "no internal FQN" rule for the
// `app.knotwork.android.*` package. References like
// `app.knotwork.android.domain.models.NodeType` outside `import`/`package`
// statements must be replaced with a top-level import + short name. KDoc
// lines (whitespace-then-`*`) and the `.editorconfig` pinned ktlint rules
// catch the rest (wildcard imports, unused imports, import ordering).
val checkNoInternalFqn by tasks.registering {
    group = "verification"
    description =
        "Fails the build if any internal `app.knotwork.android.*` FQN reference appears in source code " +
        "outside of `import`/`package` statements or KDoc comments."
    val sourceRoots = listOf(
        "src/main/java",
        "src/main/kotlin",
        "src/test/java",
        "src/test/kotlin",
        "src/androidTest/java",
        "src/androidTest/kotlin",
    )
    val ktFiles = sourceRoots.flatMap { root ->
        fileTree("$projectDir/$root") { include("**/*.kt") }.files
    }
    inputs.files(ktFiles)
    doLast {
        val fqnPattern = Regex("""\bapp\.knotwork\.android\.[a-z_]+\.[A-Za-z]""")
        // Intent action strings are namespaced by the application id by Android
        // convention (`<applicationId>.action.NAME`), so they read like an internal
        // FQN while being wire data rather than a Kotlin reference — nothing an
        // import could replace, and frozen once third-party callers use them.
        // They are scrubbed from the line rather than exempting the whole line, so a
        // real FQN sitting beside an action string is still caught.
        val intentActionPattern = Regex("""\bapp\.knotwork\.android\.action\.""")
        val violations = mutableListOf<String>()
        ktFiles.forEach { file ->
            file.useLines { lines ->
                lines.forEachIndexed { index, line ->
                    val trimmed = line.trimStart()
                    // Skip `import …` / `package …` statements and `*`-style KDoc lines.
                    if (trimmed.startsWith("import ") ||
                        trimmed.startsWith("package ") ||
                        trimmed.startsWith("*") ||
                        trimmed.startsWith("//")
                    ) {
                        return@forEachIndexed
                    }
                    if (fqnPattern.containsMatchIn(intentActionPattern.replace(line, ""))) {
                        violations += "${file.relativeTo(rootDir)}:${index + 1}: ${line.trim()}"
                    }
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Internal FQN references found (use imports instead):\n" +
                    violations.joinToString(separator = "\n"),
            )
        }
    }
}
tasks.named("check") { dependsOn(checkNoInternalFqn) }

// Browser pipeline-editor constant sync automation.
//
// `pipeline-editor.html` mirrors a slice of the Android domain (node types,
// prompt variables, available tools, default prompts). Those mirrors used to be
// kept in sync by review alone and drifted. `generateBrowserEditorConstants`
// regenerates the `AUTO-GEN` blocks straight from the domain sources;
// `verifyBrowserEditorConstants` (wired into `check`) fails the build if the
// committed HTML has drifted. The pure generation logic lives in
// `buildSrc` (`BrowserEditorConstantsGenerator`) and is unit-tested there.
val browserEditorHtmlFile = file("$rootDir/pipeline-editor.html")
val browserEditorNodeTypeFile =
    file("$projectDir/src/main/java/app/knotwork/android/domain/models/NodeType.kt")
val browserEditorDefaultPromptsFile =
    file("$projectDir/src/main/java/app/knotwork/android/domain/constants/DefaultPrompts.kt")
val browserEditorPromptModuleFile =
    file("$projectDir/src/main/java/app/knotwork/android/di/PromptTemplateModule.kt")
val browserEditorToolsModuleFile =
    file("$projectDir/src/main/java/app/knotwork/android/di/LocalToolsModule.kt")
// Provider `KEY` constants + tool `TOOL_NAME` constants the generator must resolve.
val browserEditorClassSourceFiles: Set<File> = buildSet {
    addAll(fileTree("$projectDir/src/main/java/app/knotwork/android/data/prompt") { include("**/*.kt") }.files)
    addAll(fileTree("$projectDir/src/main/java/app/knotwork/android/data/tools/local") { include("**/*.kt") }.files)
}
// Every file whose content feeds the generated blocks; drives up-to-date checks.
val browserEditorInputFiles: Set<File> = browserEditorClassSourceFiles + setOf(
    browserEditorNodeTypeFile,
    browserEditorDefaultPromptsFile,
    browserEditorPromptModuleFile,
    browserEditorToolsModuleFile,
)

val generateBrowserEditorConstants by tasks.registering {
    group = "build"
    description =
        "Regenerates the AUTO-GEN constant blocks in pipeline-editor.html from the Android domain sources."
    inputs.files(browserEditorInputFiles)
    inputs.file(browserEditorHtmlFile)
    outputs.file(browserEditorHtmlFile)
    doLast {
        val current = browserEditorHtmlFile.readText()
        val rendered = BrowserEditorConstantsGenerator.render(
            html = current,
            nodeTypeSource = browserEditorNodeTypeFile.readText(),
            defaultPromptsSource = browserEditorDefaultPromptsFile.readText(),
            promptTemplateModuleSource = browserEditorPromptModuleFile.readText(),
            localToolsModuleSource = browserEditorToolsModuleFile.readText(),
            classSources = browserEditorClassSourceFiles.associate { it.nameWithoutExtension to it.readText() },
        )
        if (rendered != current) {
            browserEditorHtmlFile.writeText(rendered)
            logger.lifecycle("pipeline-editor.html: regenerated AUTO-GEN constants.")
        } else {
            logger.lifecycle("pipeline-editor.html: AUTO-GEN constants already up to date.")
        }
    }
}

val verifyBrowserEditorConstants by tasks.registering {
    group = "verification"
    description =
        "Fails the build if pipeline-editor.html AUTO-GEN constant blocks have drifted from the Android domain sources."
    inputs.files(browserEditorInputFiles)
    inputs.file(browserEditorHtmlFile)
    doLast {
        val drifted = BrowserEditorConstantsGenerator.drift(
            html = browserEditorHtmlFile.readText(),
            nodeTypeSource = browserEditorNodeTypeFile.readText(),
            defaultPromptsSource = browserEditorDefaultPromptsFile.readText(),
            promptTemplateModuleSource = browserEditorPromptModuleFile.readText(),
            localToolsModuleSource = browserEditorToolsModuleFile.readText(),
            classSources = browserEditorClassSourceFiles.associate { it.nameWithoutExtension to it.readText() },
        )
        if (drifted.isNotEmpty()) {
            throw GradleException(
                "pipeline-editor.html is out of sync with the Android domain sources.\n" +
                    "Drifted AUTO-GEN block(s): ${drifted.joinToString(", ")}.\n" +
                    "Run `./gradlew :app:generateBrowserEditorConstants` and commit the updated pipeline-editor.html.",
            )
        }
    }
}
tasks.named("check") { dependsOn(verifyBrowserEditorConstants) }

// External-automation contract documentation sync automation.
//
// `docs/external-automation.md` publishes the action strings, extra keys,
// statuses and refusal reasons of a contract whose callers live in other apps.
// Once a Tasker profile or an `adb` one-liner is written against a key, that key
// is frozen — and documentation that has drifted from the code fails silently on
// the caller's side, since a request built from a stale key merely looks
// malformed to the app. `generateExternalAutomationDocs` regenerates the
// `AUTO-GEN` tables straight from the Kotlin declarations;
// `verifyExternalAutomationDocs` (wired into `check`) fails the build if the
// committed Markdown has drifted. The pure generation logic lives in `buildSrc`
// (`ExternalAutomationDocsGenerator`) and is unit-tested there.
//
// Inputs are resolved into local `val`s and captured by the task actions as
// plain `File` values, so the actions never reach back into the `Project` —
// keeping both tasks configuration-cache compatible.
val externalAutomationDocsFile = file("$rootDir/docs/external-automation.md")
val externalAutomationContractFile =
    file("$projectDir/src/main/java/app/knotwork/android/domain/constants/ExternalAutomationContract.kt")
val externalAutomationStatusFile =
    file("$projectDir/src/main/java/app/knotwork/android/domain/models/ExternalAutomationStatus.kt")
val externalAutomationReasonFile =
    file("$projectDir/src/main/java/app/knotwork/android/domain/models/ExternalAutomationRejectionReason.kt")
val externalAutomationInputFiles: Set<File> = setOf(
    externalAutomationContractFile,
    externalAutomationStatusFile,
    externalAutomationReasonFile,
)

val generateExternalAutomationDocs by tasks.registering {
    group = "build"
    description =
        "Regenerates the AUTO-GEN reference tables in docs/external-automation.md from the contract sources."
    inputs.files(externalAutomationInputFiles)
    inputs.file(externalAutomationDocsFile)
    outputs.file(externalAutomationDocsFile)
    doLast {
        val current = externalAutomationDocsFile.readText()
        val rendered = ExternalAutomationDocsGenerator.render(
            markdown = current,
            contractSource = externalAutomationContractFile.readText(),
            statusSource = externalAutomationStatusFile.readText(),
            reasonSource = externalAutomationReasonFile.readText(),
        )
        if (rendered != current) {
            externalAutomationDocsFile.writeText(rendered)
            logger.lifecycle("docs/external-automation.md: regenerated AUTO-GEN tables.")
        } else {
            logger.lifecycle("docs/external-automation.md: AUTO-GEN tables already up to date.")
        }
    }
}

val verifyExternalAutomationDocs by tasks.registering {
    group = "verification"
    description =
        "Fails the build if docs/external-automation.md AUTO-GEN tables have drifted from the contract sources."
    inputs.files(externalAutomationInputFiles)
    inputs.file(externalAutomationDocsFile)
    doLast {
        val drifted = ExternalAutomationDocsGenerator.drift(
            markdown = externalAutomationDocsFile.readText(),
            contractSource = externalAutomationContractFile.readText(),
            statusSource = externalAutomationStatusFile.readText(),
            reasonSource = externalAutomationReasonFile.readText(),
        )
        if (drifted.isNotEmpty()) {
            throw GradleException(
                "docs/external-automation.md is out of sync with the external-automation contract sources.\n" +
                    "Drifted AUTO-GEN block(s): ${drifted.joinToString(", ")}.\n" +
                    "Run `./gradlew :app:generateExternalAutomationDocs` and commit the updated Markdown.",
            )
        }
    }
}
tasks.named("check") { dependsOn(verifyExternalAutomationDocs) }

// Public documentation hygiene guard.
//
// Scans the public-contour Markdown for two defect classes that are cheap to
// introduce and expensive reputationally once the repo is announced: leaked LLM
// tool-call wrapper artifacts, and references to internal-only planning
// documents that external readers cannot see. The pure scanner lives in
// `buildSrc` (`DocsHygieneChecker`) and is unit-tested there
// (`./gradlew -p buildSrc test`).
//
// The root scope is a *glob* (`*.md`), not a hand-maintained allowlist, so a
// newly added top-level public doc is guarded automatically. Two families are
// excluded, both by design: `CHANGELOG.md` (a historical journal whose past
// entries legitimately name internal documents as they were called at the
// time) and the untracked, internal `CLAUDE.md` manifest family (which
// deliberately references the private planning docs). `NOTICE` carries no `.md`
// extension, so it is added explicitly when present.
//
// The file set and the root directory are resolved into *local* `val`s inside
// the configuration block and captured by the task action as plain
// `Set<File>` / `File` values, so the action never reaches back into the
// `Project` or the build script object — keeping the task configuration-cache
// compatible.
val verifyDocsHygiene by tasks.registering {
    group = "verification"
    description =
        "Fails the build if public docs contain LLM tool-call artifacts or references to internal-only documents."
    val rootDirForAction: File = rootDir
    val docsHygieneFiles: Set<File> = buildSet {
        addAll(
            fileTree("$rootDir") {
                include("*.md")
                exclude("CHANGELOG.md", "CLAUDE.md", "CLAUDE.local.md")
            }.files,
        )
        file("$rootDir/NOTICE").takeIf { it.exists() }?.let { add(it) }
        addAll(fileTree("$rootDir/docs") { include("**/*.md") }.files)
    }
    inputs.files(docsHygieneFiles)
    doLast {
        val contents = docsHygieneFiles.associate { it.relativeTo(rootDirForAction).path to it.readText() }
        val violations = DocsHygieneChecker.scan(contents)
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Public documentation hygiene check failed " +
                    "(${violations.size} violation(s)):\n" +
                    violations.joinToString(separator = "\n") { it.format() },
            )
        }
    }
}
tasks.named("check") { dependsOn(verifyDocsHygiene) }

// Lint-baseline guard for the demoted version-freshness checks.
//
// Those checks report at informational severity (see the `lint {}` block above),
// which makes the lint report their only signal — and makes the baseline a way to
// delete that signal without failing anything. Lint records informational
// incidents into a regenerated baseline exactly as it records errors (the write
// path filters by issue id, never by severity) and then filters baselined
// incidents out of the reports, so one routine `updateLintBaseline` run for an
// unrelated batch of fixes would quietly empty the drift report and leave `check`
// green. This project has already paid for that once: four such entries had
// accumulated and had to be deleted before the report showed the packages it
// exists to show.
//
// Unlike the checks it protects, this guard is a legitimate gate: its verdict is
// a function of the committed baselines and nothing else.
//
// The pure scanner lives in `buildSrc` (`LintBaselineGuard`) and is unit-tested
// there (`./gradlew -p buildSrc test`). The file set is a SINGLE-level glob on
// purpose: it matches `app/lint-baseline.xml` and `catalog/lint-baseline.xml`
// while never reaching a nested copy of the tree — notably a stale git worktree
// under `.claude/worktrees/` — which would otherwise fail the build with a
// violation that does not exist in this checkout.
val verifyLintBaselineOverrides by tasks.registering {
    group = "verification"
    description =
        "Fails the build if a lint baseline suppresses a check that was demoted to informational severity."
    val rootDirForAction: File = rootDir
    val baselineFiles: Set<File> = fileTree(rootDir) { include("*/lint-baseline.xml") }.files
    inputs.files(baselineFiles)
    doLast {
        val contents = baselineFiles.associate { it.relativeTo(rootDirForAction).path to it.readText() }
        // A guard that scans nothing passes everything, which is the failure mode
        // this task exists to prevent. `:app` always declares a baseline, so an
        // empty match set means the glob stopped finding the modules (a module
        // moved under a nested path, a renamed baseline file) rather than that
        // there is nothing to check.
        if (contents.isEmpty()) {
            throw GradleException(
                "verifyLintBaselineOverrides found no lint baseline to scan. At least " +
                    "`app/lint-baseline.xml` is expected; the single-level `*/lint-baseline.xml` " +
                    "glob has stopped matching the modules it is meant to cover.",
            )
        }
        val violations = LintBaselineGuard.scan(contents)
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Lint baseline suppresses a demoted check (${violations.size} violation(s)):\n" +
                    violations.joinToString(separator = "\n") { it.format() } +
                    "\n\nThese checks are informational so that the lint report keeps showing dependency " +
                    "drift; a baseline entry hides them again. Delete the entries above instead of " +
                    "regenerating the baseline wholesale.",
            )
        }
    }
}
tasks.named("check") { dependsOn(verifyLintBaselineOverrides) }

// `StoreMetadataTest` reads the store listing under `fastlane/metadata/` — the
// text limits, the changelog for the shipping versionCode, and the screenshot
// geometry Play enforces. Those files are not on any compile classpath, so
// without this declaration the test task stays UP-TO-DATE after a metadata edit
// and the guard reports a stale pass: exactly the failure mode it exists to
// prevent, only quieter.
//
// `InstrumentedTestExclusionGuardTest` has the same shape and the same trap. It
// parses the instrumented source set (which is on no unit-test classpath) and
// reads the emulator workflow (which is not a build input at all), so both have
// to be declared or the guard answers from a cached run of the very edit it
// polices — adding an exclusion, or renaming the annotation the workflow names
// as a string. Declaring them costs a unit-test re-run after an `androidTest`
// edit; not declaring them costs the guard its meaning.
tasks.withType<Test>().configureEach {
    inputs.dir(rootProject.file("fastlane/metadata"))
        .withPropertyName("storeMetadata")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(layout.projectDirectory.dir("src/androidTest"))
        .withPropertyName("instrumentedSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.file(".github/workflows/instrumented.yml"))
        .withPropertyName("instrumentedWorkflow")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

// Hilt/Dagger reads Kotlin metadata via `kotlin-metadata-jvm`, which is unshaded
// since Dagger 2.57 and therefore resolved through Gradle. Each Kotlin bump raises
// the emitted metadata version, so the processor must use a matching reader or it
// fails with "Provided Metadata instance has version X, while maximum supported
// version is Y". Pin it to the active Kotlin version across every configuration
// (including the Hilt aggregating processor classpath) so a Kotlin bump never
// outruns the metadata reader again.
configurations.configureEach {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-metadata-jvm:${libs.versions.kotlin.get()}")
    }
}

dependencies {
    // Design-system module — `KnotworkTheme` (currently a `MaterialTheme`
    // pass-through) plus the ported foundations in Kotlin sources.
    implementation(project(":catalog"))

    // `androidx.core.splashscreen` artefact — backs the platform-side splash
    // (`installSplashScreen(...)`) once the brand mark and accent token land.
    implementation(libs.androidx.core.splashscreen)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    // Network
    implementation(libs.okhttp)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.work.testing)
    ksp(libs.hilt.compiler)

    // Logging
    implementation(libs.timber)

    // Process restart for the Settings → restart-required banner.
    implementation(libs.process.phoenix)

    // Local Storage (Room)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore Preferences
    implementation(libs.datastore.preferences)

    // LiteRT LLM Inference
    implementation(libs.litertlm)

    // MediaPipe Tasks Text.
    //
    // It drags `com.google.android.datatransport` in for its own logging, which
    // put three Google components — an alarm receiver, a job service and a
    // backend-discovery holder — into the FOSS manifest. They are removed in
    // `src/foss/AndroidManifest.xml`; the dependency itself stays, because
    // excluding it leaves MediaPipe's own code referencing classes that are no
    // longer there. See `docs/release.md` § FOSS / F-Droid build.
    implementation(libs.mediapipe.tasks.text)

    // Koog Framework
    implementation(libs.koog.agents)
    implementation(libs.koog.mcp)
    implementation(libs.koog.openai)
    implementation(libs.koog.anthropic)
    implementation(libs.koog.google)
    implementation(libs.koog.deepseek)
    implementation(libs.koog.ollama)
    // Runtime SPI provider — see the `koog-http-client-ktor` entry in
    // `libs.versions.toml` for the rationale. Required for every Koog
    // prompt-executor since 1.0.0; without it the executors throw
    // `No KoogHttpClient.Factory provider found on the runtime classpath`
    // on the first network call.
    implementation(libs.koog.http.client.ktor)

    // SQLCipher for Android (encrypted Room database)
    implementation(libs.sqlcipher.android)

    // AppFunctions
    implementation(libs.androidx.appfunctions)
    implementation(libs.androidx.appfunctions.service)
    ksp(libs.androidx.appfunctions.compiler)

    // Markdown
    implementation(libs.markdown.m3)

    // Image loading (Coil 3) + EXIF orientation for attachment ingest
    implementation(libs.coil.compose)
    implementation(libs.androidx.exifinterface)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // JSON Serialization
    implementation(libs.gson)

    // Firebase — Crashlytics only. Analytics is deliberately NOT declared:
    // Crashlytics depends on `firebase-measurement-connector`, not on
    // `firebase-analytics`, and the app logs no analytics events. Declaring it
    // used to drag `play-services-measurement` in, which added an advertising-ID
    // permission set to the `full` manifest for a collector nothing ever fed.
    // Scoped to the `full` flavour ONLY (`fullImplementation`) so the `foss`
    // build carries no `com.google.firebase` artifact on any classpath — the
    // hard requirement for F-Droid. The BoM (Bill of Materials) pins
    // inter-library versions; individual modules are intentionally un-versioned.
    // Starting from Firebase BoM 34.0 the `-ktx` artifacts were folded into the
    // base modules and removed.
    "fullImplementation"(platform(libs.firebase.bom))
    "fullImplementation"(libs.firebase.crashlytics)

    testImplementation(libs.json)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.work.testing)
    // OkHttp 5 MockWebServer (mockwebserver3 namespace) for HttpRequestExecutor tests.
    testImplementation(libs.okhttp.mockwebserver3)
    // Robolectric is needed for the foreground service,
    // notification builder, and Doze (`ShadowPowerManager`) paths under
    // `data.services`. The version is pinned in `gradle/libs.versions.toml`.
    testImplementation(libs.robolectric)
    // Konsist (Apache-2.0) backs the architecture guard suite under
    // `app/src/test/.../architecture/`: JVM JUnit tests that fail `check`
    // when Clean Architecture layer boundaries regress (e.g. a `domain`
    // class importing `data`/`presentation` or the Android SDK).
    testImplementation(libs.konsist)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.room.testing)
}

// Install the :tools-probe debug APK alongside the agent's test
// APK so `AppFunctionsEndToEndTest` can discover the probe's `echo` AppFunction
// through the system AppFunctionManager. `androidTestUtil(project(":tools-probe"))`
// would be the ergonomic choice, but AGP 9 publishes the probe as a multi-variant
// application module without the disambiguating attributes Gradle needs to pick a
// concrete APK from a configuration-less consumer — sync fails with "Cannot choose
// between debugRuntimeElements / releaseRuntimeElements".
//
// Hooking on both `installDebugAndroidTest` and `connectedDebugAndroidTest`:
//   - `installDebugAndroidTest` covers Android Studio's "Run test" workflow which
//     ultimately goes through that task before invoking the instrumentation.
//   - `connectedDebugAndroidTest` covers CLI runs (`./gradlew :app:connectedDebugAndroidTest`)
//     where AGP installs the SUT/test APKs *inside* the connected-test task and does
//     **not** depend on `installDebugAndroidTest` at all — verified via
//     `./gradlew :app:connectedDebugAndroidTest --dry-run`. Without this branch the
//     CLI run never installs the probe and the e2e test times out with an empty
//     discovery list.
// The `distribution` flavour dimension adds the flavour segment to the
// androidTest task names. Both flavours share all `main` sources, so the e2e
// instrumented suite is meaningful on either; hook the probe install onto the
// install/connected androidTest tasks of BOTH flavours so
// `connectedFossDebugAndroidTest` (used when validating the F-Droid build) also
// installs the probe instead of timing out on an empty discovery list.
val toolsProbeInstall = ":tools-probe:installDebug"
val probeInstallHostTasks = setOf(
    "installFullDebugAndroidTest",
    "connectedFullDebugAndroidTest",
    "installFossDebugAndroidTest",
    "connectedFossDebugAndroidTest",
)
tasks.matching { it.name in probeInstallHostTasks }
    .configureEach { dependsOn(toolsProbeInstall) }

// ─── R8 keep-rule guard (release builds) ─────────────────────────────────────
// Some keep rules protect code whose failure mode is invisible to every test the
// JVM gate can run: `com.google.common.flogger` (pulled in by MediaPipe's
// `tasks-text`) resolves a log site by walking the call stack for a frame of its
// own, so when R8 renames or inlines those frames the first
// `TextEmbedder.createFromOptions` call dies with
// `IllegalStateException: no caller found on the stack for: …` — the on-device
// embedding path, i.e. any message that touches long-term memory. Debug builds
// are not minified, so unit and instrumented tests cannot see the regression.
//
// The mapping R8 emits is the one durable artefact that can: a live keep rule
// leaves the package identity-mapped. This task asserts exactly that after every
// release packaging task — APK and AAB alike — so a dropped rule fails the build
// rather than the user's first message.
val r8ProtectedPackages: List<String> = listOf("com.google.common.flogger.")

// Classes the app never constructs with `new`: protobuf-javalite materialises
// them through `Unsafe.allocateInstance` from `getDefaultInstance()`, which R8
// cannot see. MediaPipe parses its task graph as a protobuf on every
// `TextEmbedder.createFromOptions`, so an abstractified message class breaks
// the on-device embedding path — and therefore all of long-term memory.
val r8RequiredInstantiableClasses: List<String> = listOf(
    "com.google.protobuf.Any",
    "com.google.protobuf.UnknownFieldSetLite",
)
androidComponents {
    onVariants { variant ->
        if (variant.buildType != "release") return@onVariants
        val mappingFile = variant.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE)
        val variantName = variant.name.replaceFirstChar { it.uppercaseChar() }
        val verifyKeepRules = tasks.register("verify${variantName}KeepRules") {
            group = "verification"
            description = "Fails the release build if an R8 keep rule stopped pinning a protected package."
            inputs.files(mappingFile).optional().withPropertyName("r8Mapping")
            val protectedPackages = r8ProtectedPackages
            val checkedVariant = variant.name
            doLast {
                val mapping = mappingFile.orNull?.asFile
                // A release variant with no mapping is itself the regression:
                // either minification was switched off for a shipping build, or
                // this guard lost its grip on the artefact. Skipping silently
                // here would be the same vacuous pass the checker refuses.
                if (mapping == null || !mapping.exists()) {
                    throw GradleException(
                        "R8 keep-rule check cannot run for `$checkedVariant`: no obfuscation mapping was produced. " +
                            "Either minification is disabled for a release build, or the mapping artefact moved.",
                    )
                }
                val contents = mapping.readText()
                val violations = protectedPackages.flatMap { prefix ->
                    R8MappingChecker.verifyIdentityMapping(contents, prefix)
                }
                if (violations.isNotEmpty()) {
                    throw GradleException(
                        "R8 keep-rule check failed (${violations.size} violation(s)):\n" +
                            violations.joinToString(separator = "\n") { it.format() },
                    )
                }
            }
        }
        // Second guard, checking a property the first one cannot see. The
        // mapping proves a class kept its NAME; it says nothing about whether
        // the class can still be instantiated. R8 in full mode left
        // `com.google.protobuf.Any` identity-mapped and made it abstract, and
        // since protobuf-javalite instantiates through `Unsafe.allocateInstance`
        // — invisible to R8 — every `TextEmbedder.createFromOptions` threw, so
        // long-term memory failed on every release build while the mapping check
        // stayed green. This one reads the packaged dex.
        val apkDir = variant.artifacts.get(SingleArtifact.APK)
        val verifyInstantiable = tasks.register("verify${variantName}Instantiable") {
            group = "verification"
            description = "Fails the release build if a reflectively-instantiated class was made abstract or removed."
            inputs.files(apkDir).withPropertyName("packagedApk")
            val requiredClasses = r8RequiredInstantiableClasses
            val checkedVariant = variant.name
            val loader = variant.artifacts.getBuiltArtifactsLoader()
            doLast {
                val apk = loader.load(apkDir.get())
                    ?.elements
                    ?.map { File(it.outputFile) }
                    ?.firstOrNull { it.exists() }
                    ?: throw GradleException(
                        "Instantiability check cannot run for `$checkedVariant`: no packaged APK was found.",
                    )
                val dexFiles = ZipFile(apk).use { zip ->
                    zip.entries().asSequence()
                        .filter { it.name.endsWith(".dex") }
                        .map { zip.getInputStream(it).readBytes() }
                        .toList()
                }
                if (dexFiles.isEmpty()) {
                    throw GradleException("Instantiability check found no dex in ${apk.name}.")
                }
                val violations = DexInstantiabilityChecker.verify(dexFiles, requiredClasses)
                if (violations.isNotEmpty()) {
                    throw GradleException(
                        "Reflectively-instantiated classes are unusable in `$checkedVariant` " +
                            "(${violations.size} violation(s)):\n" +
                            violations.joinToString(separator = "\n") { it.format() } +
                            "\n\nAdd or restore the keep rule in `app/proguard-rules.pro`.",
                    )
                }
            }
        }

        // Both packaging paths, not just the APK: the distribution artefact for
        // Play is the AAB, and a guard that only watches `assemble` would wave
        // through exactly the build that ships.
        //
        // `tasks.matching`, not `tasks.named`: AGP registers these tasks after
        // this `onVariants` callback runs, so eager lookup fails here.
        val packagingTasks = setOf("assemble$variantName", "bundle$variantName")
        tasks.matching { it.name in packagingTasks }.configureEach { finalizedBy(verifyKeepRules) }
        // The dex guard needs a packaged APK, so it rides `assemble` only;
        // the AAB carries the same dex from the same R8 run.
        tasks.matching { it.name == "assemble$variantName" }.configureEach { finalizedBy(verifyInstantiable) }
    }
}

// ─── Release tag ↔ versionName agreement ─────────────────────────────────────
// A release is cut by pushing a `v<x>.<y>.<z>` tag, and the release workflow
// names every artefact after that tag — but the version compiled into the APK
// comes from `versionName` above. Nothing else forces the two to agree, and the
// mismatch is invisible until someone reads the About screen of a file called
// `knotwork-0.7.0-full-release.apk` and sees `0.6.0`.
//
// `versionName` stays the source of truth (the F-Droid recipe builds a tag with
// no Gradle properties injected and must still get the right number), so this
// task asserts the agreement rather than overwriting anything. It is invoked
// explicitly by `.github/workflows/release.yml` before the release build, and is
// deliberately NOT wired into `check`: the tag only exists at release time.
tasks.register("verifyReleaseVersion") {
    group = "verification"
    description = "Fails when `-PreleaseTag=<tag>` disagrees with the versionName declared in this build."
    val releaseTag = providers.gradleProperty("releaseTag")
    val declaredVersionName = android.defaultConfig.versionName.orEmpty()
    doLast {
        val tag = releaseTag.orNull
            ?: throw GradleException(
                "`verifyReleaseVersion` needs the release tag: " +
                    "run it as `./gradlew :app:verifyReleaseVersion -PreleaseTag=v<major>.<minor>.<patch>`.",
            )
        ReleaseVersionChecker.verify(tag = tag, declaredVersionName = declaredVersionName)
            ?.let { throw GradleException(it) }
        logger.lifecycle("Release tag `$tag` matches the declared versionName `$declaredVersionName`.")
    }
}
