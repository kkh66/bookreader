package com.bookreader.app.reader

import android.os.Build
import android.view.WindowManager
import android.webkit.JavascriptInterface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.max
import kotlin.math.min

/** Name of [ImageLongPressBridge] in the pages' JavaScript */
internal const val IMAGE_BRIDGE = "BookReaderImages"

/**
 * Installed into each page (once per document): a long press on an illustration opens it instead of the system's long-press behavior.
 * Readium doesn't report long presses, but the web view fires `contextmenu` for them.
 * An <svg> wrapping an <image> (common in light novel EPUBs) opens the image it references;
 * small inline images (rare characters, ornaments) and vector-only SVGs keep the normal long press
 */
internal val IMAGE_LONG_PRESS_SCRIPT = """
    (function () {
        if (window.__bookReaderImages || !window.$IMAGE_BRIDGE) return;
        window.__bookReaderImages = true;
        document.addEventListener('contextmenu', function (event) {
            var element = event.target && event.target.closest ? event.target.closest('img, image, svg') : null;
            if (!element) return;
            var image = element.localName === 'svg' ? element.querySelector('image') : element;
            var src = image && (image.getAttribute('src') || image.getAttribute('href') ||
                image.getAttributeNS('http://www.w3.org/1999/xlink', 'href'));
            if (!src) return;
            var rect = element.getBoundingClientRect();
            if (rect.width < $MIN_IMAGE_SIZE && rect.height < $MIN_IMAGE_SIZE) return;
            event.preventDefault();
            window.getSelection().removeAllRanges();
            $IMAGE_BRIDGE.open(new URL(src, document.baseURI).href);
        }, true);
    })();
""".trimIndent()

/** Images smaller than this on the page (CSS px, both sides) are treated as part of the text */
private const val MIN_IMAGE_SIZE = 48

/** Receives long-pressed images from [IMAGE_LONG_PRESS_SCRIPT]; called on the web view's JavaBridge thread */
internal class ImageLongPressBridge(private val onImage: (src: String) -> Unit) {
    @JavascriptInterface
    fun open(src: String) = onImage(src)
}

private const val DOUBLE_TAP_SCALE = 2.5f
private const val MAX_SCALE = 5f

/**
 * Full-screen illustration on black: pinch or double-tap to zoom, drag to pan while zoomed, tap or back to close
 */
@Composable
internal fun ImageViewer(image: ImageBitmap, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        LaunchedEffect(window) {
            window ?: return@LaunchedEffect
            // Covers the camera cutout too, with the system bars hidden (a swipe from the edge shows them briefly)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            WindowCompat.getInsetsController(window, window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        val dismiss by rememberUpdatedState(onDismiss)
        var scale by remember { mutableFloatStateOf(1f) }
        // Translation of the image from its centered position, in px
        var offset by remember { mutableStateOf(Offset.Zero) }

        // The zoomed image can't be dragged past its own edges, and stays centered along a side that fits on screen
        fun clamped(translation: Offset, atScale: Float, bounds: IntSize): Offset {
            val fit = min(bounds.width / image.width.toFloat(), bounds.height / image.height.toFloat())
            val maxX = max(0f, (image.width * fit * atScale - bounds.width) / 2)
            val maxY = max(0f, (image.height * fit * atScale - bounds.height) / 2)
            return Offset(translation.x.coerceIn(-maxX, maxX), translation.y.coerceIn(-maxY, maxY))
        }

        // Changes the scale while keeping the image point under [focus] where it is, then moves it by [pan]
        fun zoom(to: Float, focus: Offset, pan: Offset, bounds: IntSize) {
            val fromCenter = focus - Offset(bounds.width / 2f, bounds.height / 2f)
            offset = clamped(fromCenter - (fromCenter - offset) * (to / scale) + pan, to, bounds)
            scale = to
        }

        Box(
            Modifier.fillMaxSize()
                .background(Color.Black)
                .pointerInput(image) {
                    detectTapGestures(
                        onTap = { dismiss() },
                        onDoubleTap = { tap -> zoom(if (scale > 1f) 1f else DOUBLE_TAP_SCALE, tap, Offset.Zero, size) },
                    )
                }
                .pointerInput(image) {
                    detectTransformGestures { centroid, pan, zoomBy, _ ->
                        zoom((scale * zoomBy).coerceIn(1f, MAX_SCALE), centroid, pan, size)
                    }
                },
        ) {
            Image(
                image,
                contentDescription = "插图",
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            )
        }
    }
}
