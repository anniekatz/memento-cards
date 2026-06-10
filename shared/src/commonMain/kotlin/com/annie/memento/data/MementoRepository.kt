package com.annie.memento.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.annie.memento.db.MementoDatabase
import com.annie.memento.db.SelectDeckSummaries
import com.annie.memento.model.Card
import com.annie.memento.model.CardSide
import com.annie.memento.model.Deck
import com.annie.memento.model.DeckDetails
import com.annie.memento.model.DeckLevel
import com.annie.memento.model.DeckSummary
import com.annie.memento.model.ImportDeckSpec
import com.annie.memento.model.LevelDraft
import com.annie.memento.model.MediaInput
import com.annie.memento.model.SideInput
import com.annie.memento.model.Tag
import com.annie.memento.model.TagDraft
import com.annie.memento.platform.MediaStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.annie.memento.db.Card as DbCard
import com.annie.memento.db.Deck as DbDeck
import com.annie.memento.db.DeckLevel as DbDeckLevel
import com.annie.memento.db.Tag as DbTag

//backed by sqldelight
class MementoRepository(
    private val db: MementoDatabase,
    private val media: MediaStorage,
    private val dispatcher: CoroutineDispatcher,
) {
    private val deckQ = db.deckQueries
    private val levelQ = db.deckLevelQueries
    private val tagQ = db.tagQueries
    private val cardQ = db.cardQueries
    private val cardTagQ = db.cardTagQueries

    // deck and card reads
    fun observeDeckSummaries(): Flow<List<DeckSummary>> =
        deckQ.selectDeckSummaries().asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toSummary() } }

    fun observeDeckDetails(deckId: Long): Flow<DeckDetails?> =
        combine(
            deckQ.selectDeck(deckId).asFlow().mapToOneOrNull(dispatcher),
            levelQ.selectLevelsByDeck(deckId).asFlow().mapToList(dispatcher),
            tagQ.selectTagsByDeck(deckId).asFlow().mapToList(dispatcher),
        ) { deck, levels, tags ->
            deck?.let { DeckDetails(it.toModel(), levels.map { l -> l.toModel() }, tags.map { t -> t.toModel() }) }
        }

    fun observeCards(deckId: Long): Flow<List<Card>> =
        combine(
            cardQ.selectCardsByDeck(deckId).asFlow().mapToList(dispatcher),
            cardTagQ.selectCardTagsByDeck(deckId).asFlow().mapToList(dispatcher),
        ) { cards, links ->
            val tagsByCard: Map<Long, Set<Long>> =
                links.groupBy { it.cardId }.mapValues { entry -> entry.value.map { it.tagId }.toSet() }
            cards.map { it.toModel(tagsByCard[it.id].orEmpty()) }
        }

    suspend fun getCard(cardId: Long): Card? = withContext(dispatcher) { loadCard(cardId) }

    // deck writes
    suspend fun createDeck(
        name: String,
        description: String?,
        photo: MediaInput,
        frontName: String,
        backName: String,
        isHierarchical: Boolean,
        levels: List<LevelDraft>,
        tags: List<TagDraft>,
    ): Long = withContext(dispatcher) {
        val photoName = resolve(photo)
        db.transactionWithResult {
            deckQ.insertDeck(name, frontName, backName, isHierarchical.toDb(), description, photoName)
            val deckId = deckQ.lastInsertRowId().executeAsOne()
            levels.forEach { levelQ.insertLevel(deckId, it.position.toLong(), it.name, it.color) }
            tags.forEach { tagQ.insertTag(deckId, it.name, it.color) }
            deckId
        }
    }

    suspend fun updateDeck(
        deckId: Long,
        name: String,
        description: String?,
        photo: MediaInput,
        frontName: String,
        backName: String,
        isHierarchical: Boolean,
        levels: List<LevelDraft>,
        tags: List<TagDraft>,
    ) = withContext(dispatcher) {
        // one photo per deck
        val oldPhotos = deckQ.selectDeck(deckId).executeAsOneOrNull()?.let { decodePaths(it.photoPaths) }.orEmpty()
        val newPhoto = resolve(photo)
        db.transaction {
            deckQ.updateDeck(name, frontName, backName, isHierarchical.toDb(), description, newPhoto, deckId)

            // tags
            val existingTagIds = tagQ.selectTagsByDeck(deckId).executeAsList().map { it.id }.toSet()
            val keptTagIds = tags.mapNotNull { it.id }.toSet()
            (existingTagIds - keptTagIds).forEach { tagQ.deleteTag(it) }
            tags.forEach { t ->
                if (t.id == null) tagQ.insertTag(deckId, t.name, t.color)
                else tagQ.updateTag(t.name, t.color, t.id)
            }

            // levels
            val existingLevelIds = levelQ.selectLevelsByDeck(deckId).executeAsList().map { it.id }.toSet()
            val keptLevelIds = levels.mapNotNull { it.id }.toSet()
            (existingLevelIds - keptLevelIds).forEach { lid ->
                cardQ.clearLevelOnCards(lid)
                levelQ.deleteLevel(lid)
            }
            levels.forEach { l ->
                if (l.id == null) levelQ.insertLevel(deckId, l.position.toLong(), l.name, l.color)
                else levelQ.updateLevel(l.position.toLong(), l.name, l.color, l.id)
            }

            if (!isHierarchical) {
                cardQ.clearLevelForDeck(deckId)
                levelQ.deleteLevelsByDeck(deckId)
            } else {
                // hierarchical decks require a level on every card: cards without one
                // (hierarchy just turned on, or their level was deleted) get the first level
                levelQ.selectLevelsByDeck(deckId).executeAsList().firstOrNull()?.let { first ->
                    cardQ.setLevelOnUnleveledCards(first.id, deckId)
                }
            }
        }
        // remove dropped deck photos
        (oldPhotos - setOfNotNull(newPhoto)).forEach { media.delete(it) }
    }

    suspend fun importDeck(spec: ImportDeckSpec, onCardsInserted: (Int) -> Unit = {}): Long =
        withContext(dispatcher) {
            db.transactionWithResult {
                deckQ.insertDeck(
                    spec.name,
                    spec.frontName,
                    spec.backName,
                    spec.levels.isNotEmpty().toDb(),
                    spec.description,
                    spec.photoPath,
                )
                val deckId = deckQ.lastInsertRowId().executeAsOne()
                spec.levels.forEach { levelQ.insertLevel(deckId, it.position.toLong(), it.name, it.color) }
                spec.tags.forEach { tagQ.insertTag(deckId, it.name, it.color) }
                val levelIdByPosition =
                    levelQ.selectLevelsByDeck(deckId).executeAsList().associate { it.position.toInt() to it.id }
                val tagIdByName = tagQ.selectTagsByDeck(deckId).executeAsList().associate { it.name to it.id }
                spec.cards.forEachIndexed { index, card ->
                    cardQ.insertCard(
                        deckId,
                        card.levelPosition?.let { levelIdByPosition[it] },
                        card.front.text,
                        card.front.notes,
                        encodePaths(card.front.imagePaths),
                        encodePaths(card.front.audioPaths),
                        card.back.text,
                        card.back.notes,
                        encodePaths(card.back.imagePaths),
                        encodePaths(card.back.audioPaths),
                        encodeExamples(card.front.examples),
                        encodeExamples(card.back.examples),
                        card.front.isRichText.toDb(),
                        card.back.isRichText.toDb(),
                    )
                    if (card.tagNames.isNotEmpty()) {
                        val cardId = deckQ.lastInsertRowId().executeAsOne()
                        card.tagNames.forEach { name -> tagIdByName[name]?.let { cardTagQ.insertCardTag(cardId, it) } }
                    }
                    if ((index + 1) % 200 == 0) onCardsInserted(index + 1)
                }
                // hierarchical decks require a level on every card; default to the first level
                if (spec.levels.isNotEmpty()) {
                    levelQ.selectLevelsByDeck(deckId).executeAsList().firstOrNull()?.let { first ->
                        cardQ.setLevelOnUnleveledCards(first.id, deckId)
                    }
                }
                onCardsInserted(spec.cards.size)
                deckId
            }
        }

    suspend fun deleteDeck(deckId: Long) = withContext(dispatcher) {
        val deckPhotos = deckQ.selectDeck(deckId).executeAsOneOrNull()?.let { decodePaths(it.photoPaths) }.orEmpty()
        val mediaToDelete = deckPhotos + cardQ.selectCardsByDeck(deckId).executeAsList()
            .flatMap { decodePaths(it.frontImagePath) + decodePaths(it.frontAudioPath) + decodePaths(it.backImagePath) + decodePaths(it.backAudioPath) }
        db.transaction {
            cardTagQ.deleteCardTagsByDeck(deckId)
            cardQ.deleteCardsByDeck(deckId)
            levelQ.deleteLevelsByDeck(deckId)
            tagQ.deleteTagsByDeck(deckId)
            deckQ.deleteDeck(deckId)
        }
        mediaToDelete.forEach { media.delete(it) }
    }

    //card writes
    suspend fun createCard(
        deckId: Long,
        levelId: Long?,
        front: SideInput,
        back: SideInput,
        tagIds: Set<Long>,
    ) = withContext(dispatcher) {
        val aImg = resolveAll(front.images)
        val aAud = resolveAll(front.audios)
        val bImg = resolveAll(back.images)
        val bAud = resolveAll(back.audios)
        db.transaction {
            cardQ.insertCard(
                deckId, levelId,
                front.text, front.notes, encodePaths(aImg), encodePaths(aAud),
                back.text, back.notes, encodePaths(bImg), encodePaths(bAud),
                encodeExamples(front.examples), encodeExamples(back.examples),
                front.isRichText.toDb(), back.isRichText.toDb(),
            )
            val cardId = deckQ.lastInsertRowId().executeAsOne()
            tagIds.forEach { cardTagQ.insertCardTag(cardId, it) }
        }
    }

    suspend fun updateCard(
        cardId: Long,
        levelId: Long?,
        front: SideInput,
        back: SideInput,
        tagIds: Set<Long>,
    ) = withContext(dispatcher) {
        val old = loadCard(cardId)
        val aImg = resolveAll(front.images)
        val aAud = resolveAll(front.audios)
        val bImg = resolveAll(back.images)
        val bAud = resolveAll(back.audios)
        db.transaction {
            cardQ.updateCard(
                levelId,
                front.text, front.notes, encodePaths(aImg), encodePaths(aAud),
                back.text, back.notes, encodePaths(bImg), encodePaths(bAud),
                encodeExamples(front.examples), encodeExamples(back.examples),
                front.isRichText.toDb(), back.isRichText.toDb(),
                cardId,
            )
            cardTagQ.deleteCardTagsByCard(cardId)
            tagIds.forEach { cardTagQ.insertCardTag(cardId, it) }
        }
        if (old != null) {
            val newPaths = (aImg + aAud + bImg + bAud).toSet()
            (old.mediaPaths() - newPaths).forEach { media.delete(it) }
        }
    }

    suspend fun deleteCard(cardId: Long) = withContext(dispatcher) {
        val card = loadCard(cardId)
        db.transaction {
            cardTagQ.deleteCardTagsByCard(cardId)
            cardQ.deleteCard(cardId)
        }
        card?.mediaPaths()?.forEach { media.delete(it) }
    }

    // tag writes
    suspend fun createTag(deckId: Long, name: String, color: Long?): Long = withContext(dispatcher) {
        db.transactionWithResult {
            tagQ.insertTag(deckId, name, color)
            deckQ.lastInsertRowId().executeAsOne()
        }
    }

    // bulk card writes
    suspend fun bulkEditCards(cardIds: Collection<Long>, addTagIds: Set<Long>, levelId: Long?) =
        withContext(dispatcher) {
            db.transaction {
                cardIds.forEach { cardId ->
                    if (levelId != null) cardQ.updateCardLevel(levelId, cardId)
                    addTagIds.forEach { tagId -> cardTagQ.insertCardTag(cardId, tagId) }
                }
            }
        }

    suspend fun bulkDeleteCards(cardIds: Collection<Long>) = withContext(dispatcher) {
        val cards = cardIds.mapNotNull { loadCard(it) }
        db.transaction {
            cards.forEach { card ->
                cardTagQ.deleteCardTagsByCard(card.id)
                cardQ.deleteCard(card.id)
            }
        }
        cards.flatMap { it.mediaPaths() }.toSet().forEach { media.delete(it) }
    }

    // other
    // media slot persistence
    private suspend fun resolve(input: MediaInput): String? = when (input) {
        is MediaInput.None -> null
        is MediaInput.Keep -> input.path
        is MediaInput.New -> media.save(input.bytes, input.extension)
    }

    private suspend fun resolveAll(inputs: List<MediaInput>): List<String> = inputs.mapNotNull { resolve(it) }

    private fun loadCard(cardId: Long): Card? {
        val row = cardQ.selectCard(cardId).executeAsOneOrNull() ?: return null
        val tagIds = cardTagQ.selectTagIdsByCard(cardId).executeAsList().toSet()
        return row.toModel(tagIds)
    }
}

