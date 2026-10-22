// Macrobenchmarks (ALL_IN_ONE_PLAN.md 0.5, §16.1): cold start and Files scrolling, run against
// :app's "benchmark" build type on a device or emulator. CI only compiles this module.
plugins {
    id("atomic.android.test")
}

android {
    namespace = "com.devbehindyou.atomicfilemanager.benchmark"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Matches :app's "benchmark" build type: release-like (R8, not debuggable) app, debuggable
    // test APK, both signed with the debug key so they install side by side.
    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = getByName("debug").signingConfig
            matchingFallbacks += listOf("release")
        }
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

// Only the benchmark variant is useful; skip building debug/release test APKs.
androidComponents {
    beforeVariants(selector().all()) { it.enable = it.buildType == "benchmark" }
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    // The existing catalog runner supplies SdkSuppress and the declared AndroidJUnitRunner.
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.benchmark.macro.junit4)
}
