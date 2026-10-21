package com.bookreader.app.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpdsAddressTest {

    @Test
    fun parsesAndRebuildsLanAddress() {
        val address = OpdsAddress.parse("http://192.168.1.100:8083/opds")!!
        assertEquals(OpdsAddress(https = false, host = "192.168.1.100", port = "8083", path = "/opds"), address)
        assertEquals("http://192.168.1.100:8083/opds", address.toUrl())
    }

    @Test
    fun httpsDomainWithoutPortKeepsPathAndQuery() {
        val address = OpdsAddress.parse("HTTPS://calibre.example.com/opds/new?page=2")!!
        assertTrue(address.https)
        assertEquals("calibre.example.com", address.host)
        assertEquals("", address.port)
        assertEquals("https://calibre.example.com/opds/new?page=2", address.toUrl())
    }

    @Test
    fun savedUrlWithoutPathStaysWithoutPath() {
        assertEquals("http://nas:8080", OpdsAddress.parse("http://nas:8080")!!.toUrl())
    }

    @Test
    fun ipv6GetsBrackets() {
        val address = OpdsAddress.parse("http://[fe80::1]:8083/opds")!!
        assertEquals("fe80::1", address.host)
        assertEquals("8083", address.port)
        assertNull(address.hostError)
        assertEquals("http://[fe80::1]:8083/opds", address.toUrl())
    }

    @Test
    fun dropsCredentialsInUrl() {
        assertEquals("nas", OpdsAddress.parse("http://user:pw@nas:8083/opds")!!.host)
    }

    @Test
    fun rejectsNonHttpOrEmptyHost() {
        assertNull(OpdsAddress.parse("ftp://nas/opds"))
        assertNull(OpdsAddress.parse("http:///opds"))
        assertNull(OpdsAddress.parse("192.168.1.100"))
    }

    @Test
    fun pasteWithoutSchemeUsesCurrentChoiceAndDefaultPath() {
        assertEquals(
            OpdsAddress(https = true, host = "calibre.example.com", port = "", path = "/opds"),
            OpdsAddress.fromPaste("calibre.example.com", https = true),
        )
        assertEquals(
            OpdsAddress(https = false, host = "192.168.1.100", port = "8083", path = "/opds"),
            OpdsAddress.fromPaste(" http://192.168.1.100:8083/ ", https = true),
        )
    }

    @Test
    fun pathGetsLeadingSlash() {
        assertEquals("http://nas/opds", OpdsAddress(host = "nas", path = "opds").toUrl())
    }

    @Test
    fun validation() {
        assertFalse(OpdsAddress().valid)
        assertTrue(OpdsAddress(host = "nas").valid)
        assertEquals("端口请填在右边", OpdsAddress(host = "nas:8083").hostError)
        assertEquals("这里只填域名或 IP", OpdsAddress(host = "nas/opds").hostError)
        assertTrue(OpdsAddress(host = "nas", port = "70000").portError)
        assertTrue(OpdsAddress(host = "nas", port = "0").portError)
        assertFalse(OpdsAddress(host = "nas", port = "").portError)
    }
}
