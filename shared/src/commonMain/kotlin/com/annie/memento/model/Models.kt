package com.annie.memento.model

//deck of flashcards
data class Deck(
    val id: Long,
    val name: String,
    val description: String?,
    val photoPath: String?,
    val frontName: String,
    val backName: String,
    val isHierarchical: Boolean,
) {
    val hasPhoto: Boolean get() = photoPath != null
}

//deck plus child counts
data class DeckSummary(
    val deck: Deck,
    val cardCount: Int,
    val tagCount: Int,
    val levelCount: Int,
)

//level within a heirarchical deck
data class DeckLevel(
    val id: Long,
    val deckId: Long,
    val position: Int,
    val name: String?,
    val color: Long?,
) {
    //custom name
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: "Level $position"
}

// tag cards in a deck may utilize
data class Tag(
    val id: Long,
    val deckId: Long,
    val name: String,
    val color: Long?,
)

// side of a card, can be rich text or plain, only main text is required
data class CardSide(
    val text: String,
    val examples: List<String>,
    val notes: String?,
    val imagePaths: List<String>,
    val audioPaths: List<String>,
    val isRichText: Boolean = false,
) {
    val hasImage: Boolean get() = imagePaths.isNotEmpty()
    val hasAudio: Boolean get() = audioPaths.isNotEmpty()
    val hasExamples: Boolean get() = examples.isNotEmpty()
    val hasNotes: Boolean get() = !notes.isNullOrBlank()
}

// full card with its 2 sides
data class Card(
    val id: Long,
    val deckId: Long,
    val levelId: Long?,
    val front: CardSide,
    val back: CardSide,
    val tagIds: Set<Long>,
) {
    fun mediaPaths(): Set<String> =
        (front.imagePaths + front.audioPaths + back.imagePaths + back.audioPaths).toSet()
}

// deck, levels, and tags
data class DeckDetails(
    val deck: Deck,
    val levels: List<DeckLevel>,
    val tags: List<Tag>,
) {
    fun levelById(id: Long?): DeckLevel? = id?.let { levels.firstOrNull { lvl -> lvl.id == it } }
    fun tagsByIds(ids: Set<Long>): List<Tag> = tags.filter { it.id in ids }
}

// media slot state - no media, keep current, new media input
sealed interface MediaInput {
    data object None : MediaInput
    data class Keep(val path: String) : MediaInput
    data class New(val bytes: ByteArray, val extension: String) : MediaInput {
        override fun equals(other: Any?): Boolean =
            this === other || (other is New && extension == other.extension && bytes.contentEquals(other.bytes))

        override fun hashCode(): Int = 31 * bytes.contentHashCode() + extension.hashCode()
    }
}

// side of the card to write
data class SideInput(
    val text: String,
    val examples: List<String>,
    val notes: String?,
    val images: List<MediaInput>,
    val audios: List<MediaInput>,
    val isRichText: Boolean = false,
)

//level editing
data class LevelDraft(
    val id: Long?,
    val position: Int,
    val name: String?,
    val color: Long?,
)

//tag editing
data class TagDraft(
    val id: Long?,
    val name: String,
    val color: Long?,
)
