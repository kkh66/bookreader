package com.bookreader.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OpdsServer(
    val url: String = DEFAULT_OPDS_URL,
    val username: String = "",
    val password: String = "",
)

const val DEFAULT_OPDS_URL = "http://192.168.1.100:8083/opds"

enum class ThemeMode(val label: String) {
    System("跟随系统"),
    Light("浅色"),
    Dark("深色"),
}

/**
 * App settings (OPDS server, appearance), stored in app-private SharedPreferences
 */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _server = MutableStateFlow(readServer())
    val server: StateFlow<OpdsServer> = _server.asStateFlow()

    private val _themeMode = MutableStateFlow(
        ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeMode.System
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun save(server: OpdsServer) {
        val clean = server.copy(url = server.url.trim(), username = server.username.trim())
        prefs.edit()
            .putString(KEY_URL, clean.url)
            .putString(KEY_USERNAME, clean.username)
            .putString(KEY_PASSWORD, clean.password)
            .apply()
        _server.value = clean
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    private fun readServer() = OpdsServer(
        url = prefs.getString(KEY_URL, null) ?: DEFAULT_OPDS_URL,
        username = prefs.getString(KEY_USERNAME, null).orEmpty(),
        password = prefs.getString(KEY_PASSWORD, null).orEmpty(),
    )

    private companion object {
        const val KEY_URL = "opds_url"
        const val KEY_USERNAME = "opds_username"
        const val KEY_PASSWORD = "opds_password"
        const val KEY_THEME = "theme_mode"
    }
}
