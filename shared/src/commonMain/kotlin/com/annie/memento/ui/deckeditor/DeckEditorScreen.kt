package com.annie.memento.ui.deckeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.annie.memento.di.LocalAppGraph
import com.annie.memento.model.LevelDraft
import com.annie.memento.model.MediaInput
import com.annie.memento.model.TagDraft
import com.annie.memento.ui.components.ColorPickerRow
import com.annie.memento.ui.components.MediaField
import com.annie.memento.ui.components.MementoScaffold
import com.annie.memento.ui.components.MementoButton
import com.annie.memento.ui.components.MementoOutlineButton
import com.annie.memento.ui.components.SectionCard
import com.annie.memento.ui.components.SectionHeader
import com.annie.memento.ui.components.SquarePhotoField
import com.annie.memento.ui.components.dismissKeyboardGestures
import com.annie.memento.ui.navigation.Navigator
import com.annie.memento.ui.theme.InsetShape
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private data class EditableLevel(val id: Long?, val name: String, val color: Long?)
private data class EditableTag(val id: Long?, val name: String, val color: Long?)

@Composable
fun DeckEditorScreen(navigator: Navigator, deckId: Long?) {
    val repo = LocalAppGraph.current.repository
    val scope = rememberCoroutineScope()
    val isEditing = deckId != null

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<MediaField?>(null) }
    var front by remember { mutableStateOf("") }
    var back by remember { mutableStateOf("") }
    var hierarchical by remember { mutableStateOf(false) }
    val levels = remember { mutableStateListOf<EditableLevel>() }
    val tags = remember { mutableStateListOf<EditableTag>() }
    var initialized by remember { mutableStateOf(!isEditing) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(deckId) {
        if (deckId != null) {
            val details = repo.observeDeckDetails(deckId).first()
            if (details != null) {
                name = details.deck.name
                description = details.deck.description.orEmpty()
                photo = details.deck.photoPath?.let { MediaField.Existing(it) }
                front = details.deck.frontName
                back = details.deck.backName
                hierarchical = details.deck.isHierarchical
                levels.clear()
                levels.addAll(details.levels.sortedBy { it.position }.map { EditableLevel(it.id, it.name ?: "", it.color) })
                tags.clear()
                tags.addAll(details.tags.map { EditableTag(it.id, it.name, it.color) })
            }
            initialized = true
        }
    }

    fun attemptSave() {
        if (name.isBlank()) {
            error = "Please enter a deck name."
            return
        }
        if (hierarchical && levels.isEmpty()) {
            error = "Add at least one level, or disable hierarchy."
            return
        }
        val frontEffective = front.trim().ifBlank { "Front" }
        val backEffective = back.trim().ifBlank { "Back" }
        val levelDrafts = if (hierarchical) {
            levels.mapIndexed { index, level -> LevelDraft(level.id, index + 1, level.name.trim().ifBlank { null }, level.color) }
        } else {
            emptyList()
        }
        val tagDrafts = tags.filter { it.name.isNotBlank() }.map { TagDraft(it.id, it.name.trim(), it.color) }
        val descEffective = description.trim().ifBlank { null }
        val photoInput = photo?.toInput() ?: MediaInput.None

        saving = true
        scope.launch {
            if (deckId == null) {
                repo.createDeck(name.trim(), descEffective, photoInput, frontEffective, backEffective, hierarchical, levelDrafts, tagDrafts)
            } else {
                repo.updateDeck(deckId, name.trim(), descEffective, photoInput, frontEffective, backEffective, hierarchical, levelDrafts, tagDrafts)
            }
            navigator.pop()
        }
    }

    MementoScaffold(
        title = if (isEditing) "Edit deck" else "New deck",
        overline = "DECK",
        onBack = { navigator.pop() },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                    MementoButton(
                        text = if (isEditing) "Save changes" else "Create deck",
                        onClick = { attemptSave() },
                        enabled = !saving && initialized,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    )
                }
            }
        },
    ) { padding ->
        if (!initialized) return@MementoScaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .dismissKeyboardGestures()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("Deck name")
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; error = null },
                        placeholder = { Text("e.g. Spanish Vocabulary") },
                        singleLine = true,
                        isError = error != null && name.isBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (optional)") },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            SectionCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("Photo (optional)")
                    Text(
                        "Add a photo shown on the deck. It is cropped to a square.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SquarePhotoField(photo = photo, onChange = { photo = it })
                }
            }

            SectionCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("Card sides")
                    Text(
                        "Name the two sides shared by every card. Leave blank to use the defaults.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = front,
                        onValueChange = { front = it },
                        label = { Text("Front") },
                        placeholder = { Text("Front") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = back,
                        onValueChange = { back = it },
                        label = { Text("Back") },
                        placeholder = { Text("Back") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            SectionCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            SectionTitle("Hierarchy levels")
                            Text(
                                "Organize cards into ordered levels. Each card is assigned exactly one level.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = hierarchical,
                            onCheckedChange = {
                                hierarchical = it
                                error = null
                                if (it && levels.isEmpty()) levels.add(EditableLevel(null, "", null))
                            },
                        )
                    }
                    if (hierarchical) {
                        levels.forEachIndexed { index, level ->
                            LevelEditorRow(
                                number = index + 1,
                                level = level,
                                onChange = { levels[index] = it },
                                onRemove = if (levels.size > 1) {
                                    { levels.removeAt(index) }
                                } else {
                                    null
                                },
                            )
                        }
                        MementoOutlineButton("Add level", onClick = { levels.add(EditableLevel(null, "", null)) }, leading = "+")
                    }
                }
            }

            SectionCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("Tags (optional)")
                    Text(
                        "Cards in this deck can be labeled with any of these tags.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    tags.forEachIndexed { index, tag ->
                        TagEditorRow(
                            tag = tag,
                            onChange = { tags[index] = it },
                            onRemove = { tags.removeAt(index) },
                        )
                    }
                    MementoOutlineButton("Add tag", onClick = { tags.add(EditableTag(null, "", null)) }, leading = "+")
                }
            }

            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    SectionHeader(text)
}

@Composable
private fun LevelEditorRow(
    number: Int,
    level: EditableLevel,
    onChange: (EditableLevel) -> Unit,
    onRemove: (() -> Unit)?,
) {
    Surface(
        shape = InsetShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "LVL ${number.toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (onRemove != null) {
                    TextButton(onClick = onRemove) {
                        Text("Remove", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            OutlinedTextField(
                value = level.name,
                onValueChange = { onChange(level.copy(name = it)) },
                placeholder = { Text("Optional name (e.g. Beginner)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("COLOR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ColorPickerRow(selected = level.color, onSelect = { onChange(level.copy(color = it)) })
        }
    }
}

@Composable
private fun TagEditorRow(
    tag: EditableTag,
    onChange: (EditableTag) -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shape = InsetShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = tag.name,
                    onValueChange = { onChange(tag.copy(name = it)) },
                    placeholder = { Text("Tag name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRemove) {
                    Text("Remove", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                }
            }
            ColorPickerRow(selected = tag.color, onSelect = { onChange(tag.copy(color = it)) })
        }
    }
}
