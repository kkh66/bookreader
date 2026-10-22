package com.bookreader.app.reader

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color.TRANSPARENT
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.SystemBarStyle
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
import com.bookreader.app.container
import com.bookreader.app.R
import com.bookreader.app.data.Book
import com.bookreader.app.data.Bookmark
import com.bookreader.app.data.SettingsRepository
import com.bookreader.app.data.ThemeMode
import com.bookreader.app.data.bookmarkId
import com.bookreader.app.ui.components.Space
import com.bookreader.app.ui.shelf.ShelfFormat
import com.bookreader.app.ui.theme.AppIcons
import com.bookreader.app.ui.theme.BookReaderTheme
import com.bookreader.app.ui.theme.isDark
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.util.AbsoluteUrl
import kotlin.math.roundToInt

/**
 * Reader screen: hosts Readium's EpubNavigatorFragment; it only displays the book and reports progress to the ViewModel
 * The Publication must be opened first with [ReaderRepository.open]
 */
@OptIn(ExperimentalReadiumApi::class)
class ReaderActivity : AppCompatActivity(), EpubNavigatorFragment.Listener {

    private val viewModel: ReaderViewModel by viewModels { ReaderViewModel.Factory }
    private val bookId by lazy { intent.getStringExtra(EXTRA_BOOK_ID).orEmpty() }
    private var controlsVisible = true

    /** Hide the status bar while reading (a setting), show it again with the menu */
    private var hideStatusBar = false

