package com.devbehindyou.atomicfilemanager.data.repository

import android.content.Context
import com.devbehindyou.atomicfilemanager.data.database.HiddenFilesDatabaseHelper
import com.devbehindyou.atomicfilemanager.data.hide.FastObscureHelper
import com.devbehindyou.atomicfilemanager.data.vault.VaultFiles
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.HiddenItem
import com.devbehindyou.atomicfilemanager.domain.model.HideJournalEntry
import com.devbehindyou.atomicfilemanager.domain.model.HideMode
import com.devbehindyou.atomicfilemanager.domain.model.JournalState
import com.devbehindyou.atomicfilemanager.domain.repository.HiddenFilesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import kotlin.coroutines.coroutineContext

class HiddenFilesRepositoryImpl(
    private val context: Context,
    private val dbHelper: HiddenFilesDatabaseHelper,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** Folder (relative to a storage root) for hidden gallery files; `null` keeps them next to the original. */
    private val hiddenFolderProvider: () -> String? = { null },
    private val volumeRootResolver: (File) -> File? = ::volumeRootOf,
    /**
     * When set, the first database read runs on [ioDispatcher] in this scope instead of on the
     * constructing thread (the main thread at start-up, ALL_IN_ONE_PLAN.md §16.2 H2). Tests leave
     * it null and get the old synchronous load.
     */
    loadScope: CoroutineScope? = null,
    /**
     * When set, new Private Storage items are encrypted (ALL_IN_ONE_PLAN.md 2.5) and stored as
     * `<id>.vault`; items stored before stay plain and restore as before.
     */
    private val vault: VaultFiles? = null,
) : HiddenFilesRepository {
    private val _hiddenItems = MutableStateFlow<List<HiddenItem>>(emptyList())
    override val hiddenItems: StateFlow<List<HiddenItem>> = _hiddenItems.asStateFlow()

    private val refreshLock = Any()

    init {
        if (loadScope == null) refresh() else loadScope.launch(ioDispatcher) { refresh() }
    }

    /** Reads and publishes under one lock, so whichever refresh runs last also read last. */
    private fun refresh() {
        synchronized(refreshLock) {
            _hiddenItems.value = dbHelper.getAllHiddenItems()
        }
    }

    /**
     * Runs [block] on the IO dispatcher. Any exception (for example an IOException from
     * `createNewFile` when the original folder is gone) becomes a failed [Result] instead of
     * escaping into the UI coroutine, where nothing catches it and the app crashes.
     */
    private suspend fun <T> io(block: suspend () -> Result<T>): Result<T> =
        withContext(ioDispatcher) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun hideFromGallery(node: FileNode): Result<HiddenItem> =
        io {
            val sourceFile =
                node.id.localFileOrNull()
                    ?: return@io Result.failure(IllegalArgumentException("Hiding requires a local file"))
            if (!sourceFile.exists()) {
                return@io Result.failure(IllegalArgumentException("Source file does not exist"))
            }

            val parentDir =
                sourceFile.parentFile
                    ?: return@io Result.failure(IllegalStateException("No parent directory"))
            // Central folder on the same storage (default Atomic File Manager/Hidden); otherwise beside the file.
            val centralDir =
                hiddenFolderProvider()?.let { relative -> volumeRootResolver(sourceFile)?.let { File(it, relative) } }
            val hiddenDir = centralDir ?: File(parentDir, ".AtomicHidden")
            if (parentDir.absoluteFile == hiddenDir.absoluteFile) {
                return@io Result.failure(IllegalStateException("File is already in the hidden folder"))
            }
            if (!hiddenDir.exists()) {
                hiddenDir.mkdirs()
            }
            if (!hiddenDir.isDirectory) {
                return@io Result.failure(IllegalStateException("Could not create ${hiddenDir.absolutePath}"))
            }

            // Ensure .nomedia exists in the hidden directory
            val noMedia = File(hiddenDir, ".nomedia")
            if (!noMedia.exists()) {
                noMedia.createNewFile()
            }

            // Files from different folders share the central folder, so a name clash gets a suffix.
            val targetFile =
                if (centralDir != null) uniqueChild(hiddenDir, sourceFile.name) else File(hiddenDir, sourceFile.name)
            if (targetFile.exists()) {
                return@io Result.failure(
                    IllegalStateException("Hidden filename already exists"),
                )
            }
            val moved = sourceFile.renameTo(targetFile)
            if (!moved) {
                return@io Result.failure(
                    IllegalStateException("Failed to move file to hidden gallery directory"),
                )
            }

            val item =
                HiddenItem(
                    id = UUID.randomUUID().toString(),
                    originalLocation = sourceFile.absolutePath,
                    currentLocation = targetFile.absolutePath,
                    originalName = sourceFile.name,
                    size = targetFile.length(),
                    mode = HideMode.GALLERY,
                )

            dbHelper.insertHiddenItem(item)
            refresh()
            Result.success(item)
        }

    override suspend fun unhideFromGallery(item: HiddenItem): Result<Unit> =
        io {
            val currentFile = File(item.currentLocation)
            val originalFile = File(item.originalLocation)
            if (!currentFile.exists()) {
                return@io Result.failure(
                    IllegalStateException("Hidden file is unavailable"),
                )
            }
            if (originalFile.exists()) {
                return@io Result.failure(
                    IllegalStateException("Original filename already exists"),
                )
            }

            if (currentFile.exists()) {
                // The original folder may have been deleted since the file was hidden.
                originalFile.parentFile?.mkdirs()
                val moved = currentFile.renameTo(originalFile)
                if (!moved) {
                    return@io Result.failure(
                        IllegalStateException("Failed to restore file from hidden directory"),
                    )
                }
            }

            dbHelper.deleteHiddenItem(item.id)
            refresh()
            Result.success(Unit)
        }

    override suspend fun fastObscure(node: FileNode): Result<HiddenItem> =
        io {
            val sourceFile =
                node.id.localFileOrNull()
                    ?: return@io Result.failure(IllegalArgumentException("Obscuring requires a local file"))
            val opId = UUID.randomUUID().toString()

            dbHelper.logJournal(
                HideJournalEntry(
                    operationId = opId,
                    originalPath = sourceFile.absolutePath,
                    currentPath = sourceFile.absolutePath,
                    originalName = sourceFile.name,
                    state = JournalState.PREPARING,
                    mode = HideMode.FAST_OBSCURE,
                ),
            )

            val obscureResult = FastObscureHelper.obscureFile(sourceFile)
            val targetFile =
                obscureResult.getOrElse {
                    dbHelper.logJournal(
                        HideJournalEntry(
                            operationId = opId,
                            originalPath = sourceFile.absolutePath,
                            currentPath = sourceFile.absolutePath,
                            originalName = sourceFile.name,
                            state = JournalState.ROLLBACK_REQUIRED,
                            mode = HideMode.FAST_OBSCURE,
                        ),
                    )
                    return@io Result.failure(it)
                }

            dbHelper.logJournal(
                HideJournalEntry(
                    operationId = opId,
                    originalPath = sourceFile.absolutePath,
                    currentPath = targetFile.absolutePath,
                    originalName = sourceFile.name,
                    state = JournalState.COMMITTED,
                    mode = HideMode.FAST_OBSCURE,
                ),
            )

            val item =
                HiddenItem(
                    id = opId,
                    originalLocation = sourceFile.absolutePath,
                    currentLocation = targetFile.absolutePath,
                    originalName = sourceFile.name,
                    size = targetFile.length(),
                    mode = HideMode.FAST_OBSCURE,
                )

            dbHelper.insertHiddenItem(item)
            refresh()
            Result.success(item)
        }

    override suspend fun restoreFastObscured(item: HiddenItem): Result<Unit> =
        io {
            val currentFile = File(item.currentLocation)
            val restoreResult = FastObscureHelper.restoreFile(currentFile)
            if (restoreResult.isFailure) {
                return@io Result.failure(restoreResult.exceptionOrNull()!!)
            }

            dbHelper.deleteHiddenItem(item.id)
            dbHelper.clearJournal(item.id)
            refresh()
            Result.success(Unit)
        }

    override suspend fun moveToPrivateStorage(node: FileNode): Result<HiddenItem> =
        io {
            val sourceFile =
                node.id.localFileOrNull()

                    ?: return@io Result.failure(
                        IllegalArgumentException("Private storage requires a local file"),
                    )
            if (!sourceFile.exists()) {
                return@io Result.failure(IllegalArgumentException("Source file does not exist"))
            }

            val privateDir = File(context.filesDir, "private_storage")
            if (!privateDir.exists()) {
                privateDir.mkdirs()
            }

            vault?.let { return@io moveIntoVault(it, sourceFile, privateDir) }

            val targetFile = File(privateDir, sourceFile.name)
            if (!targetFile.createNewFile()) {
                return@io Result.failure(
                    IllegalStateException("Private filename already exists"),
                )
            }

            try {
                FileInputStream(sourceFile).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            coroutineContext.ensureActive()
                            output.write(buffer, 0, read)
                        }
                        output.flush()
                        output.fd.sync()
                    }
                }

                if (targetFile.length() != sourceFile.length()) {
                    targetFile.delete()
                    return@io Result.failure(
                        IllegalStateException("Size verification failed after copy to private storage"),
                    )
                }

                // Only delete source after verified
                if (!sourceFile.delete()) {
                    targetFile.delete()
                    return@io Result.failure(IllegalStateException("Could not remove original file"))
                }

                val item =
                    HiddenItem(
                        id = UUID.randomUUID().toString(),
                        originalLocation = sourceFile.absolutePath,
                        currentLocation = targetFile.absolutePath,
                        originalName = sourceFile.name,
                        size = targetFile.length(),
                        mode = HideMode.PRIVATE_STORAGE,
                    )

                dbHelper.insertHiddenItem(item)
                refresh()
                Result.success(item)
            } catch (e: Exception) {
                if (sourceFile.exists() && targetFile.exists()) targetFile.delete()
                if (e is CancellationException) throw e
                Result.failure(e)
            }
        }

    override suspend fun restoreFromPrivateStorage(
        item: HiddenItem,
        destinationParent: FileNodeId,
    ): Result<Unit> =
        io {
            val privateFile = File(item.currentLocation)
            if (!privateFile.exists()) {
                return@io Result.failure(IllegalStateException("Private file no longer exists"))
            }
            if (isVaultFile(item, privateFile)) {
                val cipher =
                    vault ?: return@io Result.failure(IllegalStateException("The vault isn't available"))
                return@io restoreFromVault(cipher, item, privateFile, destinationParent)
            }

            val destDir =
                destinationParent.localFileOrNull()

                    ?: return@io Result.failure(
                        IllegalArgumentException("Restoration requires a local folder"),
                    )
            // The original folder may have been deleted or renamed since the file was hidden.
            destDir.mkdirs()
            val targetFile = File(destDir, item.originalName)
            if (!targetFile.createNewFile()) {
                return@io Result.failure(
                    IllegalStateException("Destination filename already exists"),
                )
            }

            try {
                FileInputStream(privateFile).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            coroutineContext.ensureActive()
                            output.write(buffer, 0, read)
                        }
                        output.flush()
                        output.fd.sync()
                    }
                }

                if (targetFile.length() == privateFile.length()) {
                    if (!privateFile.delete()) {
                        return@io Result.failure(
                            IllegalStateException("Could not remove private original"),
                        )
                    }
                    dbHelper.deleteHiddenItem(item.id)
                    refresh()
                    Result.success(Unit)
                } else {
                    targetFile.delete()
                    Result.failure(IllegalStateException("Restoration size mismatch"))
                }
            } catch (e: Exception) {
                if (privateFile.exists()) targetFile.delete()
                if (e is CancellationException) throw e
                Result.failure(e)
            }
        }

    override suspend fun deleteHiddenItem(item: HiddenItem): Result<Unit> =
        io {
            val file = File(item.currentLocation)
            if (file.exists()) {
                if (!file.delete()) {
                    return@io Result.failure(
                        IllegalStateException("Could not delete hidden file"),
                    )
                }
            }
            dbHelper.deleteHiddenItem(item.id)
            refresh()
            Result.success(Unit)
        }

    override suspend fun recoverUnfinishedOperations() =
        withContext(ioDispatcher) {
            val unfinished = dbHelper.getUnfinishedJournalEntries()
            for (entry in unfinished) {
                when (entry.state) {
                    JournalState.PREPARING,
                    JournalState.HEADER_TRANSFORMED,
                    JournalState.ROLLBACK_REQUIRED,
                    -> {
                        val targetFile =
                            listOf(
                                File(entry.currentPath),
                                File(entry.originalPath + FastObscureHelper.OBSCURE_EXTENSION),
                            ).firstOrNull { it.exists() }
                        if (targetFile != null && FastObscureHelper.restoreFile(targetFile).isSuccess) {
                            dbHelper.clearJournal(entry.operationId)
                        }
                    }
                    JournalState.RENAMED -> {
                        // Commit into hidden_items
                        val currentFile = File(entry.currentPath)
                        if (currentFile.exists()) {
                            dbHelper.insertHiddenItem(
                                HiddenItem(
                                    id = entry.operationId,
                                    originalLocation = entry.originalPath,
                                    currentLocation = entry.currentPath,
                                    originalName = entry.originalName,
                                    size = currentFile.length(),
                                    mode = entry.mode,
                                ),
                            )
                        }
                        dbHelper.clearJournal(entry.operationId)
                    }
                    JournalState.COMMITTED, JournalState.RESTORED -> {
                        dbHelper.clearJournal(entry.operationId)
                    }
                }
            }
            refresh()
        }

    override suspend fun encryptPrivateItem(item: HiddenItem): Result<HiddenItem> =
        io {
            val cipher = vault ?: return@io Result.failure(IllegalStateException("The vault isn't available"))
            val plain = File(item.currentLocation)
            when {
                item.mode != HideMode.PRIVATE_STORAGE ->
                    return@io Result.failure(IllegalArgumentException("Only Private Storage items can be encrypted"))
                isVaultFile(item, plain) -> return@io Result.success(item)
                !plain.exists() -> return@io Result.failure(IllegalStateException("Private file no longer exists"))
            }
            val id = UUID.randomUUID().toString()
            val target = File(plain.parentFile, "$id$VAULT_SUFFIX")
            FileInputStream(plain)
                .use { input -> cipher.encrypt(input, target, id.toByteArray()) }
                .getOrElse { return@io Result.failure(it) }
            val encrypted = item.copy(id = id, currentLocation = target.absolutePath, size = plain.length())
            // New record first, then the old one and the plain copy; undo the new one if the plain copy stays.
            dbHelper.insertHiddenItem(encrypted)
            dbHelper.deleteHiddenItem(item.id)
            if (!plain.delete()) {
                dbHelper.deleteHiddenItem(encrypted.id)
                dbHelper.insertHiddenItem(item)
                target.delete()
                refresh()
                return@io Result.failure(IllegalStateException("Could not remove the plain copy"))
            }
            refresh()
            Result.success(encrypted)
        }

    override suspend fun exportCopy(
        item: HiddenItem,
        destinationParent: FileNodeId,
    ): Result<String> =
        io {
            val source = File(item.currentLocation)
            when {
                item.mode != HideMode.PRIVATE_STORAGE ->
                    return@io Result.failure(IllegalArgumentException("Only Private Storage items can be exported"))
                !source.exists() -> return@io Result.failure(IllegalStateException("Private file no longer exists"))
            }
            val destDir =
                destinationParent.localFileOrNull()
                    ?: return@io Result.failure(IllegalArgumentException("Export needs a local folder"))
            destDir.mkdirs()
            val target = reserveName(destDir, item.originalName)
            val written =
                if (isVaultFile(item, source)) {
                    val cipher = vault ?: return@io Result.failure(IllegalStateException("The vault isn't available"))
                    FileOutputStream(target).use { output ->
                        cipher.decrypt(source, output, item.id.toByteArray()).onSuccess { output.fd.sync() }
                    }
                } else {
                    runCatching {
                        FileInputStream(source).use {
                                input ->
                            FileOutputStream(target).use { input.copyTo(it) }
                        }
                    }
                }
            // The private copy stays either way; a short or failed export leaves nothing behind.
            if (written.isFailure || written.getOrNull() != item.size) {
                target.delete()
                return@io Result.failure(written.exceptionOrNull() ?: IllegalStateException("Export size mismatch"))
            }
            Result.success(target.name)
        }

    /** Creates an empty file named [name] in [dir], or "name (1).ext" and so on when it is taken. */
    private fun reserveName(
        dir: File,
        name: String,
    ): File {
        val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
        val base = name.substring(0, dot)
        val ext = name.substring(dot)
        return generateSequence(0) { it + 1 }
            .map { n -> File(dir, if (n == 0) name else "$base ($n)$ext") }
            .first { it.createNewFile() }
    }

    /** Encrypts [sourceFile] into the vault; the original is removed only after the vault copy checks out. */
    private fun moveIntoVault(
        cipher: VaultFiles,
        sourceFile: File,
        privateDir: File,
    ): Result<HiddenItem> {
        val id = UUID.randomUUID().toString()
        val target = File(privateDir, "$id$VAULT_SUFFIX")
        val plainSize = sourceFile.length()
        FileInputStream(sourceFile)
            .use { input -> cipher.encrypt(input, target, id.toByteArray()) }
            .getOrElse { return Result.failure(it) }
        if (!sourceFile.delete()) {
            target.delete()
            return Result.failure(IllegalStateException("Could not remove original file"))
        }
        val item =
            HiddenItem(
                id = id,
                originalLocation = sourceFile.absolutePath,
                currentLocation = target.absolutePath,
                originalName = sourceFile.name,
                size = plainSize,
                mode = HideMode.PRIVATE_STORAGE,
            )
        dbHelper.insertHiddenItem(item)
        refresh()
        return Result.success(item)
    }

    /** Decrypts back to the original folder; the vault copy goes only once the plain file is complete. */
    private fun restoreFromVault(
        cipher: VaultFiles,
        item: HiddenItem,
        vaultFile: File,
        destinationParent: FileNodeId,
    ): Result<Unit> {
        val destDir =
            destinationParent.localFileOrNull()
                ?: return Result.failure(IllegalArgumentException("Restoration requires a local folder"))
        destDir.mkdirs()
        val targetFile = File(destDir, item.originalName)
        if (!targetFile.createNewFile()) {
            return Result.failure(IllegalStateException("Destination filename already exists"))
        }
        val written =
            FileOutputStream(targetFile).use { output ->
                cipher.decrypt(vaultFile, output, item.id.toByteArray()).onSuccess { output.fd.sync() }
            }
        if (written.isFailure || written.getOrNull() != item.size) {
            targetFile.delete()
            return Result.failure(written.exceptionOrNull() ?: IllegalStateException("Restoration size mismatch"))
        }
        if (!vaultFile.delete()) return Result.failure(IllegalStateException("Could not remove the vault copy"))
        dbHelper.deleteHiddenItem(item.id)
        refresh()
        return Result.success(Unit)
    }

    /** Vault copies are named by their item id, so a plain file the user called "x.vault" is never mistaken for one. */
    private fun isVaultFile(
        item: HiddenItem,
        file: File,
    ): Boolean = file.name == "${item.id}$VAULT_SUFFIX"

    private fun FileNodeId.localFileOrNull(): File? =
        if (prefix == FileNodeId.Prefix.FILE) File(raw.removePrefix(FileNodeId.Prefix.FILE.scheme)) else null

    private companion object {
        const val VAULT_SUFFIX = ".vault"
    }
}

