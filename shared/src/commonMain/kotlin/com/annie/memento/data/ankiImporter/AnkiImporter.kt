package com.annie.memento.data.ankiImporter

import com.annie.memento.data.MementoRepository
import com.annie.memento.model.ImportCardSpec
import com.annie.memento.model.ImportDeckSpec
import com.annie.memento.model.ImportSideSpec
import com.annie.memento.model.LevelDraft
import com.annie.memento.model.TagDraft
import com.annie.memento.platform.MediaStorage
import com.annie.memento.platform.ioDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okio.use

sealed interface ImportProgress {
    data class ConvertingNotes(val done: Int, val total: Int) : ImportProgress

    data class SavingMedia(val done: Int, val total: Int) : ImportProgress

    data class WritingCards(val done: Int, val total: Int) : ImportProgress
}

data class ImportOptions(
    val deckName: String,
    val description: String?,
    val frontName: String,
    val backName: String,
    val importTags: Boolean,
    val subdecksAsLevels: Boolean,
)

data class ImportResult(
    val deckId: Long,
    val cardsImported: Int,
    val cardsSkippedEmptyFront: Int,
    val mediaSaved: Int,
    val mediaMissing: Int,
)

// turns user's field mapping into a deck
class AnkiImporter(
    private val repository: MementoRepository,
    private val mediaStorage: MediaStorage,
) {
    suspend fun import(
        parsed: ParsedApkg,
        mappings: List<NoteTypeMapping>,
        options: ImportOptions,
        onProgress: (ImportProgress) -> Unit = {},
    ): ImportResult = withContext(ioDispatcher) {
        val included = mappings.filter { it.include }.associateBy { it.noteTypeId }
        val typesById = parsed.noteTypes.associateBy { it.id }
        val savedMedia = ArrayList<String>()
        val db = AnkiDb(parsed.collectionDbPath)
        try {
            val ctx = coroutineContext
            val levelPositionByDeck: Map<Long, Int> =
                if (options.subdecksAsLevels && parsed.subdecks.size > 1) {
                    parsed.subdecks.mapIndexed { index, deck -> deck.id to index + 1 }.toMap()
                } else {
                    emptyMap()
                }
            val levelPositionByNote: Map<Long, Int> =
                if (levelPositionByDeck.isEmpty()) emptyMap() else readNoteLevels(db, levelPositionByDeck)

            val totalNotes = included.keys.sumOf { typesById[it]?.noteCount ?: 0 }
            val drafts = ArrayList<CardDraft>(totalNotes)
            var skippedEmptyFront = 0
            val midList = included.keys.joinToString(",")
            db.forEachRow("SELECT id, mid, flds, tags FROM notes WHERE mid IN ($midList) ORDER BY id") { row ->
                ctx.ensureActive()
                val noteId = row[0]?.toLongOrNull() ?: return@forEachRow
                val mid = row[1]?.toLongOrNull() ?: return@forEachRow
                val mapping = included[mid] ?: return@forEachRow
                val type = typesById[mid] ?: return@forEachRow
                val draft = buildDraft(
                    fields = row[2].orEmpty().split(ANKI_FIELD_SEPARATOR),
                    targets = mapping.targets,
                    fieldCount = type.fields.size,
                    levelPosition = levelPositionByNote[noteId],
                    tags = if (options.importTags) parseTags(row[3]) else emptySet(),
                )
                if (draft == null) {
                    skippedEmptyFront++
                } else {
                    drafts.add(draft)
                    if (drafts.size % 100 == 0) onProgress(ImportProgress.ConvertingNotes(drafts.size, totalNotes))
                }
            }
            onProgress(ImportProgress.ConvertingNotes(drafts.size, totalNotes))

            val referenced = LinkedHashSet<String>()
            drafts.forEach { draft ->
                referenced += draft.front.audio + draft.front.images + draft.back.audio + draft.back.images
            }
            val storedByName = HashMap<String, String>(referenced.size)
            var missing = 0
            if (referenced.isNotEmpty()) {
                val pkg = ApkgPackage.open(parsed.session)
                referenced.forEachIndexed { index, name ->
                    ctx.ensureActive()
                    val entry = parsed.mediaMap[name]
                    val stored = entry?.let { zipEntry ->
                        runCatching {
                            pkg.openMedia(zipEntry)?.use { source ->
                                mediaStorage.saveSource(source, name.substringAfterLast('.', ""))
                            }
                        }.getOrNull()
                    }
                    if (stored == null) {
                        missing++
                    } else {
                        storedByName[name] = stored
                        savedMedia.add(stored)
                    }
                    if ((index + 1) % 25 == 0) onProgress(ImportProgress.SavingMedia(index + 1, referenced.size))
                }
                onProgress(ImportProgress.SavingMedia(referenced.size, referenced.size))
            }

            val levels = if (levelPositionByDeck.isEmpty()) {
                emptyList()
            } else {
                levelNames(parsed.subdecks).mapIndexed { index, name -> LevelDraft(null, index + 1, name, null) }
            }
            val tagNames = drafts.flatMapTo(mutableSetOf()) { it.tags }
            val spec = ImportDeckSpec(
                name = options.deckName,
                description = options.description,
                frontName = options.frontName,
                backName = options.backName,
                levels = levels,
                tags = tagNames.sorted().map { TagDraft(null, it, null) },
                cards = drafts.map { it.toCardSpec(storedByName) },
            )
            onProgress(ImportProgress.WritingCards(0, spec.cards.size))
            val deckId = repository.importDeck(spec) { onProgress(ImportProgress.WritingCards(it, spec.cards.size)) }

            ImportResult(
                deckId = deckId,
                cardsImported = spec.cards.size,
                cardsSkippedEmptyFront = skippedEmptyFront,
                mediaSaved = storedByName.size,
                mediaMissing = missing,
            )
        } catch (e: Throwable) {
            savedMedia.forEach { runCatching { mediaStorage.delete(it) } }
            throw e
        } finally {
            db.close()
        }
    }

    private fun readNoteLevels(db: AnkiDb, levelPositionByDeck: Map<Long, Int>): Map<Long, Int> {
        val byNote = HashMap<Long, Int>()
        val seen = HashSet<Long>()
        db.forEachRow("SELECT nid, did, odid FROM cards ORDER BY nid, ord") { row ->
            val nid = row[0]?.toLongOrNull() ?: return@forEachRow
            if (!seen.add(nid)) return@forEachRow
            val odid = row[2]?.toLongOrNull() ?: 0L
            val did = if (odid != 0L) odid else row[1]?.toLongOrNull() ?: return@forEachRow
            levelPositionByDeck[did]?.let { byNote[nid] = it }
        }
        return byNote
    }
}

