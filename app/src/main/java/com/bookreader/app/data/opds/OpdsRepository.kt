package com.bookreader.app.data.opds

import com.bookreader.app.data.OpdsLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InputStream

/**
 * Read-only access to OPDS libraries (Calibre, Calibre-Web, etc.): fetch feeds, download epubs
 */
class OpdsRepository(private val client: OkHttpClient) {

    suspend fun load(library: OpdsLibrary, url: String = library.url): OpdsPage = withContext(Dispatchers.IO) {
        call(library, url).use { OpdsParser.parse(it.body!!.string(), url) }
    }

    /** Downloads and hands the stream to [block]; the connection is closed afterwards */
    suspend fun <T> download(library: OpdsLibrary, url: String, block: suspend (InputStream) -> T): T =
        withContext(Dispatchers.IO) {
            call(library, url).use { block(it.body!!.byteStream()) }
        }

    private fun call(library: OpdsLibrary, url: String): Response {
        val target = url.toHttpUrl()
        val request = Request.Builder().url(target).apply {
            // Send credentials only to this library's own host, so external links in a feed never receive them
            if (library.username.isNotBlank() && target.host == library.url.toHttpUrlOrNull()?.host) {
                header("Authorization", Credentials.basic(library.username, library.password, Charsets.UTF_8))
            }
        }.build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException(
                if (response.code == 401) "认证失败，请检查用户名和密码" else "请求失败: HTTP ${response.code}"
            )
        }
        return response
    }
}
