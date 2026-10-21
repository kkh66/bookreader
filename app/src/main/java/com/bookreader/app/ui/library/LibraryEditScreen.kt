package com.bookreader.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bookreader.app.ui.components.CircleIconButton
import com.bookreader.app.ui.components.Gap
import com.bookreader.app.ui.components.Space
import com.bookreader.app.ui.theme.AppIcons

/**
 * Add / edit a library. A null [libraryId] means a new one
 */
@Composable
fun LibraryEditScreen(
    libraryId: String?,
    onDone: () -> Unit,
    onSaved: () -> Unit = onDone,
    viewModel: LibraryEditViewModel = viewModel(factory = LibraryEditViewModel.Factory),
) {
    val saved = viewModel.load(libraryId)
    var name by rememberSaveable { mutableStateOf(saved?.name.orEmpty()) }
    var url by rememberSaveable { mutableStateOf(saved?.url.orEmpty()) }
    var username by rememberSaveable { mutableStateOf(saved?.username.orEmpty()) }
    var password by rememberSaveable { mutableStateOf(saved?.password.orEmpty()) }
    val fieldShape = RoundedCornerShape(16.dp)
    val validUrl = url.trim().startsWith("http://") || url.trim().startsWith("https://")

    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(0.92f).heightIn(max = maxHeight),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(Space.XL)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (saved == null) "添加书库" else "编辑书库",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    CircleIconButton(AppIcons.Close, "关闭", onDone)
                }
                Gap(Space.L)
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Space.M),
                ) {
                    Text(
                        "填写 Calibre / Calibre-Web 等的 OPDS 地址，账号只发送给这个书库的服务器",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("名称（可留空）") },
                        singleLine = true,
                        shape = fieldShape,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("OPDS 地址") },
                        placeholder = { Text("http://192.168.1.100:8083/opds") },
                        singleLine = true,
                        isError = url.isNotBlank() && !validUrl,
                        shape = fieldShape,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("用户名") },
                        singleLine = true,
                        shape = fieldShape,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("密码") },
                        singleLine = true,
                        shape = fieldShape,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Gap(Space.XL)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.M)) {
                    OutlinedButton(
                        onClick = onDone,
                        shape = CircleShape,
                        contentPadding = PaddingValues(vertical = 14.dp),
                        modifier = Modifier.weight(1f),
                    ) { Text("取消", fontSize = 16.sp) }
                    Button(
                        onClick = { viewModel.save(libraryId, name, url, username, password, onSaved) },
                        enabled = validUrl,
                        shape = CircleShape,
                        contentPadding = PaddingValues(vertical = 14.dp),
                        modifier = Modifier.weight(1f),
                    ) { Text("保存", fontSize = 16.sp) }
                }
            }
        }
    }
}
