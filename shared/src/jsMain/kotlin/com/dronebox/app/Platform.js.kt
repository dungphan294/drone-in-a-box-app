package com.dronebox.app

actual fun pageOrigin(): String? = js("window.location.origin") as String

// localStorage can be missing or throw (private windows, blocked storage);
// a preference that is not remembered is fine, a crash is not.
actual fun readPref(key: String): String? =
    runCatching { js("window.localStorage.getItem(key)") as String? }.getOrNull()

actual fun writePref(key: String, value: String) {
    runCatching { js("window.localStorage.setItem(key, value)") }
}
