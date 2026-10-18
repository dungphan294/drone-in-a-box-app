// HTTP transport. Depends on Ktor, so a version problem here cannot stop
// :wire:jvmTest from running.
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

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

    // The browser build of the app talks to the dock through this client as
    // well. In a browser the request goes through fetch(), so it is subject to
    // the same-origin rules -- see webApp/webpack.config.d/dock-proxy.js.
    js { browser() }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

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
        commonMain.dependencies {
            api(project(":wire"))   // api, not implementation: required by export()
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.kotlinx.coroutinesCore)
        }
        // Each platform gets its own engine, so `HttpClient {}` with no
        // explicit engine resolves to the right one everywhere.
        androidMain.dependencies { implementation(libs.ktor.client.okhttp) }
        jvmMain.dependencies { implementation(libs.ktor.client.okhttp) }
        iosMain.dependencies { implementation(libs.ktor.client.darwin) }
        jsMain.dependencies { implementation(libs.ktor.client.js) }
        wasmJsMain.dependencies { implementation(libs.ktor.client.js) }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutinesTest)
        }
    }
}
