package com.devbehindyou.atomicfilemanager.ui.screens

import android.content.Context
import android.os.Environment
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Context.filesDir may create the directory, so resolve all quick-access paths off the UI thread. */
internal suspend fun loadHomeQuickAccessFolders(
    context: Context,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    publicDirectory: (String) -> File? = Environment::getExternalStoragePublicDirectory,
): List<Pair<String, FileNodeId>> =
    withContext(dispatcher) {
        val fallback = FileNodeId.file(context.filesDir.absolutePath)
        listOf(
            Environment.DIRECTORY_DOWNLOADS,
            Environment.DIRECTORY_DCIM,
            Environment.DIRECTORY_DOCUMENTS,
            Environment.DIRECTORY_PICTURES,
            Environment.DIRECTORY_MUSIC,
        ).map { type ->
            val path =
                try {
                    publicDirectory(type)?.absolutePath
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            type to (path?.let { FileNodeId.file(it) } ?: fallback)
        }
    }
