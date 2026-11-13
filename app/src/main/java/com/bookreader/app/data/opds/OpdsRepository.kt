package com.bookreader.app.data.opds

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.content.ContextCompat
import com.bookreader.app.R
import com.bookreader.app.data.OpdsLibrary
import com.bookreader.app.data.decodeScaledBitmap
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Read-only access to OPDS libraries (Calibre, Calibre-Web, etc.): fetch feeds, download epubs
 */
class OpdsRepository(private val client: OkHttpClient, private val context: Context) {
    private val strings get() = ContextCompat.getContextForLanguage(context).resources


    suspend fun load(library: OpdsLibrary, url: String = library.url): OpdsPage = withContext(Dispatchers.IO) {
        call(library, url).use { OpdsParser.parse(it.body!!.string(), url, strings.getString(R.string.invalid_opds_feed)) }
    }

    /** Downloads and hands the stream to [block]; the connection is closed afterwards */
    suspend fun <T> download(library: OpdsLibrary, url: String, block: suspend (InputStream) -> T): T =
        withContext(Dispatchers.IO) {
            call(library, url).use { block(it.body!!.byteStream()) }
        }

    /** Downloads a cover/thumbnail (also with the library's credentials), downsampled to [maxWidth]x[maxHeight]; null on failure */
    suspend fun image(library: OpdsLibrary, url: String, maxWidth: Int, maxHeight: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching { call(library, url).use { it.body!!.bytes() } }.getOrNull()
                ?.let { decodeScaledBitmap(it, maxWidth, maxHeight) }
        }

    private fun call(library: OpdsLibrary, url: String): Response {
        val target = url.toHttpUrl()
        val request = Request.Builder().url(target).apply {
            // Send credentials only to this library's own host, so external links in a feed never receive them
            if (library.username.isNotBlank() && target.host == library.url.toHttpUrlOrNull()?.host) {
                header("Authorization", Credentials.basic(library.username, library.password, Charsets.UTF_8))
            }
        }.build()
        val response = try {
            client.newCall(request).execute()
        } catch (e: SocketTimeoutException) {
            // Without local network access Android 17+ silently drops LAN traffic: name the cause instead of a bare timeout
            throw if (context.localNetworkBlocked()) IOException(strings.getString(R.string.local_network_permission_needed), e) else e
        }
        if (!response.isSuccessful) {
            response.close()
            throw IOException(
                if (response.code == 401) strings.getString(R.string.authentication_failed) else strings.getString(R.string.http_failed, response.code)
            )
        }
        return response
    }
}

/**
 * Android 17+ drops traffic to LAN addresses (connections just time out) until the user grants ACCESS_LOCAL_NETWORK;
 * true while that permission is missing
 */
fun Context.localNetworkBlocked(): Boolean =
    Build.VERSION.SDK_INT >= 37 &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED
