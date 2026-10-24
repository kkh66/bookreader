package com.bookreader.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import com.bookreader.app.R
import com.bookreader.app.data.PageAnimation

/** Reading behavior settings, shared by the reader's settings panel and Me → 阅读样式 so both always list the same options */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReadingBehaviorControls(
    pageAnimation: PageAnimation,
    volumeKeyPaging: Boolean,
    hideStatusBar: Boolean,
    openLastOnLaunch: Boolean,
    onPageAnimationChange: (PageAnimation) -> Unit,
    onVolumeKeyPagingChange: (Boolean) -> Unit,
    onHideStatusBarChange: (Boolean) -> Unit,
    onOpenLastOnLaunchChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    Column {
        Column(Modifier.padding(vertical = Space.S), verticalArrangement = Arrangement.spacedBy(Space.S)) {
            Text(context.getString(R.string.page_animation), fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S), verticalArrangement = Arrangement.spacedBy(Space.S)) {
                PageAnimation.entries.forEach { ChoiceChip(context.getString(it.label), selected = it == pageAnimation) { onPageAnimationChange(it) } }
            }
        }
        ToggleRow(volumeKeyPaging, context.getString(R.string.volume_key_paging), context.getString(R.string.volume_key_hint), onVolumeKeyPagingChange)
        ToggleRow(hideStatusBar, context.getString(R.string.hide_status_bar), context.getString(R.string.hide_status_bar_hint), onHideStatusBarChange)
        ToggleRow(openLastOnLaunch, context.getString(R.string.open_last_on_launch), context.getString(R.string.open_last_hint), onOpenLastOnLaunchChange)
    }
}

/** Toggle row clickable as a whole: title + description + Switch on the right */
@Composable
fun ToggleRow(on: Boolean, title: String, subtitle: String, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = on, onValueChange = onChange, role = Role.Switch)
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
