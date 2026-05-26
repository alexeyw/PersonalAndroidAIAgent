import dev.detekt.gradle.Detekt

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
    // Phase 23 / Task 3/9 — export the Room schema for every version so that
    // `MigrationTestHelper` can validate migrations against frozen JSON
    // snapshots in `app/schemas/`. The corresponding `exportSchema = true`
    // flag is set on the `@Database` annotation. Every future schema bump
    // must commit the newly generated `N.json` alongside the migration.
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "ai.agent.android"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "ai.agent.android"
        minSdk = 36
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Phase 19 / Task 1/10: license metadata exposed as Android string
        // resources so a future "About" dialog can render the license name and
        // link to the canonical text without hardcoding the values in UI code.
        resValue("string", "license_name", "Apache License 2.0")
        resValue("string", "license_url", "https://www.apache.org/licenses/LICENSE-2.0")

        // Phase 22 / Task 8: surface the build's short git SHA inside the
        // About row of the Knotwork settings screen so users can paste a
        // precise build identifier into bug reports without needing the APK
        // hash. Falls back to "unknown" when `git` is unavailable (e.g. a
        // tarball build).
        buildConfigField("String", "GIT_SHA", "\"${resolveGitSha()}\"")

        // Phase 22 / Task 9: build date surfaced in the Settings top-app-bar
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

    buildTypes {
        release {
            // Phase 22 / Task 17 — R8 in full mode + resource shrinking.
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
            // v0.2.0 ships signed with the debug keystore. A real
            // `signingConfigs.release` block (with `keyAlias` / `keyPassword`
            // sourced from `local.properties` or environment variables) is
            // intentionally deferred: the Play Store submission lands in a
            // separate task once the production keystore is provisioned,
            // tracked in `docs/release.md`. Installing the GitHub-Release
            // APK on a developer device still works with the debug signing
            // identity.
            signingConfig = signingConfigs.getByName("debug")
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
        // Phase 23 / Task 4/9 — Robolectric needs the merged Android resources
        // and assets on the JVM unit-test classpath (string lookups in
        // `LongRunningTaskNotifierImpl`, drawables resolved by NotificationCompat
        // builders). Without this flag Robolectric falls back to its own minimal
        // resource table and `context.getString(R.string.…)` returns a placeholder.
        unitTests.isIncludeAndroidResources = true
    }

    // Phase 23 / Task 3/9 — expose the exported Room schemas to the
    // androidTest classpath so `MigrationTestHelper` (which reads them from
    // `assets/`) can validate every migration step against the frozen
    // snapshots in `app/schemas/`.
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    packaging {
        resources {
            excludes += "META-INF/*"
            // Phase 22 / Task 17 — Jansi (transitive via Koog → log adapter)
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
        // Phase 18 / Task 9-10: strict mode. `abortOnError` + `warningsAsErrors`
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
        // Phase 21 / Task 11/11: the release variant ships `arm64-v8a` only
        // (every `minSdk = 36` device is 64-bit). ChromeOS support is not in
        // scope for v0.1 — disable the lint check that demands an x86 binary.
        disable += "ChromeOsAbiSupport"
    }
}

detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    // Phase 18 / Task 9-10: strict mode. Any finding emitted at
    // `severity: error` (default `failOnSeverity`) now fails the build.
    // The legacy 1.x `failFast` switch was removed in detekt 1.22 and is
    // intentionally not used here.
    ignoreFailures = false
    basePath.set(rootDir)
    source.setFrom("src/main/java", "src/main/kotlin")
}

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
    // Phase 18 / Task 9-10: strict mode. Any ktlint violation that survives
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
// Phase 18 / Task 9-10: aggregate threshold enforced via `koverVerifyDebug`.
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
// The single rule then enforces a 70 % aggregate floor over the
// remaining "unit-testable" surface — domain + data.repositories +
// presentation ViewModels / UiStates. Baseline numbers for those layers
// (89 % / 83 % / ~55 % respectively) leave comfortable headroom; the floor
// protects against silent regressions while leaving room for refactors.
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
                    "ai.agent.android.data.local.AppDatabase",
                    "ai.agent.android.data.local.AppDatabase_Impl*",
                    "*_AutoMigration_*",
                    "ai.agent.android.data.local.dao.*",
                    // Compose synthetic singletons + project-convention preview files
                    // (every Compose `@Preview` lives in a `*Preview.kt` file).
                    "*ComposableSingletons*",
                    "ComposableSingletons$*",
                    "*Preview",
                    "*PreviewKt",
                    // Hilt DI modules — wiring code, no business logic to cover.
                    "ai.agent.android.di.*",
                    // Generated build artefacts.
                    "ai.agent.android.BuildConfig",
                    "*.databinding.*",
                    "*.BR",
                    // Android-runtime-bound presentation classes that need
                    // instrumented (androidTest / Compose UI test) coverage,
                    // out of scope for the JVM-only Kover pipeline.
                    "ai.agent.android.App",
                    "ai.agent.android.presentation.ui.MainActivity",
                    "ai.agent.android.presentation.ui.MainActivity$*",
                    "ai.agent.android.presentation.ui.*Screen",
                    "ai.agent.android.presentation.ui.*ScreenKt",
                    "ai.agent.android.presentation.ui.*Screen$*",
                    // Legacy chat surface (Phase 21 / Task 8: moved under chat/legacy/
                    // pending the post-v0.1 orchestrator-rewiring task).
                    "ai.agent.android.presentation.ui.chat.legacy.ConsoleFullLogSheet*",
                    "ai.agent.android.presentation.ui.chat.legacy.ConsolePanelCollapsed*",
                    "ai.agent.android.presentation.ui.chat.legacy.AgentThoughtIndicator*",
                    "ai.agent.android.presentation.ui.chat.legacy.ClarificationCard*",
                    "ai.agent.android.presentation.ui.chat.legacy.PipelineTraceCard*",
                    "ai.agent.android.presentation.ui.chat.legacy.ApprovalBanner*",
                    "ai.agent.android.presentation.ui.chat.legacy.ChatScreen*",
                    // Redesigned chat-home Composables (Phase 21 / Task 8) — Compose
                    // surfaces are covered by `:catalog` Roborazzi snapshots; the
                    // testable VM / state-mapping live next to them and are
                    // included in coverage.
                    "ai.agent.android.presentation.ui.chat.home.ChatHomeScreen*",
                    "ai.agent.android.presentation.ui.chat.home.ChatHomeDebugStatePicker*",
                    "ai.agent.android.presentation.ui.chat.home.DebugStateRows*",
                    "ai.agent.android.presentation.ui.components.*",
                    "ai.agent.android.presentation.ui.orchestrator.components.*",
                    // Phase 21 / Task 9 — pipeline editor Compose layer. Gestures,
                    // animations, and Bezier draw paths are intentionally outside the
                    // JVM Kover scope; the pure-Kotlin core (CanvasTransform,
                    // AutoLayout, EditorUndoRedo, BezierEdge, NodeConfigCodec) plus
                    // the VM hooks ARE covered. Screen-level visual coverage rides
                    // on the catalog's PipelineEditorCatalogPageSnapshotTest (Task 7)
                    // and the Phase 21 / Task 11 a11y + release-candidate gate.
                    "ai.agent.android.presentation.ui.pipeline.editor.canvas.*",
                    "ai.agent.android.presentation.ui.pipeline.editor.bars.*",
                    "ai.agent.android.presentation.ui.pipeline.editor.sheet.*",
                    "ai.agent.android.presentation.ui.pipeline.editor.PipelineEditorContent*",
                    "ai.agent.android.presentation.ui.pipeline.editor.PipelineEditorScreen*",
                    "ai.agent.android.presentation.ui.splash.SplashScreen*",
                    "ai.agent.android.presentation.theme.*",
                    "ai.agent.android.presentation.notifications.*",
                    "ai.agent.android.presentation.receivers.*",
                    "ai.agent.android.presentation.state.*",
                    // Phase 23 / Task 4/9 — `data.services.*` is now covered by
                    // Robolectric tests (`AgentForegroundServiceTest`,
                    // `AgentWorkerTest`, `AgentIdleManagerTest`,
                    // `AgentPowerManagerTest`, `LongRunningTaskNotifierImplTest`).
                    // The exclusion that was here while the package waited for
                    // Robolectric coverage has been lifted.
                    // Tool-execution Android glue (AppFunctions service, search
                    // tool HTTP client, delegate-task LLM bridge) needs either
                    // an Android runtime or live LLM/HTTP fixtures.
                    "ai.agent.android.data.tools.local.AgentAppFunctionService",
                    "ai.agent.android.data.tools.local.AgentAppFunctionService$*",
                    "ai.agent.android.data.tools.local.LocalAppFunctionManager",
                    "ai.agent.android.data.tools.local.SearchTool*",
                    "ai.agent.android.data.tools.local.DelegateTaskTool*",
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
                    "ai.agent.android.data.logging.CrashlyticsTimberTree*",
                )
                // Belt-and-braces: also skip any @Preview-annotated function that
                // happens to live outside a *Preview.kt file.
                annotatedBy("androidx.compose.ui.tooling.preview.Preview")
            }
        }

        verify {
            rule("Aggregate unit-testable coverage must stay ≥70%") {
                groupBy = kotlinx.kover.gradle.plugin.dsl.GroupingEntityType.APPLICATION
                bound {
                    minValue = 70
                    coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE
                    aggregationForGroup =
                        kotlinx.kover.gradle.plugin.dsl.AggregationType.COVERED_PERCENTAGE
                }
            }
        }
    }
}

