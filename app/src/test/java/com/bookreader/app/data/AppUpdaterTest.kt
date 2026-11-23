package com.bookreader.app.data

import com.bookreader.app.data.AppUpdater.Companion.isNewer
import com.bookreader.app.data.AppUpdater.Companion.pickAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdaterTest {

    @Test
    fun comparesVersionsNumerically() {
        assertTrue(isNewer("0.4.8", "0.4.7"))
        assertTrue(isNewer("0.4.10", "0.4.9"))
        assertTrue(isNewer("1.0", "0.9.9"))
        assertFalse(isNewer("0.4.7", "0.4.7"))
        assertFalse(isNewer("0.4.7", "0.4.7.0"))
        assertFalse(isNewer("0.4.6", "0.4.7"))
    }

    @Test
    fun picksTheApkForThePreferredAbi() {
        val names = listOf("BookReader-0.4.8-armeabi-v7a.apk", "BookReader-0.4.8-arm64-v8a.apk", "BookReader-0.4.8-x86_64.apk")
        assertEquals("BookReader-0.4.8-arm64-v8a.apk", pickAsset(names, listOf("arm64-v8a", "armeabi-v7a")))
        assertEquals("BookReader-0.4.8-armeabi-v7a.apk", pickAsset(names, listOf("armeabi-v7a")))
        // "x86" must not match the x86_64 APK
        assertNull(pickAsset(names, listOf("x86")))
    }
}
