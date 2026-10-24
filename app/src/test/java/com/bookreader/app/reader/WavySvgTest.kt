package com.bookreader.app.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.net.URLDecoder

class WavySvgTest {

    @Test
    fun urlIsSafeInsideAnXhtmlAttribute() {
        val url = wavySvg(0xFF4FA3F7.toInt())
        // The template's style attribute is double-quoted and parsed as XML on XHTML pages
        listOf('<', '>', '"', '&', '#').forEach { assertFalse("contains $it", it in url) }
    }

    @Test
    fun urlDecodesToTheWaveInTheTint() {
        val svg = URLDecoder.decode(wavySvg(0xFF4FA3F7.toInt()).substringAfter(','), "UTF-8")
        assertEquals(
            "<svg xmlns='http://www.w3.org/2000/svg' width='8' height='4'>" +
                "<path d='M0 3 Q2 0 4 3 T8 3' fill='none' stroke='#4FA3F7' stroke-width='1.2'/></svg>",
            svg,
        )
    }
}
