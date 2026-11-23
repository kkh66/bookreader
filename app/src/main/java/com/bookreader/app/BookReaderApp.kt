package com.bookreader.app

import android.app.Application
import android.content.Context
import com.bookreader.app.data.AppFiles
import com.bookreader.app.data.AppUpdater
import com.bookreader.app.data.BackupRepository
import com.bookreader.app.data.BookRepository
import com.bookreader.app.data.FontRepository
import com.bookreader.app.data.LibraryRepository
import com.bookreader.app.data.SettingsRepository
import com.bookreader.app.data.StatsRepository
import com.bookreader.app.data.opds.OpdsRepository
import com.bookreader.app.reader.ReaderRepository
import com.bookreader.app.reader.Readium
import okhttp3.OkHttpClient

class BookReaderApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * Manual dependency injection: app-wide singletons are created here; ViewModels get them via [container]
 */
class AppContainer(context: Context) {
    private val files = AppFiles(context.filesDir)
    val settings = SettingsRepository(context)
    val readium = Readium(context)
    val books = BookRepository(files.books, readium, context)
    val stats = StatsRepository(files.stats)
    val libraries = LibraryRepository(files.libraries, settings)
    private val http = OkHttpClient()
    val opds = OpdsRepository(http, context)
    val updater = AppUpdater(http, context)
    val fonts = FontRepository(files.fonts, context)
    val reader = ReaderRepository(readium, books, fonts)
    val backup = BackupRepository(files, books, fonts, stats, libraries, settings, context)
}

val Context.container: AppContainer
    get() = (applicationContext as BookReaderApp).container
