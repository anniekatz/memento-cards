package com.annie.memento.data.ankiImporter

sealed class ApkgException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotAnApkg(detail: String) : ApkgException(detail)

    class UnsupportedVersion(val found: Int) : ApkgException("apkg package version $found is not supported")

    class CorruptArchive(detail: String, cause: Throwable? = null) : ApkgException(detail, cause)

    class CorruptCollection(detail: String, cause: Throwable? = null) : ApkgException(detail, cause)

    class IoFailure(cause: Throwable) : ApkgException("file read/write failed during import", cause)
}

fun ApkgException.userMessage(): String = when (this) {
    is ApkgException.NotAnApkg ->
        "This file doesn't look like an Anki deck export (.apkg)."
    is ApkgException.UnsupportedVersion ->
        "This deck was exported by a newer Anki version than Memento supports. " +
            "In Anki, export with “Support older Anki versions” checked, then try again."
    is ApkgException.CorruptArchive ->
        "The deck file appears to be damaged or uses an unsupported archive format."
    is ApkgException.CorruptCollection ->
        "The deck's card database couldn't be read. The file may be damaged."
    is ApkgException.IoFailure ->
        "Couldn't read or write files while importing. Check free storage and try again."
}
