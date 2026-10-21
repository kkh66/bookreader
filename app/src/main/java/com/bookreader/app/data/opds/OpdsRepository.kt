package com.bookreader.app.data.opds

import com.bookreader.app.data.SettingsRepository
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
 * Read-only access to an OPDS server (Calibre / Calibre-Web): fetch feeds, download epubs
 */
class OpdsRepository(
    private val client: OkHttpClient,
    private val settings: SettingsRepository,
) {

    val rootUrl: String get() = settings.server.value.url

    suspend fun load(url: String): OpdsPage = withContext(Dispatchers.IO) {
        call(url).use { OpdsParser.parse(it.body!!.string(), url) }
    }

    /** Downloads and hands the stream to [block]; the connection is closed afterwards */
    suspend fun <T> download(url: String, block: suspend (InputStream) -> T): T =
        withContext(Dispatchers.IO) {
            call(url).use { block(it.body!!.byteStream()) }
        }

    private fun call(url: String): Response {
        val server = settings.server.value
        val target = url.toHttpUrl()
        val request = Request.Builder().url(target).apply {
            // Send credentials only to the configured server, so external links in a feed never receive them
            if (server.username.isNotBlank() && target.host == server.url.toHttpUrlOrNull()?.host) {
                header("Authorization", Credentials.basic(server.username, server.password, Charsets.UTF_8))
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
