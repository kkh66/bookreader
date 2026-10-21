package com.bookreader.app.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bookreader.app.container
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.util.AbsoluteUrl

/**
 * Reader screen: hosts Readium's EpubNavigatorFragment; it only displays the book and reports progress to the ViewModel
 * The Publication must be opened first with [ReaderRepository.open]
 */
@OptIn(ExperimentalReadiumApi::class)
class ReaderActivity : AppCompatActivity(), EpubNavigatorFragment.Listener {

    private val viewModel: ReaderViewModel by viewModels { ReaderViewModel.Factory }
    private val bookId by lazy { intent.getStringExtra(EXTRA_BOOK_ID).orEmpty() }

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

        if (savedInstanceState == null) {
            supportFragmentManager.commitNow {
                add(android.R.id.content, EpubNavigatorFragment::class.java, Bundle(), NAVIGATOR_TAG)
            }
        }
        val navigator = supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as EpubNavigatorFragment
        // Tapping the left or right 30% turns the page (following the reading direction); keyboard arrow keys work too
        navigator.addInputListener(DirectionalNavigationAdapter(navigator, animatedTransition = true))

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                navigator.currentLocator.collect { viewModel.saveProgress(bookId, it) }
            }
        }
    }

    // Reading timer: time the screen is in the foreground counts as reading time
    private var resumedAt = 0L

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

        fun intent(context: Context, bookId: String) =
            Intent(context, ReaderActivity::class.java).putExtra(EXTRA_BOOK_ID, bookId)
    }
}
