package com.devbehindyou.atomicfilemanager.domain.usecase

import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

data class ArchiveEntryInfo(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val uncompressedSize: Long,
    val compressedSize: Long,
    val modifiedAt: Long,
)

@Singleton
class InspectArchiveUseCase
    @Inject
    constructor(
        /** Random-access copies for 7z; without one, 7z archives can't be listed. */
        private val spool: SeekableSpool? = null,
        private val backendSelector: (FileNodeId) -> StorageBackend,
    ) {
        /** [password] opens an encrypted ZIP; it is used for this call only and not kept. */
        suspend operator fun invoke(
            archiveId: FileNodeId,
            password: CharArray? = null,
        ): FileResult<List<ArchiveEntryInfo>> =
            withContext(Dispatchers.IO) {
                val backend = backendSelector(archiveId)
                val inRes = backend.openInput(archiveId)
                val inProvider =
                    when (inRes) {
                        is FileResult.Success -> inRes.value
                        is FileResult.Failure -> return@withContext FileResult.Failure(inRes.error)
                    }

                val node = (backend.getNode(archiveId) as? FileResult.Success)?.value
                val name = node?.name ?: archiveId.raw.substringAfterLast('/')
                // Anything not recognised by name is read as ZIP, as before.
                val kind = ArchiveFormats.kindOf(name) ?: ArchiveFormats.Kind.ZIP
                val entries = mutableListOf<ArchiveEntryInfo>()
                val browsePaths = HashSet<String>()
                try {
                    inProvider.stream().use { stream ->
                        ArchiveFormats.open(
                            kind,
                            name,
                            stream,
                            node?.size ?: -1,
                            password = password,
                            spool = spool,
                        ).use {
                                archive ->
                            var header = archive.next()
                            while (header != null) {
                                coroutineContext.ensureActive()
                                if (escapesTarget(header.path)) {
                                    return@withContext FileResult.Failure(
                                        FileError.SuspiciousArchive(header.path, "Path traversal sequence detected"),
                                    )
                                }
                                if (entries.size >= ArchiveFormats.MAX_ENTRIES) {
                                    return@withContext FileResult.Failure(
                                        FileError.SuspiciousArchive(
                                            name,
                                            "More than ${ArchiveFormats.MAX_ENTRIES} entries",
                                        ),
                                    )
                                }
                                val entryName = header.path.replace('\\', '/').trimStart('/')
                                // Lazy browse rows use this exact normalized path as their identity.
                                if (!browsePaths.add(entryName)) {
                                    return@withContext FileResult.Failure(
                                        FileError.SuspiciousArchive(entryName, "Duplicate entry path"),
                                    )
                                }
                                val fileName = entryName.trimEnd('/').substringAfterLast('/')
                                entries.add(
                                    ArchiveEntryInfo(
                                        name = fileName.ifEmpty { entryName },
                                        path = entryName,
                                        isDirectory = header.isDirectory,
                                        uncompressedSize = header.size.coerceAtLeast(0L),
                                        compressedSize = header.compressedSize.coerceAtLeast(0L),
                                        modifiedAt = header.modifiedAt.coerceAtLeast(0L),
                                    ),
                                )
                                header = archive.next()
                            }
                        }
                    }
                    FileResult.Success(entries)
                } catch (_: ArchiveWrongPassword) {
                    FileResult.Failure(FileError.UnsupportedFormat(WRONG_PASSWORD))
                } catch (_: ArchivePasswordRequired) {
                    FileResult.Failure(FileError.UnsupportedFormat(PASSWORD_PROTECTED))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    FileResult.Failure(FileError.CorruptedArchive(archiveId.raw))
                }
            }
    }
