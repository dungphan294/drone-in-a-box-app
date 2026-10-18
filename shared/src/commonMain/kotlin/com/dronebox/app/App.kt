package com.dronebox.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

private const val THEME_KEY = "theme"

/**
 * The whole app, on every platform: a connect screen, then the remote.
 *
 * The key, when there is one, lives only in memory for the session. Storing
 * it belongs in the platform keystore, never in plain preferences -- see
 * README-dock-app.md.
 *
 * `onDarkTheme` lets a platform match what Compose does not draw itself --
 * the status bar -- to the theme in use.
 */
@Composable
fun App(onDarkTheme: (Boolean) -> Unit = {}) {
    var themeMode by remember {
        mutableStateOf(ThemeMode.entries.firstOrNull { it.name == readPref(THEME_KEY) } ?: ThemeMode.System)
    }
    val dark = themeMode.isDark()
    SideEffect { onDarkTheme(dark) }

    DockTheme(dark) {
        val scope = rememberCoroutineScope()
        var session by remember { mutableStateOf<DockSession?>(null) }
        var host by remember { mutableStateOf(pageOrigin() ?: "192.168.4.1") }
        var key by remember { mutableStateOf("") }

        Surface(color = Dock.Bg, modifier = Modifier.fillMaxSize()) {
            val current = session
            if (current == null) {
                ConnectScreen(host, key) { h, k ->
                    host = h
                    key = k
                    session = DockSession(dockClient(k, h), h, signed = k.isNotEmpty(), parent = scope)
                }
            } else {
                DisposableEffect(current) { onDispose { current.close() } }
                ControlScreen(
                    current,
                    themeMode = themeMode,
                    onThemeMode = {
                        themeMode = it
                        writePref(THEME_KEY, it.name)
                    },
                    onDisconnect = { session = null },
                )
            }
        }
    }
}
