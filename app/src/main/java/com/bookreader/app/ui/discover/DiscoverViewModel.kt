package com.bookreader.app.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bookreader.app.container
import com.bookreader.app.data.LibraryRepository
import com.bookreader.app.data.SettingsRepository
import kotlinx.coroutines.launch

/**
 * Discover tab: library list (grid or list), deleting libraries
 */
class DiscoverViewModel(
    private val libraries: LibraryRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val items = libraries.libraries
    val grid = settings.libraryGrid

    fun setGrid(grid: Boolean) = settings.setLibraryGrid(grid)

    fun delete(id: String) {
        viewModelScope.launch { libraries.delete(id) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = this[APPLICATION_KEY]!!.container
                DiscoverViewModel(container.libraries, container.settings)
            }
        }
    }
}
