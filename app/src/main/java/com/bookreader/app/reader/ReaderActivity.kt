package com.bookreader.app.reader

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color.TRANSPARENT
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.ActionMode
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bookreader.app.R
import com.bookreader.app.data.Book
import com.bookreader.app.data.BookFormat
import com.bookreader.app.data.Bookmark
import com.bookreader.app.data.Highlight
import com.bookreader.app.data.HighlightKind
import com.bookreader.app.data.ReadingStyle
import com.bookreader.app.data.ThemeMode
import com.bookreader.app.data.UserFont
import com.bookreader.app.data.bookmarkId
import com.bookreader.app.ui.components.Space
import com.bookreader.app.ui.shelf.ShelfFormat
import com.bookreader.app.ui.theme.AppIcons
import com.bookreader.app.ui.theme.BookReaderTheme
import com.bookreader.app.ui.theme.isDark
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider
import org.readium.adapter.pdfium.navigator.PdfiumNavigatorFragment
import org.readium.adapter.pdfium.navigator.PdfiumPreferences
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.image.ImageNavigatorFragment
import org.readium.r2.navigator.pdf.PdfNavigatorFactory
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.util.AbsoluteUrl
import kotlin.math.roundToInt

/**
 * Reader screen: hosts Readium's navigator for the book's format (EPUB, PDF via PDFium, or the image navigator for CBZ);
 * it only displays the book and reports progress to the ViewModel
 * Text selection, highlights, search and text style exist only for EPUB; PDF and comics turn pages in a direction chosen per book
 * The Publication must be opened first with [ReaderRepository.open]
 */
@OptIn(ExperimentalReadiumApi::class)
class ReaderActivity : AppCompatActivity(), EpubNavigatorFragment.Listener {

    private val viewModel: ReaderViewModel by viewModels { ReaderViewModel.factory(bookId) }
    private val bookId by lazy { intent.getStringExtra(EXTRA_BOOK_ID).orEmpty() }
    private var controlsVisible = true

    /** Hide the status bar while reading (a setting), show it again with the menu */
    private var hideStatusBar = false

    /** With the status bar hidden the text reaches the top edge, so the menu's top bar must leave room for the status bar itself (px) */
    private val headerInset = mutableIntStateOf(0)

    /** Popup menu over the page, for a text selection or a tapped highlight */
    private val markTarget = mutableStateOf<MarkTarget?>(null)

    /** Highlight whose note is being edited */
    private val editingNote = mutableStateOf<Highlight?>(null)

    private val showingSearch = mutableStateOf(false)

    private var selectionWatch: Job? = null

    private val navigator get() = supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as OverflowableNavigator

    /** The navigator when the book is an EPUB, which alone has text selection, highlights and page scripts */
    private val epubNavigator get() = supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? EpubNavigatorFragment

    /** The navigator when the book is a PDF, created with the PDFium engine */
    @Suppress("UNCHECKED_CAST")
    private val pdfNavigator get() = supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? PdfiumNavigatorFragment

    /** Page turn direction picked for this PDF / comic */
    private val rightToLeft get() = viewModel.book.value?.rightToLeft ?: false

    /**
     * Replaces the system text selection toolbar: an empty action mode keeps the selection handles,
     * while [MarkMenu] shows next to the selection instead
     */
    private val selectionCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.clear()
            watchSelection()
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.clear()
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = false

