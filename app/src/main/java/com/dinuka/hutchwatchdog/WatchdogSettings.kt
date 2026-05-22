package com.dinuka.hutchwatchdog

import android.content.Context

class WatchdogSettings(context: Context) {
    private val prefs = context.getSharedPreferences("watchdog_settings", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var preset: ProbePreset
        get() = ProbePreset.valueOf(prefs.getString(KEY_PRESET, ProbePreset.BALANCED.name)!!)
        set(value) = prefs.edit().putString(KEY_PRESET, value.name).apply()

    var customProbeUrl: String
        get() = prefs.getString(KEY_CUSTOM_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_URL, value.trim()).apply()

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_PRESET = "preset"
        private const val KEY_CUSTOM_URL = "custom_url"
    }
}
