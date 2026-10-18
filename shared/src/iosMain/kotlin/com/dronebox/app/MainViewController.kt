package com.dronebox.app

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

fun MainViewController() = ComposeUIViewController {
    App(onDarkTheme = ::matchWindowStyle)
}

/**
 * Pin the window to the app's theme, so the status bar's text is readable
 * when the app is in light mode on a phone set to dark, and the other way round.
 */
private fun matchWindowStyle(dark: Boolean) {
    val style = if (dark) UIUserInterfaceStyle.UIUserInterfaceStyleDark else UIUserInterfaceStyle.UIUserInterfaceStyleLight
    UIApplication.sharedApplication.connectedScenes.forEach { scene ->
        (scene as? UIWindowScene)?.windows?.forEach { (it as? UIWindow)?.overrideUserInterfaceStyle = style }
    }
}
