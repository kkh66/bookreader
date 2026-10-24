package com.bookreader.app

import android.app.Application
import android.content.Context
import com.bookreader.app.data.BookRepository
import com.bookreader.app.data.FontRepository
import com.bookreader.app.data.LibraryRepository
import com.bookreader.app.data.SettingsRepository
import com.bookreader.app.data.StatsRepository
import com.bookreader.app.data.opds.OpdsRepository
import com.bookreader.app.reader.ReaderRepository
import com.bookreader.app.reader.Readium
import java.io.File
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
    val settings = SettingsRepository(context)
    val readium = Readium(context)
    val books = BookRepository(File(context.filesDir, "books"), readium, context)
    val stats = StatsRepository(File(context.filesDir, "stats.json"))
    val libraries = LibraryRepository(File(context.filesDir, "libraries.json"), settings)
    val opds = OpdsRepository(OkHttpClient(), context)
    val fonts = FontRepository(File(context.filesDir, "fonts"), context)
    val reader = ReaderRepository(readium, books, fonts)
}

val Context.container: AppContainer
    get() = (applicationContext as BookReaderApp).container
