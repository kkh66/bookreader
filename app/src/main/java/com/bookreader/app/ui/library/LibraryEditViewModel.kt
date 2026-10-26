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
import com.bookreader.app.data.opds.OpdsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Add / edit a library
 */
class LibraryEditViewModel(
    private val libraries: LibraryRepository,
    private val opds: OpdsRepository,
    private val context: Context,
) : ViewModel() {
    private val strings get() = ContextCompat.getContextForLanguage(context).resources


    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    /** Outcome of the connection test for the address and account as typed; null when untested since they last changed */
    sealed interface TestResult {
        data object Running : TestResult
        data class Connected(val title: String) : TestResult
        data class Failed(val message: String) : TestResult
    }

    private val _test = MutableStateFlow<TestResult?>(null)
    val test = _test.asStateFlow()
    private var testJob: Job? = null

    /** Fetches the feed with these settings, without saving them: wrong address, account or a non-OPDS page each fail with their own message */
    fun test(url: String, username: String, password: String) {
        testJob?.cancel()
        _test.value = TestResult.Running
        testJob = viewModelScope.launch {
            _test.value = try {
                val page = opds.load(OpdsLibrary(name = "", url = url, username = username.trim(), password = password))
                TestResult.Connected(page.title.ifBlank { url })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                TestResult.Failed(e.message ?: e.toString())
            }
        }
    }

    /** The settings changed: an earlier result no longer applies, and a test still running is dropped */
    fun clearTest() {
        testJob?.cancel()
        _test.value = null
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
                _error.value = strings.getString(R.string.library_save_failed, e.message.orEmpty())
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                LibraryEditViewModel(app.container.libraries, app.container.opds, app)
            }
        }
    }
}
