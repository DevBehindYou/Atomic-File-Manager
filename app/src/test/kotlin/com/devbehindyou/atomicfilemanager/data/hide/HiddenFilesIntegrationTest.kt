package com.devbehindyou.atomicfilemanager.data.hide

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.atomicfilemanager.data.backend.FileSystemBackend
import com.devbehindyou.atomicfilemanager.data.database.HiddenFilesDatabaseHelper
import com.devbehindyou.atomicfilemanager.data.repository.HiddenFilesRepositoryImpl
import com.devbehindyou.atomicfilemanager.data.vault.VaultFiles
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.HiddenItem
import com.devbehindyou.atomicfilemanager.domain.model.isVaultEncrypted
import com.devbehindyou.atomicfilemanager.domain.model.originalParent
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
class HiddenFilesIntegrationTest {
    @get:Rule
    val temporary = TemporaryFolder()
    private lateinit var database: HiddenFilesDatabaseHelper
    private lateinit var repository: HiddenFilesRepositoryImpl
    private lateinit var backend: FileSystemBackend

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = HiddenFilesDatabaseHelper(context, null)
        repository = HiddenFilesRepositoryImpl(context, database)
        backend = FileSystemBackend(context)
    }

    @After
    fun close() = database.close()

    private suspend fun node(file: File): FileNode =
        (
            backend.getNode(
                FileNodeId.file(file.absolutePath),
            ) as FileResult.Success
        ).value

    /** Domain path helpers receive Android paths; Robolectric's real files use host separators. */
    private fun HiddenItem.withAndroidPaths(): HiddenItem =
        copy(
            originalLocation = originalLocation.replace(File.separatorChar, '/'),
            currentLocation = currentLocation.replace(File.separatorChar, '/'),
        )

    @Test
    fun galleryHideRestoresBytesUsingRealFileNodeId() =
        runBlocking {
            val file = temporary.newFile("photo.jpg").apply { writeBytes(ByteArray(1024) { it.toByte() }) }
            val bytes = file.readBytes()
            val item = repository.hideFromGallery(node(file)).getOrThrow()
            assertFalse(file.exists())
            assertTrue(File(File(item.currentLocation).parentFile, ".nomedia").exists())
            repository.unhideFromGallery(item).getOrThrow()
            assertArrayEquals(bytes, file.readBytes())
        }

    @Test
    fun obscureRestoresBytesThroughRepository() =
        runBlocking {
            val file = temporary.newFile("secret.bin").apply { writeBytes(ByteArray(1025) { it.toByte() }) }
            val bytes = file.readBytes()
            val item = repository.fastObscure(node(file)).getOrThrow()
            repository.restoreFastObscured(item).getOrThrow()
            assertArrayEquals(bytes, file.readBytes())
            assertTrue(repository.hiddenItems.value.isEmpty())
        }

    @Test
    fun privateMoveRestoresBytesUsingRealFileNodeId() =
        runBlocking {
            val file = temporary.newFile("private.bin").apply { writeBytes(ByteArray(1024) { it.toByte() }) }
            val bytes = file.readBytes()
            val item = repository.moveToPrivateStorage(node(file)).getOrThrow()
            assertFalse(file.exists())
            repository.restoreFromPrivateStorage(item, FileNodeId.file(temporary.root.absolutePath)).getOrThrow()
            assertArrayEquals(bytes, file.readBytes())
        }

    private fun vaultRepository(): HiddenFilesRepositoryImpl {
        StreamingAeadConfig.register()
        val aead =
            KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM_HKDF_1MB"))
                .getPrimitive(RegistryConfiguration.get(), StreamingAead::class.java)
        return HiddenFilesRepositoryImpl(
            ApplicationProvider.getApplicationContext(),
            database,
            vault = VaultFiles { aead },
        )
    }

    @Test
    fun privateMoveEncryptsAndRestoresTheSameBytes() =
        runBlocking {
            val vaulted = vaultRepository()
            val file = temporary.newFile("diary.txt").apply { writeBytes(ByteArray(70_000) { (it % 251).toByte() }) }
            val bytes = file.readBytes()

            val item = vaulted.moveToPrivateStorage(node(file)).getOrThrow().withAndroidPaths()

            assertFalse(file.exists())
            val stored = File(item.currentLocation)
            assertTrue(stored.name.endsWith(".vault"))
            assertFalse(bytes.contentEquals(stored.readBytes()))
            assertEquals(bytes.size.toLong(), item.size)
            vaulted.restoreFromPrivateStorage(item, item.originalParent()).getOrThrow()
            assertArrayEquals(bytes, file.readBytes())
            assertFalse(stored.exists())
        }

    @Test
    fun exportWritesADecryptedCopyAndKeepsThePrivateOne() =
        runBlocking {
            val vaulted = vaultRepository()
            val file = temporary.newFile("report.pdf").apply { writeBytes(ByteArray(40_000) { (it % 233).toByte() }) }
            val bytes = file.readBytes()
            val item = vaulted.moveToPrivateStorage(node(file)).getOrThrow()
            val out = temporary.newFolder("exported")
            File(out, "report.pdf").writeText("already here")

            val name = vaulted.exportCopy(item, FileNodeId.file(out.absolutePath)).getOrThrow()

            assertEquals("report (1).pdf", name)
            assertArrayEquals(bytes, File(out, name).readBytes())
            assertEquals("already here", File(out, "report.pdf").readText())
            assertTrue(File(item.currentLocation).exists())
            assertEquals(listOf(item.id), vaulted.hiddenItems.value.map { it.id })
        }

    @Test
    fun exportRefusesGalleryItemsAndLeavesNothingBehind() =
        runBlocking {
            val file = temporary.newFile("pic.jpg").apply { writeText("photo") }
            val item = repository.hideFromGallery(node(file)).getOrThrow()
            val out = temporary.newFolder("out")
            assertTrue(repository.exportCopy(item, FileNodeId.file(out.absolutePath)).isFailure)
            assertTrue(out.listFiles().orEmpty().isEmpty())
        }

    @Test
    fun plainPrivateItemsFromBeforeTheVaultStillRestore() =
        runBlocking {
            val file = temporary.newFile("old.bin").apply { writeBytes(ByteArray(512) { it.toByte() }) }
            val bytes = file.readBytes()
            val item = repository.moveToPrivateStorage(node(file)).getOrThrow().withAndroidPaths()

            vaultRepository().restoreFromPrivateStorage(item, item.originalParent()).getOrThrow()

            assertArrayEquals(bytes, file.readBytes())
        }

    @Test
    fun plainPrivateItemsCanBeEncryptedLaterAndStillRestore() =
        runBlocking {
            val file = temporary.newFile("plain.bin").apply { writeBytes(ByteArray(3_000) { (it % 97).toByte() }) }
            val bytes = file.readBytes()
            val plainItem = repository.moveToPrivateStorage(node(file)).getOrThrow().withAndroidPaths()
            assertFalse(plainItem.isVaultEncrypted)
            val vaulted = vaultRepository()

            val encrypted = vaulted.encryptPrivateItem(plainItem).getOrThrow().withAndroidPaths()

            assertTrue(encrypted.isVaultEncrypted)
            assertFalse(File(plainItem.currentLocation).exists())
            assertFalse(bytes.contentEquals(File(encrypted.currentLocation).readBytes()))
            assertEquals(listOf(encrypted.id), vaulted.hiddenItems.value.map { it.id })
            vaulted.restoreFromPrivateStorage(encrypted, encrypted.originalParent()).getOrThrow()
            assertArrayEquals(bytes, file.readBytes())
        }

    @Test
    fun missingHiddenFileDoesNotEraseRecoveryMetadata() =
        runBlocking {
            val file = temporary.newFile("missing.jpg").apply { writeText("photo") }
            val item = repository.hideFromGallery(node(file)).getOrThrow()
            assertTrue(File(item.currentLocation).delete())
            assertTrue(repository.unhideFromGallery(item).isFailure)
            assertTrue(repository.hiddenItems.value.any { it.id == item.id })
        }

    @Test
    fun privateRestoreUsesTheItemsOriginalFolder() =
        runBlocking {
            val file = temporary.newFile("private-parent.bin").apply { writeBytes(ByteArray(64) { it.toByte() }) }
            val bytes = file.readBytes()
            val item = repository.moveToPrivateStorage(node(file)).getOrThrow().withAndroidPaths()
            repository.restoreFromPrivateStorage(item, item.originalParent()).getOrThrow()
            assertArrayEquals(bytes, file.readBytes())
        }

    @Test
    fun privateRestoreRecreatesAMissingOriginalFolder() =
        runBlocking {
            val folder = temporary.newFolder("gone")
            val file = File(folder, "note.txt").apply { writeText("note") }
            val item = repository.moveToPrivateStorage(node(file)).getOrThrow().withAndroidPaths()
            assertTrue(folder.deleteRecursively())
            repository.restoreFromPrivateStorage(item, item.originalParent()).getOrThrow()
            assertEquals("note", file.readText())
        }

    @Test
    fun galleryHideUsesTheCentralFolderAndKeepsClashingNamesApart() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val central =
                HiddenFilesRepositoryImpl(
                    context,
                    database,
                    hiddenFolderProvider = { "Refract/Hidden" },
                    volumeRootResolver = { temporary.root },
                )
            val first = File(temporary.newFolder("a"), "photo.jpg").apply { writeText("one") }
            val second = File(temporary.newFolder("b"), "photo.jpg").apply { writeText("two") }

            val hiddenFirst = central.hideFromGallery(node(first)).getOrThrow()
            val hiddenSecond = central.hideFromGallery(node(second)).getOrThrow()

            val hiddenDir = File(temporary.root, "Refract/Hidden")
            assertEquals(hiddenDir, File(hiddenFirst.currentLocation).parentFile)
            assertEquals(hiddenDir, File(hiddenSecond.currentLocation).parentFile)
            assertNotEquals(hiddenFirst.currentLocation, hiddenSecond.currentLocation)
            assertTrue(File(hiddenDir, ".nomedia").exists())

            central.unhideFromGallery(hiddenFirst).getOrThrow()
            central.unhideFromGallery(hiddenSecond).getOrThrow()
            assertEquals("one", first.readText())
            assertEquals("two", second.readText())
        }
}
