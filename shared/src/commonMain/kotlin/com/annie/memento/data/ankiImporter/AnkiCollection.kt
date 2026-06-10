package com.annie.memento.data.ankiImporter

import com.annie.memento.platform.ioDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val ANKI_FIELD_SEPARATOR = '\u001F'

// preview value (w rich text) for the mapping screen
data class FieldSample(val text: String, val isRich: Boolean)

data class AnkiField(val ord: Int, val name: String, val sample: FieldSample?)

data class AnkiNoteType(
    val id: Long,
    val name: String,
    val fields: List<AnkiField>,
    val noteCount: Int,
)

data class AnkiSubdeck(val id: Long, val name: String, val noteCount: Int)

data class ParsedApkg(
    val session: ImportSession,
    val version: Int,
    val collectionDbPath: String,
    val suggestedDeckName: String,
    val noteTypes: List<AnkiNoteType>,
    val subdecks: List<AnkiSubdeck>,
    val hasTags: Boolean,
    val mediaMap: Map<String, String>,
    val noteCount: Int,
) {
    val mediaCount: Int get() = mediaMap.size
}

class ApkgParser {
    suspend fun parse(session: ImportSession): ParsedApkg = withContext(ioDispatcher) {
        val pkg = ApkgPackage.open(session)
        val dbPath = pkg.extractCollectionDb().toString()
        val mediaMap = pkg.readMediaMap()
        val db = AnkiDb(dbPath)
        try {
            val modern = db.hasTable("notetypes")
            val rawTypes = if (modern) readModernNoteTypes(db) else readLegacyNoteTypes(db)
            val deckNames = if (modern) readModernDecks(db) else readLegacyDecks(db)

            val noteCounts = HashMap<Long, Int>()
            db.forEachRow("SELECT mid, COUNT(*) FROM notes GROUP BY mid") { row ->
                val mid = row[0]?.toLongOrNull() ?: return@forEachRow
                noteCounts[mid] = row[1]?.toIntOrNull() ?: 0
            }

            val noteTypes = rawTypes
                .filter { (noteCounts[it.id] ?: 0) > 0 }
                .map { type ->
                    val samples = collectSamples(db, type.id, type.fields.size)
                    type.copy(
                        noteCount = noteCounts.getValue(type.id),
                        fields = type.fields.map { f -> f.copy(sample = samples.getOrNull(f.ord)) },
                    )
                }
                .sortedByDescending { it.noteCount }
            if (noteTypes.isEmpty()) {
                throw ApkgException.CorruptCollection("the deck contains no notes")
            }

            // note belongs to its first card's deck
            val notesPerDeck = HashMap<Long, Int>()
            val seenNotes = HashSet<Long>()
            db.forEachRow("SELECT nid, did, odid FROM cards ORDER BY nid, ord") { row ->
                val nid = row[0]?.toLongOrNull() ?: return@forEachRow
                if (!seenNotes.add(nid)) return@forEachRow
                val odid = row[2]?.toLongOrNull() ?: 0L
                val did = if (odid != 0L) odid else row[1]?.toLongOrNull() ?: return@forEachRow
                notesPerDeck[did] = (notesPerDeck[did] ?: 0) + 1
            }
            val subdecks = notesPerDeck.entries
                .map { (did, count) -> AnkiSubdeck(did, deckNames[did] ?: "Deck $did", count) }
                .sortedBy { it.name }

            val hasTags = db.firstRow("SELECT 1 FROM notes WHERE TRIM(tags) != '' LIMIT 1") != null

            ParsedApkg(
                session = session,
                version = pkg.version,
                collectionDbPath = dbPath,
                suggestedDeckName = suggestDeckName(subdecks, session.displayName),
                noteTypes = noteTypes,
                subdecks = subdecks,
                hasTags = hasTags,
                mediaMap = mediaMap,
                noteCount = noteTypes.sumOf { it.noteCount },
            )
        } finally {
            db.close()
        }
    }

    private fun AnkiDb.hasTable(name: String): Boolean =
        firstRow("SELECT name FROM sqlite_master WHERE type='table' AND name='$name'") != null

    private fun readModernNoteTypes(db: AnkiDb): List<AnkiNoteType> {
        val fieldsByType = HashMap<Long, MutableList<AnkiField>>()
        db.forEachRow("SELECT ntid, ord, name FROM fields ORDER BY ntid, ord") { row ->
            val ntid = row[0]?.toLongOrNull() ?: return@forEachRow
            val ord = row[1]?.toIntOrNull() ?: return@forEachRow
            fieldsByType.getOrPut(ntid) { ArrayList() }.add(AnkiField(ord, row[2].orEmpty(), null))
        }
        val types = ArrayList<AnkiNoteType>()
        db.forEachRow("SELECT id, name FROM notetypes") { row ->
            val id = row[0]?.toLongOrNull() ?: return@forEachRow
            types.add(AnkiNoteType(id, row[1].orEmpty(), fieldsByType[id].orEmpty(), 0))
        }
        return types
    }

