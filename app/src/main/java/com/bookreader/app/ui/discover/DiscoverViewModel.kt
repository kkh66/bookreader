package com.bookreader.app.ui.discover

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bookreader.app.R
import com.bookreader.app.container
import com.bookreader.app.data.LibraryRepository
import com.bookreader.app.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Discover tab: library list (grid or list), deleting libraries
 */
class DiscoverViewModel(
    private val libraries: LibraryRepository,
    private val settings: SettingsRepository,
    private val context: Context,
) : ViewModel() {
    private val strings get() = ContextCompat.getContextForLanguage(context).resources


    val items = libraries.libraries
    val grid = settings.libraryGrid

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    fun setGrid(grid: Boolean) = settings.setLibraryGrid(grid)

    fun delete(id: String) {
        viewModelScope.launch {
            try {
                libraries.delete(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = strings.getString(R.string.library_delete_failed, e.message.orEmpty())
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = this[APPLICATION_KEY]!!.container
                DiscoverViewModel(container.libraries, container.settings, this[APPLICATION_KEY]!!)
            }
        }
    }
}
