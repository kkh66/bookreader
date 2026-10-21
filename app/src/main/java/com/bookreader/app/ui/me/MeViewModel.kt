package com.bookreader.app.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bookreader.app.container
import com.bookreader.app.data.SettingsRepository
import com.bookreader.app.data.ThemeMode

class MeViewModel(private val settings: SettingsRepository) : ViewModel() {

    val themeMode = settings.themeMode
    val server = settings.server

    fun setThemeMode(mode: ThemeMode) = settings.setThemeMode(mode)

    companion object {
        val Factory = viewModelFactory {
            initializer { MeViewModel(this[APPLICATION_KEY]!!.container.settings) }
        }
    }
}
