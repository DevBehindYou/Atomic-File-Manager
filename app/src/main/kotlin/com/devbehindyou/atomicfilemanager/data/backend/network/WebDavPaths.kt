package com.devbehindyou.atomicfilemanager.data.backend.network

/** Canonical URI paths, preserving encoded separators and already encoded provider IDs. */
internal object WebDavPaths {
    private const val HEX = "0123456789ABCDEF"
    private const val UNRESERVED = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~"
    private const val PATH_CHARACTERS = "$UNRESERVED/:@!$&'()*+,;="

    fun sameResource(
        first: String,
        second: String,
    ): Boolean = encode(first).trimEnd('/') == encode(second).trimEnd('/')

    /**
     * Paths can come from a user-entered root or an encoded DAV href. Retain valid percent
     * escapes, normalizing only unreserved ASCII. In particular %2F and %25 are never decoded:
     * an encoded separator remains part of its original segment, and %252F stays distinct.
     */
    fun encode(path: String): String {
        val bytes = path.toByteArray(Charsets.UTF_8)
        return buildString {
            var index = 0
            while (index < bytes.size) {
                val byte = bytes[index].toInt() and 0xff
                val high = bytes.getOrNull(index + 1)?.toInt()?.toChar()?.digitToIntOrNull(16)
                val low = bytes.getOrNull(index + 2)?.toInt()?.toChar()?.digitToIntOrNull(16)
                if (byte == '%'.code && high != null && low != null) {
                    val decoded = high * 16 + low
                    if (decoded.toChar() in UNRESERVED) {
                        append(decoded.toChar())
                    } else {
                        append('%').append(HEX[high]).append(HEX[low])
                    }
                    index += 3
                } else {
                    if (byte.toChar() in PATH_CHARACTERS) {
                        append(byte.toChar())
                    } else {
                        append('%').append(HEX[byte ushr 4]).append(HEX[byte and 0xf])
                    }
                    index++
                }
            }
        }
    }
}
