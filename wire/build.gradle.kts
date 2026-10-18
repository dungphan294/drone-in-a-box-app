// The wire format: canonical form, HMAC, BLE framing.
//
// Deliberately has NO third-party dependencies. These tests pin the bytes that
// the firmware, the bench client and the app must all agree on, and they must
// never be blocked by an unrelated library failing to resolve -- the same
// reason the firmware's `auth` component has no ESP-IDF headers.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
    androidLibrary {
        namespace = "box.dock.wire"
        compileSdk = 37
        minSdk = 26
    }

    jvm()                       // commonTest runs here: no device, no emulator
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
