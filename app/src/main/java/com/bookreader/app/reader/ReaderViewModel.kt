package com.bookreader.app.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bookreader.app.container
import com.bookreader.app.data.BookRepository
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Locator

class ReaderViewModel(private val books: BookRepository) : ViewModel() {

    fun saveProgress(bookId: String, locator: Locator) {
        viewModelScope.launch {
            books.updateLocator(bookId, locator.toJSON().toString())
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { ReaderViewModel(this[APPLICATION_KEY]!!.container.books) }
        }
    }
}
