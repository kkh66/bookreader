package com.bookreader.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.bookreader.app.ui.opds.OpdsScreen
import com.bookreader.app.ui.settings.SettingsScreen
import com.bookreader.app.ui.shelf.ShelfScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                AppRoot()
            }
        }
    }
}

private enum class Screen { Shelf, Opds, Settings }

/** Only three screens, so plain state switching instead of a navigation library */
@Composable
private fun AppRoot() {
    var screen by rememberSaveable { mutableStateOf(Screen.Shelf) }
    when (screen) {
        Screen.Shelf -> ShelfScreen(
            onOpenOpds = { screen = Screen.Opds },
            onOpenSettings = { screen = Screen.Settings },
        )
        Screen.Opds -> OpdsScreen(onExit = { screen = Screen.Shelf })
        Screen.Settings -> SettingsScreen(onDone = { screen = Screen.Shelf })
    }
}
