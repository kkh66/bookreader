package com.bookreader.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bookreader.app.data.OpdsServer
import com.bookreader.app.ui.components.BackHeader
import com.bookreader.app.ui.components.PagePadding

@Composable
fun SettingsScreen(
    onDone: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val saved = viewModel.server
    var url by rememberSaveable { mutableStateOf(saved.url) }
    var username by rememberSaveable { mutableStateOf(saved.username) }
    var password by rememberSaveable { mutableStateOf(saved.password) }
    val fieldShape = RoundedCornerShape(16.dp)

    BackHandler(onBack = onDone)

    Column {
        BackHeader("Calibre 服务器", onBack = onDone)
        Column(
            Modifier.padding(horizontal = PagePadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "填写 Calibre / Calibre-Web 的 OPDS 地址，账号只发送给这台服务器",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("OPDS 地址") },
                placeholder = { Text("http://192.168.1.100:8083/opds") },
                singleLine = true,
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
            Button(
                onClick = {
                    viewModel.save(OpdsServer(url, username, password))
                    onDone()
                },
                shape = CircleShape,
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("保存", fontSize = 16.sp) }
        }
    }
}
