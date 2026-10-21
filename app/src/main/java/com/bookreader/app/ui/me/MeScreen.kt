package com.bookreader.app.ui.me

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bookreader.app.BuildConfig
import com.bookreader.app.data.ThemeMode
import com.bookreader.app.ui.components.Gap
import com.bookreader.app.ui.components.PageHeader
import com.bookreader.app.ui.components.PagePadding
import com.bookreader.app.ui.components.SectionTitle
import com.bookreader.app.ui.components.SettingRow
import com.bookreader.app.ui.components.SurfaceCard
import com.bookreader.app.ui.theme.AppIcons
import kotlinx.coroutines.launch
import java.net.URI

@Composable
fun MeScreen(
    onOpenServer: () -> Unit,
    onOpenStats: () -> Unit,
    viewModel: MeViewModel = viewModel(factory = MeViewModel.Factory),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val server by viewModel.server.collectAsStateWithLifecycle()
    var choosingTheme by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val comingSoon = { name: String -> scope.launch { snackbar.showSnackbar("$name 稍后支持") }; Unit }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            PageHeader("我的", "按照你的方式阅读")
            Column(Modifier.padding(horizontal = PagePadding, vertical = 16.dp)) {
                AppCard()
                Gap(28)
                SectionTitle("阅读", Modifier.padding(start = 4.dp, bottom = 12.dp))
                SurfaceCard {
                    SettingRow(AppIcons.FontSize, "阅读设置", onClick = { comingSoon("阅读设置") })
                    SettingRow(AppIcons.Contrast, "外观", value = themeMode.label, onClick = { choosingTheme = true })
                    SettingRow(AppIcons.Bookmark, "书签", onClick = { comingSoon("书签") })
                    SettingRow(AppIcons.History, "阅读记录", onClick = onOpenStats, showDivider = false)
                }
                Gap(28)
                SectionTitle("书库", Modifier.padding(start = 4.dp, bottom = 12.dp))
                SurfaceCard {
                    SettingRow(
                        AppIcons.Server, "Calibre 服务器",
                        value = runCatching { URI(server.url).host }.getOrNull() ?: "未设置",
                        onClick = onOpenServer,
                        showDivider = false,
                    )
                }
                Gap(28)
                SectionTitle("关于", Modifier.padding(start = 4.dp, bottom = 12.dp))
                SurfaceCard {
                    SettingRow(AppIcons.Library, "版本", value = BuildConfig.VERSION_NAME, onClick = {}, showDivider = false)
                }
                Gap(24)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (choosingTheme) {
        ThemeDialog(themeMode, onDismiss = { choosingTheme = false }) {
            viewModel.setThemeMode(it)
            choosingTheme = false
        }
    }
}

@Composable
private fun AppCard() {
    SurfaceCard {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Shelf, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
            Column(Modifier.weight(1f).padding(horizontal = 18.dp)) {
                Text("BookReader", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Gap(4)
                Text("Calibre 书库，自由阅读", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
                        Modifier.fillMaxWidth().clip(CircleShape).clickable { onSelect(mode) }.padding(vertical = 4.dp),
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