private fun parseTags(raw: String?): Set<String> =
    raw?.trim()?.split(' ', '\t')?.filter { it.isNotBlank() }?.toSet().orEmpty()

// level names for subdecks as levels if sharing root
internal fun levelNames(subdecks: List<AnkiSubdeck>): List<String> {
    val roots = subdecks.map { it.name.substringBefore(" / ") }.distinct()
    val root = roots.singleOrNull() ?: return subdecks.map { it.name }
    return subdecks.map { deck ->
        if (deck.name.startsWith("$root / ")) deck.name.removePrefix("$root / ") else deck.name
    }
}

private class SideDraft {
    val textPieces = ArrayList<ConvertedField>()
    val examplePieces = ArrayList<ConvertedField>()
    val notesPieces = ArrayList<ConvertedField>()
    val audio = LinkedHashSet<String>()
    val images = LinkedHashSet<String>()

    val isRich: Boolean
        get() = (textPieces + examplePieces + notesPieces).any { it.isRich }

    fun toSpec(storedByName: Map<String, String>): ImportSideSpec {
        val rich = isRich
        fun ConvertedField.content() = if (rich) markup else plain
        return ImportSideSpec(
            text = textPieces.map { it.content() }.filter { it.isNotBlank() }
                .joinToString(if (rich) "<br>" else "\n"),
            examples = examplePieces.map { it.content() }.filter { it.isNotBlank() },
            notes = notesPieces.map { it.content() }.filter { it.isNotBlank() }
                .joinToString("\n").ifBlank { null },
            imagePaths = images.mapNotNull { storedByName[it] },
            audioPaths = audio.mapNotNull { storedByName[it] },
            isRichText = rich,
        )
    }
}

private class CardDraft(
    val front: SideDraft,
    val back: SideDraft,
    val levelPosition: Int?,
    val tags: Set<String>,
) {
    fun toCardSpec(storedByName: Map<String, String>): ImportCardSpec = ImportCardSpec(
        levelPosition = levelPosition,
        front = front.toSpec(storedByName),
        back = back.toSpec(storedByName),
        tagNames = tags,
    )
}

private fun buildDraft(
    fields: List<String>,
    targets: List<FieldTarget>,
    fieldCount: Int,
    levelPosition: Int?,
    tags: Set<String>,
): CardDraft? {
    val front = SideDraft()
    val back = SideDraft()
    for (ord in 0 until fieldCount) {
        val target = targets.getOrNull(ord) ?: FieldTarget.SKIP
        if (target == FieldTarget.SKIP) continue
        val raw = fields.getOrNull(ord).orEmpty()
        if (raw.isBlank()) continue
        val converted = convertAnkiField(raw)
        val side = if (target.isFront) front else back
        side.audio += converted.soundRefs
        side.images += converted.imageRefs
        when (target) {
            FieldTarget.FRONT_TEXT, FieldTarget.BACK_TEXT -> side.textPieces.add(converted)
            FieldTarget.FRONT_EXAMPLE, FieldTarget.BACK_EXAMPLE -> side.examplePieces.add(converted)
            FieldTarget.FRONT_NOTES, FieldTarget.BACK_NOTES -> side.notesPieces.add(converted)
            else -> {}
        }
    }
    val frontEmpty = front.textPieces.all { it.markup.isBlank() } &&
        front.audio.isEmpty() && front.images.isEmpty() &&
        front.examplePieces.isEmpty() && front.notesPieces.isEmpty()
    if (frontEmpty) return null
    return CardDraft(front, back, levelPosition, tags)
}
