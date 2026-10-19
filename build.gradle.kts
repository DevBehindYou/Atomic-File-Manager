// Intentionally minimal. Per architecture/MODULES.md §6, shared build configuration
// lives in build-logic/ convention plugins, not in root-project `subprojects {}` blocks
// or `allprojects {}` blocks — those hide configuration from the module that uses it.
// The root shares Android and Kotlin plugin classes; modules still configure themselves.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

