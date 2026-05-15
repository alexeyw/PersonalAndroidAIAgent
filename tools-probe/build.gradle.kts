plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// The androidx.appfunctions KSP processor generates the per-class
// `*_AppFunctionInventory.kt` / `*_AppFunctionInvoker.kt` artefacts unconditionally,
// but the leaf-application `app_functions_v2.xml` (and the legacy `app_functions.xml`)
// that the platform's AppSearch indexer actually reads at install time is only
// produced when `appfunctions:aggregateAppFunctions=true`. Without this flag the probe
// APK ships an `app_functions_schema.xsd` but no inventory XML, so the system
// AppFunctionManager has nothing to advertise to other apps and the agent's
// `observeAppFunctions` search comes back empty — exactly the symptom that broke
// `AppFunctionsEndToEndTest` on the Pixel 9 Pro Android 16 emulator.
ksp {
    arg("appfunctions:aggregateAppFunctions", "true")
}

android {
    namespace = "ai.agent.android.toolsprobe"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "ai.agent.android.toolsprobe"
        minSdk = 36
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
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
    }

    packaging {
        resources {
            excludes += "META-INF/*"
        }
    }

    lint {
        abortOnError = false
        warningsAsErrors = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    implementation(libs.androidx.appfunctions)
    implementation(libs.androidx.appfunctions.service)
    ksp(libs.androidx.appfunctions.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
