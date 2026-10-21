package com.bookreader.app.ui.me

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bookreader.app.BuildConfig
import com.bookreader.app.data.ThemeMode
import com.bookreader.app.ui.components.Gap
import com.bookreader.app.ui.components.PageHeader
import com.bookreader.app.ui.components.PagePadding
import com.bookreader.app.ui.components.Space
import com.bookreader.app.ui.components.SectionTitle
import com.bookreader.app.ui.components.SettingRow
import com.bookreader.app.ui.components.SurfaceCard
import com.bookreader.app.ui.theme.AppIcons

@Composable
fun MeScreen(
    onOpenLibraries: () -> Unit,
    onOpenStats: () -> Unit,
    viewModel: MeViewModel = viewModel(factory = MeViewModel.Factory),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val libraries by viewModel.libraries.collectAsStateWithLifecycle()
    var choosingTheme by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("我的")
        Column(Modifier.padding(horizontal = PagePadding, vertical = Space.L)) {
            SectionTitle("阅读", Modifier.padding(start = Space.XS, bottom = Space.M))
            SurfaceCard {
                SettingRow(AppIcons.Contrast, "外观", value = themeMode.label, onClick = { choosingTheme = true })
                SettingRow(AppIcons.History, "阅读记录", onClick = onOpenStats, showDivider = false)
            }
            Gap(Space.XL)
            SectionTitle("书库", Modifier.padding(start = Space.XS, bottom = Space.M))
            SurfaceCard {
                SettingRow(
                    AppIcons.Server, "网上书库",
                    value = "${libraries.size} 个",
                    onClick = onOpenLibraries,
                    showDivider = false,
                )
            }
            Gap(Space.XL)
            SectionTitle("关于", Modifier.padding(start = Space.XS, bottom = Space.M))
            SurfaceCard {
                SettingRow(AppIcons.Library, "版本", value = BuildConfig.VERSION_NAME, onClick = {}, showDivider = false)
            }
            Gap(Space.XL)
        }
    }

    if (choosingTheme) {
        ThemeDialog(themeMode, onDismiss = { choosingTheme = false }) {
            viewModel.setThemeMode(it)
            choosingTheme = false
        }
    }
}

@Composable
private fun ThemeDialog(current: ThemeMode, onDismiss: () -> Unit, onSelect: (ThemeMode) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("外观") },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        Modifier.fillMaxWidth().clip(CircleShape).clickable { onSelect(mode) }.padding(vertical = Space.XS),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == current, onClick = { onSelect(mode) })
                        Text(mode.label, fontSize = 16.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
