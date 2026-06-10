package com.annie.memento.data.mementoZip

import com.annie.memento.data.MementoRepository
import com.annie.memento.data.ankiImporter.ImportProgress
import com.annie.memento.data.ankiImporter.ImportResult
import com.annie.memento.model.ImportCardSpec
import com.annie.memento.model.ImportDeckSpec
import com.annie.memento.model.ImportSideSpec
import com.annie.memento.model.LevelDraft
import com.annie.memento.model.TagDraft
import com.annie.memento.platform.MediaStorage
import com.annie.memento.platform.ioDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.openZip

enum class ImportKind { MEMENTO_DECK, ANKI_APKG, UNKNOWN }

suspend fun detectImportKind(filePath: String): ImportKind = withContext(ioDispatcher) {
    runCatching {
        val zip = FileSystem.SYSTEM.openZip(filePath.toPath())
        when {
            zip.exists("/$MEMENTO_MANIFEST_ENTRY".toPath()) -> ImportKind.MEMENTO_DECK
            zip.exists("/collection.anki21b".toPath()) ||
                zip.exists("/collection.anki21".toPath()) ||
                zip.exists("/collection.anki2".toPath()) -> ImportKind.ANKI_APKG
            else -> ImportKind.UNKNOWN
        }
    }.getOrDefault(ImportKind.UNKNOWN)
}

class MementoZipImporter(
    private val repository: MementoRepository,
    private val media: MediaStorage,
) {
    suspend fun import(filePath: String, onProgress: (ImportProgress) -> Unit = {}): ImportResult =
        withContext(ioDispatcher) {
            val zip = try {
                FileSystem.SYSTEM.openZip(filePath.toPath())
            } catch (e: Exception) {
                throw MementoZipException("The file couldn't be opened as a zip archive.", e)
            }
            val manifest = try {
                val raw = zip.read("/$MEMENTO_MANIFEST_ENTRY".toPath()) { readUtf8() }
                mementoJson.decodeFromString(MementoManifest.serializer(), raw)
            } catch (e: Exception) {
                throw MementoZipException("The Memento deck file couldn't be read. It may be damaged.", e)
            }
            if (manifest.format != MEMENTO_ZIP_FORMAT) {
                throw MementoZipException("This isn't a Memento deck export.")
            }
            if (manifest.version > MEMENTO_ZIP_VERSION) {
                throw MementoZipException("This deck was exported by a newer version of Memento. Update the app and try again.")
            }

            val wantedMedia = LinkedHashSet<String>()
            manifest.deck.photo?.let { wantedMedia.add(it) }
            manifest.cards.forEach { card ->
                listOf(card.front, card.back).forEach { side ->
                    wantedMedia.addAll(side.images)
                    wantedMedia.addAll(side.audios)
                }
            }

            val storedByEntry = HashMap<String, String>()
            var mediaMissing = 0
            try {
                wantedMedia.forEachIndexed { index, entry ->
                    ensureActive()
                    val path = "/$entry".toPath()
                    if (zip.exists(path)) {
                        val bytes = zip.read(path) { readByteArray() }
                        storedByEntry[entry] = media.save(bytes, entry.substringAfterLast('.', ""))
                    } else {
                        mediaMissing++
                    }
                    onProgress(ImportProgress.SavingMedia(index + 1, wantedMedia.size))
                }

                fun side(side: MementoCardSide) = ImportSideSpec(
                    text = side.text,
                    examples = side.examples,
                    notes = side.notes,
                    imagePaths = side.images.mapNotNull { storedByEntry[it] },
                    audioPaths = side.audios.mapNotNull { storedByEntry[it] },
                    isRichText = side.isRichText,
                )

                val spec = ImportDeckSpec(
                    name = manifest.deck.name.ifBlank { "Imported deck" },
                    description = manifest.deck.description,
                    frontName = manifest.deck.frontName,
                    backName = manifest.deck.backName,
                    levels = if (manifest.deck.isHierarchical) {
                        manifest.levels.map { LevelDraft(null, it.position, it.name, it.color) }
                    } else {
                        emptyList()
                    },
                    tags = manifest.tags.map { TagDraft(null, it.name, it.color) },
                    cards = manifest.cards.map { card ->
                        ImportCardSpec(
                            levelPosition = card.levelPosition,
                            front = side(card.front),
                            back = side(card.back),
                            tagNames = card.tagIndexes.mapNotNull { manifest.tags.getOrNull(it)?.name }.toSet(),
                        )
                    },
                    photoPath = manifest.deck.photo?.let { storedByEntry[it] },
                )

                val deckId = repository.importDeck(spec) { done ->
                    onProgress(ImportProgress.WritingCards(done, spec.cards.size))
                }
                ImportResult(
                    deckId = deckId,
                    cardsImported = spec.cards.size,
                    cardsSkippedEmptyFront = 0,
                    mediaSaved = storedByEntry.size,
                    mediaMissing = mediaMissing,
                )
            } catch (t: Throwable) {
                storedByEntry.values.forEach { media.delete(it) }
                throw t
            }
        }
}
