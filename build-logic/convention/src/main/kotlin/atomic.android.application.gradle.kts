import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion

// Shared Android application baseline. Per architecture/MODULES.md §6, this is the one
// place compileSdk/minSdk/targetSdk are set — an app-level build.gradle.kts should never
// set these directly, so a version bump is a one-line change here.
//
// AGP 9.x note: com.android.application now ships built-in Kotlin support (no separate
// org.jetbrains.kotlin.android plugin needed/applied here) — see PHASE_1_NOTES.md for
// why this differs from pre-2026 Android build setups.

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

extensions.configure<ApplicationExtension> {
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        targetSdk = 36
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}