        override fun onDestroyActionMode(mode: ActionMode) {
            selectionWatch?.cancel()
            if (markTarget.value is MarkTarget.Selection) markTarget.value = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val session = viewModel.session
        if (session == null) {
            // After process death the Publication is no longer in memory; go back to the shelf and reopen it
            super.onCreate(null)
            finish()
            return
        }
        // Must be set before super.onCreate; fragment restoration needs it
        // Page colors and text style are passed up front so the first page never flashes in the wrong style
        val dark = isDark(viewModel.themeMode.value)
        // A PDF or comic recreated with the activity starts from its last saved page rather than where the book was opened
        val lastLocator = viewModel.book.value?.locator?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }
            ?: session.initialLocator
        supportFragmentManager.fragmentFactory = when (session.format) {
            BookFormat.EPUB -> EpubNavigatorFactory(session.publication).createFragmentFactory(
                initialLocator = session.initialLocator,
                initialPreferences = readerPreferences(dark, viewModel.readingStyle.value),
                listener = this,
                configuration = EpubNavigatorFragment.Configuration {
                    decorationTemplates = highlightTemplates()
                    selectionActionModeCallback = selectionCallback
                    registerJavascriptInterface(IMAGE_BRIDGE) { ImageLongPressBridge { src -> runOnUiThread { openImage(src) } } }
                    // Imported fonts, each served as a resource of the publication (see ReaderRepository)
                    session.fonts.forEach { served ->
                        addFontFamilyDeclaration(FontFamily(served.font.family)) {
                            addFontFace { addSource(served.url) }
                        }
                    }
                },
            )
            BookFormat.PDF -> PdfNavigatorFactory(session.publication, PdfiumEngineProvider()).createFragmentFactory(
                initialLocator = lastLocator,
                initialPreferences = pdfPreferences(rightToLeft),
            )
            BookFormat.CBZ -> ImageNavigatorFragment.createFactory(session.publication, lastLocator)
        }
        super.onCreate(savedInstanceState)
        title = session.book.title
        enableEdgeToEdge()

