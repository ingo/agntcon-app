@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
    applyDefaultHierarchyTemplate()

    android {
        namespace = "com.jetbrains.kotlinconf.core"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions.jvmTarget = JvmTarget.JVM_11
    }

    jvm()

    iosArm64()
    iosSimulatorArm64()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }
    js {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.serialization.core)
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }

    jvmToolchain(21)
}

// Real Sessionize feed snapshots for the JVM tests (see testdata/sessionize/README.md)
tasks.named<Test>("jvmTest") {
    val testData = rootProject.layout.projectDirectory.dir("testdata/sessionize")
    inputs.dir(testData)
    systemProperty("sessionize.testdata", testData.asFile.absolutePath)
}