    /** With the status bar hidden the text reaches the top edge, so the menu's top bar must leave room for the status bar itself (px) */
    private val headerInset = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        val session = container.reader[bookId]
        if (session == null) {
            // After process death the Publication is no longer in memory; go back to the shelf and reopen it
            super.onCreate(null)
            finish()
            return
        }
        // Must be set before super.onCreate; fragment restoration needs it
        // Page colors are passed up front so a dark page never flashes white while loading
        val dark = isDark(container.settings.themeMode.value)
        supportFragmentManager.fragmentFactory = session.navigatorFactory.createFragmentFactory(
            initialLocator = session.initialLocator,
            initialPreferences = readerPreferences(dark),
            listener = this,
        )
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
            setBackgroundColor(pageBackground(dark))
        }
        val footer = ComposeView(this).apply {
            id = R.id.reader_footer
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        // The page stops above the status line; the menus still overlay both
        val footerHeight = (FOOTER_HEIGHT.value * resources.displayMetrics.density).roundToInt()
        val content = FrameLayout(this).apply {
            addView(readerContainer, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply { bottomMargin = footerHeight })
            addView(footer, FrameLayout.LayoutParams(MATCH_PARENT, footerHeight, Gravity.BOTTOM))
            addView(controls, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP))
            addView(bottomControls, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
        }
        hideStatusBar = container.settings.readerHideStatusBar.state.value
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            // System bar insets are taken as when visible, so showing/hiding the status bar doesn't resize the text area, re-layout or jump pages
            val bars = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val statusTop = maxOf(bars.top, cutout.top)
            // With the status bar hidden the text reaches the top edge (avoiding only the cutout); when the menu opens, the status bar overlays the text
            val top = if (hideStatusBar) cutout.top else statusTop
            view.setPadding(maxOf(bars.left, cutout.left), top, maxOf(bars.right, cutout.right), maxOf(bars.bottom, cutout.bottom))
            headerInset.intValue = statusTop - top
            WindowInsetsCompat.CONSUMED
        }
        setContentView(content)

        if (savedInstanceState == null) {
            supportFragmentManager.commitNow {
                add(R.id.reader_container, EpubNavigatorFragment::class.java, Bundle(), NAVIGATOR_TAG)
            }
        }
        val navigator = supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as EpubNavigatorFragment
        // Tapping the left or right 30% turns the page (following the reading direction); keyboard arrow keys work too
        navigator.addInputListener(DirectionalNavigationAdapter(navigator, animatedTransition = true))
        navigator.addInputListener(object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                val width = navigator.publicationView.width
                val edge = maxOf(80f, width * 0.3f)
                if (event.point.x <= edge || event.point.x >= width - edge) return false
                controlsVisible = !controlsVisible
                controls.visibility = if (controlsVisible) View.VISIBLE else View.GONE
                bottomControls.visibility = controls.visibility
                updateStatusBar()
                return true
            }
        })

        footer.setContent {
            val locator by navigator.currentLocator.collectAsStateWithLifecycle()
            val mode by container.settings.themeMode.collectAsStateWithLifecycle()
            val night = mode.isDark()
            ReaderFooter(
                publication = session.publication,
                locator = locator,
                background = Color(pageBackground(night)),
                contentColor = Color(if (night) DARK_TEXT else LIGHT_TEXT).copy(alpha = 0.55f),
            )
        }
        controls.setContent {
            val topInset = with(LocalDensity.current) { headerInset.intValue.toDp() }
            ReaderTheme {
                ReaderHeader(session.book.title, topInset, onBack = ::finish)
            }
        }
        bottomControls.setContent {
            val books by viewModel.bookList.collectAsStateWithLifecycle()
            val locator by navigator.currentLocator.collectAsStateWithLifecycle()
            val book = books.firstOrNull { it.id == bookId } ?: session.book
            ReaderTheme {
                ReaderControls(
                    book = book,
                    publication = session.publication,
                    navigator = navigator,
                    locator = locator,
                    onMessage = ::showMessage,
                    onToggle = { viewModel.toggleBookmark(bookId, locator, ::showMessage) },
                    onOpen = { bookmark ->
                        val target = runCatching { Locator.fromJSON(JSONObject(bookmark.locator)) }.getOrNull()
                        val opened = target != null && navigator.go(target)
                        if (!opened) showMessage("无法跳转到此书签")
                        opened
                    },
                    onRemove = { viewModel.removeBookmark(bookId, it.id, ::showMessage) },
                    settings = container.settings,
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { navigator.currentLocator.collect { viewModel.saveProgress(bookId, it) } }
                // Switching appearance (Me tab or the day/night button) recolors the page right away
                launch {
                    container.settings.themeMode.collect { mode ->
                        val night = isDark(mode)
                        readerContainer.setBackgroundColor(pageBackground(night))
                        navigator.submitPreferences(readerPreferences(night))
                    }
                }
                // Toggling "hide status bar" in settings takes effect immediately
                container.settings.readerHideStatusBar.state.collect { hide ->
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val forward = volumePageDirection(keyCode) ?: return super.onKeyDown(keyCode, event)
        // Holding the key doesn't keep turning pages; one press turns one page
        if (event.repeatCount == 0) {
            val navigator = supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? EpubNavigatorFragment
            if (forward) navigator?.goForward(animated = true) else navigator?.goBackward(animated = true)
        }
        return true
    }

    // Consume the key up too, otherwise the system still changes the volume
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        volumePageDirection(keyCode) != null || super.onKeyUp(keyCode, event)

    /** With volume key paging on: volume down = next page (true), volume up = previous page (false); null for other keys or when off */
    private fun volumePageDirection(keyCode: Int): Boolean? {
        if (!container.settings.volumeKeyPaging.state.value) return null
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
        val mode by container.settings.themeMode.collectAsStateWithLifecycle()
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
            viewModel.addReadingTime(bookId, (SystemClock.elapsedRealtime() - resumedAt) / 1000)
            resumedAt = 0
        }
    }

    override fun onExternalLinkActivated(url: AbsoluteUrl) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.toString()))) }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) container.reader.close(bookId)
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
private fun ReaderHeader(title: String, topInset: Dp, onBack: () -> Unit) {
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
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

private const val SEEK_SETTLE_MS = 500L

// Page colors: the dark page matches the app's dark background so it blends with the menus;
// dark text is slightly dimmed to cut glare
private val DARK_PAGE = 0xFF0E0E0F.toInt()
private val DARK_TEXT = 0xFFDADADA.toInt()
private val LIGHT_PAGE = 0xFFFFFFFF.toInt()
private val LIGHT_TEXT = 0xFF141414.toInt()

private fun pageBackground(dark: Boolean) = if (dark) DARK_PAGE else LIGHT_PAGE

/** EPUB page appearance for the current light / dark mode */
private fun readerPreferences(dark: Boolean) = EpubPreferences(
    theme = if (dark) Theme.DARK else Theme.LIGHT,
    backgroundColor = ReadiumColor(pageBackground(dark)),
    textColor = ReadiumColor(if (dark) DARK_TEXT else LIGHT_TEXT),
)

/** Bottom menu: previous chapter / whole-book progress slider / next chapter, with contents, bookmarks and reading settings below */
@Composable
private fun ReaderControls(
    book: Book,
    publication: Publication,
    navigator: EpubNavigatorFragment,
    locator: Locator,
    onMessage: (String) -> Unit,
    onToggle: () -> Unit,
    onOpen: (Bookmark) -> Boolean,
    onRemove: (Bookmark) -> Unit,
    settings: SettingsRepository,
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
                TextButton(onClick = { goChapter(-1) }) { Text("上一章") }
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
                TextButton(onClick = { goChapter(1) }) { Text("下一章") }
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
        ReaderSettingsDialog(settings, onDismiss = { showingSettings = false })
    }
    if (showingNavigation) {
        ReaderNavigation(
            publication = publication,
            navigator = navigator,
            bookmarks = book.bookmarks,
            onOpenBookmark = onOpen,
            onRemoveBookmark = onRemove,
            onDismiss = { showingNavigation = false },
        )
    }
}
