package com.bookreader.app.reader

import android.content.Context
import android.content.Intent
import android.graphics.Color.TRANSPARENT
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import com.bookreader.app.data.ThemeMode
import com.bookreader.app.data.bookmarkId
import com.bookreader.app.ui.components.Space
import com.bookreader.app.ui.theme.AppIcons
import com.bookreader.app.ui.theme.BookReaderTheme
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl

/**
 * Reader screen: hosts Readium's EpubNavigatorFragment; it only displays the book and reports progress to the ViewModel
 * The Publication must be opened first with [ReaderRepository.open]
 */
@OptIn(ExperimentalReadiumApi::class)
class ReaderActivity : AppCompatActivity(), EpubNavigatorFragment.Listener {

    private val viewModel: ReaderViewModel by viewModels { ReaderViewModel.Factory }
    private val bookId by lazy { intent.getStringExtra(EXTRA_BOOK_ID).orEmpty() }
    private var controlsVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        val session = container.reader[bookId]
        if (session == null) {
            // After process death the Publication is no longer in memory; go back to the shelf and reopen it
            super.onCreate(null)
            finish()
            return
        }
        // Must be set before super.onCreate; fragment restoration needs it
        supportFragmentManager.fragmentFactory = session.navigatorFactory.createFragmentFactory(
            initialLocator = session.initialLocator,
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
        val readerContainer = FragmentContainerView(this).apply { id = R.id.reader_container }
        val content = FrameLayout(this).apply {
            addView(readerContainer, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(controls, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP))
            addView(bottomControls, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
        }
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
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
                return true
            }
        })

        controls.setContent {
            ReaderTheme {
                ReaderHeader(session.book.title, onBack = ::finish)
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
                    locationId = bookmarkId(locator.href.toString(), locator.locations.position, locator.locations.progression),
                    onToggle = { viewModel.toggleBookmark(bookId, locator, ::showMessage) },
                    onOpen = { bookmark ->
                        val target = runCatching { Locator.fromJSON(JSONObject(bookmark.locator)) }.getOrNull()
                        val opened = target != null && navigator.go(target)
                        if (!opened) showMessage("无法跳转到此书签")
                        opened
                    },
                    onRemove = { viewModel.removeBookmark(bookId, it.id, ::showMessage) },
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                navigator.currentLocator.collect { viewModel.saveProgress(bookId, it) }
            }
        }
    }

    @Composable
    private fun ReaderTheme(content: @Composable () -> Unit) {
        val mode by container.settings.themeMode.collectAsStateWithLifecycle()
        val dark = when (mode) {
            ThemeMode.System -> isSystemInDarkTheme()
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
        }
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
private fun ReaderHeader(title: String, onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
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

@Composable
private fun ReaderControls(
    book: Book,
    publication: Publication,
    navigator: EpubNavigatorFragment,
    locationId: String,
    onToggle: () -> Unit,
    onOpen: (Bookmark) -> Boolean,
    onRemove: (Bookmark) -> Unit,
) {
    var showingNavigation by rememberSaveable { mutableStateOf(false) }
    val bookmarked = book.bookmarks.any { it.id == locationId }
    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.S, vertical = Space.XS),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconToggleButton(checked = bookmarked, onCheckedChange = { onToggle() }) {
                    Icon(
                        if (bookmarked) AppIcons.Check else AppIcons.Bookmark,
                        if (bookmarked) "移除当前书签" else "添加书签",
                    )
                }
                IconButton(onClick = { showingNavigation = true }) { Icon(AppIcons.List, "目录与书签") }
            }
        }
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
