package com.annie.memento.data.mementoZip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MementoManifestTest {

    private fun sampleManifest() = MementoManifest(
        format = MEMENTO_ZIP_FORMAT,
        version = MEMENTO_ZIP_VERSION,
        deck = MementoDeck(
            name = "日本語 N5",
            description = "kanji practice",
            frontName = "Kanji",
            backName = "Meaning",
            isHierarchical = true,
            photo = "media/photo.jpg",
        ),
        levels = listOf(MementoLevel(1, "Basics", 0xFFFF0000L), MementoLevel(2, null, null)),
        tags = listOf(MementoTag("noun", 0xFF00FF00L), MementoTag("verb", null)),
        cards = listOf(
            MementoCard(
                levelPosition = 1,
                tagIndexes = listOf(0, 1),
                front = MementoCardSide(
                    text = "勉強[べんきょう]",
                    examples = listOf("勉強する", "勉強になる"),
                    notes = "a note",
                    images = listOf("media/a.jpg"),
                    audios = listOf("media/b.mp3"),
                    isRichText = true,
                ),
                back = MementoCardSide(text = "study"),
            ),
        ),
    )

    @Test
    fun roundTripsThroughJson() {
        val manifest = sampleManifest()
        val encoded = mementoJson.encodeToString(MementoManifest.serializer(), manifest)
        assertEquals(manifest, mementoJson.decodeFromString(MementoManifest.serializer(), encoded))
    }

    @Test
    fun ignoresUnknownKeysFromNewerVersions() {
        val json = """
            {"format":"memento-deck","version":1,"futureField":42,
             "deck":{"name":"x","frontName":"F","backName":"B","brandNewThing":true}}
        """.trimIndent()
        val manifest = mementoJson.decodeFromString(MementoManifest.serializer(), json)
        assertEquals("x", manifest.deck.name)
        assertTrue(manifest.cards.isEmpty())
    }
}
