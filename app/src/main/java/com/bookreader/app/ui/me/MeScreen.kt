package com.bookreader.app.ui.me

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bookreader.app.BuildConfig
import com.bookreader.app.R
import com.bookreader.app.data.ThemeMode
import com.bookreader.app.ui.components.BackHeader
import com.bookreader.app.ui.components.Gap
import com.bookreader.app.ui.components.PageHeader
import com.bookreader.app.ui.components.PagePadding
import com.bookreader.app.ui.components.SettingRow
import com.bookreader.app.ui.components.Space
import com.bookreader.app.ui.components.SurfaceCard
import com.bookreader.app.ui.components.ToggleRow
import com.bookreader.app.ui.theme.AppIcons

/**
 * "Me" tab: just the reading, libraries and app entries plus the version; each entry opens its own page of options
 * within the tab (back returns to the list), so the tab itself stays short
 */
@Composable
fun MeScreen(
    onAddLibrary: () -> Unit,
    onEditLibrary: (id: String) -> Unit,
    onOpenStats: () -> Unit,
    onOpenReadingStyle: () -> Unit,
    viewModel: MeViewModel = viewModel(factory = MeViewModel.Factory),
) {
    val context = LocalContext.current
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val startupAnimation by viewModel.startupAnimation.collectAsStateWithLifecycle()
    val libraries by viewModel.libraries.collectAsStateWithLifecycle()
    var choosingTheme by remember { mutableStateOf(false) }
    var choosingLanguage by remember { mutableStateOf(false) }
    val language = stringResource(R.string.language_code)
    var page by rememberSaveable { mutableStateOf(MePage.Home) }
    BackHandler(enabled = page != MePage.Home) { page = MePage.Home }

    // Each page starts at its top
    key(page) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            when (page) {
                MePage.Home -> {
                    PageHeader(context.getString(R.string.me))
                    Column(Modifier.padding(horizontal = PagePadding, vertical = Space.L)) {
                        SurfaceCard {
                            SettingRow(AppIcons.Shelf, context.getString(R.string.reading), onClick = { page = MePage.Reading })
                            SettingRow(
                                AppIcons.Server, context.getString(R.string.library),
                                value = context.getString(R.string.item_count, libraries.size),
                                onClick = { page = MePage.Libraries },
                            )
                            SettingRow(AppIcons.Settings, stringResource(R.string.application), onClick = { page = MePage.App }, showDivider = false)
                        }
                        Gap(Space.XL)
                        SurfaceCard {
                            SettingRow(AppIcons.Info, context.getString(R.string.version), onClick = null, value = BuildConfig.VERSION_NAME, showDivider = false)
                        }
                    }
                }
                MePage.Reading -> SubPage(context.getString(R.string.reading), onBack = { page = MePage.Home }) {
                    SurfaceCard {
                        SettingRow(AppIcons.Contrast, context.getString(R.string.appearance), value = context.getString(themeMode.label), onClick = { choosingTheme = true })
                        SettingRow(AppIcons.FontSize, context.getString(R.string.reading_style), onClick = onOpenReadingStyle)
                        SettingRow(AppIcons.History, context.getString(R.string.reading_history), onClick = onOpenStats, showDivider = false)
                    }
                }
                // Libraries are added and edited here in place, in the same sheet as on the Discover tab
                MePage.Libraries -> SubPage(context.getString(R.string.library), onBack = { page = MePage.Home }) {
                    SurfaceCard {
                        libraries.forEach { library ->
                            SettingRow(AppIcons.Server, library.name, subtitle = library.host, onClick = { onEditLibrary(library.id) })
                        }
                        SettingRow(AppIcons.Add, context.getString(R.string.add_library), onClick = onAddLibrary, showDivider = false)
                    }
                }
                MePage.App -> SubPage(stringResource(R.string.application), onBack = { page = MePage.Home }) {
                    SurfaceCard {
                        Column(Modifier.padding(horizontal = Space.L)) {
                            ToggleRow(
                                startupAnimation, stringResource(R.string.startup_animation),
                                stringResource(R.string.startup_animation_hint), viewModel::setStartupAnimation,
                            )
                        }
                        SettingRow(
                            AppIcons.Settings, stringResource(R.string.language),
                            value = if (language == "zh") "简体中文" else "English",
                            onClick = { choosingLanguage = true }, showDivider = false,
                        )
                    }
                }
            }
        }
    }

    if (choosingTheme) {
        ThemeDialog(themeMode, onDismiss = { choosingTheme = false }) {
            viewModel.setThemeMode(it)
            choosingTheme = false
        }
    }
    if (choosingLanguage) {
        AlertDialog(
            onDismissRequest = { choosingLanguage = false },
            title = { Text(stringResource(R.string.language)) },
            text = {
                Column {
                    listOf("zh-CN" to "简体中文", "en" to "English").forEach { (tag, label) ->
                        val select = {
                            choosingLanguage = false
                            viewModel.setLanguage(tag)
                        }
                        Row(
                            Modifier.fillMaxWidth().clip(CircleShape).clickable(onClick = select).padding(vertical = Space.XS),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = language == tag.substringBefore('-'), onClick = select)
                            Text(label, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingLanguage = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ThemeDialog(current: ThemeMode, onDismiss: () -> Unit, onSelect: (ThemeMode) -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.appearance)) },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        Modifier.fillMaxWidth().clip(CircleShape).clickable { onSelect(mode) }.padding(vertical = Space.XS),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == current, onClick = { onSelect(mode) })
                        Text(context.getString(mode.label), fontSize = 16.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(context.getString(R.string.cancel)) } },
    )
}

private enum class MePage { Home, Reading, Libraries, App }

/** One of the Me tab's pages of options, under a header whose back arrow returns to the list */
@Composable
private fun SubPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHeader(title, onBack = onBack)
    Column(Modifier.padding(horizontal = PagePadding, vertical = Space.L), content = content)
}
