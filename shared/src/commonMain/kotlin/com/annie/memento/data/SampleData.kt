package com.annie.memento.data

import com.annie.memento.model.LevelDraft
import com.annie.memento.model.MediaInput
import com.annie.memento.model.SideInput
import com.annie.memento.model.TagDraft
import kotlinx.coroutines.flow.first

//seeded example data made by claude opus
object SampleData {

    suspend fun seedIfEmpty(repo: MementoRepository) {
        if (repo.observeDeckSummaries().first().isNotEmpty()) return
        seed(repo)
    }

    suspend fun seed(repo: MementoRepository) {
        seedSpanish(repo)
        seedMusicTheory(repo)
        seedJapanese(repo)
    }

    private suspend fun seedSpanish(repo: MementoRepository) {
        val deckId = repo.createDeck(
            name = "Spanish Vocabulary",
            description = "Everyday Spanish words and phrases.",
            photo = MediaInput.None,
            frontName = "Spanish",
            backName = "English",
            isHierarchical = false,
            levels = emptyList(),
            tags = listOf(
                TagDraft(id = null, name = "Noun", color = null),
                TagDraft(id = null, name = "Verb", color = null),
                TagDraft(id = null, name = "Phrase", color = null),
            ),
        )
        val tagId = repo.observeDeckDetails(deckId).first()?.tags?.associate { it.name to it.id }.orEmpty()

        suspend fun card(spanish: String, english: String, examples: List<String>, notes: String?, vararg tags: String) {
            repo.createCard(
                deckId = deckId,
                levelId = null,
                front = SideInput(spanish, examples, notes, emptyList(), emptyList()),
                back = SideInput(english, emptyList(), null, emptyList(), emptyList()),
                tagIds = tags.mapNotNull { tagId[it] }.toSet(),
            )
        }

        card(
            spanish = "Hola",
            english = "Hello",
            examples = listOf("¡Hola! ¿Cómo estás?", "Hola a todos."),
            notes = "A greeting you can use at any time of day.",
            "Phrase",
        )
        card(
            spanish = "Gato",
            english = "Cat",
            examples = listOf("El gato duerme en el sofá.", "Tengo dos gatos."),
            notes = "Masculine noun. Use \"gata\" for a female cat.",
            "Noun",
        )
        card(
            spanish = "Correr",
            english = "To run",
            examples = listOf(
                "Me gusta correr por la mañana.",
                "Ella corre muy rápido.",
                "Corremos juntos los domingos.",
            ),
            notes = "Regular -er verb.",
            "Verb",
        )
        card(
            spanish = "Biblioteca",
            english = "Library",
            examples = listOf("Estudio en la biblioteca todos los días."),
            notes = "False friend: it means \"library\", not \"bookstore\" (that is \"librería\").",
            "Noun",
        )
    }

    private suspend fun seedMusicTheory(repo: MementoRepository) {
        val deckId = repo.createDeck(
            name = "Music Theory",
            description = "Core terms, grouped from basics to intermediate.",
            photo = MediaInput.None,
            frontName = "Term",
            backName = "Definition",
            isHierarchical = true,
            levels = listOf(
                LevelDraft(id = null, position = 1, name = "Basics", color = null),
                LevelDraft(id = null, position = 2, name = "Intermediate", color = null),
            ),
            tags = emptyList(),
        )
        val levelId = repo.observeDeckDetails(deckId).first()?.levels?.associate { (it.name ?: "") to it.id }.orEmpty()

        suspend fun card(level: String, term: String, definition: String, examples: List<String>, notes: String?) {
            repo.createCard(
                deckId = deckId,
                levelId = levelId[level],
                front = SideInput(term, emptyList(), null, emptyList(), emptyList()),
                back = SideInput(definition, examples, notes, emptyList(), emptyList()),
                tagIds = emptySet(),
            )
        }

        card(
            level = "Basics",
            term = "Tempo",
            definition = "The speed of a piece of music.",
            examples = listOf("Allegro = fast", "Adagio = slow"),
            notes = "Measured in beats per minute (BPM).",
        )
        card(
            level = "Basics",
            term = "Dynamics",
            definition = "How loud or soft the music is.",
            examples = listOf("piano (p) = soft", "forte (f) = loud"),
            notes = null,
        )
        card(
            level = "Intermediate",
            term = "Cadence",
            definition = "A chord progression that closes a musical phrase.",
            examples = listOf("Perfect: V → I", "Plagal: IV → I"),
            notes = "Gives the music a sense of resolution.",
        )
        card(
            level = "Intermediate",
            term = "Syncopation",
            definition = "Emphasis placed on normally weak beats.",
            examples = listOf("Common in jazz, funk and reggae."),
            notes = null,
        )
    }

    private suspend fun seedJapanese(repo: MementoRepository) {
        val deckId = repo.createDeck(
            name = "Japanese (Furigana)",
            description = "Kanji with readings shown above, using custom formatting.",
            photo = MediaInput.None,
            frontName = "Japanese",
            backName = "English",
            isHierarchical = false,
            levels = emptyList(),
            tags = emptyList(),
        )

        suspend fun card(japanese: String, english: String, examples: List<String> = emptyList(), notes: String? = null) {
            repo.createCard(
                deckId = deckId,
                levelId = null,
                // testing .. only japanese side is rich
                front = SideInput(japanese, examples, notes, emptyList(), emptyList(), isRichText = true),
                back = SideInput(english, emptyList(), null, emptyList(), emptyList()),
                tagIds = emptySet(),
            )
        }

        card(
            japanese = "食[た]べる",
            english = "to eat",
            examples = listOf("ご飯[はん]を食[た]べる"),
            notes = "Ichidan (る) verb.",
        )
        card(japanese = "日本語[にほんご]", english = "Japanese (language)")
        card(
            japanese = "私[わたし]は<b>学生[がくせい]</b>です",
            english = "I am a student.",
            notes = "学生[がくせい] = student.",
        )
    }
}