// Phase 18 / Task 9-10: wire `koverVerifyDebug` into the `check` lifecycle
// so a single `./gradlew check` invocation runs detekt + ktlintCheck +
// lintDebug + unit tests + coverage verification.
tasks.named("check") { dependsOn("koverVerifyDebug") }

// Phase 18 / Task 7/10 — enforces "no internal FQN" rule for the
// `ai.agent.android.*` package. References like
// `ai.agent.android.domain.models.NodeType` outside `import`/`package`
// statements must be replaced with a top-level import + short name. KDoc
// lines (whitespace-then-`*`) and the `.editorconfig` pinned ktlint rules
// catch the rest (wildcard imports, unused imports, import ordering).
val checkNoInternalFqn by tasks.registering {
    group = "verification"
    description =
        "Fails the build if any internal `ai.agent.android.*` FQN reference appears in source code " +
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
        val fqnPattern = Regex("""\bai\.agent\.android\.[a-z_]+\.[A-Za-z]""")
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

dependencies {
    // Phase 21 / Task 1/11: design-system module — `KnotworkTheme` (currently
    // a `MaterialTheme` pass-through) plus the foundations Task 2/11 will
    // port into Kotlin sources.
    implementation(project(":catalog"))

    // Phase 21 / Task 1/11: `androidx.core.splashscreen` artefact — the
    // platform-side splash is wired in Task 3/11 once the brand mark and
    // accent token land. Declaring the dependency here keeps the build green
    // when downstream tasks reference `installSplashScreen(...)`.
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

    // Security Crypto
    implementation(libs.androidx.security.crypto)

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
    // Phase 23 / Task 4/9 — Robolectric is needed for the foreground service,
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

// Phase 20 / Task 6/7: install the :tools-probe debug APK alongside the agent's test
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
