package com.bookreader.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bookreader.app.container
import com.bookreader.app.data.OpdsServer
import com.bookreader.app.data.SettingsRepository

class SettingsViewModel(private val settings: SettingsRepository) : ViewModel() {

    val server get() = settings.server.value

    fun save(server: OpdsServer) = settings.save(server)

    companion object {
        val Factory = viewModelFactory {
            initializer { SettingsViewModel(this[APPLICATION_KEY]!!.container.settings) }
        }
    }
}
