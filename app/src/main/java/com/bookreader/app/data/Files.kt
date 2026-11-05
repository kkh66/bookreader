package com.bookreader.app.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest

/**
 * Atomic write: write to a temp file, sync it to disk, then replace, so being killed or losing power midway never leaves partial content
 */
fun File.writeAtomically(text: String) {
    parentFile?.mkdirs()
    val file = AtomicFile(this)
    val out = file.startWrite()
    try {
        out.write(text.toByteArray())
        file.finishWrite(out)
    } catch (e: Exception) {
        file.failWrite(out)
        throw e
    }
}

/**
 * Reads and parses a file written by [writeAtomically]; returns null if the file doesn't exist.
 * On a parse failure the original is renamed to .bad and null is returned, so the next write can't overwrite the only copy of the data
 */
fun <T> File.readAtomically(decode: (String) -> T): T? {
    val text = try {
        AtomicFile(this).readFully().toString(Charsets.UTF_8)
    } catch (e: IOException) {
        return null
    }
    return runCatching { decode(text) }.getOrElse {
        renameTo(File("$path.bad"))
        null
    }
}

/** SHA-1 of the bytes read through [input], as lowercase hex; [copy] reads (and closes) the stream */
inline fun sha1Of(input: InputStream, copy: (InputStream) -> Unit): String {
    val digest = MessageDigest.getInstance("SHA-1")
    DigestInputStream(input, digest).use(copy)
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun File.sha1(): String = sha1Of(inputStream()) { stream ->
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (stream.read(buffer) >= 0) Unit
}

/** File name of a picked document, e.g. 方正书宋_GBK.ttf; null when the provider doesn't say (lastPathSegment is often just an id) */
fun ContentResolver.displayName(uri: Uri): String? = runCatching {
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()?.takeIf { it.isNotBlank() }
