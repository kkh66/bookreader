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

/**
 * OPDS server settings, stored in app-private SharedPreferences
 */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _server = MutableStateFlow(read())
    val server: StateFlow<OpdsServer> = _server.asStateFlow()

    fun save(server: OpdsServer) {
        val clean = server.copy(url = server.url.trim(), username = server.username.trim())
        prefs.edit()
            .putString(KEY_URL, clean.url)
            .putString(KEY_USERNAME, clean.username)
            .putString(KEY_PASSWORD, clean.password)
            .apply()
        _server.value = clean
    }

    private fun read() = OpdsServer(
        url = prefs.getString(KEY_URL, null) ?: DEFAULT_OPDS_URL,
        username = prefs.getString(KEY_USERNAME, null).orEmpty(),
        password = prefs.getString(KEY_PASSWORD, null).orEmpty(),
    )

    private companion object {
        const val KEY_URL = "opds_url"
        const val KEY_USERNAME = "opds_username"
        const val KEY_PASSWORD = "opds_password"
    }
}
