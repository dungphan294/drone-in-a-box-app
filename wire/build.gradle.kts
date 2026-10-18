// The wire format: canonical form, HMAC, BLE framing.
//
// Deliberately has NO third-party dependencies. These tests pin the bytes that
// the firmware, the bench client and the app must all agree on, and they must
// never be blocked by an unrelated library failing to resolve -- the same
// reason the firmware's `auth` component has no ESP-IDF headers.
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
    androidLibrary {
        namespace = "com.dronebox.app.wire"
        compileSdk = 37
        minSdk = 26
    }

    jvm()                       // commonTest runs here: no device, no emulator
    iosArm64()
    iosSimulatorArm64()

    // The web app signs requests too, so the wire format has to reach it.
    js { browser() }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    sourceSets {
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
