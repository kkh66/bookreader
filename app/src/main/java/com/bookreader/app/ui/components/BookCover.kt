package com.bookreader.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.absoluteValue

const val COVER_RATIO = 0.7f

private val coverCache = LruCache<String, ImageBitmap>(48)

/** Book cover: shows the extracted cover image if there is one, otherwise a cover generated from the title */
@Composable
fun BookCover(title: String, author: String, coverPath: String?, modifier: Modifier = Modifier) {
    // State is keyed on the path, so recycled list items don't keep the previous book's cover
    val bitmap = remember(coverPath) { mutableStateOf(coverPath?.let { coverCache.get(it) }) }
    LaunchedEffect(coverPath) {
        if (coverPath != null && bitmap.value == null) {
            bitmap.value = withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(coverPath)?.asImageBitmap()?.also { coverCache.put(coverPath, it) }
            }
        }
    }
    CoverFrame(title, author, bitmap.value, modifier)
}

/**
 * Remote cover (e.g. a Calibre library thumbnail), downloaded and decoded by [load]; shows the generated cover until it loads
 */
@Composable
fun RemoteCover(
    title: String,
    author: String,
    url: String?,
    load: suspend (String) -> Bitmap?,
    modifier: Modifier = Modifier,
) {
    val image = remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        if (url != null) image.value = load(url)?.asImageBitmap()
    }
    CoverFrame(title, author, image.value, modifier)
}

/** Cover frame: rounded corners + spine shadow on the left; uses the generated cover when there is no image */
@Composable
fun CoverFrame(title: String, author: String, image: ImageBitmap?, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(6.dp))) {
        if (image != null) {
            Image(image, title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            GeneratedCover(title, author)
        }
        // Spine shadow on the left
        Box(
            Modifier.fillMaxHeight().width(6.dp)
                .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent))),
        )
    }
}

private val palette = listOf(
    Color(0xFF26272B),
    Color(0xFF3B5A73),
    Color(0xFFB5725A),
    Color(0xFF5E7462),
    Color(0xFF5B4A63),
)

private fun isCjk(c: Char) = Character.UnicodeScript.of(c.code).let {
    it == Character.UnicodeScript.HAN || it == Character.UnicodeScript.HIRAGANA || it == Character.UnicodeScript.KATAKANA
}

/** Generated cover: solid background + title (short Chinese titles set vertically) + author + moon and wave decorations */
@Composable
private fun GeneratedCover(title: String, author: String) {
    val color = palette[title.hashCode().absoluteValue % palette.size]
    val ink = Color(0xFFF4F1E8)
    BoxWithConstraints(Modifier.fillMaxSize().background(color)) {
        val unit = maxWidth.value
        val vertical = title.length <= 6 && title.all(::isCjk)
        // Moon
        Box(
            Modifier.align(Alignment.TopEnd).offset(x = (-unit * 0.14f).dp, y = (unit * 0.2f).dp)
                .size((unit * 0.17f).dp).clip(CircleShape).background(ink.copy(alpha = 0.9f)),
        )
        // Wave lines at the bottom
        Canvas(Modifier.fillMaxSize()) {
            repeat(3) { i ->
                val y = size.height * (0.78f + i * 0.06f)
                val path = Path().apply {
                    moveTo(0f, y)
                    cubicTo(size.width * 0.3f, y - size.height * 0.06f, size.width * 0.6f, y + size.height * 0.06f, size.width, y - size.height * 0.02f)
                }
                drawPath(path, ink.copy(alpha = 0.16f), style = Stroke(width = 1.2.dp.toPx()))
            }
        }
        Column(Modifier.padding(start = (unit * 0.14f).dp, top = (unit * 0.14f).dp, end = (unit * 0.3f).dp)) {
            Text(
                if (vertical) title.toList().joinToString("\n") else title,
                color = ink,
                fontSize = (unit * if (vertical) 0.14f else 0.12f).sp,
                lineHeight = (unit * if (vertical) 0.18f else 0.16f).sp,
                fontWeight = FontWeight.Bold,
                maxLines = if (vertical) 6 else 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            author,
            color = ink.copy(alpha = 0.7f),
            fontSize = (unit * 0.07f).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart)
                .padding(start = (unit * 0.14f).dp, end = (unit * 0.1f).dp, bottom = (unit * 0.16f).dp),
        )
    }
}
