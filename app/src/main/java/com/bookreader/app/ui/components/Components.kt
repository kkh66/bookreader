package com.bookreader.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookreader.app.R
import com.bookreader.app.ui.theme.AppIcons

/**
 * Shared spacing: all paddings and gaps on every screen use only these 5 values
 * XS between text lines · S between icon and text / small elements · M from title to content / list row vertical padding
 * L card padding / between cards / header top padding · XL screen side margins / between sections / large card padding
 */
object Space {
    val XS = 4.dp
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val XL = 24.dp
}

val PagePadding = Space.XL

/** Large page title + subtitle, with round buttons on the right */
@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = PagePadding,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(start = horizontalPadding, end = horizontalPadding, top = Space.L),
        // Without a subtitle, the title and the buttons on the right are vertically centered
        verticalAlignment = if (subtitle == null) Alignment.CenterVertically else Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            if (subtitle != null) {
                Spacer(Modifier.height(Space.S))
                Text(subtitle, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.M), content = actions)
    }
}

/** Top bar for secondary screens: back + title */
@Composable
fun BackHeader(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.L, vertical = Space.M),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(AppIcons.ArrowBack, context.getString(R.string.back), onBack)
        Text(
            title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = Space.M),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Space.M), content = actions)
    }
}

@Composable
fun CircleIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
}

/** Light gray card with large rounded corners */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: String? = null, large: Boolean = false) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            fontSize = if (large) 22.sp else 15.sp,
            fontWeight = if (large) FontWeight.Bold else FontWeight.Normal,
            color = if (large) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        trailing?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Thin progress bar */
@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier.height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(
            Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/** Settings row: icon + title + value on the right + chevron, placed inside a SurfaceCard */
@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    /** Null for a row that only shows a value (no chevron, not tappable) */
    onClick: (() -> Unit)?,
    value: String? = null,
    showDivider: Boolean = true,
    /** Second line under the title, in the secondary text color */
    subtitle: String? = null,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(Space.L),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
            Column(Modifier.weight(1f).padding(start = Space.L)) {
                Text(title, fontSize = 17.sp)
                subtitle?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            value?.let { ValueChip(it) }
            if (onClick != null) {
                Icon(
                    AppIcons.ChevronRight, null,
                    Modifier.padding(start = Space.S).size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // The divider starts at the text: start padding + 24dp icon + icon-to-text gap
        if (showDivider) {
            HorizontalDivider(
                Modifier.padding(start = Space.L + 24.dp + Space.L, end = Space.L),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

@Composable
fun ValueChip(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.background)
            .padding(horizontal = Space.M, vertical = Space.XS),
    )
}

/** List row with an icon tile: Discover categories, library folders */
@Composable
fun IconTileRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    subtitle: String = "",
    showDivider: Boolean = true,
    trailing: @Composable () -> Unit = {
        Icon(AppIcons.ChevronRight, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    },
) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = PagePadding, vertical = Space.M),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onSurface)
            }
            Column(Modifier.weight(1f).padding(horizontal = Space.L)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            trailing()
        }
        if (showDivider) {
            HorizontalDivider(
                Modifier.padding(start = PagePadding + 52.dp + Space.L, end = PagePadding),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

/** Vertical gap; pass only values from [Space] */
@Composable
fun Gap(height: Dp) = Spacer(Modifier.height(height))

/** Row that replaces the header while searching: rounded text field + close button, focused when opened */
@Composable
fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onClose: () -> Unit,
    horizontalPadding: Dp = PagePadding,
    onSearch: () -> Unit = {},
) {
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        Modifier.fillMaxWidth().padding(start = horizontalPadding, end = horizontalPadding, top = Space.L),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).height(48.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = Space.L),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.Search, null, Modifier.size(20.dp), tint = secondary)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.weight(1f).padding(start = Space.S).focusRequester(focus),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text(placeholder, fontSize = 16.sp, color = secondary, maxLines = 1)
                        inner()
                    }
                },
            )
        }
        Spacer(Modifier.width(Space.M))
        CircleIconButton(AppIcons.Close, context.getString(R.string.close_search), onClose)
    }
}