    private fun readLegacyNoteTypes(db: AnkiDb): List<AnkiNoteType> {
        val json = db.firstRow("SELECT models FROM col LIMIT 1")?.firstOrNull()
            ?: throw ApkgException.CorruptCollection("missing col.models")
        return try {
            Json.parseToJsonElement(json).jsonObject.map { (mid, model) ->
                val obj = model.jsonObject
                val fields = obj["flds"]?.jsonArray.orEmpty().mapIndexed { index, f ->
                    val fo = f.jsonObject
                    AnkiField(
                        ord = fo["ord"]?.jsonPrimitive?.content?.toIntOrNull() ?: index,
                        name = fo["name"]?.jsonPrimitive?.content.orEmpty(),
                        sample = null,
                    )
                }.sortedBy { it.ord }
                AnkiNoteType(
                    id = mid.toLongOrNull() ?: 0L,
                    name = obj["name"]?.jsonPrimitive?.content.orEmpty(),
                    fields = fields,
                    noteCount = 0,
                )
            }
        } catch (e: Exception) {
            if (e is ApkgException) throw e
            throw ApkgException.CorruptCollection("couldn't parse note type definitions", e)
        }
    }

    private fun readModernDecks(db: AnkiDb): Map<Long, String> {
        val decks = HashMap<Long, String>()
        db.forEachRow("SELECT id, name FROM decks") { row ->
            val id = row[0]?.toLongOrNull() ?: return@forEachRow
            decks[id] = normalizeDeckName(row[1].orEmpty())
        }
        return decks
    }

    private fun readLegacyDecks(db: AnkiDb): Map<Long, String> {
        val json = db.firstRow("SELECT decks FROM col LIMIT 1")?.firstOrNull() ?: return emptyMap()
        return try {
            Json.parseToJsonElement(json).jsonObject.entries.mapNotNull { (did, deck) ->
                val id = did.toLongOrNull() ?: return@mapNotNull null
                id to normalizeDeckName(deck.jsonObject["name"]?.jsonPrimitive?.content.orEmpty())
            }.toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun collectSamples(db: AnkiDb, noteTypeId: Long, fieldCount: Int): List<FieldSample?> {
        val accumulator = SampleAccumulator(fieldCount)
        db.forEachRow("SELECT flds FROM notes WHERE mid = $noteTypeId ORDER BY id LIMIT $SAMPLE_SCAN_LIMIT") { row ->
            accumulator.offer(row[0].orEmpty().split(ANKI_FIELD_SEPARATOR))
        }
        return accumulator.samples().map { raw -> raw?.let { fieldPreview(it) } }
    }
}

private const val SAMPLE_SCAN_LIMIT = 500

internal fun normalizeDeckName(raw: String): String =
    raw.replace(ANKI_FIELD_SEPARATOR.toString(), " / ").replace("::", " / ").trim()

internal fun suggestDeckName(subdecks: List<AnkiSubdeck>, displayName: String): String {
    val roots = subdecks.map { it.name.substringBefore(" / ").trim() }.filter { it.isNotEmpty() }.distinct()
    if (roots.size == 1) return roots.single()
    val fromFile = displayName.substringBeforeLast('.').replace('_', ' ').trim()
    return fromFile.ifEmpty { "Imported deck" }
}

// preview of a raw anki field
internal fun fieldPreview(raw: String): FieldSample? {
    if (raw.isBlank()) return null
    val converted = convertAnkiField(raw)
    val plain = converted.plain.replace('\n', ' ').trim()
    return when {
        plain.isNotEmpty() && converted.isRich && converted.markup.length <= MAX_RICH_SAMPLE_MARKUP ->
            FieldSample(converted.markup, isRich = true)
        plain.isNotEmpty() -> FieldSample(plain.take(120), isRich = false)
        converted.soundRefs.isNotEmpty() -> FieldSample("audio: ${converted.soundRefs.first()}", isRich = false)
        converted.imageRefs.isNotEmpty() -> FieldSample("image: ${converted.imageRefs.first()}", isRich = false)
        else -> null
    }
}

private const val MAX_RICH_SAMPLE_MARKUP = 400

// picks samples
// row with most non-empty fields wins
internal class SampleAccumulator(private val fieldCount: Int) {
    private var bestScore = -1
    private var best: List<String> = emptyList()
    private val firstNonEmpty = arrayOfNulls<String>(fieldCount)
    private val firstRich = arrayOfNulls<String>(fieldCount)

    fun offer(fields: List<String>) {
        val score = fields.count { it.isNotBlank() }
        if (score > bestScore) {
            bestScore = score
            best = fields
        }
        for (i in 0 until minOf(fieldCount, fields.size)) {
            val value = fields[i]
            if (value.isBlank()) continue
            if (firstNonEmpty[i] == null) firstNonEmpty[i] = value
            if (firstRich[i] == null && convertAnkiField(value).isRich) firstRich[i] = value
        }
    }

    fun samples(): List<String?> = List(fieldCount) { i ->
        val baseValue = best.getOrNull(i)?.takeIf { it.isNotBlank() }
        when {
            baseValue != null && (firstRich[i] == null || convertAnkiField(baseValue).isRich) -> baseValue
            firstRich[i] != null -> firstRich[i]
            else -> baseValue ?: firstNonEmpty[i]
        }
    }
}
