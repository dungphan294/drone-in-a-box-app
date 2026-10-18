package com.dronebox.app

import android.content.Context
import android.content.SharedPreferences

actual fun pageOrigin(): String? = null

private var store: SharedPreferences? = null

/** Called once by the activity, before the UI reads anything. */
fun initPrefs(context: Context) {
    store = context.applicationContext.getSharedPreferences("dock", Context.MODE_PRIVATE)
}

actual fun readPref(key: String): String? = store?.getString(key, null)

actual fun writePref(key: String, value: String) {
    store?.edit()?.putString(key, value)?.apply()
}
