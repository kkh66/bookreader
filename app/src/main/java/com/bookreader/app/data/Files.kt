package com.bookreader.app.data

import android.util.AtomicFile
import java.io.File
import java.io.IOException

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