        controlsVisible = savedInstanceState?.getBoolean(STATE_CONTROLS_VISIBLE, true) ?: true
        val controls = ComposeView(this).apply {
            id = R.id.reader_controls
            visibility = if (controlsVisible) View.VISIBLE else View.GONE
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        val bottomControls = ComposeView(this).apply {
            id = R.id.reader_bottom_controls
            visibility = if (controlsVisible) View.VISIBLE else View.GONE
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        val readerContainer = FragmentContainerView(this).apply {
            id = R.id.reader_container
            // Readium's image navigator lays its pages out in the view's direction: right to left reverses them, as in manga
            if (session.format == BookFormat.CBZ) layoutDirection = pageDirection(rightToLeft)
        }
        val footer = ComposeView(this).apply {
            id = R.id.reader_footer
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        // Selection / highlight menu; touches pass through to the page wherever nothing is shown
        val marks = ComposeView(this).apply {
            id = R.id.reader_marks
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        // The page stops above the status line; the menus still overlay both
        val footerHeight = (FOOTER_HEIGHT.value * resources.displayMetrics.density).roundToInt()
        val content = FrameLayout(this).apply {
            // The page color fills the whole screen, including the strips under the status bar / cutout and the navigation bar,
            // which would otherwise show the theme's gray window background
            setBackgroundColor(pageBackground(dark))
            addView(readerContainer, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply { bottomMargin = footerHeight })
            addView(footer, FrameLayout.LayoutParams(MATCH_PARENT, footerHeight, Gravity.BOTTOM))
            addView(controls, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP))
            addView(bottomControls, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
            // On top so the menu is never hidden under the reader's bars
            addView(marks, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply { bottomMargin = footerHeight })
        }
        hideStatusBar = viewModel.hideStatusBar.value
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            // System bar insets are taken as when visible, so showing/hiding the status bar doesn't resize the text area, re-layout or jump pages
            val bars = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val statusTop = maxOf(bars.top, cutout.top)
            // With the status bar hidden the text reaches the top edge (avoiding only the cutout); when the menu opens, the status bar overlays the text
            val top = if (hideStatusBar) cutout.top else statusTop
            // Only the page layers are pushed down; the header is not, so its background always extends under the status bar (edge-to-edge on Android 15+)
            view.setPadding(maxOf(bars.left, cutout.left), 0, maxOf(bars.right, cutout.right), maxOf(bars.bottom, cutout.bottom))
            for (page in listOf(readerContainer, marks)) {
                val params = page.layoutParams as FrameLayout.LayoutParams
                if (params.topMargin != top) {
                    params.topMargin = top
                    page.layoutParams = params
                }
            }
            headerInset.intValue = statusTop
            WindowInsetsCompat.CONSUMED
        }
        setContentView(content)

        if (savedInstanceState == null) {
            val navigatorClass = when (session.format) {
                BookFormat.EPUB -> EpubNavigatorFragment::class.java
                BookFormat.PDF -> PdfNavigatorFragment::class.java
                BookFormat.CBZ -> ImageNavigatorFragment::class.java
            }
            supportFragmentManager.commitNow {
                add(R.id.reader_container, navigatorClass, Bundle(), NAVIGATOR_TAG)
            }
        }
        val navigator = supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as OverflowableNavigator
        val epub = navigator as? EpubNavigatorFragment
        navigator.addInputListener(object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                // A tap anywhere on the page first closes an open highlight menu
                if (markTarget.value != null) {
                    closeMarkMenu()
                    return true
                }
                val width = navigator.publicationView.width
                val edge = maxOf(TAP_EDGE_MIN, width * TAP_EDGE_RATIO)
                val left = event.point.x <= edge
                val right = event.point.x >= width - edge
                if (left || right) {
                    // The image navigator only knows the book's own direction, so a comic's edges are turned here; other formats use the adapter below
                    if (session.format != BookFormat.CBZ) return false
                    if (right != rightToLeft) navigator.goForward(animated = true) else navigator.goBackward(animated = true)
                    return true
                }
                controlsVisible = !controlsVisible
                controls.visibility = if (controlsVisible) View.VISIBLE else View.GONE
                bottomControls.visibility = controls.visibility
                updateStatusBar()
                return true
            }
        })
        // Tapping the left or right 30% turns the page (following the reading direction); keyboard arrow keys work too
        navigator.addInputListener(
            DirectionalNavigationAdapter(
                navigator,
                minimumHorizontalEdgeSize = TAP_EDGE_MIN.toDouble(),
                horizontalEdgeThresholdPercent = TAP_EDGE_RATIO.toDouble(),
                animatedTransition = true,
            ),
        )

        epub?.addDecorationListener(HIGHLIGHT_GROUP, object : DecorableNavigator.Listener {
            override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
                val highlight = currentBook()?.highlights?.firstOrNull { it.id == event.decoration.id } ?: return false
                val rect = event.rect ?: event.point?.let { RectF(it.x, it.y, it.x, it.y) } ?: return false
                selectionWatch?.cancel()
                markTarget.value = MarkTarget.Existing(highlight, rect)
                return true
            }
        })
        marks.setContent {
            val book by viewModel.book.collectAsStateWithLifecycle()
            val last by viewModel.lastHighlight.collectAsStateWithLifecycle()
            val highlights = book?.highlights.orEmpty()
            ReaderTheme {
                markTarget.value?.let { target ->
                    BackHandler(onBack = ::closeMarkMenu)
                    // Latest copy of the highlight, falling back to the one the menu opened with until the save lands
                    val current = (target as? MarkTarget.Existing)?.let { t -> highlights.firstOrNull { it.id == t.highlight.id } ?: t.highlight }
                    val text = current?.text ?: (target as? MarkTarget.Selection)?.locator?.text?.highlight.orEmpty()
                    MarkMenu(
                        target = target,
                        current = current,
                        lastKind = last.first,
                        lastColor = last.second,
                        onStyle = { kind, color -> applyMarkStyle(target, current, kind, color) },
                        onHighlight = {
                            (target as? MarkTarget.Selection)?.let { viewModel.createHighlight(it.locator, last.first, last.second) }
                            closeMarkMenu()
                        },
                        onCopy = {
                            TextActions.copy(this, text)
                            closeMarkMenu()
                        },
                        onNote = {
                            editingNote.value = current
                                ?: (target as? MarkTarget.Selection)?.let { viewModel.createHighlight(it.locator, last.first, last.second) }
                            closeMarkMenu()
                        },
                        onDelete = {
                            current?.let { viewModel.removeHighlight(it.id) }
                            closeMarkMenu()
                        },
                        onDictionary = {
                            TextActions.lookUp(this, text)
                            closeMarkMenu()
                        },
                        onSearch = {
                            viewModel.setSearchQuery(text)
                            viewModel.search()
                            showingSearch.value = true
                            closeMarkMenu()
                        },
                        onShare = {
                            TextActions.share(this, text)
                            closeMarkMenu()
                        },
                        onWebSearch = {
                            TextActions.webSearch(this, text)
                            closeMarkMenu()
                        },
                    )
                }
                editingNote.value?.let { editing ->
                    val highlight = highlights.firstOrNull { it.id == editing.id } ?: editing
                    NoteDialog(
                        highlight,
                        onSave = { note ->
                            viewModel.saveHighlight(highlight.copy(note = note))
                            editingNote.value = null
                        },
                        onDismiss = { editingNote.value = null },
                    )
                }
                val image by viewModel.viewingImage.collectAsStateWithLifecycle()
                image?.let { ImageViewer(it, onDismiss = viewModel::closeImage) }
            }
        }
        footer.setContent {
            val locator by navigator.currentLocator.collectAsStateWithLifecycle()
            val mode by viewModel.themeMode.collectAsStateWithLifecycle()
            val night = mode.isDark()
            ReaderFooter(
                publication = session.publication,
                locator = locator,
                background = Color(pageBackground(night)),
                contentColor = Color(if (night) DARK_TEXT else LIGHT_TEXT).copy(alpha = 0.55f),
                chapterProgress = session.format == BookFormat.EPUB,
            )
        }
        controls.setContent {
            val topInset = with(LocalDensity.current) { headerInset.intValue.toDp() }
            ReaderTheme {
                ReaderHeader(
                    session.book.title, topInset, onBack = ::finish,
                    // Only EPUB text can be searched
                    onSearch = if (session.format == BookFormat.EPUB) ({ showingSearch.value = true }) else null,
                )
            }
        }
        bottomControls.setContent {
            val currentBook by viewModel.book.collectAsStateWithLifecycle()
            val locator by navigator.currentLocator.collectAsStateWithLifecycle()
            val book = currentBook ?: session.book
            ReaderTheme {
                ReaderControls(
                    book = book,
                    publication = session.publication,
                    format = session.format,
                    navigator = navigator,
                    locator = locator,
                    onMessage = ::showMessage,
                    onToggle = { viewModel.toggleBookmark(locator) },
                    onOpen = { bookmark ->
                        val target = runCatching { Locator.fromJSON(JSONObject(bookmark.locator)) }.getOrNull()
                        val opened = target != null && navigator.go(target)
                        if (!opened) showMessage("无法跳转到此书签")
                        opened
                    },
                    onRemove = { viewModel.removeBookmark(it.id) },
                    viewModel = viewModel,
                    fonts = session.fonts.map { it.font },
                    showingSearch = showingSearch,
                    onOpenHighlight = { highlight ->
                        val target = runCatching { Locator.fromJSON(JSONObject(highlight.locator)) }.getOrNull()
                        val opened = target != null && navigator.go(target)
                        if (!opened) showMessage("无法跳转到此笔记")
                        opened
                    },
                    onRemoveHighlight = { viewModel.removeHighlight(it.id) },
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.messages.collect(::showMessage) }
                launch { navigator.currentLocator.collect { viewModel.saveProgress(it) } }
                if (epub != null) {
                    // Long press on illustrations: the script goes into whichever document is showing (it skips documents that already have it)
                    launch { epub.currentLocator.collectLatest { epub.evaluateJavascript(IMAGE_LONG_PRESS_SCRIPT) } }
                    // Saved highlights drawn on the page; Readium only redraws what changed
                    launch {
                        viewModel.book
                            .map { it?.highlights.orEmpty() }
                            .distinctUntilChanged()
                            .collect { highlights ->
                                epub.applyDecorations(highlights.mapNotNull { it.toDecoration() }, HIGHLIGHT_GROUP)
                            }
                    }
                }
                // Appearance (Me tab) and text style (reading settings) apply to the page right away
                launch {
                    combine(viewModel.themeMode, viewModel.readingStyle, ::Pair).collect { (mode, style) ->
                        val night = isDark(mode)
                        content.setBackgroundColor(pageBackground(night))
                        epub?.submitPreferences(readerPreferences(night, style))
                    }
                }
                // Page turn direction picked in the settings (the first value is already in place): a PDF re-lays out its pages,
                // a comic's pager reverses its order
                launch {
                    viewModel.book.map { it?.rightToLeft ?: false }.distinctUntilChanged().drop(1).collect { rtl ->
                        if (session.format == BookFormat.PDF) pdfNavigator?.submitPreferences(pdfPreferences(rtl))
                        if (session.format == BookFormat.CBZ) readerContainer.layoutDirection = pageDirection(rtl)
                    }
                }
                // Toggling "hide status bar" in settings takes effect immediately
                viewModel.hideStatusBar.collect { hide ->
                    hideStatusBar = hide
                    ViewCompat.requestApplyInsets(content)
                    updateStatusBar()
                }
            }
        }
    }

    /** With hide status bar on, the status bar only shows while the menu is open; swiping down from the top reveals it briefly */
    private fun updateStatusBar() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hideStatusBar && !controlsVisible) {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    private fun currentBook() = viewModel.book.value

    /** Keeps the menu next to the selection while its handles are dragged, and closes it once the selection is gone */
    private fun watchSelection() {
        selectionWatch?.cancel()
        selectionWatch = lifecycleScope.launch {
            while (true) {
                val selection = epubNavigator?.currentSelection()
                if (markTarget.value !is MarkTarget.Existing) {
                    markTarget.value = selection?.let {
                        MarkTarget.Selection(it.locator, it.rect ?: RectF(0f, 0f, navigator.publicationView.width.toFloat(), 0f))
                    }
                }
                delay(SELECTION_WATCH_MS)
            }
        }
    }

    /**
     * A kind or color tapped in the menu: remembered for next time and applied to the highlight being edited;
     * on a fresh selection it creates the highlight, and the menu stays open to keep adjusting it
     */
    private fun applyMarkStyle(target: MarkTarget, current: Highlight?, kind: HighlightKind, color: Int) {
        viewModel.setLastHighlight(kind, color)
        when {
            current != null -> viewModel.saveHighlight(current.copy(kind = kind, color = color))
            target is MarkTarget.Selection -> {
                val created = viewModel.createHighlight(target.locator, kind, color)
                selectionWatch?.cancel()
                markTarget.value = MarkTarget.Existing(created, target.rect)
                epubNavigator?.clearSelection()
            }
        }
    }

    private fun closeMarkMenu() {
        selectionWatch?.cancel()
        markTarget.value = null
        epubNavigator?.clearSelection()
    }

    /** A long-pressed illustration, shown full screen at about the screen's size */
    private fun openImage(src: String) {
        window.decorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        closeMarkMenu()
        val screen = resources.displayMetrics
        viewModel.openImage(src, screen.widthPixels, screen.heightPixels)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val forward = volumePageDirection(keyCode) ?: return super.onKeyDown(keyCode, event)
        // Holding the key doesn't keep turning pages; one press turns one page
        if (event.repeatCount == 0) {
            val navigator = supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? OverflowableNavigator
            if (forward) navigator?.goForward(animated = true) else navigator?.goBackward(animated = true)
        }
        return true
    }

    // Consume the key up too, otherwise the system still changes the volume
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        volumePageDirection(keyCode) != null || super.onKeyUp(keyCode, event)

    /** With volume key paging on: volume down = next page (true), volume up = previous page (false); null for other keys or when off */
    private fun volumePageDirection(keyCode: Int): Boolean? {
        if (!viewModel.volumeKeyPaging.value) return null
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> true
            KeyEvent.KEYCODE_VOLUME_UP -> false
            else -> null
        }
    }

    /** Same rule as [ThemeMode.isDark], for use outside Compose (System follows the device night mode) */
    private fun isDark(mode: ThemeMode) = when (mode) {
        ThemeMode.System ->
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    @Composable
    private fun ReaderTheme(content: @Composable () -> Unit) {
        val mode by viewModel.themeMode.collectAsStateWithLifecycle()
        val dark = mode.isDark()
        LaunchedEffect(dark) {
            val style = if (dark) SystemBarStyle.dark(TRANSPARENT) else SystemBarStyle.light(TRANSPARENT, TRANSPARENT)
            enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        }
        BookReaderTheme(dark, content)
    }

    // Reading timer: time the screen is in the foreground counts as reading time
    private var resumedAt = 0L

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_CONTROLS_VISIBLE, controlsVisible)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        resumedAt = SystemClock.elapsedRealtime()
        // The system may have brought the status bar back while we were in another app
        updateStatusBar()
    }

    override fun onPause() {
        super.onPause()
        if (resumedAt > 0) {
            viewModel.addReadingTime((SystemClock.elapsedRealtime() - resumedAt) / 1000)
            resumedAt = 0
        }
    }

    override fun onExternalLinkActivated(url: AbsoluteUrl) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.toString()))) }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) viewModel.close()
    }

    companion object {
        private const val EXTRA_BOOK_ID = "book_id"
        private const val NAVIGATOR_TAG = "navigator"
        private const val STATE_CONTROLS_VISIBLE = "controls_visible"

        fun intent(context: Context, bookId: String) =
            Intent(context, ReaderActivity::class.java).putExtra(EXTRA_BOOK_ID, bookId)
    }
}

