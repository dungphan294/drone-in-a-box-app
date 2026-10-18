package com.dronebox.app

import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Hosts the shared Compose app. The remote itself -- connect screen, controls,
 * the 400 ms dead-man poll -- lives in :shared, so every platform runs the
 * same code.
 *
 * Polling stops in onStop via the lifecycle the app observes: the dock then
 * stops a running actuator by itself ~1.5 s later, by design.
 */
class MainActivity : ComponentActivity() {

    private val askLocalNetwork = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Nothing to do either way: granted, the polls get through; refused,
        // they time out and the app says it has lost contact.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        initPrefs(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestLocalNetwork()
        setContent { App(onDarkTheme = ::matchSystemBars) }
    }

    /** Light icons on the dark theme, dark icons on the light one -- whatever the device is set to. */
    private fun matchSystemBars(dark: Boolean) {
        val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
        else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    /** The dock is only ever on the local network, so the app is useless without this. */
    private fun requestLocalNetwork() {
        if (Build.VERSION.SDK_INT < LOCAL_NETWORK_SDK) return
        if (checkSelfPermission(LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED) return
        askLocalNetwork.launch(LOCAL_NETWORK)
    }

    private companion object {
        const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
        const val LOCAL_NETWORK_SDK = 37
    }
}
