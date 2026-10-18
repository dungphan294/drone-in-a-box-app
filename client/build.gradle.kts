// HTTP transport. Depends on Ktor, so a version problem here cannot stop
// :wire:jvmTest from running.
//
// Versions are unverified -- let Android Studio's sync correct them.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidLibrary {
        namespace = "com.dronebox.app.client"
        compileSdk = 37
        minSdk = 26
    }

    jvm()

    // One umbrella framework for the iOS app to link. `export` makes wire's
    // types (DockCrypto, DockGatt) visible to Swift as well; without it they
    // are compiled in but not exposed.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "DockKit"
            isStatic = true
            export(project(":wire"))
        }
    }

    sourceSets {
        val ktor = "3.3.3"
        commonMain.dependencies {
            api(project(":wire"))   // api, not implementation: required by export()
            implementation("io.ktor:ktor-client-core:$ktor")
            implementation("io.ktor:ktor-client-content-negotiation:$ktor")
            implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
        }
        iosMain.dependencies { implementation("io.ktor:ktor-client-darwin:$ktor") }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutinesTest)
        }
    }
}
