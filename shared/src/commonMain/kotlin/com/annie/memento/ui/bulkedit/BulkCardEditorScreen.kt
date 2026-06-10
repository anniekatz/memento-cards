@file:OptIn(ExperimentalLayoutApi::class)

package com.annie.memento.ui.bulkedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.annie.memento.di.LocalAppGraph
import com.annie.memento.ui.components.ColorPickerRow
import com.annie.memento.ui.components.ColoredChip
import com.annie.memento.ui.components.MementoButton
import com.annie.memento.ui.components.MementoOutlineButton
import com.annie.memento.ui.components.MementoScaffold
import com.annie.memento.ui.components.SectionCard
import com.annie.memento.ui.components.SectionHeader
import com.annie.memento.ui.theme.toColor
import kotlinx.coroutines.launch

//bulk edit: add tags (new or existing) and change level
@Composable
fun BulkCardEditorScreen(deckId: Long, cardIds: List<Long>, onClose: () -> Unit) {
    val repo = LocalAppGraph.current.repository
    val scope = rememberCoroutineScope()
    val details by repo.observeDeckDetails(deckId).collectAsState(initial = null)

    var levelId by remember { mutableStateOf<Long?>(null) }
    var addTagIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    // tags created from this screen
    var createdTagIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var addTagDialogOpen by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val cardCount = cardIds.size
    val cardWord = if (cardCount == 1) "card" else "cards"
    val hasChanges = levelId != null || addTagIds.isNotEmpty()

    fun attemptSave() {
        val current = details ?: return
        if (!hasChanges) return
        // only deck's own tags/levels can be applied
        val tagsToAdd = addTagIds intersect (current.tags.map { it.id }.toSet() + createdTagIds)
        val chosenLevel = levelId
        val targetLevel = chosenLevel?.takeIf { id ->
            current.deck.isHierarchical && current.levels.any { it.id == id }
        }

        saving = true
        scope.launch {
            repo.bulkEditCards(cardIds, tagsToAdd, targetLevel)
            onClose()
        }
    }

    MementoScaffold(
        title = "Edit $cardCount $cardWord",
        overline = "BULK EDIT",
        onBack = onClose,
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                    MementoButton(
                        text = "Save changes",
                        onClick = { attemptSave() },
                        enabled = hasChanges && !saving && details != null,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    )
                }
            }
        },
    ) { padding ->
        val current = details
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@MementoScaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader("Selection", trailing = "$cardCount")
                    Text(
                        "Changes below apply to all $cardCount selected $cardWord when you save.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        current.deck.isHierarchical && current.levels.isNotEmpty() -> {
                            SectionHeader("Move to level")
                            Text(
                                "Optional. Tap a level to move every selected card there; tap it again to keep each card's current level.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                current.levels.forEach { level ->
                                    ColoredChip(
                                        label = level.displayName,
                                        color = level.color?.toColor(),
                                        selected = levelId == level.id,
                                        onClick = { levelId = if (levelId == level.id) null else level.id },
                                    )
                                }
                            }
                        }
                        current.deck.isHierarchical -> {
                            SectionHeader("Move to level", accent = MaterialTheme.colorScheme.outline)
                            Text(
                                "This deck doesn't have levels yet. Add them on the Edit Deck page.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        else -> {
                            SectionHeader("Move to level", accent = MaterialTheme.colorScheme.outline)
                            Text(
                                "To assign levels, make this deck hierarchical on the Edit Deck page.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            SectionCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("Add tags")
                    Text(
                        if (current.tags.isEmpty()) {
                            "This deck has no tags yet. Create one and every selected card will get it."
                        } else {
                            "Tags are added to every selected card. Cards that already have a tag keep it as is — no duplicates."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (current.tags.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            current.tags.forEach { tag ->
                                val selected = tag.id in addTagIds
                                ColoredChip(
                                    label = tag.name,
                                    color = tag.color?.toColor(),
                                    selected = selected,
                                    onClick = {
                                        addTagIds = if (selected) addTagIds - tag.id else addTagIds + tag.id
                                    },
                                )
                            }
                        }
                    }
                    MementoOutlineButton(
                        text = "Add new tag…",
                        onClick = { addTagDialogOpen = true },
                        leading = "+",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (addTagDialogOpen) {
        AddTagDialog(
            onDismiss = { addTagDialogOpen = false },
            onAdd = { name, color ->
                addTagDialogOpen = false
                val existing = details?.tags?.firstOrNull { it.name.equals(name, ignoreCase = true) }
                if (existing != null) {
                    addTagIds = addTagIds + existing.id
                } else {
                    scope.launch {
                        val newId = repo.createTag(deckId, name, color)
                        createdTagIds = createdTagIds + newId
                        addTagIds = addTagIds + newId
                    }
                }
            },
        )
    }
}

//name + color for a new deck tag
@Composable
private fun AddTagDialog(onAdd: (String, Long?) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf<Long?>(null) }
    val canAdd = name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New tag".uppercase(), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "The tag is added to this deck and applied to the selected cards when you save.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tag name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ColorPickerRow(selected = color, onSelect = { color = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name.trim(), color) }, enabled = canAdd) {
                Text(
                    "Add".uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (canAdd) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel".uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
