package com.annie.memento.data.ankiImporter

enum class FieldTarget {
    FRONT_TEXT, FRONT_EXAMPLE, FRONT_NOTES, FRONT_AUDIO, FRONT_IMAGE,
    BACK_TEXT, BACK_EXAMPLE, BACK_NOTES, BACK_AUDIO, BACK_IMAGE,
    SKIP,
    ;

    val isFront: Boolean get() = this in setOf(FRONT_TEXT, FRONT_EXAMPLE, FRONT_NOTES, FRONT_AUDIO, FRONT_IMAGE)

    fun label(frontName: String, backName: String): String = when (this) {
        FRONT_TEXT -> "$frontName · text"
        FRONT_EXAMPLE -> "$frontName · example"
        FRONT_NOTES -> "$frontName · notes"
        FRONT_AUDIO -> "$frontName · audio"
        FRONT_IMAGE -> "$frontName · photo"
        BACK_TEXT -> "$backName · text"
        BACK_EXAMPLE -> "$backName · example"
        BACK_NOTES -> "$backName · notes"
        BACK_AUDIO -> "$backName · audio"
        BACK_IMAGE -> "$backName · photo"
        SKIP -> "Don't import"
    }
}

data class NoteTypeMapping(
    val noteTypeId: Long,
    val include: Boolean,
    val targets: List<FieldTarget>,
)

fun defaultTargets(fields: List<AnkiField>): List<FieldTarget> {
    val targets = fields.map { defaultTargetFor(it.name) }.toMutableList()

    if (targets.none { it == FieldTarget.FRONT_TEXT }) {
        val free = targets.indexOfFirst { it == FieldTarget.SKIP }
        if (free != -1) targets[free] = FieldTarget.FRONT_TEXT
    }
    if (targets.none { it == FieldTarget.BACK_TEXT }) {
        val later = targets.drop(1).indexOfFirst { it == FieldTarget.SKIP }
        val free = if (later != -1) later + 1 else targets.indexOfFirst { it == FieldTarget.SKIP }
        if (free != -1) targets[free] = FieldTarget.BACK_TEXT
    }
    return targets
}

private fun defaultTargetFor(fieldName: String): FieldTarget {
    val name = fieldName.lowercase()
    fun has(vararg keys: String) = keys.any { it in name }
    return when {
        has("furigana") -> FieldTarget.SKIP // duplicates a base+reading pair
        has("audio", "sound") -> if (has("sentence", "example")) FieldTarget.BACK_AUDIO else FieldTarget.FRONT_AUDIO
        has("picture", "image", "photo", "screenshot") -> FieldTarget.BACK_IMAGE
        has("note", "comment", "hint", "mnemonic", "extra") -> FieldTarget.BACK_NOTES
        has("sentence", "example", "usage") ->
            if (has("meaning", "translation", "english")) FieldTarget.BACK_EXAMPLE else FieldTarget.FRONT_EXAMPLE
        has("reading", "pronunciation", "pinyin", "kana", "romaji") -> FieldTarget.FRONT_NOTES
        has("back", "meaning", "answer", "definition", "translation", "english") -> FieldTarget.BACK_TEXT
        has("front", "expression", "word", "term", "question", "kanji", "vocab") -> FieldTarget.FRONT_TEXT
        else -> FieldTarget.SKIP
    }
}
