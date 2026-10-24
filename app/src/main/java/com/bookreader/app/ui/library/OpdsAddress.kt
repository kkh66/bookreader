package com.bookreader.app.ui.library

/**
 * A library URL split into form fields: protocol / address (domain or IP) / port / path
 * Storage is still a single full URL: split for editing, joined on save, so existing libraries need no migration; plain Kotlin for easy unit testing
 */
data class OpdsAddress(
    val https: Boolean = false,
    val host: String = "",
    /** Empty means the protocol's default port (80 / 443) */
    val port: String = "",
    val path: String = DEFAULT_PATH,
) {
    /** Hint when the address field contains something it shouldn't; empty is not an error */
    val hostError: HostError?
        get() {
            val host = host.trim()
            return when {
                host.any { it.isWhitespace() || it in "/?#@" } -> HostError.Address
                ':' in host && !host.isIpv6() -> HostError.Port
                else -> null
            }
        }

    enum class HostError { Address, Port }

    val portError: Boolean get() = port.isNotEmpty() && port.toIntOrNull() !in 1..65535

    val valid: Boolean get() = host.isNotBlank() && hostError == null && !portError

    fun toUrl(): String {
        val host = host.trim().let { if (it.isIpv6() && !it.startsWith("[")) "[$it]" else it }
        val port = port.trim().let { if (it.isEmpty()) "" else ":$it" }
        val path = path.trim().let { if (it.isEmpty() || it.startsWith("/")) it else "/$it" }
        return "${if (https) "https" else "http"}://$host$port$path"
    }

    companion object {
        /** Both Calibre-Web and the Calibre content server serve OPDS at this path */
        const val DEFAULT_PATH = "/opds"

        /** Splits a full URL, keeping the path as is; null if it isn't an http(s) URL or has no host */
        fun parse(url: String): OpdsAddress? {
            val text = url.trim()
            val https = when {
                text.startsWith("https://", ignoreCase = true) -> true
                text.startsWith("http://", ignoreCase = true) -> false
                else -> return null
            }
            val rest = text.substringAfter("://")
            val authority = rest.takeWhile { it != '/' && it != '?' && it != '#' }
            // Drop any credentials embedded in the URL; username and password have their own fields
            val hostPort = authority.substringAfterLast('@')
            val (host, port) = when {
                hostPort.startsWith("[") -> {
                    val end = hostPort.indexOf(']').takeIf { it > 0 } ?: return null
                    hostPort.substring(1, end) to hostPort.substring(end + 1).removePrefix(":")
                }
                hostPort.count { it == ':' } == 1 -> hostPort.substringBefore(':') to hostPort.substringAfter(':')
                else -> hostPort to ""
            }
            if (host.isBlank()) return null
            return OpdsAddress(https, host, port, rest.drop(authority.length))
        }

        /**
         * A full URL pasted into the address field: split it into the fields
         * Without a scheme, keep the current selection; without a path (usually a library home page copied from a browser), use /opds
         */
        fun fromPaste(text: String, https: Boolean): OpdsAddress? {
            val trimmed = text.trim()
            val parsed = parse(if ("://" in trimmed) trimmed else "${if (https) "https" else "http"}://$trimmed")
                ?: return null
            return parsed.copy(path = parsed.path.takeUnless { it.isBlank() || it == "/" } ?: DEFAULT_PATH)
        }

        /** Two or more colons means an IPv6 address */
        private fun String.isIpv6() = removePrefix("[").removeSuffix("]").count { it == ':' } >= 2
    }
}
