package com.bookreader.app.ui.library

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
import com.bookreader.app.data.OpdsLibrary
import com.bookreader.app.data.opds.OpdsEntry
import com.bookreader.app.data.opds.OpdsRepository
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val library: OpdsLibrary? = null,
    val folders: List<OpdsEntry.Folder> = emptyList(),
    val searchTemplate: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * Home of a single library: categories and lists from its root catalog
 */
class LibraryViewModel(
    private val libraries: LibraryRepository,
    private val opds: OpdsRepository,
    private val context: Context,
) : ViewModel() {
    private val strings get() = ContextCompat.getContextForLanguage(context).resources


    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    /** Called when the screen opens: reloads only when the library or its URL/credentials changed */
    fun start(libraryId: String) {
        val library = libraries.find(libraryId)
        if (library == null) {
            loadJob?.cancel()
            _state.value = LibraryUiState(error = strings.getString(R.string.library_not_found))
            return
        }
        if (library == _state.value.library && _state.value.error == null) return
        _state.value = LibraryUiState(library = library)
        refresh()
    }

    fun refresh() {
        val library = _state.value.library ?: return
        // Drop the previous request when switching libraries or refreshing again, so stale results can't overwrite the current library
        loadJob?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                val page = opds.load(library)
                _state.update {
                    it.copy(
                        folders = page.entries.filterIsInstance<OpdsEntry.Folder>(),
                        searchTemplate = page.searchTemplate,
                        loading = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: e.toString()) }
            }
        }
    }

    /** Builds the search URL from the server's search template; spaces become %20 so the query can sit in the path */
    fun searchUrl(query: String): String? {
        val template = _state.value.searchTemplate ?: return null
        val term = URLEncoder.encode(query.trim(), "UTF-8").replace("+", "%20")
        return template.replace("{searchTerms}", term)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = this[APPLICATION_KEY]!!.container
                LibraryViewModel(container.libraries, container.opds, this[APPLICATION_KEY]!!)
            }
        }
    }
}
