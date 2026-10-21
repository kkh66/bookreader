package com.bookreader.app.ui

import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bookreader.app.container
import com.bookreader.app.data.SettingsRepository
import com.bookreader.app.data.ThemeMode
import com.bookreader.app.ui.discover.DiscoverScreen
import com.bookreader.app.ui.me.MeScreen
import com.bookreader.app.ui.opds.OpdsScreen
import com.bookreader.app.ui.settings.SettingsScreen
import com.bookreader.app.ui.shelf.ShelfScreen
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

/** Four bottom tabs plus secondary screens stacked on top (library browsing, server settings), without a navigation library */
@Composable
private fun AppRoot() {
    var tab by rememberSaveable { mutableStateOf(Tab.Shelf) }
    var browseUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var browseTitle by rememberSaveable { mutableStateOf("") }
    var showServer by rememberSaveable { mutableStateOf(false) }
    val background = MaterialTheme.colorScheme.background

    val url = browseUrl
    when {
        showServer -> Box(Modifier.fillMaxSize().background(background).systemBarsPadding()) {
            SettingsScreen(onDone = { showServer = false })
        }
        url != null -> Box(Modifier.fillMaxSize().background(background).systemBarsPadding()) {
            OpdsScreen(startUrl = url, startTitle = browseTitle, onExit = { browseUrl = null })
        }
        else -> {
            BackHandler(enabled = tab != Tab.Shelf) { tab = Tab.Shelf }
            Scaffold(
                containerColor = background,
                bottomBar = { BottomBar(tab) { tab = it } },
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    when (tab) {
                        Tab.Shelf -> ShelfScreen(onOpenDiscover = { tab = Tab.Discover })
                        Tab.Discover -> DiscoverScreen(
                            onBrowse = { u, t ->
                                browseTitle = t
                                browseUrl = u
                            },
                            onOpenServer = { showServer = true },
                        )
                        Tab.Stats -> StatsScreen()
                        Tab.Me -> MeScreen(onOpenServer = { showServer = true }, onOpenStats = { tab = Tab.Stats })
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
            Tab.entries.forEach { tab ->
                NavigationBarItem(
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                    icon = { Icon(tab.icon, tab.label) },
                    label = { Text(tab.label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onBackground,
                        selectedTextColor = MaterialTheme.colorScheme.onBackground,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = Color.Transparent,
                    ),
                )
            }
        }
    }
}
