package com.bookreader.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.util.UUID

/** An OPDS library (Calibre, Calibre-Web, etc.) */
@Serializable
data class OpdsLibrary(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val username: String = "",
    val password: String = "",
) {
    /** Address shown on the card: host name only */
    val host: String get() = runCatching { URI(url).host }.getOrNull() ?: url
}

/**
 * Library list, stored in libraries.json in the app-private directory (includes credentials, never leaves the app)
 * On first launch, migrates the old single-server setting into the first library
 */
class LibraryRepository(private val file: File, settings: SettingsRepository) {

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private val _libraries = MutableStateFlow(load(settings))
    val libraries: StateFlow<List<OpdsLibrary>> = _libraries.asStateFlow()

    fun find(id: String) = _libraries.value.firstOrNull { it.id == id }

    /** Create or update */
    suspend fun save(library: OpdsLibrary) = update { list ->
        if (list.any { it.id == library.id }) list.map { if (it.id == library.id) library else it } else list + library
    }

    suspend fun delete(id: String) = update { list -> list.filterNot { it.id == id } }

    private suspend fun update(transform: (List<OpdsLibrary>) -> List<OpdsLibrary>) = mutex.withLock {
        val list = transform(_libraries.value)
        withContext(Dispatchers.IO) { write(list) }
        _libraries.value = list
    }

    private fun write(list: List<OpdsLibrary>) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(list))
    }

    private fun load(settings: SettingsRepository): List<OpdsLibrary> {
        if (file.exists()) {
            return runCatching { json.decodeFromString<List<OpdsLibrary>>(file.readText()) }.getOrDefault(emptyList())
        }
        val migrated = listOfNotNull(settings.legacyServer())
        runCatching { write(migrated) }
        return migrated
    }
}
