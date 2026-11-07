package com.devbehindyou.atomicfilemanager.data.archive

import com.devbehindyou.atomicfilemanager.data.preview.ScratchCopies
import com.devbehindyou.atomicfilemanager.domain.usecase.SeekableSpool
import com.devbehindyou.atomicfilemanager.domain.usecase.Spooled
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption

/** [SeekableSpool] backed by a temporary file in [dir] (the app cache), deleted when closed. */
class TempFileSpool(private val dir: File) : SeekableSpool {
    override fun spool(input: InputStream): Spooled {
        dir.mkdirs()
        val temp = ScratchCopies.create(dir, ScratchCopies.ARCHIVE_PREFIX, ".7z")
        try {
            temp.outputStream().use { input.copyTo(it) }
            val channel = Files.newByteChannel(temp.toPath(), StandardOpenOption.READ)
            return object : Spooled {
                override val channel: SeekableByteChannel = channel

                override fun close() {
                    try {
                        channel.close()
                    } finally {
                        temp.delete()
                    }
                }
            }
        } catch (e: IOException) {
            temp.delete()
            throw e
        }
    }
}
