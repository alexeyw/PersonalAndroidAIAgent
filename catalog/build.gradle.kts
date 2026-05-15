import dev.detekt.gradle.Detekt

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "app.knotwork.design"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 36

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            consumerProguardFiles("consumer-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Roborazzi requires Robolectric to load real Android resources during
    // unit tests so Compose can render against actual font / drawable
    // pipelines instead of stubs.
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources {
            excludes += "META-INF/*"
        }
    }

    lint {
        // Phase 21 / Task 1/11: mirror :app's strict-mode posture so design-system
        // code is held to the same gate as the application surface. The baseline is
        // generated lazily — `./gradlew :catalog:updateLintBaseline` writes the file
        // on first run; until then the module has no known issues to grandfather.
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
    // Mirror :app — any `severity: error` finding fails the build.
    ignoreFailures = false
    basePath.set(rootDir)
    source.setFrom("src/main/java", "src/test/java")
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
    // Mirror :app — any ktlint violation that survives `ktlintFormat` fails the build.
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

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}
