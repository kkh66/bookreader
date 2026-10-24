package com.bookreader.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookreader.app.ui.components.Gap
import com.bookreader.app.ui.components.Space
import com.bookreader.app.ui.theme.AppIcons

/**
 * Add / edit a library. A null [libraryId] means a new one
 * The address is split into server address + protocol (dropdown) + port + path fields, with a preview of the full URL below the path
 */
@Composable
fun LibraryEditScreen(
    libraryId: String?,
    onDone: () -> Unit,
    onSaved: () -> Unit = onDone,
    viewModel: LibraryEditViewModel = viewModel(factory = LibraryEditViewModel.Factory),
) {
    val error by viewModel.error.collectAsStateWithLifecycle()
    LaunchedEffect(libraryId) { viewModel.clearError() }
    val saved = viewModel.load(libraryId)
    // An old address that can't be split goes into the address field as is, and the hint guides the fix
    val initial = saved?.url?.let { OpdsAddress.parse(it) ?: OpdsAddress(host = it) } ?: OpdsAddress()
    var name by rememberSaveable { mutableStateOf(saved?.name.orEmpty()) }
    var https by rememberSaveable { mutableStateOf(initial.https) }
    var host by rememberSaveable { mutableStateOf(initial.host) }
    var port by rememberSaveable { mutableStateOf(initial.port) }
    var path by rememberSaveable { mutableStateOf(initial.path) }
    var username by rememberSaveable { mutableStateOf(saved?.username.orEmpty()) }
    var password by rememberSaveable { mutableStateOf(saved?.password.orEmpty()) }
    var protocolMenu by remember { mutableStateOf(false) }
    val address = OpdsAddress(https, host, port, path)
    val fieldShape = RoundedCornerShape(16.dp)

    /** Address field input: a pasted full URL (or several characters at once, like host:port) is split into the fields; normal typing only changes the address */
    fun changeHost(text: String) {
        val pasted = "://" in text || (text.length - host.length > 1 && (':' in text || '/' in text))
        val split = if (pasted) OpdsAddress.fromPaste(text, https) else null
        if (split == null) {
            host = text
        } else {
            https = split.https
            host = split.host
            port = split.port
            path = split.path
        }
    }

    /** When switching protocol, a port equal to the old protocol's default switches to the new protocol's default */
    fun changeProtocol(secure: Boolean) {
        if (port == defaultPort(https)) port = defaultPort(secure)
        https = secure
    }

    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(0.92f).heightIn(max = maxHeight),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(Space.XL)) {
                Text(if (saved == null) "添加书库" else "编辑书库", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Gap(Space.L)
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Space.M),
                ) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = ::changeHost,
                        label = { Text("服务器地址") },
                        placeholder = { Text("192.168.1.100 或域名") },
                        singleLine = true,
                        isError = address.hostError != null,
                        supportingText = address.hostError?.let { error -> @Composable { Text(error) } },
                        shape = fieldShape,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.M)) {
                        // Read-only text field with a click layer on top, so tapping anywhere opens the protocol menu
                        Box(Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = if (https) "HTTPS" else "HTTP",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("协议") },
                                trailingIcon = { Icon(AppIcons.ArrowDropDown, null) },
                                singleLine = true,
                                shape = fieldShape,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Box(Modifier.matchParentSize().clickable { protocolMenu = true })
                            DropdownMenu(expanded = protocolMenu, onDismissRequest = { protocolMenu = false }) {
                                listOf(false, true).forEach { secure ->
                                    DropdownMenuItem(
                                        text = { Text(if (secure) "HTTPS" else "HTTP") },
                                        onClick = {
                                            changeProtocol(secure)
                                            protocolMenu = false
                                        },
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it.filter(Char::isDigit).take(5) },
                            label = { Text("端口") },
                            // Empty means the protocol's default port; the placeholder shows which one
                            placeholder = { Text(defaultPort(https)) },
                            singleLine = true,
                            isError = address.portError,
                            shape = fieldShape,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        value = path,
                        onValueChange = { path = it.trim() },
                        label = { Text("路径（可选）") },
                        placeholder = { Text(OpdsAddress.DEFAULT_PATH) },
                        singleLine = true,
                        // The assembled full URL for a quick check before saving; red when the address is invalid
                        supportingText = if (host.isBlank()) null else {
                            @Composable {
                                Text(
                                    "完整地址：${address.toUrl()}",
                                    color = if (address.valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                                )
                            }
                        },
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
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("显示名称（可留空）") },
                        singleLine = true,
                        shape = fieldShape,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                Gap(Space.XL)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S, Alignment.End)) {
                    TextButton(onClick = onDone) { Text("取消", fontSize = 16.sp) }
                    Button(
                        onClick = { viewModel.save(libraryId, name, address.toUrl(), username, password, onSaved) },
                        enabled = address.valid,
                        shape = CircleShape,
                    ) { Text("保存", fontSize = 16.sp) }
                }
            }
        }
    }
}

private fun defaultPort(https: Boolean) = if (https) "443" else "80"
