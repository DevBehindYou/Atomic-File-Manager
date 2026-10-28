import com.android.build.api.dsl.TestExtension
import org.gradle.api.JavaVersion

// Shared baseline for com.android.test modules (today only :benchmark). Applying the plugin
// here puts AGP on the module's classpath through build-logic, the same way :app gets it; a
// bare id("com.android.test") in the module can't be resolved without a version. AGP 9's
// built-in Kotlin covers test modules too, so no org.jetbrains.kotlin.android.

plugins {
    id("com.android.test")
}

extensions.configure<TestExtension> {
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        targetSdk = 36
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
