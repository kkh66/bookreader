package com.bookreader.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

enum class ThemeMode(val label: String) {
    System("跟随系统"),
    Light("浅色"),
    Dark("深色"),
}

/**
 * App settings (appearance, library layout, reading style and toggles), stored in app-private SharedPreferences
 * The library list lives in [LibraryRepository]
 */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

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

    /** EPUB text layout: font, size and spacing */
    private val _readingStyle = MutableStateFlow(
        prefs.getString(KEY_READING_STYLE, null)
            ?.let { runCatching { json.decodeFromString<ReadingStyle>(it) }.getOrNull() }
            ?: ReadingStyle()
    )
    val readingStyle: StateFlow<ReadingStyle> = _readingStyle.asStateFlow()

    fun setReadingStyle(style: ReadingStyle) {
        prefs.edit().putString(KEY_READING_STYLE, json.encodeToString(style)).apply()
        _readingStyle.value = style
    }

    /** Style and color of the last highlight, preselected for the next one */
    private val _lastHighlight = MutableStateFlow(
        (HighlightKind.entries.getOrNull(prefs.getInt(KEY_HIGHLIGHT_KIND, 0)) ?: HighlightKind.Highlight) to
            prefs.getInt(KEY_HIGHLIGHT_COLOR, 0)
    )
    val lastHighlight: StateFlow<Pair<HighlightKind, Int>> = _lastHighlight.asStateFlow()

    fun setLastHighlight(kind: HighlightKind, color: Int) {
        prefs.edit().putInt(KEY_HIGHLIGHT_KIND, kind.ordinal).putInt(KEY_HIGHLIGHT_COLOR, color).apply()
        _lastHighlight.value = kind to color
    }

    /** Toggle setting: stored in prefs and exposed as a StateFlow for the UI to observe */
    inner class Toggle(private val key: String, default: Boolean) {
        private val _state = MutableStateFlow(prefs.getBoolean(key, default))
        val state: StateFlow<Boolean> = _state.asStateFlow()

        fun set(on: Boolean) {
            prefs.edit().putBoolean(key, on).apply()
            _state.value = on
        }
    }

    /** Volume keys turn pages while reading: volume down = next page, volume up = previous page */
    val volumeKeyPaging = Toggle("volume_key_paging", default = false)

    /** Go straight back to the last book read when the app opens */
    val openLastOnLaunch = Toggle("open_last_on_launch", default = false)

    /** Hide the system status bar while reading, show it again with the menu */
    val readerHideStatusBar = Toggle("reader_hide_status_bar", default = true)

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
        const val KEY_READING_STYLE = "reading_style"
        const val KEY_HIGHLIGHT_KIND = "highlight_kind"
        const val KEY_HIGHLIGHT_COLOR = "highlight_color"
    }
}
