@file:OptIn(ExperimentalLayoutApi::class)

package com.annie.memento.ui.cardeditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.annie.memento.di.LocalAppGraph
import com.annie.memento.model.DeckDetails
import com.annie.memento.model.SideInput
import com.annie.memento.ui.components.AudioListEditor
import com.annie.memento.ui.components.ColoredChip
import com.annie.memento.ui.components.ConfirmDialog
import com.annie.memento.ui.components.ImageListEditor
import com.annie.memento.ui.components.MediaField
import com.annie.memento.ui.components.MementoScaffold
import com.annie.memento.ui.components.MementoButton
import com.annie.memento.ui.components.SectionCard
import com.annie.memento.ui.components.SectionHeader
import com.annie.memento.ui.components.TextListEditor
import com.annie.memento.ui.components.dismissKeyboardGestures
import com.annie.memento.ui.components.toInputs
import com.annie.memento.ui.richtext.RichText
import com.annie.memento.ui.theme.InsetShape
import com.annie.memento.ui.theme.toColor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

//card create/edit
@Composable
fun CardEditorScreen(deckId: Long, cardId: Long?, onClose: () -> Unit) {
    val repo = LocalAppGraph.current.repository
    val scope = rememberCoroutineScope()
    val isEditing = cardId != null

    var details by remember { mutableStateOf<DeckDetails?>(null) }
    var initialized by remember { mutableStateOf(false) }

    var textA by remember { mutableStateOf("") }
    var examplesA by remember { mutableStateOf<List<String>>(emptyList()) }
    var descA by remember { mutableStateOf("") }
    var imagesA by remember { mutableStateOf<List<MediaField>>(emptyList()) }
    var audiosA by remember { mutableStateOf<List<MediaField>>(emptyList()) }
    var richA by remember { mutableStateOf(false) }

    var textB by remember { mutableStateOf("") }
    var examplesB by remember { mutableStateOf<List<String>>(emptyList()) }
    var descB by remember { mutableStateOf("") }
    var imagesB by remember { mutableStateOf<List<MediaField>>(emptyList()) }
    var audiosB by remember { mutableStateOf<List<MediaField>>(emptyList()) }
    var richB by remember { mutableStateOf(false) }

    var levelId by remember { mutableStateOf<Long?>(null) }
    var selectedTags by remember { mutableStateOf<Set<Long>>(emptySet()) }

    var showErrors by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(deckId, cardId) {
        val loaded = repo.observeDeckDetails(deckId).first()
        details = loaded
        if (cardId != null) {
            val card = repo.getCard(cardId)
            if (card != null) {
                textA = card.front.text
                examplesA = card.front.examples
                descA = card.front.notes.orEmpty()
                imagesA = card.front.imagePaths.map { MediaField.Existing(it) }
                audiosA = card.front.audioPaths.map { MediaField.Existing(it) }
                richA = card.front.isRichText
                textB = card.back.text
                examplesB = card.back.examples
                descB = card.back.notes.orEmpty()
                imagesB = card.back.imagePaths.map { MediaField.Existing(it) }
                audiosB = card.back.audioPaths.map { MediaField.Existing(it) }
                richB = card.back.isRichText
                levelId = card.levelId
                selectedTags = card.tagIds
            }
        }
        initialized = true
    }

    val deck = details?.deck

    fun attemptSave() {
        val current = details ?: return
        showErrors = true
        if (textA.isBlank() || textB.isBlank()) return
        if (current.deck.isHierarchical && levelId == null) return

        val effectiveLevel = if (current.deck.isHierarchical) levelId else null
        val frontInput = SideInput(textA.trim(), examplesA.cleaned(), descA.trim().ifBlank { null }, imagesA.toInputs(), audiosA.toInputs(), richA)
        val backInput = SideInput(textB.trim(), examplesB.cleaned(), descB.trim().ifBlank { null }, imagesB.toInputs(), audiosB.toInputs(), richB)
        // use deck's tags
        val validTagIds = current.tags.map { it.id }.toSet()
        val tagIds = selectedTags.intersect(validTagIds)

        saving = true
        scope.launch {
            if (cardId == null) {
                repo.createCard(deckId, effectiveLevel, frontInput, backInput, tagIds)
            } else {
                repo.updateCard(cardId, effectiveLevel, frontInput, backInput, tagIds)
            }
            onClose()
        }
    }

    MementoScaffold(
        title = if (isEditing) "Edit card" else "New card",
        overline = "CARD",
        onBack = onClose,
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    MementoButton(
                        text = if (isEditing) "Save changes" else "Create card",
                        onClick = { attemptSave() },
                        enabled = !saving && initialized,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    )
                    if (isEditing) {
                        TextButton(
                            onClick = { showDeleteConfirm = true },
                            enabled = !saving,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Delete card", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (!initialized || deck == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@MementoScaffold
        }
        val current = details!!

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .dismissKeyboardGestures()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SideEditor(
                sideName = deck.frontName,
                text = textA,
                onText = { textA = it },
                examples = examplesA,
                onExamples = { examplesA = it },
                notes = descA,
                onNotes = { descA = it },
                images = imagesA,
                onImages = { imagesA = it },
                audios = audiosA,
                onAudios = { audiosA = it },
                rich = richA,
                onRich = { richA = it },
                textError = showErrors && textA.isBlank(),
            )
            SideEditor(
                sideName = deck.backName,
                text = textB,
                onText = { textB = it },
                examples = examplesB,
                onExamples = { examplesB = it },
                notes = descB,
                onNotes = { descB = it },
                images = imagesB,
                onImages = { imagesB = it },
                audios = audiosB,
                onAudios = { audiosB = it },
                rich = richB,
                onRich = { richB = it },
                textError = showErrors && textB.isBlank(),
            )

            if (deck.isHierarchical && current.levels.isNotEmpty()) {
                SectionCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionTitle("Level")
                        Text(
                            "Each card must be associated with a level.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            current.levels.forEach { level ->
                                ColoredChip(
                                    label = level.displayName,
                                    color = level.color?.toColor(),
                                    selected = levelId == level.id,
                                    onClick = { levelId = level.id },
                                )
                            }
                        }
                        if (showErrors && levelId == null) {
                            Text("Please select a level.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (current.tags.isNotEmpty()) {
                SectionCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionTitle("Tags")
                        Text(
                            "Optionally label this card. Tap to toggle.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            current.tags.forEach { tag ->
                                val selected = tag.id in selectedTags
                                ColoredChip(
                                    label = tag.name,
                                    color = tag.color?.toColor(),
                                    selected = selected,
                                    onClick = {
                                        selectedTags = if (selected) selectedTags - tag.id else selectedTags + tag.id
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "Delete card?",
            message = "Are you sure you want to delete this card? This can't be undone.",
            onConfirm = {
                showDeleteConfirm = false
                cardId?.let { id ->
                    scope.launch {
                        repo.deleteCard(id)
                        onClose()
                    }
                }
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    SectionHeader(text)
}

@Composable
private fun SideEditor(
    sideName: String,
    text: String,
    onText: (String) -> Unit,
    examples: List<String>,
    onExamples: (List<String>) -> Unit,
    notes: String,
    onNotes: (String) -> Unit,
    images: List<MediaField>,
    onImages: (List<MediaField>) -> Unit,
    audios: List<MediaField>,
    onAudios: (List<MediaField>) -> Unit,
    rich: Boolean,
    onRich: (Boolean) -> Unit,
    textError: Boolean,
) {
    // main text, examples, notes, audio, photos
    SectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(sideName, accent = MaterialTheme.colorScheme.primary)
            // opt in for custom rich text: this side's text fields are read as markup (styling, furigana, etc) when on
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionHeader("Custom formatting")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Styling with <b>, <i>, <u>, <br>; Alt text/Furigana with [] (ex. 漢字[かんじ])",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = rich, onCheckedChange = onRich)
            }
            OutlinedTextField(
                value = text,
                onValueChange = onText,
                label = { Text("Main text") },
                isError = textError,
                supportingText = if (textError) {
                    { Text("Required") }
                } else {
                    null
                },
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            TextListEditor(values = examples, onChange = onExamples, label = "Examples (optional)")
            OutlinedTextField(
                value = notes,
                onValueChange = onNotes,
                label = { Text("Notes (optional)") },
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AudioListEditor(audios, onAudios, modifier = Modifier.weight(1f))
                ImageListEditor(images, onImages, modifier = Modifier.weight(1f))
            }
            if (rich) SidePreview(text = text, examples = examples, notes = notes)
        }
    }
}

//live render rich text
@Composable
private fun SidePreview(text: String, examples: List<String>, notes: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Preview", accent = MaterialTheme.colorScheme.primary)
        Surface(
            shape = InsetShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RichText(
                    markup = text.ifBlank { "—" },
                    rich = true,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                examples.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.let { ex ->
                    RichText(
                        markup = ex.joinToString("\n") { "• $it" },
                        rich = true,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                notes.takeIf { it.isNotBlank() }?.let { note ->
                    RichText(
                        markup = note,
                        rich = true,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}


private fun List<String>.cleaned(): List<String> = mapNotNull { it.trim().ifBlank { null } }
