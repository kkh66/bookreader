package com.bookreader.app

import android.app.Application
import android.content.Context
import com.bookreader.app.data.BookRepository
import com.bookreader.app.data.LibraryRepository
import com.bookreader.app.data.SettingsRepository
import com.bookreader.app.data.StatsRepository
import com.bookreader.app.data.opds.OpdsRepository
import com.bookreader.app.reader.Readium
import com.bookreader.app.reader.ReaderRepository
import okhttp3.OkHttpClient
import java.io.File

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
    val books = BookRepository(File(context.filesDir, "books"), readium)
    val stats = StatsRepository(File(context.filesDir, "stats.json"))
    val libraries = LibraryRepository(File(context.filesDir, "libraries.json"), settings)
    val opds = OpdsRepository(OkHttpClient())
    val reader = ReaderRepository(readium, books)
}

val Context.container: AppContainer
    get() = (applicationContext as BookReaderApp).container
