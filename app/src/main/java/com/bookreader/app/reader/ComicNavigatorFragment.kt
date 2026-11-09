package com.bookreader.app.reader

import android.annotation.SuppressLint
import android.graphics.PointF
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.core.SnapSpec
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bookreader.app.data.decodeScaledBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.saket.telephoto.subsamplingimage.SubSamplingImage
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.subsamplingimage.rememberSubSamplingImageState
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableContentLocation
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import okio.Buffer
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.KeyEvent
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.Axis
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.navigator.util.SingleFragmentFactory
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.indexOfFirstWithHref
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.shared.util.use
import android.view.KeyEvent as AndroidKeyEvent

/**
 * Comic (CBZ) navigator in place of Readium's image navigator, which decodes every page whole on the main thread:
 * pages are decoded off the main thread, in tiles at screen resolution that sharpen as the page is zoomed (telephoto).
 * It's a Readium navigator like the EPUB / PDF ones, so page turns, the footer, contents and bookmarks work the same
 */
@SuppressLint("ValidFragment")
@OptIn(ExperimentalReadiumApi::class)
internal class ComicNavigatorFragment(
    private val publication: Publication,
    initialLocator: Locator?,
    rightToLeft: Boolean,
) : Fragment(), OverflowableNavigator {

    /** One position per page, as with Readium's image navigator */
    private val positions = runBlocking { publication.positions() }

    private val pager = PagerState(initialLocator?.let(::pageOf) ?: 0) { publication.readingOrder.size }

    private val _currentLocator = MutableStateFlow(positions[pager.currentPage])
    override val currentLocator: StateFlow<Locator> = _currentLocator.asStateFlow()

    private val _overflow = MutableStateFlow<OverflowableNavigator.Overflow>(ComicOverflow(rightToLeft))
    override val overflow: StateFlow<OverflowableNavigator.Overflow> = _overflow.asStateFlow()

    private var reversed by mutableStateOf(rightToLeft)

    /** Manga order: the first page on the right, the next one coming in from the left */
    var rightToLeft: Boolean
        get() = reversed
        set(value) {
            reversed = value
            _overflow.value = ComicOverflow(value)
        }

    /** Whether the page on screen is zoomed in: a drag then pans it instead of turning the page */
    var zoomed = false
        private set

    private val listeners = mutableListOf<InputListener>()

    /** The pages' composition scope: it has the frame clock animated turns need, which lifecycleScope lacks (it'd crash) */
    private var turnScope: CoroutineScope? = null

    override val publicationView: View get() = requireView()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val pages = ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val scope = rememberCoroutineScope()
                DisposableEffect(scope) {
                    turnScope = scope
                    onDispose { turnScope = null }
                }
                HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, reverseLayout = reversed) { Page(it) }
            }
        }
        // Keys go to the input listeners (arrow keys turn pages), as with Readium's navigators
        return object : FrameLayout(requireContext()) {
            override fun onKeyDown(keyCode: Int, event: AndroidKeyEvent) = key(KeyEvent.Type.Down, event) || super.onKeyDown(keyCode, event)
            override fun onKeyUp(keyCode: Int, event: AndroidKeyEvent) = key(KeyEvent.Type.Up, event) || super.onKeyUp(keyCode, event)
        }.apply {
            addView(pages)
            isFocusable = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) defaultFocusHighlightEnabled = false
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            snapshotFlow { pager.settledPage }.collect { page -> positions.getOrNull(page)?.let { _currentLocator.value = it } }
        }
    }

    @Composable
    private fun Page(page: Int) {
        val zoom = rememberZoomableState(ZoomSpec(maxZoomFactor = MAX_ZOOM))
        val link = publication.readingOrder[page]
        val metrics = resources.displayMetrics
        val image by produceState<PageImage?>(null, link) { value = load(link, metrics.widthPixels, metrics.heightPixels) }
        val modifier = Modifier.fillMaxSize().zoomable(zoom, onClick = ::tap)
        when (val loaded = image) {
            is PageImage.Tiled -> SubSamplingImage(rememberSubSamplingImageState(loaded.source, zoom), null, modifier)
            is PageImage.Whole -> {
                zoom.setContentLocation(ZoomableContentLocation.scaledInsideAndCenterAligned(Size(loaded.bitmap.width.toFloat(), loaded.bitmap.height.toFloat())))
                Image(loaded.bitmap, null, modifier, contentScale = ContentScale.Inside)
            }
            null -> Unit
        }
        if (pager.settledPage == page) {
            LaunchedEffect(zoom) { snapshotFlow { (zoom.zoomFraction ?: 0f) > ZOOMED_IN }.collect { zoomed = it } }
        } else {
            // Off screen: back to fitting the page, so coming back to it doesn't find it still zoomed
            LaunchedEffect(Unit) { zoom.resetZoom(SnapSpec()) }
        }
    }

    /** JPEG / PNG / WebP are decoded in tiles; anything else (GIF, say) whole, downsampled to the screen */
    private suspend fun load(link: Link, width: Int, height: Int): PageImage? = withContext(Dispatchers.IO) {
        val bytes = publication.get(link)?.use { it.read().getOrNull() } ?: return@withContext null
        if (TILED_TYPES.any { link.mediaType?.matches(it) == true }) {
            PageImage.Tiled(SubSamplingImageSource.rawSource({ Buffer().write(bytes) }))
        } else {
            decodeScaledBitmap(bytes, width, height)?.let { PageImage.Whole(it.asImageBitmap()) }
        }
    }

    private fun tap(offset: Offset) {
        val event = TapEvent(PointF(offset.x, offset.y))
        listeners.any { it.onTap(event) }
    }

    private fun key(type: KeyEvent.Type, event: AndroidKeyEvent): Boolean {
        val key = KeyEvent(type, event) ?: return false
        return listeners.any { it.onKey(key) }
    }

    private fun pageOf(locator: Locator): Int? = publication.readingOrder.indexOfFirstWithHref(locator.href)

    /** Turns to [page], returning false when the book has no such page */
    private fun turnTo(page: Int, animated: Boolean): Boolean {
        if (page !in publication.readingOrder.indices) return false
        // Before the pages are composed there's no frame clock: jump there without animating
        val scope = turnScope
        if (scope == null) lifecycleScope.launch { pager.scrollToPage(page) }
        else scope.launch { if (animated) pager.animateScrollToPage(page) else pager.scrollToPage(page) }
        return true
    }

    override fun go(locator: Locator, animated: Boolean): Boolean = pageOf(locator)?.let { turnTo(it, animated) } ?: false

    override fun go(link: Link, animated: Boolean): Boolean = publication.locatorFromLink(link)?.let { go(it, animated) } ?: false

    override fun goForward(animated: Boolean): Boolean = turnTo(pager.targetPage + 1, animated)

    override fun goBackward(animated: Boolean): Boolean = turnTo(pager.targetPage - 1, animated)

    override fun addInputListener(listener: InputListener) {
        listeners.add(listener)
    }

    override fun removeInputListener(listener: InputListener) {
        listeners.remove(listener)
    }

    private sealed interface PageImage {
        class Tiled(val source: SubSamplingImageSource) : PageImage
        class Whole(val bitmap: ImageBitmap) : PageImage
    }

    private data class ComicOverflow(val rightToLeft: Boolean) : OverflowableNavigator.Overflow {
        override val readingProgression get() = if (rightToLeft) ReadingProgression.RTL else ReadingProgression.LTR
        override val scroll get() = false
        override val axis get() = Axis.HORIZONTAL
    }

    companion object {
        private const val MAX_ZOOM = 3f

        /** Zoom fraction (0 = fitted, 1 = fully zoomed) above which the page counts as zoomed in */
        private const val ZOOMED_IN = 0.01f

        private val TILED_TYPES = listOf(MediaType.JPEG, MediaType.PNG, MediaType.WEBP)

        fun createFactory(publication: Publication, initialLocator: Locator?, rightToLeft: Boolean) =
            SingleFragmentFactory(ComicNavigatorFragment::class.java) { ComicNavigatorFragment(publication, initialLocator, rightToLeft) }
    }
}
