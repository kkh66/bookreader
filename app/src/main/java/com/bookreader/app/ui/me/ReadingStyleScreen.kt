package com.bookreader.app.ui.me

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.bookreader.app.data.FontRepository
import com.bookreader.app.data.PageAnimation
import com.bookreader.app.data.ReadingStyle
import com.bookreader.app.data.SettingsRepository
import com.bookreader.app.data.UserFont
import com.bookreader.app.data.displayName
import com.bookreader.app.ui.components.BackHeader
import com.bookreader.app.ui.components.Gap
import com.bookreader.app.ui.components.PagePadding
import com.bookreader.app.ui.components.ReadingBehaviorControls
import com.bookreader.app.ui.components.ReadingStyleControls
import com.bookreader.app.ui.components.SectionTitle
import com.bookreader.app.ui.components.Space
import com.bookreader.app.ui.components.SurfaceCard
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Me → 阅读样式: default text style for every book, plus importing and removing fonts
 * The reader's settings panel edits the same style with the page visible
 */
@Composable
fun ReadingStyleScreen(
    onExit: () -> Unit,
    viewModel: ReadingStyleViewModel = viewModel(factory = ReadingStyleViewModel.Factory),
) {
    val context = LocalContext.current
    val style by viewModel.style.collectAsStateWithLifecycle()
    val fonts by viewModel.fonts.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val pageAnimation by viewModel.pageAnimation.collectAsStateWithLifecycle()
    val volumeKeyPaging by viewModel.volumeKeyPaging.collectAsStateWithLifecycle()
    val hideStatusBar by viewModel.hideStatusBar.collectAsStateWithLifecycle()
    val openLastOnLaunch by viewModel.openLastOnLaunch.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<UserFont?>(null) }
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    // Font files rarely carry a reliable MIME type, so accept anything and check the file itself on import
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::import)
    }

    BackHandler(onBack = onExit)
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            BackHeader(context.getString(R.string.reading_style), onBack = onExit)
            Column(Modifier.padding(horizontal = PagePadding, vertical = Space.M)) {
                SectionTitle(context.getString(R.string.typography), Modifier.padding(start = Space.XS, bottom = Space.M))
                SurfaceCard {
                    Column(Modifier.padding(Space.L)) {
                        ReadingStyleControls(style, fonts, onChange = viewModel::setStyle)
                    }
                }
                Text(
                    context.getString(R.string.typography_hint),
                    fontSize = 13.sp, color = secondary,
                    modifier = Modifier.padding(start = Space.XS, top = Space.S),
                )
                Gap(Space.XL)
                SectionTitle(context.getString(R.string.reading_behavior), Modifier.padding(start = Space.XS, bottom = Space.M))
                SurfaceCard {
                    Column(Modifier.padding(horizontal = Space.L, vertical = Space.XS)) {
                        ReadingBehaviorControls(
                            pageAnimation, volumeKeyPaging, hideStatusBar, openLastOnLaunch,
                            viewModel::setPageAnimation, viewModel::setVolumeKeyPaging, viewModel::setHideStatusBar,
                            viewModel::setOpenLastOnLaunch,
                        )
                    }
                }
                Gap(Space.XL)
                SectionTitle(
                    context.getString(R.string.my_fonts), Modifier.padding(start = Space.XS, bottom = Space.M),
                    trailing = if (fonts.isEmpty()) null else context.getString(R.string.item_count, fonts.size),
                )
                SurfaceCard {
                    if (fonts.isEmpty()) {
                        Text(context.getString(R.string.no_fonts), fontSize = 15.sp, color = secondary, modifier = Modifier.padding(Space.L))
                    }
                    fonts.forEachIndexed { index, font ->
                        Row(
                            Modifier.fillMaxWidth().padding(start = Space.L, end = Space.XS),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                font.name, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { pendingDelete = font }) { Text(context.getString(R.string.delete)) }
                        }
                        if (index != fonts.lastIndex) {
                            HorizontalDivider(Modifier.padding(horizontal = Space.L), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
                Gap(Space.M)
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    enabled = !importing,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (importing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(context.getString(R.string.import_font))
                    }
                }
                Text(
                    context.getString(R.string.font_import_hint),
                    fontSize = 13.sp, color = secondary,
                    modifier = Modifier.padding(start = Space.XS, top = Space.S),
                )
                Gap(Space.XL)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    pendingDelete?.let { font ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(context.getString(R.string.delete_font_confirm, font.name)) },
            text = { Text(context.getString(R.string.delete_font_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(font)
                    pendingDelete = null
                }) { Text(context.getString(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(context.getString(R.string.cancel)) } },
        )
    }
}

class ReadingStyleViewModel(
    private val settings: SettingsRepository,
    private val fontRepository: FontRepository,
    private val resolver: ContentResolver,
    private val context: Context,
) : ViewModel() {
    private val strings get() = ContextCompat.getContextForLanguage(context).resources


    val style = settings.readingStyle
    val fonts = fontRepository.fonts
    val pageAnimation = settings.pageAnimation
    val volumeKeyPaging = settings.volumeKeyPaging.state
    val hideStatusBar = settings.readerHideStatusBar.state
    val openLastOnLaunch = settings.openLastOnLaunch.state

    private val _importing = MutableStateFlow(false)
    val importing = _importing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun setStyle(style: ReadingStyle) = settings.setReadingStyle(style)

    fun setPageAnimation(animation: PageAnimation) = settings.setPageAnimation(animation)

    fun setVolumeKeyPaging(on: Boolean) = settings.volumeKeyPaging.set(on)

    fun setHideStatusBar(on: Boolean) = settings.readerHideStatusBar.set(on)

    fun setOpenLastOnLaunch(on: Boolean) = settings.openLastOnLaunch.set(on)

    fun import(uri: Uri) {
        viewModelScope.launch {
            _importing.value = true
            try {
                val input = resolver.openInputStream(uri) ?: throw IOException(strings.getString(R.string.file_read_failed))
                val font = fontRepository.import(input, displayName(uri))
                _message.value = strings.getString(R.string.font_imported, font.name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = e.message ?: e.toString()
            } finally {
                _importing.value = false
            }
        }
    }

    fun remove(font: UserFont) {
        viewModelScope.launch {
            try {
                fontRepository.remove(font)
                // A deleted font can't be shown any more; fall back to the book's own fonts
                val style = settings.readingStyle.value
                if (style.fontFamily == font.family) settings.setReadingStyle(style.copy(fontFamily = null))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = strings.getString(R.string.font_delete_failed, e.message.orEmpty())
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    /** File name without extension, e.g. 方正书宋_GBK.ttf → 方正书宋_GBK */
    private fun displayName(uri: Uri): String =
        (resolver.displayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/')).orEmpty().substringBeforeLast('.')

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                ReadingStyleViewModel(app.container.settings, app.container.fonts, app.contentResolver, app)
            }
        }
    }
}
