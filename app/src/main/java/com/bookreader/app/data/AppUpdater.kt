package com.bookreader.app.data

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.bookreader.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/** A newer release on GitHub, with the APK built for this phone's CPU */
data class AppUpdate(val version: String, val apkUrl: String, val size: Long)

/**
 * Checks GitHub Releases for a newer version, downloads its APK and hands it to the system installer.
 * Releases are signed with the same key, so they install over the current app
 */
class AppUpdater(private val client: OkHttpClient, private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val dir get() = File(context.cacheDir, "updates")

    /** The newest release if it is newer than this build and has an APK for this phone; null when up to date */
    suspend fun check(): AppUpdate? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(LATEST_URL).header("Accept", "application/vnd.github+json").build()
        val release = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            json.decodeFromString<Release>(response.body!!.string())
        }
        val version = release.tag.removePrefix("v")
        if (!isNewer(version, BuildConfig.VERSION_NAME)) return@withContext null
        val asset = pickAsset(release.assets.map { it.name }, Build.SUPPORTED_ABIS.toList())
            ?.let { name -> release.assets.first { it.name == name } }
            ?: return@withContext null
        AppUpdate(version, asset.url, asset.size)
    }

    /** Downloads the APK into the cache (replacing older downloads), reporting progress 0..1 */
    suspend fun download(update: AppUpdate, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        dir.deleteRecursively()
        dir.mkdirs()
        val file = File(dir, "BookReader-${update.version}.apk")
        val part = File(dir, file.name + ".part")
        client.newCall(Request.Builder().url(update.apkUrl).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body!!
            val total = body.contentLength().takeIf { it > 0 } ?: update.size
            body.byteStream().use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        ensureActive() // Cancelled from the dialog
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        if (!part.renameTo(file)) throw IOException("Could not save the update")
        file
    }

    /** Opens the system installer; Android asks once to allow installs from this app */
    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    @Serializable
    private data class Release(@SerialName("tag_name") val tag: String, val assets: List<Asset> = emptyList())

    @Serializable
    private data class Asset(val name: String, @SerialName("browser_download_url") val url: String, val size: Long = 0)

    companion object {
        private const val LATEST_URL = "https://api.github.com/repos/kkh66/bookreader/releases/latest"

        /** Compares dotted versions number by number: 0.4.10 > 0.4.9 */
        fun isNewer(remote: String, current: String): Boolean {
            val a = remote.split('.').map { it.toIntOrNull() ?: 0 }
            val b = current.split('.').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val diff = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
                if (diff != 0) return diff > 0
            }
            return false
        }

        /** The APK for the phone's preferred ABI, e.g. BookReader-0.4.8-arm64-v8a.apk */
        fun pickAsset(names: List<String>, abis: List<String>): String? =
            abis.firstNotNullOfOrNull { abi -> names.firstOrNull { it.endsWith("-$abi.apk") } }
    }
}
