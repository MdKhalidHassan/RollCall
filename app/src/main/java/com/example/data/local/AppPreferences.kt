package com.example.data.local

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isGuestMode(): Boolean = prefs.getBoolean(KEY_GUEST_MODE, false)

    fun setGuestMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GUEST_MODE, enabled).apply()
    }

    private companion object {
        const val PREFS_NAME = "rollcall_prefs"
        const val KEY_GUEST_MODE = "guest_mode_enabled"
    }
}
