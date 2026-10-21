package com.bookreader.app.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bookreader.app.container
import com.bookreader.app.data.opds.OpdsEntry
import com.bookreader.app.data.opds.OpdsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLEncoder

data class DiscoverUiState(
    val rootUrl: String = "",
    val libraryName: String = "",
    val folders: List<OpdsEntry.Folder> = emptyList(),
    val searchTemplate: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * Discover tab: shows the categories and lists from the Calibre root catalog
 */
class DiscoverViewModel(private val opds: OpdsRepository) : ViewModel() {

    private val _state = MutableStateFlow(DiscoverUiState())
    val state: StateFlow<DiscoverUiState> = _state.asStateFlow()

    /** Called when the screen opens: reloads only on first entry or when the server URL changed */
    fun start() {
        val root = opds.rootUrl
        if (root == _state.value.rootUrl && _state.value.error == null) return
        refresh()
    }

    fun refresh() {
        val root = opds.rootUrl
        if (root.isBlank()) {
            _state.value = DiscoverUiState(error = "请先填写 Calibre 服务器地址")
            return
        }
        _state.update { it.copy(rootUrl = root, loading = true, error = null) }
        viewModelScope.launch {
            try {
                val page = opds.load(root)
                _state.update {
                    it.copy(
                        libraryName = page.title,
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
            initializer { DiscoverViewModel(this[APPLICATION_KEY]!!.container.opds) }
        }
    }
}
