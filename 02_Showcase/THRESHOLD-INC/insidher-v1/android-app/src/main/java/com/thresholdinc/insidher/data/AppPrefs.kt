package com.thresholdinc.insidher.data

import android.content.Context

/**
 * Tiny SharedPreferences store for onboarding / device id.
 */
class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var deviceId: String?
        get() = prefs.getString(KEY_DEVICE_ID, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_ID, value).apply()

    var personaId: String?
        get() = prefs.getString(KEY_PERSONA_ID, null)
        set(value) = prefs.edit().putString(KEY_PERSONA_ID, value).apply()

    var personaName: String?
        get() = prefs.getString(KEY_PERSONA_NAME, null)
        set(value) = prefs.edit().putString(KEY_PERSONA_NAME, value).apply()

    var onboarded: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDED, value).apply()

    var backendUrl: String
        get() = prefs.getString(KEY_BACKEND, null) ?: DEFAULT_BACKEND
        set(value) = prefs.edit().putString(KEY_BACKEND, value).apply()

    companion object {
        private const val PREFS = "insidher_prefs"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_PERSONA_ID = "persona_id"
        private const val KEY_PERSONA_NAME = "persona_name"
        private const val KEY_ONBOARDED = "onboarded"
        private const val KEY_BACKEND = "backend_url"
        const val DEFAULT_BACKEND = "http://10.0.2.2:8788"
    }
}
