package com.bookreader.app.reader

import android.content.Context
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File
import java.io.IOException

/**
 * Readium components for opening EPUBs (following the official Readium getting-started guide)
 */
class Readium(context: Context) {

    private val httpClient = DefaultHttpClient()
    private val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
    private val opener = PublicationOpener(
        DefaultPublicationParser(context, httpClient, assetRetriever, pdfFactory = null)
    )

    suspend fun open(file: File): Publication {
        val asset = assetRetriever.retrieve(file)
            .getOrElse { throw IOException("无法读取文件: ${it.message}") }
        return opener.open(asset, allowUserInteraction = false)
            .getOrElse {
                asset.close()
                throw IOException("无法解析 EPUB: ${it.message}")
            }
    }
}
