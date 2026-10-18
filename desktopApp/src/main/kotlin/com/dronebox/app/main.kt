package com.dronebox.app

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Dock",
        // Wide enough for the two-column layout; narrower windows get the phone layout.
        state = rememberWindowState(width = 1120.dp, height = 820.dp),
    ) {
        App()
    }
}
