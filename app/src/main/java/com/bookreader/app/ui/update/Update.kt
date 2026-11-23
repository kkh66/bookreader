package com.bookreader.app.ui.update

import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bookreader.app.R
import com.bookreader.app.container
import com.bookreader.app.data.AppUpdate
import com.bookreader.app.data.AppUpdater
import com.bookreader.app.ui.components.Space
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val update: AppUpdate) : UpdateState
    data class Downloading(val update: AppUpdate, val progress: Float) : UpdateState
    /** Downloaded; the installer was opened, and can be opened again if it was dismissed */
    data class Ready(val update: AppUpdate, val apk: File) : UpdateState
}

/** Activity-wide: the startup check and the Me tab's "check for updates" share one state and one dialog */
class UpdateViewModel(private val updater: AppUpdater, autoCheck: Boolean) : ViewModel() {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state = _state.asStateFlow()

    /** One-off result of a manual check, shown as a toast: true = up to date, false = failed */
    private val _message = MutableStateFlow<Boolean?>(null)
    val message = _message.asStateFlow()

    private var job: Job? = null

    init {
        if (autoCheck) check(manual = false)
    }

    fun check(manual: Boolean) {
        if (_state.value != UpdateState.Idle) return
        _state.value = UpdateState.Checking
        job = viewModelScope.launch {
            val result = runCatching { updater.check() }
            val update = result.getOrNull()
            _state.value = if (update != null) UpdateState.Available(update) else UpdateState.Idle
            if (manual && update == null) _message.value = result.isSuccess
        }
    }

    fun download() {
        val update = (_state.value as? UpdateState.Available)?.update ?: return
        _state.value = UpdateState.Downloading(update, 0f)
        job = viewModelScope.launch {
            runCatching { updater.download(update) { _state.value = UpdateState.Downloading(update, it) } }
                .onSuccess {
                    _state.value = UpdateState.Ready(update, it)
                    install()
                }
                .onFailure {
                    _state.value = UpdateState.Available(update)
                    _message.value = false
                }
        }
    }

    fun install() {
        (_state.value as? UpdateState.Ready)?.let { runCatching { updater.install(it.apk) } }
    }

    fun dismiss() {
        job?.cancel()
        _state.value = UpdateState.Idle
    }

    fun messageShown() {
        _message.value = null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = this[APPLICATION_KEY]!!.container
                UpdateViewModel(container.updater, container.settings.autoUpdateCheck.state.value)
            }
        }
    }
}

/** Update prompt with download progress; shown over the main screen whenever an update is found */
@Composable
fun UpdateDialog(viewModel: UpdateViewModel = viewModel(factory = UpdateViewModel.Factory)) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    LaunchedEffect(message) {
        val ok = message ?: return@LaunchedEffect
        val text = if (ok) R.string.update_latest else R.string.update_failed
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        viewModel.messageShown()
    }

    val current = state
    val update = when (current) {
        is UpdateState.Available -> current.update
        is UpdateState.Downloading -> current.update
        is UpdateState.Ready -> current.update
        else -> return
    }

    AlertDialog(
        onDismissRequest = { if (current !is UpdateState.Downloading) viewModel.dismiss() },
        title = { Text(stringResource(R.string.update_available)) },
        text = {
            Column {
                Text(stringResource(R.string.update_version, update.version, Formatter.formatShortFileSize(context, update.size)))
                if (current is UpdateState.Downloading) {
                    LinearProgressIndicator(
                        progress = { current.progress },
                        modifier = Modifier.fillMaxWidth().padding(top = Space.L),
                    )
                }
            }
        },
        confirmButton = {
            when (current) {
                is UpdateState.Available -> TextButton(onClick = viewModel::download) { Text(stringResource(R.string.update_now)) }
                is UpdateState.Ready -> TextButton(onClick = viewModel::install) { Text(stringResource(R.string.update_install)) }
                else -> {}
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismiss) {
                Text(stringResource(if (current is UpdateState.Downloading) R.string.cancel else R.string.update_later))
            }
        },
    )
}
