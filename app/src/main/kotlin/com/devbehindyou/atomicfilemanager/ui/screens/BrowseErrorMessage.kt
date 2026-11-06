package com.devbehindyou.atomicfilemanager.ui.screens

import com.devbehindyou.atomicfilemanager.domain.model.FileError

internal fun FileError.browseMessage(): String =
    when (this) {
        is FileError.FileNotFound -> "File not found: ${name.orEmpty()}"
        is FileError.FileAlreadyExists -> "File already exists: $name"
        is FileError.AccessDenied -> "Access denied: ${name.orEmpty()}"
        is FileError.PermissionDenied -> "Permission denied"
        is FileError.PlatformRestricted -> "Access restricted by Android OS"
        is FileError.ProviderUnavailable -> "Storage provider unavailable"
        is FileError.StorageUnavailable ->
            volumeLabel?.let { "Storage volume unavailable: $it" }
                ?: "The storage was removed or can't be reached. Reconnect it and try again."
        is FileError.ReadOnlyStorage -> "Storage is read-only"
        is FileError.DiskFull -> "Insufficient storage space"
        is FileError.OutOfMemory -> "Out of memory"
        is FileError.PathTooLong -> "Path is too long: ${name.orEmpty()}"
        is FileError.InvalidDestination -> "Invalid destination: ${name.orEmpty()}"
        is FileError.InvalidName -> "Invalid name: $name"
        is FileError.OperationCancelled -> "Operation cancelled"
        is FileError.IncompleteWrite -> "Write incomplete for $name"
        is FileError.PartialFailure -> "Partial failure: $failedCount of $totalCount failed"
        is FileError.IoFailure -> "I/O failure: ${name.orEmpty()}"
        is FileError.UnsupportedFormat -> "Unsupported format: ${mimeType.orEmpty()}"
        is FileError.CorruptedArchive -> "Corrupted archive: ${name.orEmpty()}"
        is FileError.SuspiciousArchive -> "Suspicious archive: $reason"
        is FileError.FileTooLarge -> "File too large: $name"
        is FileError.Unknown -> "Unknown error ($marker)"
    }
