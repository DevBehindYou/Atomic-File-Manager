package com.devbehindyou.atomicfilemanager

import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * Hand-built PDF bytes for cases [android.graphics.pdf.PdfDocument] cannot produce: a standard
 * security handler (RC4 40-bit, revision 2) with a non-empty user password, and structurally
 * valid documents with zero pages. Pure JVM so the bytes are deterministic on every API level.
 */
internal object HostilePdfFixtures {
    private val padding =
        intArrayOf(
            0x28, 0xBF, 0x4E, 0x5E, 0x4E, 0x75, 0x8A, 0x41, 0x64, 0x00, 0x4E, 0x56, 0xFF, 0xFA, 0x01, 0x08,
            0x2E, 0x2E, 0x00, 0xB6, 0xD0, 0x68, 0x3E, 0x80, 0x2F, 0x0C, 0xA9, 0xFE, 0x64, 0x53, 0x69, 0x7A,
        ).map(Int::toByte).toByteArray()
    private val fileId = ByteArray(16) { (it * 17 + 3).toByte() }
    private const val PERMISSIONS = -4

    /** One blue page that opens only with [userPassword]; the empty password is rejected. */
    fun encrypted(
        userPassword: String = "atomic-user",
        ownerPassword: String = "atomic-owner",
    ): ByteArray {
        val owner = rc4(md5(pad(ownerPassword)).copyOf(5), pad(userPassword))
        val key =
            md5(
                pad(userPassword) + owner + intLe(PERMISSIONS) + fileId,
            ).copyOf(5)
        val user = rc4(key, padding)
        val content = rc4(objectKey(key, 4), BLUE_PAGE.toByteArray(Charsets.ISO_8859_1))
        return write(
            listOf(
                "<< /Type /Catalog /Pages 2 0 R >>".toByteArray(),
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>".toByteArray(),
                PAGE.toByteArray(),
                stream(content),
                (
                    "<< /Filter /Standard /V 1 /R 2 /Length 40 /O <${hex(owner)}> /U <${hex(user)}> " +
                        "/P $PERMISSIONS >>"
                ).toByteArray(),
            ),
            trailerExtra = "/Encrypt 5 0 R /ID [<${hex(fileId)}> <${hex(fileId)}>]",
        )
    }

    /** A well-formed catalog whose page tree is empty. */
    fun zeroPages(): ByteArray =
        write(
            listOf(
                "<< /Type /Catalog /Pages 2 0 R >>".toByteArray(),
                "<< /Type /Pages /Kids [] /Count 0 >>".toByteArray(),
            ),
        )

    /** An unencrypted one-page twin of [encrypted], proving the fixture writer itself is valid. */
    fun plain(): ByteArray =
        write(
            listOf(
                "<< /Type /Catalog /Pages 2 0 R >>".toByteArray(),
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>".toByteArray(),
                PAGE.toByteArray(),
                stream(BLUE_PAGE.toByteArray(Charsets.ISO_8859_1)),
            ),
        )

    private const val PAGE = "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 280] /Contents 4 0 R /Resources << >> >>"
    private const val BLUE_PAGE = "0 0 1 rg 0 0 200 280 re f\n"

    private fun stream(bytes: ByteArray) =
        "<< /Length ${bytes.size} >>\nstream\n".toByteArray() + bytes + "\nendstream".toByteArray()

    private fun write(
        objects: List<ByteArray>,
        trailerExtra: String = "",
    ): ByteArray {
        val output = ByteArrayOutputStream()
        output.write("%PDF-1.4\n%âãÏÓ\n".toByteArray(Charsets.ISO_8859_1))
        val offsets =
            objects.mapIndexed { index, body ->
                val offset = output.size()
                output.write("${index + 1} 0 obj\n".toByteArray())
                output.write(body)
                output.write("\nendobj\n".toByteArray())
                offset
            }
        val xref = output.size()
        output.write("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n".toByteArray())
        for (offset in offsets) output.write("%010d 00000 n \n".format(offset).toByteArray())
        output.write("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R $trailerExtra >>\n".toByteArray())
        output.write("startxref\n$xref\n%%EOF\n".toByteArray())
        return output.toByteArray()
    }

    private fun pad(password: String): ByteArray {
        val bytes = password.toByteArray(Charsets.ISO_8859_1).take(32).toByteArray()
        return bytes + padding.copyOf(32 - bytes.size)
    }

    private fun objectKey(
        key: ByteArray,
        number: Int,
    ): ByteArray =
        md5(key + byteArrayOf(number.toByte(), (number shr 8).toByte(), (number shr 16).toByte(), 0, 0))
            .copyOf(minOf(key.size + 5, 16))

    private fun intLe(value: Int) = ByteArray(4) { (value shr (8 * it)).toByte() }

    private fun md5(bytes: ByteArray): ByteArray = MessageDigest.getInstance("MD5").digest(bytes)

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02X".format(it.toInt() and 0xFF) }

    private fun rc4(
        key: ByteArray,
        input: ByteArray,
    ): ByteArray {
        val state = IntArray(256) { it }
        var j = 0
        for (i in 0 until 256) {
            j = (j + state[i] + (key[i % key.size].toInt() and 0xFF)) and 0xFF
            state[i] = state[j].also { state[j] = state[i] }
        }
        var i = 0
        j = 0
        return ByteArray(input.size) { index ->
            i = (i + 1) and 0xFF
            j = (j + state[i]) and 0xFF
            state[i] = state[j].also { state[j] = state[i] }
            (input[index].toInt() xor state[(state[i] + state[j]) and 0xFF]).toByte()
        }
    }
}
