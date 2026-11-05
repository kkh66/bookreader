package com.bookreader.app.ui

import android.animation.ValueAnimator
import android.graphics.Color.TRANSPARENT
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bookreader.app.R
import com.bookreader.app.container
import com.bookreader.app.data.SettingsRepository
import com.bookreader.app.data.ThemeMode
import com.bookreader.app.data.opds.OpdsParser
import com.bookreader.app.ui.components.Space
import com.bookreader.app.ui.discover.DiscoverScreen
import com.bookreader.app.ui.library.LibraryEditScreen
import com.bookreader.app.ui.library.LibraryScreen
import com.bookreader.app.ui.me.MeScreen
import com.bookreader.app.ui.me.ReadingStyleScreen
import com.bookreader.app.ui.opds.OpdsScreen
import com.bookreader.app.ui.shelf.ShelfScreen
import com.bookreader.app.ui.shelf.ShelfViewModel
import com.bookreader.app.ui.stats.StatsScreen
import com.bookreader.app.ui.theme.AppIcons
import com.bookreader.app.ui.theme.BookReaderTheme

class MainViewModel(settings: SettingsRepository) : ViewModel() {
    val themeMode = settings.themeMode
    val startupAnimation = settings.startupAnimation.state

    companion object {
        val Factory = viewModelFactory {
            initializer { MainViewModel(this[APPLICATION_KEY]!!.container.settings) }
        }
    }
}

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    /** Same instance the shelf tab gets from viewModel() (same Activity, same default key) */
    private val shelfViewModel: ShelfViewModel by viewModels { ShelfViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Reopen the last book per the setting only on cold start, not on recreation such as rotation
        if (savedInstanceState == null) shelfViewModel.openLastOnLaunch()
        val motionEnabled = if (Build.VERSION.SDK_INT >= 26) ValueAnimator.areAnimatorsEnabled()
            else Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        val playStartup = savedInstanceState == null && viewModel.startupAnimation.value && motionEnabled
        splash.setOnExitAnimationListener { it.remove() }
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
            BookReaderTheme(dark) {
                Box(Modifier.fillMaxSize()) {
                    AppRoot()
                    // The splash stays while that book opens, capped so a slow book never holds the app back
                    // Leaves with the icon growing as the whole screen fades into the shelf (or the book)
                    StartupOverlay(playStartup, shelfViewModel.launching, dark)
                }
            }
        }
    }
}

private enum class Tab(val label: Int, val icon: ImageVector) {
    Shelf(R.string.shelf, AppIcons.Shelf),
    Discover(R.string.discover, AppIcons.Discover),
    Stats(R.string.stats, AppIcons.Stats),
    Me(R.string.me, AppIcons.Me),
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
    var readingStyle by rememberSaveable { mutableStateOf(false) }
    val shelfViewModel: ShelfViewModel = viewModel(factory = ShelfViewModel.Factory)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            shelfViewModel.importBook(it)
            tab = Tab.Shelf
        }
    }
    val importBook = { picker.launch(OpdsParser.IMPORT_TYPES) }
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
        readingStyle -> Box(overlay) { ReadingStyleScreen(onExit = { readingStyle = false }) }
        else -> {
            BackHandler(enabled = tab != Tab.Shelf) { tab = Tab.Shelf }
            Scaffold(
                containerColor = background,
                bottomBar = { BottomBar(tab) { tab = it } },
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    tabStates.SaveableStateProvider(tab.name) {
                    when (tab) {
                        Tab.Shelf -> ShelfScreen(onAddLibrary = { editLibrary = "" }, onImportBook = importBook)
                        Tab.Discover -> DiscoverScreen(
                            onOpenLibrary = { libraryId = it },
                            onAddLibrary = { editLibrary = "" },
                            onEditLibrary = { editLibrary = it },
                            onImportBook = importBook,
                        )
                        Tab.Stats -> StatsScreen()
                        Tab.Me -> MeScreen(
                            onAddLibrary = { editLibrary = "" },
                            onEditLibrary = { editLibrary = it },
                            onOpenStats = { tab = Tab.Stats },
                            onOpenReadingStyle = { readingStyle = true },
                        )
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
    val context = LocalContext.current
    Column(Modifier.background(MaterialTheme.colorScheme.background)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Tab.entries.forEach { tab ->
                val active = tab == selected
                val color = if (active) MaterialTheme.colorScheme.onBackground
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                Column(
                    // No press ripple: tapping only changes the selected tab's color and weight
                    Modifier.weight(1f).fillMaxHeight().clickable(interactionSource = null, indication = null) { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(tab.icon, context.getString(tab.label), Modifier.size(22.dp), tint = color)
                    Text(
                        context.getString(tab.label), fontSize = 11.sp, color = color,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(top = Space.XS),
                    )
                }
            }
        }
    }
}
