package com.annie.memento.data.mementoZip

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val MEMENTO_MANIFEST_ENTRY = "memento.json"
const val MEMENTO_ZIP_FORMAT = "memento-deck"
const val MEMENTO_ZIP_VERSION = 1

internal val mementoJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
data class MementoManifest(
    val format: String,
    val version: Int,
    val deck: MementoDeck,
    val levels: List<MementoLevel> = emptyList(),
    val tags: List<MementoTag> = emptyList(),
    val cards: List<MementoCard> = emptyList(),
)

@Serializable
data class MementoDeck(
    val name: String,
    val description: String? = null,
    val frontName: String,
    val backName: String,
    val isHierarchical: Boolean = false,
    val photo: String? = null,
)

@Serializable
data class MementoLevel(
    val position: Int,
    val name: String? = null,
    val color: Long? = null,
)

@Serializable
data class MementoTag(
    val name: String,
    val color: Long? = null,
)

@Serializable
data class MementoCard(
    val levelPosition: Int? = null,
    val tagIndexes: List<Int> = emptyList(),
    val front: MementoCardSide,
    val back: MementoCardSide,
)

@Serializable
data class MementoCardSide(
    val text: String,
    val examples: List<String> = emptyList(),
    val notes: String? = null,
    val images: List<String> = emptyList(),
    val audios: List<String> = emptyList(),
    val isRichText: Boolean = false,
)
