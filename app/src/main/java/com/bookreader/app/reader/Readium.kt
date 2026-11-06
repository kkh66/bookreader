package com.bookreader.app.reader

import android.content.Context
import androidx.core.content.ContextCompat
import com.bookreader.app.R
import com.bookreader.app.data.BookFormat
import java.io.File
import java.io.IOException
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.data.Container
import org.readium.r2.shared.util.file.FileResource
import org.readium.r2.shared.util.format.Specification
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.shared.util.resource.Resource
import org.readium.r2.shared.util.resource.map
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

/**
 * Readium components for opening EPUB, PDF and CBZ (following the official Readium getting-started guide)
 * PDFs are parsed and rendered with the PDFium adapter
 */
class Readium(private val context: Context) {
    private val strings get() = ContextCompat.getContextForLanguage(context).resources


    private val httpClient = DefaultHttpClient()
    private val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
    private val opener = PublicationOpener(
        DefaultPublicationParser(context, httpClient, assetRetriever, pdfFactory = PdfiumDocumentFactory(context))
    )

    /** The book format by the file's content, or null for anything else; a download's file name or MIME type can't be trusted */
    suspend fun sniff(file: File): BookFormat? {
        val asset = assetRetriever.retrieve(file).getOrElse { return null }
        return try {
            val format = asset.format
            when {
                format.conformsTo(Specification.Epub) -> BookFormat.EPUB
                format.conformsTo(Specification.Pdf) -> BookFormat.PDF
                // Comics packed as RAR (CBR) are recognized too, but Readium can't open them
                format.conformsTo(Specification.InformalComic) && format.conformsTo(Specification.Zip) -> BookFormat.CBZ
                else -> null
            }
        } finally {
            asset.close()
        }
    }

    /** [fonts] are added to the publication as extra resources so the reader's stylesheet can load them */
    suspend fun open(file: File, fonts: List<FontFile> = emptyList()): Publication {
        val asset = assetRetriever.retrieve(file)
            .getOrElse { throw IOException(strings.getString(R.string.file_read_error, it.message)) }
        return opener.open(asset, allowUserInteraction = false, onCreatePublication = { addFonts(fonts); popUpFootnotes() })
            .getOrElse {
                asset.close()
                throw IOException(strings.getString(R.string.file_parse_error, it.message))
            }
    }
}

/** A font file served inside the publication at [href] */
class FontFile(val href: Url, val file: File) {
    val mediaType: MediaType = when (file.extension) {
        "otf" -> MediaType.OTF
        "woff" -> MediaType.WOFF
        "woff2" -> MediaType("font/woff2")!!
        else -> MediaType.TTF
    }
}

/**
 * The reader only loads fonts from the app's assets or from the publication itself,
 * so imported fonts are listed as publication resources and read from their files
 */
private fun Publication.Builder.addFonts(fonts: List<FontFile>) {
    if (fonts.isEmpty()) return
    manifest = manifest.copy(resources = manifest.resources + fonts.map { Link(it.href, mediaType = it.mediaType) })
    container = FontContainer(container, fonts.associate { it.href to it.file })
}

/** Chapters are read through [markFootnotes], so footnotes marked only by a class pop up too */
private fun Publication.Builder.popUpFootnotes() {
    container = FootnoteContainer(container)
}

private class FootnoteContainer(private val base: Container<Resource>) : Container<Resource> by base {
    override fun get(url: Url): Resource? = base[url]?.let { resource ->
        if (url.path?.substringAfterLast('.')?.lowercase() in HTML_EXTENSIONS) {
            resource.map { bytes -> Try.success(markFootnotes(bytes)) }
        } else {
            resource
        }
    }

    private companion object {
        val HTML_EXTENSIONS = setOf("xhtml", "html", "htm")
    }
}

private class FontContainer(
    private val base: Container<Resource>,
    private val fonts: Map<Url, File>,
) : Container<Resource> {
    override val sourceUrl get() = base.sourceUrl
    override val entries: Set<Url> = base.entries + fonts.keys
    override fun get(url: Url): Resource? = fonts[url]?.let { FileResource(it) } ?: base[url]
    override fun close() = base.close()
}
