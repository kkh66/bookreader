package com.bookreader.app.ui.me

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bookreader.app.R
import com.bookreader.app.container
import com.bookreader.app.data.BackupManifest
import com.bookreader.app.data.BackupRepository
import com.bookreader.app.data.BackupSize
import com.bookreader.app.ui.components.Gap
import com.bookreader.app.ui.components.Space
import com.bookreader.app.ui.components.SurfaceCard
import com.bookreader.app.ui.components.ToggleRow
import java.io.IOException
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BackupUiState(
    val size: BackupSize? = null,
    /** Take the book and font files along */
    val withFiles: Boolean = false,
    val busy: Boolean = false,
    /** A picked backup waiting for the user to confirm the restore */
    val pending: Pair<Uri, BackupManifest>? = null,
    val message: String? = null,
)

class BackupViewModel(
    private val backup: BackupRepository,
    private val resolver: ContentResolver,
    private val context: Context,
) : ViewModel() {
    private val strings get() = ContextCompat.getContextForLanguage(context).resources

    private val _state = MutableStateFlow(BackupUiState())
    val state = _state.asStateFlow()

    /** The restore finished: the screen restarts the app so everything reloads */
    private val _restart = Channel<Unit>(Channel.BUFFERED)
    val restart = _restart.receiveAsFlow()

    /** Called each time the page opens: the shelf may have changed since */
    fun refreshSize() {
        viewModelScope.launch { _state.update { it.copy(size = runCatching { backup.size() }.getOrNull()) } }
    }

    fun setWithFiles(on: Boolean) = _state.update { it.copy(withFiles = on) }

    fun export(uri: Uri) = launchBusy(R.string.export_failed) {
        try {
            val output = withContext(Dispatchers.IO) {
                resolver.openOutputStream(uri) ?: throw IOException(strings.getString(R.string.file_save_failed))
            }
            backup.export(output, _state.value.withFiles)
        } catch (e: Exception) {
            // Don't leave a half-written backup behind
            withContext(Dispatchers.IO) { runCatching { DocumentsContract.deleteDocument(resolver, uri) } }
            throw e
        }
        _state.update { it.copy(message = strings.getString(R.string.backup_exported)) }
    }

    fun inspect(uri: Uri) = launchBusy(R.string.restore_failed) {
        val input = withContext(Dispatchers.IO) {
            resolver.openInputStream(uri) ?: throw IOException(strings.getString(R.string.file_read_failed))
        }
        val manifest = backup.inspect(input)
        _state.update { it.copy(pending = uri to manifest) }
    }

    fun cancelRestore() = _state.update { it.copy(pending = null) }

    fun restore() {
        val uri = _state.value.pending?.first ?: return
        _state.update { it.copy(pending = null) }
        launchBusy(R.string.restore_failed) {
            val input = withContext(Dispatchers.IO) {
                resolver.openInputStream(uri) ?: throw IOException(strings.getString(R.string.file_read_failed))
            }
            backup.restore(input)
            _restart.send(Unit)
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun launchBusy(failure: Int, block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = strings.getString(failure, e.message ?: e.toString())) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                BackupViewModel(app.container.backup, app.contentResolver, app)
            }
        }
    }
}

/** Me → Backup and restore: export a zip (optionally with the book files), or restore one, which replaces everything and restarts */
@Composable
internal fun BackupPage(viewModel: BackupViewModel = viewModel(factory = BackupViewModel.Factory)) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(viewModel::export)
    }
    // Any type: chat apps and cloud drives don't always label a zip as one; the manifest decides
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::inspect) }
    LaunchedEffect(Unit) { viewModel.refreshSize() }
    LaunchedEffect(Unit) { viewModel.restart.collect { context.restartApp() } }
    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessage()
        }
    }

    SurfaceCard {
        Column(Modifier.padding(Space.L)) {
            Text(context.getString(R.string.export_backup), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Gap(Space.XS)
            Text(context.getString(R.string.export_backup_hint), fontSize = 13.sp, color = secondary)
            Gap(Space.S)
            val size = state.size?.let { if (state.withFiles) it.data + it.files else it.data }
            ToggleRow(
                state.withFiles,
                context.getString(R.string.backup_with_files),
                size?.let { context.getString(R.string.backup_size, Formatter.formatShortFileSize(context, it)) }.orEmpty(),
                viewModel::setWithFiles,
            )
            Gap(Space.S)
            Button(
                onClick = { exporter.launch("BookReader-${LocalDate.now()}.zip") },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(context.getString(R.string.export)) }
        }
    }
    Gap(Space.XL)
    SurfaceCard {
        Column(Modifier.padding(Space.L)) {
            Text(context.getString(R.string.restore_backup), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Gap(Space.XS)
            Text(context.getString(R.string.restore_backup_hint), fontSize = 13.sp, color = secondary)
            Gap(Space.M)
            OutlinedButton(
                onClick = { picker.launch(arrayOf("*/*")) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(context.getString(R.string.choose_backup_file)) }
        }
    }
    if (state.busy) {
        Gap(Space.L)
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }

    state.pending?.let { (_, manifest) ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text(context.getString(R.string.restore_confirm_title)) },
            text = {
                Text(
                    context.getString(
                        R.string.restore_confirm_message,
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(manifest.createdAt)),
                        manifest.bookCount,
                        context.getString(if (manifest.withFiles) R.string.backup_has_files else R.string.backup_no_files),
                    ),
                )
            },
            confirmButton = { TextButton(onClick = viewModel::restore) { Text(context.getString(R.string.restore)) } },
            dismissButton = { TextButton(onClick = viewModel::cancelRestore) { Text(context.getString(R.string.cancel)) } },
        )
    }
}

/** Starts the app afresh in a new process, so every repository loads the restored data */
private fun Context.restartApp() {
    val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return
    startActivity(Intent.makeRestartActivityTask(launch.component))
    Runtime.getRuntime().exit(0)
}
