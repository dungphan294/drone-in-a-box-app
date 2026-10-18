import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.dronebox.app.MainKt"

        // The dock is IPv4-only. Without this, looking up dock.local also asks
        // for an IPv6 address, and macOS waits ~5 s for that answer -- longer
        // than the app's 2 s request timeout, so dock.local never connects.
        jvmArgs += "-Djava.net.preferIPv4Stack=true"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Dock"
            packageVersion = "1.0.0"
        }
    }
}