private fun Boolean.toDb(): Long = if (this) 1L else 0L

private const val MEDIA_PATH_SEPARATOR = "\n"

//list to media column
private fun encodePaths(paths: List<String>): String? =
    paths.takeIf { it.isNotEmpty() }?.joinToString(MEDIA_PATH_SEPARATOR)

// media column to list
private fun decodePaths(stored: String?): List<String> =
    stored?.split(MEDIA_PATH_SEPARATOR)?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()


private val EXAMPLE_SEPARATOR = 30.toChar().toString()

private fun encodeExamples(examples: List<String>): String? =
    examples.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.joinToString(EXAMPLE_SEPARATOR)

private fun decodeExamples(stored: String?): List<String> =
    stored?.split(EXAMPLE_SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty()

private fun DbDeck.toModel() = Deck(
    id = id,
    name = name,
    description = description,
    photoPath = decodePaths(photoPaths).firstOrNull(),
    frontName = frontName,
    backName = backName,
    isHierarchical = isHierarchical != 0L,
)

private fun DbDeckLevel.toModel() = DeckLevel(
    id = id,
    deckId = deckId,
    position = position.toInt(),
    name = name,
    color = color,
)

private fun DbTag.toModel() = Tag(
    id = id,
    deckId = deckId,
    name = name,
    color = color,
)

private fun DbCard.toModel(tagIds: Set<Long>) = Card(
    id = id,
    deckId = deckId,
    levelId = levelId,
    front = CardSide(frontText, decodeExamples(frontExamples), frontNotes, decodePaths(frontImagePath), decodePaths(frontAudioPath), frontRichText != 0L),
    back = CardSide(backText, decodeExamples(backExamples), backNotes, decodePaths(backImagePath), decodePaths(backAudioPath), backRichText != 0L),
    tagIds = tagIds,
)

private fun SelectDeckSummaries.toSummary() = DeckSummary(
    deck = Deck(
        id = id,
        name = name,
        description = description,
        photoPath = decodePaths(photoPaths).firstOrNull(),
        frontName = frontName,
        backName = backName,
        isHierarchical = isHierarchical != 0L,
    ),
    cardCount = cardCount.toInt(),
    tagCount = tagCount.toInt(),
    levelCount = levelCount.toInt(),
)
