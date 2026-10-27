package com.bookreader.app.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bookreader.app.R
import com.bookreader.app.ui.components.Gap
import com.bookreader.app.ui.components.Space
import com.bookreader.app.ui.library.LibraryEditViewModel.TestResult
import com.bookreader.app.ui.theme.AppIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Add / edit a library, in a bottom sheet. A null [libraryId] means a new one
 * The address is split into server address + protocol (dropdown) + port + path fields, with a preview of the full URL below the path;
 * the connection test runs with the fields as typed and reports in a toast over the sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryEditScreen(
    libraryId: String?,
    onDone: () -> Unit,
    onSaved: () -> Unit = onDone,
    viewModel: LibraryEditViewModel = viewModel(factory = LibraryEditViewModel.Factory),
) {
    val context = LocalContext.current
    val error by viewModel.error.collectAsStateWithLifecycle()
    val test by viewModel.test.collectAsStateWithLifecycle()
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
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var protocolMenu by remember { mutableStateOf(false) }
    val address = OpdsAddress(https, host, port, path)
    // A test result belongs to the address and account it was run with
    LaunchedEffect(address.toUrl(), username, password) { viewModel.clearTest() }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

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

    /** Slides the sheet down before it leaves the screen */
    fun close(then: () -> Unit) {
        scope.launch { sheet.hide() }.invokeOnCompletion { then() }
    }

    ModalBottomSheet(onDismissRequest = onDone, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface) {
        Box {
            Column(Modifier.padding(start = Space.XL, end = Space.XL, bottom = Space.L)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        context.getString(if (saved == null) R.string.add_library else R.string.edit_library),
                        Modifier.weight(1f),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = { close(onDone) }) {
                        Icon(AppIcons.Close, context.getString(R.string.close), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Gap(Space.S)
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Space.M),
                ) {
                    SheetField(
                        label = context.getString(R.string.server_address),
                        value = host,
                        onValueChange = ::changeHost,
                        placeholder = context.getString(R.string.server_address_hint),
                        isError = address.hostError != null,
                        keyboardType = KeyboardType.Uri,
                        note = address.hostError?.let { error ->
                            context.getString(when (error) {
                                OpdsAddress.HostError.Address -> R.string.host_error
                                OpdsAddress.HostError.Port -> R.string.port_error
                            }) to MaterialTheme.colorScheme.error
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.M)) {
                        // Read-only field with a click layer on top, so tapping anywhere opens the protocol menu
                        Box(Modifier.weight(1f)) {
                            SheetField(
                                label = context.getString(R.string.protocol),
                                value = if (https) "HTTPS" else "HTTP",
                                onValueChange = {},
                                readOnly = true,
                                trailing = { Icon(AppIcons.ArrowDropDown, null) },
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
                        SheetField(
                            label = context.getString(R.string.port),
                            value = port,
                            onValueChange = { port = it.filter(Char::isDigit).take(5) },
                            modifier = Modifier.weight(1f),
                            // Empty means the protocol's default port; the placeholder shows which one
                            placeholder = defaultPort(https),
                            isError = address.portError,
                            keyboardType = KeyboardType.Number,
                        )
                    }
                    SheetField(
                        label = context.getString(R.string.optional_path),
                        value = path,
                        onValueChange = { path = it.trim() },
                        placeholder = OpdsAddress.DEFAULT_PATH,
                        keyboardType = KeyboardType.Uri,
                        // The assembled full URL for a quick check before saving; red when the address is invalid
                        note = context.getString(R.string.full_address, address.toUrl())
                            .takeIf { host.isNotBlank() }
                            ?.to(if (address.valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error),
                    )
                    SheetField(
                        label = context.getString(R.string.username),
                        value = username,
                        onValueChange = { username = it },
                    )
                    SheetField(
                        label = context.getString(R.string.password),
                        value = password,
                        onValueChange = { password = it },
                        keyboardType = KeyboardType.Password,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailing = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) AppIcons.Visibility else AppIcons.VisibilityOff,
                                    context.getString(if (showPassword) R.string.hide_password else R.string.show_password),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                    SheetField(
                        label = context.getString(R.string.display_name),
                        value = name,
                        onValueChange = { name = it },
                    )
                }
                error?.let {
                    Gap(Space.S)
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
                Gap(Space.L)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S), verticalAlignment = Alignment.CenterVertically) {
                    // Tries the address and account as typed, before saving
                    OutlinedButton(
                        onClick = { viewModel.test(address.toUrl(), username, password) },
                        enabled = address.valid && test != TestResult.Running,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = Space.L),
                    ) {
                        if (test == TestResult.Running) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(AppIcons.Wifi, null, Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(Space.S))
                        Text(context.getString(R.string.test_connection), fontSize = 15.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { close(onDone) }) {
                        Text(context.getString(R.string.cancel), fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = { viewModel.save(libraryId, name, address.toUrl(), username, password) { close(onSaved) } },
                        enabled = address.valid,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = Space.XL),
                    ) { Text(context.getString(R.string.save), fontSize = 16.sp) }
                }
            }
            // Floats over the lowest fields, just above the buttons
            TestToast(test, Modifier.align(Alignment.BottomCenter).padding(start = Space.XL, end = Space.XL, bottom = 76.dp))
        }
    }
}

/**
 * A field with its label above it, filled in the card color with no outline, 48dp tall (denser than [TextField]'s 56dp);
 * [note] is a line of text below it, in its color
 */
@Composable
private fun SheetField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isError: Boolean = false,
    readOnly: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
    note: Pair<String, Color>? = null,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val fieldColors = TextFieldDefaults.colors(
        // The field being typed in is a shade lighter, in place of an outline
        focusedContainerColor = colors.secondaryContainer,
        unfocusedContainerColor = colors.surfaceVariant,
        errorContainerColor = colors.surfaceVariant,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.XS)) {
        Text(label, color = colors.onSurfaceVariant, fontSize = 13.sp)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            readOnly = readOnly,
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            interactionSource = interaction,
            decorationBox = { inner ->
                TextFieldDefaults.DecorationBox(
                    value = value,
                    innerTextField = inner,
                    enabled = true,
                    singleLine = true,
                    visualTransformation = visualTransformation,
                    interactionSource = interaction,
                    isError = isError,
                    placeholder = placeholder?.let { { Text(it) } },
                    trailingIcon = trailing,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    contentPadding = PaddingValues(horizontal = Space.L, vertical = Space.M),
                )
            },
        )
        note?.let { (text, color) -> Text(text, Modifier.padding(start = Space.XS), color = color, fontSize = 13.sp) }
    }
}

