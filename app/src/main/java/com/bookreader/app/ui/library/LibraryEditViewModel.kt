package com.bookreader.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bookreader.app.container
import com.bookreader.app.data.LibraryRepository
import com.bookreader.app.data.OpdsLibrary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Add / edit a library
 */
class LibraryEditViewModel(private val libraries: LibraryRepository) : ViewModel() {

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    /** The library being edited, or null when creating a new one */
    fun load(id: String?): OpdsLibrary? = id?.let(libraries::find)

    /** Falls back to the host name when the name is left empty */
    fun save(id: String?, name: String, url: String, username: String, password: String, onSaved: () -> Unit) {
        val base = OpdsLibrary(name = name.trim(), url = url.trim(), username = username.trim(), password = password)
        val library = (if (id != null) base.copy(id = id) else base).let { it.copy(name = it.name.ifBlank { it.host }) }
        clearError()
        viewModelScope.launch {
            try {
                libraries.save(library)
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "保存书库失败：${e.message.orEmpty()}"
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { LibraryEditViewModel(this[APPLICATION_KEY]!!.container.libraries) }
        }
    }
}
