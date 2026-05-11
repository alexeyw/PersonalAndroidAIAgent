import dev.detekt.gradle.Detekt

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
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
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "META-INF/*"
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
                    "ai.agent.android.presentation.ui.chat.ConsoleFullLogSheet*",
                    "ai.agent.android.presentation.ui.chat.ConsolePanelCollapsed*",
                    "ai.agent.android.presentation.ui.chat.AgentThoughtIndicator*",
                    "ai.agent.android.presentation.ui.chat.ClarificationCard*",
                    "ai.agent.android.presentation.ui.chat.PipelineTraceCard*",
                    "ai.agent.android.presentation.ui.components.*",
                    "ai.agent.android.presentation.ui.orchestrator.components.*",
                    "ai.agent.android.presentation.ui.splash.SplashScreen*",
                    "ai.agent.android.presentation.theme.*",
                    "ai.agent.android.presentation.notifications.*",
                    "ai.agent.android.presentation.receivers.*",
                    "ai.agent.android.presentation.state.*",
                    // Background-execution glue (Foreground service / WorkManager
                    // worker / power & idle managers) — runs only on a real
                    // Android process; not unit-testable from the JVM.
                    "ai.agent.android.data.services.*",
                    // Tool-execution Android glue (AppFunctions service, search
                    // tool HTTP client, delegate-task LLM bridge) needs either
                    // an Android runtime or live LLM/HTTP fixtures.
                    "ai.agent.android.data.tools.local.AgentAppFunctionService",
                    "ai.agent.android.data.tools.local.AgentAppFunctionService$*",
                    "ai.agent.android.data.tools.local.LocalAppFunctionManager",
                    "ai.agent.android.data.tools.local.SearchTool*",
                    "ai.agent.android.data.tools.local.DelegateTaskTool*",
                    "ai.agent.android.data.tools.local.executors.*",
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

    testImplementation(libs.json)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.work.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.mockk.android)
}
