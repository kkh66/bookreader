package com.bookreader.app.data

import android.content.Context
import androidx.core.content.ContextCompat
import com.bookreader.app.R
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Where the app keeps its data, in the app-private files directory */
class AppFiles(val root: File) {
    val books = File(root, "books")
    val fonts = File(root, "fonts")
    val stats = File(root, "stats.json")
    val libraries = File(root, "libraries.json")
}

@Serializable
data class BackupManifest(
    val version: Int,
    val createdAt: Long,
    val bookCount: Int,
    /** Book and font files are in the archive */
    val withFiles: Boolean,
)

/** Size of a backup without book files ([data]), and of the book and font files it can take along */
data class BackupSize(val data: Long, val files: Long)

/**
 * Backup archive (zip) for moving to a new phone: shelf (progress, bookmarks, highlights, groups), covers, reading stats,
 * libraries without their passwords, and settings; book and font files only when asked for.
 * Restoring replaces all of it on this device; every repository keeps its data in memory, so the app restarts afterwards
 */
class BackupRepository(
    private val files: AppFiles,
    private val books: BookRepository,
    private val fonts: FontRepository,
    private val stats: StatsRepository,
    private val libraries: LibraryRepository,
    private val settings: SettingsRepository,
    private val context: Context,
) {
    private val strings get() = ContextCompat.getContextForLanguage(context).resources
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun size(): BackupSize = withContext(Dispatchers.IO) {
        val shelf = books.books.value
        BackupSize(
            data = shelf.sumOf { books.cover(it)?.length() ?: 0L } +
                File(files.books, BOOK_INDEX).length() + files.stats.length() + files.libraries.length(),
            // length() is 0 for a book whose file isn't here
            files = shelf.sumOf { books.file(it).length() } + fonts.fonts.value.sumOf { fonts.file(it).length() },
        )
    }

    suspend fun export(output: OutputStream, withFiles: Boolean) = withContext(Dispatchers.IO) {
        val shelf = books.books.value
        ZipOutputStream(output.buffered()).use { zip ->
            // Books, covers and fonts are compressed already
            zip.setLevel(Deflater.BEST_SPEED)
            fun putText(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
            fun putFile(name: String, file: File) {
                if (!file.isFile) return
                zip.putNextEntry(ZipEntry(name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            // First, so a restore can say what's in the backup without reading the rest
            putText(MANIFEST, json.encodeToString(BackupManifest(VERSION, System.currentTimeMillis(), shelf.size, withFiles)))
            // Without the files, a local import takes a fingerprint of its file along, to check the file picked to restore it
            val listed = if (withFiles) shelf else shelf.map { book ->
                val file = books.file(book)
                if (book.source != null || book.sha1 != null || !file.isFile) book else book.copy(sha1 = file.sha1())
            }
            putText("$BOOKS/$BOOK_INDEX", json.encodeToString(listed))
            shelf.forEach { book -> books.cover(book)?.let { putFile("$BOOKS/$COVERS/${it.name}", it) } }
            putText(STATS, json.encodeToString(stats.stats.value))
            putText(LIBRARIES, json.encodeToString(libraries.libraries.value.map { it.copy(password = "") }))
            putText(SETTINGS, json.encodeToString(encodePrefs(settings.snapshot())))
            if (withFiles) {
                shelf.forEach { putFile("$BOOKS/${it.fileName}", books.file(it)) }
                putText("$FONTS/$FONT_INDEX", json.encodeToString(fonts.fonts.value))
                fonts.fonts.value.forEach { putFile("$FONTS/${it.fileName}", fonts.file(it)) }
            }
        }
    }

    /** What a backup holds, from its manifest; throws when it isn't a backup this version can restore */
    suspend fun inspect(input: InputStream): BackupManifest = withContext(Dispatchers.IO) {
        ZipInputStream(input.buffered()).use { zip ->
            val entry = runCatching { zip.nextEntry }.getOrNull()
            checked(entry?.takeIf { it.name == MANIFEST }?.let { runCatching { json.decodeFromString<BackupManifest>(zip.readBytes().decodeToString()) }.getOrNull() })
        }
    }

    /**
     * Replaces the shelf, stats, libraries and settings (and the fonts, when the backup has them) with the backup's.
     * The archive is unpacked and read in full first, so a broken one leaves this device's data alone; the caller then restarts the app
     */
    suspend fun restore(input: InputStream) = withContext(Dispatchers.IO) {
        val staging = File(files.root, STAGING).apply { deleteRecursively() }
        try {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    // Only what a backup holds: a crafted entry name can't write outside the staging folder
                    if (entry.isDirectory || !isBackupEntry(entry.name)) continue
                    val target = File(staging, entry.name)
                    target.parentFile!!.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                }
            }
            val manifest = checked(runCatching { json.decodeFromString<BackupManifest>(File(staging, MANIFEST).readText()) }.getOrNull())
            val stagedBooks = File(staging, BOOKS)
            val content = runCatching {
                Staged(
                    shelf = json.decodeFromString<List<Book>>(File(stagedBooks, BOOK_INDEX).readText()),
                    stats = json.encodeToString(File(staging, STATS).takeIf { it.isFile }?.let { json.decodeFromString<ReadingStats>(it.readText()) } ?: ReadingStats()),
                    libraries = File(staging, LIBRARIES).takeIf { it.isFile }?.let { json.decodeFromString<List<OpdsLibrary>>(it.readText()) }.orEmpty(),
                    settings = File(staging, SETTINGS).takeIf { it.isFile }?.let { decodePrefs(json.decodeFromString<Map<String, String>>(it.readText())) }.orEmpty(),
                    fonts = File(staging, "$FONTS/$FONT_INDEX").takeIf { it.isFile }?.let { json.decodeFromString<List<UserFont>>(it.readText()) },
                )
            }.getOrElse { throw IOException(strings.getString(R.string.not_a_backup), it) }
            // Passwords aren't in a backup; restoring on the same phone keeps the ones it has
            val current = libraries.libraries.value.associateBy { it.id }
            val merged = content.libraries.map { library ->
                current[library.id]?.takeIf { it.url == library.url && it.username == library.username }
                    ?.let { library.copy(password = it.password) } ?: library
            }

            // From here on this device's data is replaced
            if (!manifest.withFiles) {
                // Book files already on this device stay (restoring on the same phone)
                content.shelf.forEach { book ->
                    val here = File(files.books, book.fileName)
                    val staged = File(stagedBooks, book.fileName)
                    if (here.isFile && !staged.exists()) here.renameTo(staged)
                }
            }
            files.books.deleteRecursively()
            if (!stagedBooks.renameTo(files.books)) throw IOException(strings.getString(R.string.file_save_failed))
            if (manifest.withFiles) {
                files.fonts.deleteRecursively()
                File(staging, FONTS).takeIf { it.isDirectory }?.renameTo(files.fonts)
            }
            files.stats.writeAtomically(content.stats)
            files.libraries.writeAtomically(json.encodeToString(merged))
            val userFonts = (if (manifest.withFiles) content.fonts.orEmpty() else fonts.fonts.value).mapTo(HashSet()) { it.family }
            settings.replaceAll(content.settings, userFonts, passwordNotice = merged.any { it.username.isNotBlank() && it.password.isBlank() })
        } finally {
            staging.deleteRecursively()
        }
    }

    private class Staged(
        val shelf: List<Book>,
        val stats: String,
        val libraries: List<OpdsLibrary>,
        val settings: Map<String, Any>,
        val fonts: List<UserFont>?,
    )

    private fun checked(manifest: BackupManifest?): BackupManifest {
        manifest ?: throw IOException(strings.getString(R.string.not_a_backup))
        if (manifest.version > VERSION) throw IOException(strings.getString(R.string.backup_too_new))
        return manifest
    }
}

/** Format version; a backup from a newer version is refused */
private const val VERSION = 1
private const val MANIFEST = "manifest.json"
private const val STATS = "stats.json"
private const val LIBRARIES = "libraries.json"
private const val SETTINGS = "settings.json"
private const val BOOKS = "books"
private const val COVERS = "covers"
private const val FONTS = "fonts"
/** Index file names inside the books / fonts folders, as [BookRepository] and [FontRepository] keep them */
private const val BOOK_INDEX = "books.json"
private const val FONT_INDEX = "fonts.json"
private const val STAGING = "restore"

/** Entry names a backup may hold: the top-level files, then files directly in books (index, book files), books/covers and fonts */
internal fun isBackupEntry(name: String): Boolean {
    val parts = name.split('/')
    if (parts.any { it.isEmpty() || it == "." || it == ".." || '\\' in it }) return false
    return when (parts.size) {
        1 -> parts[0] in setOf(MANIFEST, STATS, LIBRARIES, SETTINGS)
        2 -> parts[0] == BOOKS || parts[0] == FONTS
        3 -> parts[0] == BOOKS && parts[1] == COVERS
        else -> false
    }
}

/** Settings as "type:value" strings, so a restore puts back an Int as an Int and a Float as a Float */
internal fun encodePrefs(values: Map<String, Any>): Map<String, String> = values.mapNotNull { (key, value) ->
    val type = when (value) {
        is Boolean -> "b"
        is Int -> "i"
        is Long -> "l"
        is Float -> "f"
        is String -> "s"
        else -> null
    }
    type?.let { key to "$it:$value" }
}.toMap()

internal fun decodePrefs(values: Map<String, String>): Map<String, Any> = values.mapNotNull { (key, raw) ->
    val value = raw.substringAfter(':')
    when (raw.substringBefore(':')) {
        "b" -> value.toBooleanStrictOrNull()
        "i" -> value.toIntOrNull()
        "l" -> value.toLongOrNull()
        "f" -> value.toFloatOrNull()
        "s" -> value
        else -> null
    }?.let { key to it }
}.toMap()
