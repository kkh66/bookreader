package com.bookreader.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) {
    System("跟随系统"),
    Light("浅色"),
    Dark("深色"),
}

/**
 * App settings (appearance, library layout), stored in app-private SharedPreferences
 * The library list lives in [LibraryRepository]
 */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeMode.System
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    /** Discover tab shows libraries as a grid (otherwise a list) */
    private val _libraryGrid = MutableStateFlow(prefs.getBoolean(KEY_LIBRARY_GRID, true))
    val libraryGrid: StateFlow<Boolean> = _libraryGrid.asStateFlow()

    /** Shelf uses a grid (otherwise a list) */
    private val _shelfGrid = MutableStateFlow(prefs.getBoolean(KEY_SHELF_GRID, true))
    val shelfGrid: StateFlow<Boolean> = _shelfGrid.asStateFlow()

    fun setShelfGrid(grid: Boolean) {
        prefs.edit().putBoolean(KEY_SHELF_GRID, grid).apply()
        _shelfGrid.value = grid
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    fun setLibraryGrid(grid: Boolean) {
        prefs.edit().putBoolean(KEY_LIBRARY_GRID, grid).apply()
        _libraryGrid.value = grid
    }

    /** 0.2.x had a single server stored here; unused after migrating to the library list */
    fun legacyServer(): OpdsLibrary? {
        val url = prefs.getString(KEY_URL, null)?.takeIf { it.isNotBlank() } ?: return null
        return OpdsLibrary(
            name = "Calibre 书库",
            url = url,
            username = prefs.getString(KEY_USERNAME, null).orEmpty(),
            password = prefs.getString(KEY_PASSWORD, null).orEmpty(),
        )
    }

    private companion object {
        const val KEY_URL = "opds_url"
        const val KEY_USERNAME = "opds_username"
        const val KEY_PASSWORD = "opds_password"
        const val KEY_THEME = "theme_mode"
        const val KEY_LIBRARY_GRID = "library_grid"
        const val KEY_SHELF_GRID = "shelf_grid"
    }
}