/** `/storage/emulated/0/...` gives `/storage/emulated/0`, `/storage/ABCD-1234/...` gives `/storage/ABCD-1234`. */
internal fun volumeRootOf(file: File): File? = volumeRootPathOf(file.absolutePath)?.let(::File)

/** Parse Android paths without depending on the JVM test host's filesystem. */
internal fun volumeRootPathOf(path: String): String? {
    val segments = path.replace('\\', '/').split('/').filter { it.isNotEmpty() }
    return when {
        segments.size < 2 || segments[0] != "storage" -> null
        segments[1] == "emulated" -> if (segments.size >= 3) "/storage/emulated/${segments[2]}" else null
        segments[1] == "self" -> null
        else -> "/storage/${segments[1]}"
    }
}

/** [name] inside [dir], or `name (1).ext`, `name (2).ext`... when that name is taken. */
internal fun uniqueChild(
    dir: File,
    name: String,
): File {
    val plain = File(dir, name)
    if (!plain.exists()) return plain
    val dot = name.lastIndexOf('.')
    val stem = if (dot > 0) name.substring(0, dot) else name
    val extension = if (dot > 0) name.substring(dot) else ""
    for (number in 1..MAX_NAME_SUFFIX) {
        val candidate = File(dir, "$stem ($number)$extension")
        if (!candidate.exists()) return candidate
    }
    return plain
}

private const val MAX_NAME_SUFFIX = 999
