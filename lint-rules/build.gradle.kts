import java.util.Properties

plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    compileOnly(libs.lint.api)
    compileOnly(libs.lint.checks)

    testImplementation(libs.lint.api)
    testImplementation(libs.lint.checks)
    testImplementation(libs.lint.tests)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // Show lint's expected-vs-actual report in CI logs, not just the assertion class.
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    val localProps =
        Properties().apply {
            val propFile = rootProject.file("local.properties")
            if (propFile.exists()) {
                propFile.inputStream().use { load(it) }
            }
        }
    val sdkDir = localProps.getProperty("sdk.dir") ?: System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    if (sdkDir != null) {
        environment("ANDROID_HOME", sdkDir)
        environment("ANDROID_SDK_ROOT", sdkDir)
    }
}

detekt {
    config.setFrom(files("$rootDir/detekt.yml"))
    buildUponDefaultConfig = true
}