@Composable
private fun ReaderHeader(title: String, topInset: Dp, onBack: () -> Unit, onSearch: (() -> Unit)?) {
    Surface(color = MaterialTheme.colorScheme.background) {
        // When the status bar overlays the top bar, the bar's background extends under it and the content moves down
        Column(Modifier.padding(top = topInset)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.S, vertical = Space.XS),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, "返回") }
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = Space.S),
                )
                onSearch?.let { IconButton(onClick = it) { Icon(AppIcons.Search, "搜索") } }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

private const val SEEK_SETTLE_MS = 500L

/** Taps within this share of the page width from either edge (at least [TAP_EDGE_MIN] px) turn the page */
private const val TAP_EDGE_RATIO = 0.3f
private const val TAP_EDGE_MIN = 80f

/** How often the selection menu follows the selection while its handles are dragged */
private const val SELECTION_WATCH_MS = 300L

/** Decoration group for the in-page highlight of the last opened search hit */
private const val SEARCH_GROUP = "search"

// Page colors: the dark page matches the app's dark background so it blends with the menus;
// dark text is slightly dimmed to cut glare
private val DARK_PAGE = 0xFF0E0E0F.toInt()
private val DARK_TEXT = 0xFFDADADA.toInt()
private val LIGHT_PAGE = 0xFFFFFFFF.toInt()
private val LIGHT_TEXT = 0xFF141414.toInt()

