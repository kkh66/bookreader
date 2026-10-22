package com.bookreader.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingStyleTest {

    @Test
    fun recognizesFontSignatures() {
        assertEquals("ttf", FontRepository.fontExtension(byteArrayOf(0, 1, 0, 0)))
        assertEquals("ttf", FontRepository.fontExtension("true".toByteArray()))
        assertEquals("otf", FontRepository.fontExtension("OTTO".toByteArray()))
        assertEquals("woff", FontRepository.fontExtension("wOFF".toByteArray()))
        assertEquals("woff2", FontRepository.fontExtension("wOF2".toByteArray()))
    }

    @Test
    fun rejectsOtherFiles() {
        // Font collections (.ttc) can't be loaded through @font-face
        assertNull(FontRepository.fontExtension("ttcf".toByteArray()))
        assertNull(FontRepository.fontExtension(byteArrayOf(0x50, 0x4B, 0x03, 0x04)))
        assertNull(FontRepository.fontExtension(byteArrayOf(0, 1)))
    }

    @Test
    fun spacingOverridesPublisherStyles() {
        assertFalse(ReadingStyle().overridesPublisherStyles)
        assertFalse(ReadingStyle(fontSize = 1.5, fontFamily = ReadingStyle.SERIF).overridesPublisherStyles)
        assertTrue(ReadingStyle(lineHeight = 1.8).overridesPublisherStyles)
        assertTrue(ReadingStyle(paragraphSpacing = 0.5).overridesPublisherStyles)
    }
}
