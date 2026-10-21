package com.bookreader.app.ui

import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bookreader.app.container
import com.bookreader.app.data.SettingsRepository
import com.bookreader.app.data.ThemeMode
import com.bookreader.app.data.opds.OpdsParser
import com.bookreader.app.ui.components.Space
import com.bookreader.app.ui.discover.DiscoverScreen
import com.bookreader.app.ui.me.MeScreen
import com.bookreader.app.ui.opds.OpdsScreen
import com.bookreader.app.ui.library.LibraryEditScreen
import com.bookreader.app.ui.library.LibraryScreen
import com.bookreader.app.ui.shelf.ShelfScreen
import com.bookreader.app.ui.shelf.ShelfViewModel
import com.bookreader.app.ui.stats.StatsScreen
import com.bookreader.app.ui.theme.AppIcons
import com.bookreader.app.ui.theme.BookReaderTheme

class MainViewModel(settings: SettingsRepository) : ViewModel() {
    val themeMode = settings.themeMode

    companion object {
        val Factory = viewModelFactory {
            initializer { MainViewModel(this[APPLICATION_KEY]!!.container.settings) }
        }
    }
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val mode by viewModel.themeMode.collectAsStateWithLifecycle()
            val dark = when (mode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            // Status bar icon color follows the app theme rather than the system theme
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(TRANSPARENT) else SystemBarStyle.light(TRANSPARENT, TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            BookReaderTheme(dark) { AppRoot() }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Shelf("书架", AppIcons.Shelf),
    Discover("发现", AppIcons.Discover),
    Stats("统计", AppIcons.Stats),
    Me("我的", AppIcons.Me),
}

/**
 * Four bottom tabs plus secondary screens stacked on top, without a navigation library:
 * Discover → library home (libraryId) → folder browsing (browseUrl); add/edit library (editLibrary, "" means new)
 */
@Composable
private fun AppRoot() {
    var tab by rememberSaveable { mutableStateOf(Tab.Shelf) }
    var libraryId by rememberSaveable { mutableStateOf<String?>(null) }
    var browseUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var browseTitle by rememberSaveable { mutableStateOf("") }
    var editLibrary by rememberSaveable { mutableStateOf<String?>(null) }
    var libraryRevision by rememberSaveable { mutableStateOf(0) }
    val shelfViewModel: ShelfViewModel = viewModel(factory = ShelfViewModel.Factory)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            shelfViewModel.importEpub(it)
            tab = Tab.Shelf
        }
    }
    val importEpub = { picker.launch(arrayOf(OpdsParser.EPUB_TYPE)) }
    val background = MaterialTheme.colorScheme.background
    val tabStates = rememberSaveableStateHolder()
    val overlay = Modifier.fillMaxSize().background(background).systemBarsPadding()

    val openLibrary = libraryId
    val url = browseUrl
    val editing = editLibrary
    when {
        openLibrary != null && url != null -> Box(overlay) {
            OpdsScreen(libraryId = openLibrary, startUrl = url, startTitle = browseTitle, onExit = { browseUrl = null })
        }
        openLibrary != null -> Box(overlay) {
            LibraryScreen(
                libraryId = openLibrary,
                onBrowse = { u, t ->
                    browseTitle = t
                    browseUrl = u
                },
                onEdit = { editLibrary = openLibrary },
                onExit = { libraryId = null },
                revision = libraryRevision,
            )
        }
        else -> {
            BackHandler(enabled = tab != Tab.Shelf) { tab = Tab.Shelf }
            Scaffold(
                containerColor = background,
                bottomBar = { BottomBar(tab) { tab = it } },
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    tabStates.SaveableStateProvider(tab.name) {
                    when (tab) {
                        Tab.Shelf -> ShelfScreen(onOpenDiscover = { tab = Tab.Discover }, onImportEpub = importEpub)
                        Tab.Discover -> DiscoverScreen(
                            onOpenLibrary = { libraryId = it },
                            onAddLibrary = { editLibrary = "" },
                            onEditLibrary = { editLibrary = it },
                            onImportEpub = importEpub,
                        )
                        Tab.Stats -> StatsScreen()
                        Tab.Me -> MeScreen(onOpenLibraries = { tab = Tab.Discover }, onOpenStats = { tab = Tab.Stats })
                    }
                    }
                }
            }
        }
    }

    if (editing != null) {
        LibraryEditScreen(
            libraryId = editing.ifEmpty { null },
            onDone = { editLibrary = null },
            onSaved = {
                libraryRevision++
                editLibrary = null
            },
        )
    }
}

/** Compact bottom bar: icon + small label, 56dp tall (excluding the system navigation bar) */
@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.background(MaterialTheme.colorScheme.background)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Tab.entries.forEach { tab ->
                val active = tab == selected
                val color = if (active) MaterialTheme.colorScheme.onBackground
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(tab.icon, tab.label, Modifier.size(22.dp), tint = color)
                    Text(
                        tab.label, fontSize = 11.sp, color = color,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(top = Space.XS),
                    )
                }
            }
        }
    }
}