private fun pageBackground(dark: Boolean) = if (dark) DARK_PAGE else LIGHT_PAGE

/** PDF pages turn sideways like a book's, right to left for manga */
private fun pdfPreferences(rightToLeft: Boolean) =
    PdfiumPreferences(readingProgression = if (rightToLeft) ReadingProgression.RTL else ReadingProgression.LTR)

private fun pageDirection(rightToLeft: Boolean) = if (rightToLeft) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR

/** EPUB page appearance: light / dark colors plus the user's text style */
@OptIn(ExperimentalReadiumApi::class)
private fun readerPreferences(dark: Boolean, style: ReadingStyle) = EpubPreferences(
    theme = if (dark) Theme.DARK else Theme.LIGHT,
    backgroundColor = ReadiumColor(pageBackground(dark)),
    textColor = ReadiumColor(if (dark) DARK_TEXT else LIGHT_TEXT),
    fontFamily = style.fontFamily?.let(::FontFamily),
    fontSize = style.fontSize,
    fontWeight = style.fontWeight,
    lineHeight = style.lineHeight,
    paragraphSpacing = style.paragraphSpacing,
    pageMargins = style.pageMargins,
    publisherStyles = !style.overridesPublisherStyles,
)

/**
 * Bottom menu: previous chapter / whole-book progress slider / next chapter, with contents, bookmarks and reading settings below;
 * also hosts the search dialog, opened from the top bar or the selection menu
 * A book without contents (a comic) has no chapter buttons
 */
