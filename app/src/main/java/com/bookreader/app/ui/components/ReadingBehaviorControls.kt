package com.bookreader.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookreader.app.data.SettingsRepository

/** Reading behavior switches, shared by the reader's settings panel and Me → 阅读样式 so both always list the same options */
@Composable
fun ReadingBehaviorControls(settings: SettingsRepository) {
    Column {
        ToggleRow(settings.volumeKeyPaging, "音量键翻页", "音量下 = 下一页，音量上 = 上一页")
        ToggleRow(settings.readerHideStatusBar, "阅读时隐藏状态栏", "点屏幕中间呼出菜单时再显示")
        ToggleRow(settings.openLastOnLaunch, "启动时打开上次阅读", "打开应用直接回到上次读的书")
    }
}

/** Toggle row clickable as a whole: title + description + Switch on the right */
@Composable
fun ToggleRow(toggle: SettingsRepository.Toggle, title: String, subtitle: String) {
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
