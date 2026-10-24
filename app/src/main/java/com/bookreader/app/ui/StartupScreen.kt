package com.bookreader.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookreader.app.R
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private const val SPLASH_MAX_MS = 3_000L
private const val SPLASH_EXIT_MS = 200

@Composable
internal fun StartupOverlay(enabled: Boolean, launching: StateFlow<Boolean>, dark: Boolean) {
    var visible by rememberSaveable { mutableStateOf(enabled) }
    LaunchedEffect(Unit) {
        if (visible) {
            // Wait only for real work, with no minimum display time added to a fast launch.
            withTimeoutOrNull(SPLASH_MAX_MS) { launching.first { !it } }
            visible = false
        }
    }
    val easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    AnimatedVisibility(
        visible = visible,
        enter = EnterTransition.None,
        exit = fadeOut(tween(SPLASH_EXIT_MS, easing = easing)) +
            scaleOut(tween(SPLASH_EXIT_MS, easing = easing), targetScale = 1.08f),
    ) {
        val background = if (dark) Color(0xFF171C27) else Color(0xFFE8EDF4)
        val center = if (dark) Color(0xFF293548) else Color.White
        Box(
            Modifier.fillMaxSize().background(Brush.radialGradient(listOf(center, background)))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Image(
                    painterResource(R.drawable.splash_icon), contentDescription = null,
                    modifier = Modifier.size(240.dp).graphicsLayer { scaleX = 1.8f; scaleY = 1.8f },
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp), color = Color(0xFF4CAFE8),
                        trackColor = Color(0xFF4CAFE8).copy(alpha = 0.16f), strokeWidth = 3.dp,
                    )
                    Text(stringResource(R.string.loading), fontSize = 22.sp, color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
    }
}