@OptIn(ExperimentalReadiumApi::class)
@Composable
private fun ReaderControls(
    book: Book,
    publication: Publication,
    format: BookFormat,
    navigator: OverflowableNavigator,
    locator: Locator,
    onMessage: (String) -> Unit,
    onToggle: () -> Unit,
    onOpen: (Bookmark) -> Boolean,
    onRemove: (Bookmark) -> Unit,
    onOpenHighlight: (Highlight) -> Boolean,
    onRemoveHighlight: (Highlight) -> Unit,
    showingSearch: MutableState<Boolean>,
    viewModel: ReaderViewModel,
    fonts: List<UserFont>,
) {
    var showingNavigation by rememberSaveable { mutableStateOf(false) }
    var showingSettings by rememberSaveable { mutableStateOf(false) }
    val locationId = bookmarkId(locator.href.toString(), locator.locations.position, locator.locations.progression)
    val bookmarked = book.bookmarks.any { it.id == locationId }
    val scope = rememberCoroutineScope()
    val chapters = remember(publication) { publication.chapters() }
    // Slider position while dragging; after release and the jump, it waits a moment before handing back to the real progress so the slider doesn't snap back before the reading position updates
    var seeking by remember { mutableStateOf<Float?>(null) }
    var seekJob by remember { mutableStateOf<Job?>(null) }

    // The current chapter is only accurate when read from heading positions in the page, so it's computed on button taps only, with no extra script on every page turn
    fun goChapter(step: Int) {
        scope.launch {
            val current = currentChapterIndex(publication, navigator, chapters, locator)
            val target = adjacentChapter(chapters, current, step)
            when {
                target == null -> onMessage(if (step < 0) "已经是第一章" else "已经是最后一章")
                !navigator.go(target) -> onMessage("无法跳转到此章节")
            }
        }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            seeking?.let {
                Text(
                    ShelfFormat.percent(it.toDouble()),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = Space.S),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.S),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (chapters.isNotEmpty()) TextButton(onClick = { goChapter(-1) }) { Text("上一章") }
                Slider(
                    value = seeking ?: (locator.locations.totalProgression ?: 0.0).toFloat(),
                    onValueChange = {
                        // Dragging again before the last release was handed back: don't let it reset the slider midway
                        seekJob?.cancel()
                        seeking = it
                    },
                    onValueChangeFinished = {
                        seeking?.let { target ->
                            seekJob = scope.launch {
                                val destination = publication.locateProgression(target.toDouble())
                                if (destination == null || !navigator.go(destination)) {
                                    onMessage("无法跳转到此位置")
                                } else {
                                    delay(SEEK_SETTLE_MS)
                                }
                                seeking = null
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).padding(horizontal = Space.XS),
                )
                if (chapters.isNotEmpty()) TextButton(onClick = { goChapter(1) }) { Text("下一章") }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.S, vertical = Space.XS),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { showingNavigation = true }) { Icon(AppIcons.List, "目录与书签") }
                IconToggleButton(checked = bookmarked, onCheckedChange = { onToggle() }) {
                    Icon(
                        if (bookmarked) AppIcons.Check else AppIcons.Bookmark,
                        if (bookmarked) "移除当前书签" else "添加书签",
                    )
                }
                IconButton(onClick = { showingSettings = true }) { Icon(AppIcons.Settings, "阅读设置") }
            }
        }
    }
    if (showingSettings) {
        ReaderSettingsDialog(viewModel, format, fonts, onDismiss = { showingSettings = false })
    }
    if (showingSearch.value) {
        val search by viewModel.searchState.collectAsStateWithLifecycle()
        ReaderSearchDialog(
            state = search,
            onQueryChange = viewModel::setSearchQuery,
            onSearch = {
                // A new search drops the previous hit's highlight from the page
                scope.launch { (navigator as? DecorableNavigator)?.applyDecorations(emptyList(), SEARCH_GROUP) }
                viewModel.search()
            },
            onOpen = { hit ->
                if (navigator.go(hit)) {
                    showingSearch.value = false
                    // Mark the hit on the page so it's easy to spot after the jump
                    scope.launch {
                        (navigator as? DecorableNavigator)?.applyDecorations(
                            listOf(Decoration("search", hit, Decoration.Style.Highlight(tint = SEARCH_HIGHLIGHT.toArgb()))),
                            SEARCH_GROUP,
                        )
                    }
                } else {
                    onMessage("无法跳转到此位置")
                }
            },
            onDismiss = { showingSearch.value = false },
        )
    }
    if (showingNavigation) {
        ReaderNavigation(
            publication = publication,
            format = format,
            navigator = navigator,
            bookmarks = book.bookmarks,
            onOpenBookmark = onOpen,
            onRemoveBookmark = onRemove,
            highlights = book.highlights,
            onOpenHighlight = onOpenHighlight,
            onRemoveHighlight = onRemoveHighlight,
            onDismiss = { showingNavigation = false },
        )
    }
}
