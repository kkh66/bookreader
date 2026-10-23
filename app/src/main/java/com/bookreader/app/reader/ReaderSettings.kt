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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookreader.app.data.UserFont
import com.bookreader.app.ui.components.Gap
import com.bookreader.app.ui.components.ReadingBehaviorControls
import com.bookreader.app.ui.components.ReadingStyleControls
import com.bookreader.app.ui.components.Space

/**
 * Reading settings as a bottom panel over the page, without dimming, so text style changes can be seen as they apply
 * [fonts] are the imported fonts registered when the book opened
 */
@Composable
internal fun ReaderSettingsDialog(viewModel: ReaderViewModel, fonts: List<UserFont>, onDismiss: () -> Unit) {
    val style by viewModel.readingStyle.collectAsStateWithLifecycle()
    val volumeKeyPaging by viewModel.volumeKeyPaging.collectAsStateWithLifecycle()
    val hideStatusBar by viewModel.hideStatusBar.collectAsStateWithLifecycle()
    val openLastOnLaunch by viewModel.openLastOnLaunch.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
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
                // Two tabs: text style (adjusted with the page visible) and reading behavior switches
                TabRow(
                    selectedTabIndex = tab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    listOf("文字", "行为").forEachIndexed { index, label ->
                        Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label, fontSize = 15.sp) })
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.XL)) {
                    Gap(Space.S)
                    if (tab == 0) {
                        ReadingStyleControls(style, fonts, onChange = viewModel::setReadingStyle)
                    } else {
                        ReadingBehaviorControls(
                            volumeKeyPaging, hideStatusBar, openLastOnLaunch,
                            viewModel::setVolumeKeyPaging, viewModel::setHideStatusBar, viewModel::setOpenLastOnLaunch,
                        )
                    }
                    Gap(Space.L)
                }
            }
        }
    }
}
