package com.dronebox.app

import java.util.prefs.Preferences

actual fun pageOrigin(): String? = null

private val node: Preferences = Preferences.userRoot().node("com/dronebox/app")

actual fun readPref(key: String): String? = node.get(key, null)

actual fun writePref(key: String, value: String) = node.put(key, value)
