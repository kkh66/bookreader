package com.bookreader.app.ui.me

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bookreader.app.container
import com.bookreader.app.data.LibraryRepository
import com.bookreader.app.data.SettingsRepository
import com.bookreader.app.data.ThemeMode

class MeViewModel(
    private val settings: SettingsRepository,
    libraries: LibraryRepository,
) : ViewModel() {

    val themeMode = settings.themeMode
    val startupAnimation = settings.startupAnimation.state
    val libraries = libraries.libraries

    fun setThemeMode(mode: ThemeMode) = settings.setThemeMode(mode)

    fun setStartupAnimation(enabled: Boolean) = settings.startupAnimation.set(enabled)

    fun setLanguage(language: String) = AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = this[APPLICATION_KEY]!!.container
                MeViewModel(container.settings, container.libraries)
            }
        }
    }
}
