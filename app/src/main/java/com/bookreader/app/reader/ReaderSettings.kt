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
import com.bookreader.app.data.BookFormat
import com.bookreader.app.data.ReadingStyle
import com.bookreader.app.data.UserFont
import com.bookreader.app.ui.components.Gap
import com.bookreader.app.ui.components.ReadingBehaviorControls
import com.bookreader.app.ui.components.ReadingDirectionControls
import com.bookreader.app.ui.components.ReadingStyleControls
import com.bookreader.app.ui.components.Space

/** Reading settings as the panel shows them: the text style, this book's page turn direction and the behavior switches */
internal data class ReaderSettingsState(
    val style: ReadingStyle = ReadingStyle(),
    val rightToLeft: Boolean = false,
    val volumeKeyPaging: Boolean = false,
    val hideStatusBar: Boolean = false,
    val openLastOnLaunch: Boolean = false,
)

/**
 * Reading settings as a bottom panel over the page, without dimming, so text style changes can be seen as they apply
 * [fonts] are the imported fonts registered when the book opened; a PDF or comic has no text style, only its page turn direction
 */
@Composable
internal fun ReaderSettingsDialog(
    format: BookFormat,
    settings: ReaderSettingsState,
    fonts: List<UserFont>,
    onStyleChange: (ReadingStyle) -> Unit,
    onRightToLeftChange: (Boolean) -> Unit,
    onVolumeKeyPagingChange: (Boolean) -> Unit,
    onHideStatusBarChange: (Boolean) -> Unit,
    onOpenLastOnLaunchChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
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
                // Two tabs: text style (adjusted with the page visible) or page turn direction, and reading behavior switches
                TabRow(
                    selectedTabIndex = tab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    listOf(if (format == BookFormat.EPUB) "文字" else "翻页", "行为").forEachIndexed { index, label ->
                        Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label, fontSize = 15.sp) })
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.XL)) {
                    Gap(Space.S)
                    if (tab == 0 && format == BookFormat.EPUB) {
                        ReadingStyleControls(settings.style, fonts, onChange = onStyleChange)
                    } else if (tab == 0) {
                        ReadingDirectionControls(settings.rightToLeft, onChange = onRightToLeftChange)
                    } else {
                        ReadingBehaviorControls(
                            settings.volumeKeyPaging, settings.hideStatusBar, settings.openLastOnLaunch,
                            onVolumeKeyPagingChange, onHideStatusBarChange, onOpenLastOnLaunchChange,
                        )
                    }
                    Gap(Space.L)
                }
            }
        }
    }
}
