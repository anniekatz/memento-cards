package com.annie.memento.data.mementoZip

import com.annie.memento.data.MementoRepository
import com.annie.memento.model.CardSide
import com.annie.memento.platform.MediaStorage
import com.annie.memento.platform.ioDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import okio.use

class MementoZipException(message: String, cause: Throwable? = null) : Exception(message, cause)

// packs deck into shareable zip
class MementoZipExporter(
    private val repository: MementoRepository,
    private val media: MediaStorage,
) {
    suspend fun export(deckId: Long, cacheDirPath: String): String = withContext(ioDispatcher) {
        val details = repository.observeDeckDetails(deckId).first()
            ?: throw MementoZipException("Deck not found.")
        val cards = repository.observeCards(deckId).first()
        val deck = details.deck

        val exportDir = cacheDirPath.toPath() / "memento_export"
        runCatching { FileSystem.SYSTEM.deleteRecursively(exportDir) }
        FileSystem.SYSTEM.createDirectories(exportDir)
        val zipPath = exportDir / (sanitizeFileName(deck.name) + ".memento.zip")

        val levelPositionById = details.levels.associate { it.id to it.position }
        val tagIndexById = details.tags.withIndex().associate { (index, tag) -> tag.id to index }

        FileSystem.SYSTEM.sink(zipPath).buffer().use { sink ->
            val writer = ZipWriter(sink)

            val entryByStoredName = HashMap<String, String?>()
            suspend fun mediaEntry(storedName: String): String? {
                if (entryByStoredName.containsKey(storedName)) return entryByStoredName[storedName]
                val bytes = media.loadBytes(storedName)
                val entry = if (bytes == null) {
                    null
                } else {
                    "media/$storedName".also { writer.add(it, bytes) }
                }
                entryByStoredName[storedName] = entry
                return entry
            }

            suspend fun side(side: CardSide) = MementoCardSide(
                text = side.text,
                examples = side.examples,
                notes = side.notes,
                images = side.imagePaths.mapNotNull { mediaEntry(it) },
                audios = side.audioPaths.mapNotNull { mediaEntry(it) },
                isRichText = side.isRichText,
            )

            val manifest = MementoManifest(
                format = MEMENTO_ZIP_FORMAT,
                version = MEMENTO_ZIP_VERSION,
                deck = MementoDeck(
                    name = deck.name,
                    description = deck.description,
                    frontName = deck.frontName,
                    backName = deck.backName,
                    isHierarchical = deck.isHierarchical,
                    photo = deck.photoPath?.let { mediaEntry(it) },
                ),
                levels = details.levels.sortedBy { it.position }.map { MementoLevel(it.position, it.name, it.color) },
                tags = details.tags.map { MementoTag(it.name, it.color) },
                cards = cards.map { card ->
                    MementoCard(
                        levelPosition = card.levelId?.let { levelPositionById[it] },
                        tagIndexes = card.tagIds.mapNotNull { tagIndexById[it] }.sorted(),
                        front = side(card.front),
                        back = side(card.back),
                    )
                },
            )

            writer.add(
                MEMENTO_MANIFEST_ENTRY,
                mementoJson.encodeToString(MementoManifest.serializer(), manifest).encodeToByteArray(),
            )
            writer.finish()
        }
        zipPath.toString()
    }
}

private fun sanitizeFileName(raw: String): String {
    val cleaned = raw.map { c -> if (c.isLetterOrDigit() || c in " -_") c else '_' }.joinToString("").trim()
    return cleaned.ifBlank { "deck" }
}
