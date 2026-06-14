import app.knotwork.android.buildtools.BrowserEditorConstantsGenerator
import dev.detekt.gradle.Detekt
import java.util.Properties

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
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
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
        versionCode = 5
        versionName = "0.5.0"

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
        // the locale-specific rendering. Stable across CI builds — the value
        // reflects when the APK was assembled, not when the binary is
        // installed.
        buildConfigField(
            "long",
            "GIT_COMMIT_DATE_EPOCH_MS",
            "${System.currentTimeMillis()}L",
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        // Robolectric needs the merged Android resources
        // and assets on the JVM unit-test classpath (string lookups in
        // `LongRunningTaskNotifierImpl`, drawables resolved by NotificationCompat
        // builders). Without this flag Robolectric falls back to its own minimal
        // resource table and `context.getString(R.string.…)` returns a placeholder.
        unitTests.isIncludeAndroidResources = true
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
        // `NewerVersionAvailable` / `GradleDependency` (the "a newer version of
        // X is available" checks) are kept ENABLED on purpose: surfacing an
        // outdated dependency is the whole point of the analysis, so we update
        // the dependency rather than silence the check. Genuine false positives
        // (e.g. date-versioned artefacts whose "newer" version is actually
        // older) are grandfathered individually in `lint-baseline.xml`.
    }
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
// rule lives in a second, deliberately narrow run: `detektDebug` (the
// plugin-generated type-resolution task for the debug variant) is rewired to
// `detekt-cancellation.yml`, a config that activates only that single rule.
// Running the full strict config under type resolution instead would surface
// ~1.1k findings from rules that have never been part of the gate — adopting
// them is a separate effort, not a side effect of this wiring. Full-config
// type-resolution analysis remains available via `detektMain`/`detektRelease`.
tasks.matching { it.name == "detektDebug" }.configureEach {
    this as Detekt
    config.setFrom(files("$rootDir/config/detekt/detekt-cancellation.yml"))
    buildUponDefaultConfig.set(false)
}
tasks.named("check") { dependsOn("detektDebug") }

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
                    // Firebase Crashlytics glue: the repository impl and the
                    // Timber tree thinly wrap `FirebaseCrashlytics` /
                    // `FirebaseAnalytics` singletons which need the Android
                    // runtime and Google Play services to initialise.
                    // Unit-test coverage for the no-op-when-disabled and
                    // dispatch branches lives under
                    // `FirebaseCrashReportingRepositoryImplTest` /
                    // `CrashlyticsTimberTreeTest`, but the production
                    // `getInstance()` paths are not exercised on the JVM.
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

// Wire `koverVerifyDebug` into the `check` lifecycle
// so a single `./gradlew check` invocation runs detekt + ktlintCheck +
// lintDebug + unit tests + coverage verification.
tasks.named("check") { dependsOn("koverVerifyDebug") }

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
                    if (fqnPattern.containsMatchIn(line)) {
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

    // MediaPipe Tasks Text
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

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // JSON Serialization
    implementation(libs.gson)

    // Firebase — Crashlytics + Analytics (Analytics is required by Crashlytics).
    // The BoM (Bill of Materials) pins inter-library versions; individual
    // modules are intentionally un-versioned. Starting from Firebase BoM 34.0
    // the `-ktx` artifacts were folded into the base modules and removed.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)

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
val toolsProbeInstall = ":tools-probe:installDebug"
tasks.matching { it.name == "installDebugAndroidTest" || it.name == "connectedDebugAndroidTest" }
    .configureEach { dependsOn(toolsProbeInstall) }
