package com.bookreader.app.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookreader.app.data.SettingsRepository
import com.bookreader.app.data.UserFont
import com.bookreader.app.ui.components.Gap
import com.bookreader.app.ui.components.ReadingStyleControls
import com.bookreader.app.ui.components.Space

/**
 * Reading settings as a bottom panel over the page, without dimming, so text style changes can be seen as they apply
 * [fonts] are the imported fonts registered when the book opened
 */
@Composable
internal fun ReaderSettingsDialog(settings: SettingsRepository, fonts: List<UserFont>, onDismiss: () -> Unit) {
    val style by settings.readingStyle.collectAsStateWithLifecycle()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }
        Box(
            Modifier.fillMaxSize().clickable(interactionSource = null, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().fillMaxHeight(0.6f)
                    // A shade off the page color so the panel's edge shows against the text
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    // The panel takes its own taps so they don't close it
                    .clickable(interactionSource = null, indication = null) {}
                    .navigationBarsPadding(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = Space.XL, end = Space.S, top = Space.S),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("阅读设置", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("完成") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.XL)) {
                    ReadingStyleControls(style, fonts, onChange = settings::setReadingStyle)
                    HorizontalDivider(Modifier.padding(vertical = Space.S), color = MaterialTheme.colorScheme.outlineVariant)
                    ToggleRow(settings.volumeKeyPaging, "音量键翻页", "音量下 = 下一页，音量上 = 上一页")
                    ToggleRow(settings.openLastOnLaunch, "启动时打开上次阅读", "打开应用直接回到上次读的书")
                    ToggleRow(settings.readerHideStatusBar, "阅读时隐藏状态栏", "点屏幕中间呼出菜单时再显示")
                    Gap(Space.L)
                }
            }
        }
    }
}

/** Toggle row clickable as a whole: title + description + Switch on the right */
@Composable
private fun ToggleRow(toggle: SettingsRepository.Toggle, title: String, subtitle: String) {
    val on by toggle.state.collectAsStateWithLifecycle()
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = on, onValueChange = toggle::set, role = Role.Switch)
            .padding(vertical = Space.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = Space.M)) {
            Text(title, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // The row handles clicks; the Switch is display only
        Switch(checked = on, onCheckedChange = null)
    }
}
