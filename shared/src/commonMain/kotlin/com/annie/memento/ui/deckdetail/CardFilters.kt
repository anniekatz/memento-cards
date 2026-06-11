package com.annie.memento.ui.deckdetail

import com.annie.memento.model.Card

// filter cards in search
enum class ContentFilter(val label: String) {
    Image("Image"),
    Audio("Audio"),
    Notes("Notes"),
    Examples("Examples"),
    ;

    fun matches(card: Card): Boolean = when (this) {
        Image -> card.front.hasImage || card.back.hasImage
        Audio -> card.front.hasAudio || card.back.hasAudio
        Notes -> card.front.hasNotes || card.back.hasNotes
        Examples -> card.front.hasExamples || card.back.hasExamples
    }
}

data class CardFilters(
    val levelIds: Set<Long?> = emptySet(),
    val tagIds: Set<Long?> = emptySet(),
    val content: Set<ContentFilter> = emptySet(),
) {
    val isActive: Boolean get() = activeCount > 0
    val activeCount: Int get() = levelIds.size + tagIds.size + content.size

    fun matches(card: Card): Boolean {
        if (levelIds.isNotEmpty() && card.levelId !in levelIds) return false
        if (tagIds.isNotEmpty()) {
            val byTag = card.tagIds.any { it in tagIds }
            val untagged = null in tagIds && card.tagIds.isEmpty()
            if (!byTag && !untagged) return false
        }
        return content.all { it.matches(card) }
    }

    fun pruned(validLevelIds: Set<Long>, validTagIds: Set<Long>): CardFilters = copy(
        levelIds = levelIds.filterTo(mutableSetOf()) { it == null || it in validLevelIds },
        tagIds = tagIds.filterTo(mutableSetOf()) { it == null || it in validTagIds },
    )
}

fun <T> Set<T>.toggled(item: T): Set<T> = if (item in this) this - item else this + item