/** The connection test's result, shown for a few seconds like a toast; a new test or a changed field takes it away */
@Composable
private fun TestToast(test: TestResult?, modifier: Modifier) {
    val context = LocalContext.current
    var shown by remember { mutableStateOf<TestResult?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(test) {
        if (test is TestResult.Connected || test is TestResult.Failed) {
            shown = test
            visible = true
            // A failure is longer to read
            delay(if (test is TestResult.Failed) FAILED_TOAST_MS else CONNECTED_TOAST_MS)
        }
        visible = false
    }
    AnimatedVisibility(
        visible,
        modifier,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        val result = shown
        val connected = result is TestResult.Connected
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 6.dp,
        ) {
            Row(
                Modifier.padding(horizontal = Space.L, vertical = Space.M),
                horizontalArrangement = Arrangement.spacedBy(Space.S),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (connected) AppIcons.CheckCircle else AppIcons.Error,
                    null,
                    Modifier.size(20.dp),
                    tint = if (connected) CONNECTED_COLOR else FAILED_COLOR,
                )
                Text(
                    when (result) {
                        is TestResult.Connected -> context.getString(R.string.connection_ok, result.title)
                        is TestResult.Failed -> result.message
                        else -> ""
                    },
                    fontSize = 14.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun defaultPort(https: Boolean) = if (https) "443" else "80"

private const val CONNECTED_TOAST_MS = 2500L
private const val FAILED_TOAST_MS = 4000L

// Icon colors that read on the toast in both themes (it's dark in light mode and light in dark mode)
private val CONNECTED_COLOR = Color(0xFF2DA44E)
private val FAILED_COLOR = Color(0xFFE5484D)
