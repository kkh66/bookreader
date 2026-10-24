package com.bookreader.app.data

import android.content.Context
import androidx.core.content.ContextCompat
import com.bookreader.app.R
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A font the user imported; [family] is the CSS font-family name it's registered under in the reader */
@Serializable
data class UserFont(val id: String, val name: String, val fileName: String) {
    val family: String get() = "BookReader-$id"
}

/**
 * Imported fonts: files in [dir], display names in dir/fonts.json
 * No fonts ship with the app: users bring their own (licensing), and CJK fonts are 10+ MB each
 */
class FontRepository(private val dir: File, private val context: Context) {
    private val strings get() = ContextCompat.getContextForLanguage(context).resources


    private val json = Json { ignoreUnknownKeys = true }
    private val indexFile = File(dir, "fonts.json")
    private val mutex = Mutex()

    private val _fonts = MutableStateFlow(load())
    val fonts: StateFlow<List<UserFont>> = _fonts.asStateFlow()

    fun file(font: UserFont) = File(dir, font.fileName)

    /** Copies a font in; throws for anything that isn't a TrueType / OpenType / WOFF font */
    suspend fun import(input: InputStream, name: String): UserFont {
        val id = UUID.randomUUID().toString()
        val file = withContext(Dispatchers.IO) {
            dir.mkdirs()
            val temp = File(dir, "$id.tmp")
            try {
                input.use { src -> temp.outputStream().use { src.copyTo(it) } }
                // readNBytes needs API 33, so read the 4-byte signature by hand
                val header = ByteArray(4)
                val read = temp.inputStream().use { it.read(header) }
                if (read < header.size) throw IOException(strings.getString(R.string.unsupported_font))
                val extension = fontExtension(header) ?: throw IOException(strings.getString(R.string.unsupported_font))
                File(dir, "$id.$extension").also { if (!temp.renameTo(it)) throw IOException(strings.getString(R.string.font_save_failed)) }
            } finally {
                temp.delete()
            }
        }
        val font = UserFont(id, name.trim().ifEmpty { strings.getString(R.string.font) }, file.name)
        update { it + font }
        return font
    }

    suspend fun remove(font: UserFont) {
        update { list -> list.filterNot { it.id == font.id } }
        withContext(Dispatchers.IO) { file(font).delete() }
    }

    private suspend fun update(transform: (List<UserFont>) -> List<UserFont>) = mutex.withLock {
        val list = transform(_fonts.value)
        withContext(Dispatchers.IO) { indexFile.writeAtomically(json.encodeToString(list)) }
        _fonts.value = list
    }

    private fun load(): List<UserFont> =
        indexFile.readAtomically { json.decodeFromString<List<UserFont>>(it) }.orEmpty()

    companion object {
        /** File extension from a font file's first 4 bytes, or null if it isn't a font the reader's WebView can load */
        fun fontExtension(header: ByteArray): String? {
            if (header.size < 4) return null
            val tag = String(header, 0, 4, Charsets.ISO_8859_1)
            return when {
                tag == "\u0000\u0001\u0000\u0000" || tag == "true" -> "ttf"
                tag == "OTTO" -> "otf"
                tag == "wOFF" -> "woff"
                tag == "wOF2" -> "woff2"
                else -> null
            }
        }
    }
}
