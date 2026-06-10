package com.annie.memento.ui.importdeck

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.annie.memento.data.ankiImporter.AnkiImporter
import com.annie.memento.data.ankiImporter.AnkiNoteType
import com.annie.memento.data.ankiImporter.ApkgException
import com.annie.memento.data.ankiImporter.ApkgParser
import com.annie.memento.data.ankiImporter.FieldTarget
import com.annie.memento.data.ankiImporter.ImportOptions
import com.annie.memento.data.ankiImporter.ImportProgress
import com.annie.memento.data.ankiImporter.ImportResult
import com.annie.memento.data.ankiImporter.ImportSession
import com.annie.memento.data.ankiImporter.NoteTypeMapping
import com.annie.memento.data.ankiImporter.ParsedApkg
import com.annie.memento.data.ankiImporter.defaultTargets
import com.annie.memento.data.ankiImporter.userMessage
import com.annie.memento.data.mementoZip.ImportKind
import com.annie.memento.data.mementoZip.MementoZipException
import com.annie.memento.data.mementoZip.MementoZipImporter
import com.annie.memento.data.mementoZip.detectImportKind
import com.annie.memento.di.LocalAppGraph
import com.annie.memento.platform.rememberFilePicker
import com.annie.memento.ui.components.EmptyState
import com.annie.memento.ui.components.InfoPill
import com.annie.memento.ui.components.MementoButton
import com.annie.memento.ui.components.MementoOutlineButton
import com.annie.memento.ui.components.MementoScaffold
import com.annie.memento.ui.components.SectionCard
import com.annie.memento.ui.components.SectionHeader
import com.annie.memento.ui.components.dismissKeyboardGestures
import com.annie.memento.ui.navigation.Navigator
import com.annie.memento.ui.navigation.PlatformBackHandler
import com.annie.memento.ui.navigation.Screen
import com.annie.memento.ui.richtext.RichText
import com.annie.memento.ui.theme.ChipShape
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

private sealed interface ImportStep {
    data object Pick : ImportStep

    data object Parsing : ImportStep

    data class Mapping(val parsed: ParsedApkg) : ImportStep

    data class Importing(val parsed: ParsedApkg, val progress: ImportProgress?) : ImportStep

    // memento zips skip review
    data class ImportingMemento(val progress: ImportProgress?) : ImportStep

    data class Failed(val message: String, val retryParsed: ParsedApkg?) : ImportStep
}

@Composable
fun ImportDeckScreen(navigator: Navigator) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf<ImportStep>(ImportStep.Pick) }
    var session by remember { mutableStateOf<ImportSession?>(null) }
    var parseJob by remember { mutableStateOf<Job?>(null) }
    var importJob by remember { mutableStateOf<Job?>(null) }
    var completed by remember { mutableStateOf<ImportResult?>(null) }

    var deckName by remember { mutableStateOf("") }
    var frontName by remember { mutableStateOf("Front") }
    var backName by remember { mutableStateOf("Back") }
    var importTags by remember { mutableStateOf(true) }
    var subdecksAsLevels by remember { mutableStateOf(true) }
    val mappings = remember { mutableStateMapOf<Long, NoteTypeMapping>() }

    DisposableEffect(Unit) {
        onDispose { session?.delete() }
    }

    val pickApkg = rememberFilePicker { picked ->
        if (picked == null) return@rememberFilePicker
        session?.delete()
        val newSession = ImportSession(picked.tempPath, picked.displayName)
        session = newSession
        step = ImportStep.Parsing
        parseJob = scope.launch {
            try {
                when (detectImportKind(newSession.apkgPath)) {
                    ImportKind.MEMENTO_DECK -> {
                        step = ImportStep.ImportingMemento(null)
                        try {
                            val result = MementoZipImporter(graph.repository, graph.mediaStorage)
                                .import(newSession.apkgPath) { progress -> step = ImportStep.ImportingMemento(progress) }
                            completed = result
                        } catch (e: CancellationException) {
                            session?.delete()
                            session = null
                            step = ImportStep.Pick
                            throw e
                        }
                    }

                    ImportKind.ANKI_APKG -> {
                        val parsed = ApkgParser().parse(newSession)
                        ensureActive()
                        deckName = parsed.suggestedDeckName
                        mappings.clear()
                        parsed.noteTypes.forEach { type ->
                            mappings[type.id] = NoteTypeMapping(type.id, include = true, targets = defaultTargets(type.fields))
                        }
                        importTags = parsed.hasTags
                        subdecksAsLevels = parsed.subdecks.size > 1
                        step = ImportStep.Mapping(parsed)
                    }

                    ImportKind.UNKNOWN -> {
                        step = ImportStep.Failed(
                            "This file isn't a supported deck format. Choose an Anki .apkg or a Memento deck .zip.",
                            retryParsed = null,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                step = ImportStep.Failed(e.toUserMessage(), retryParsed = null)
            }
        }
    }

    fun startImport(parsed: ParsedApkg) {
        val options = ImportOptions(
            deckName = deckName.trim(),
            description = "Imported from “${session?.displayName ?: "Anki"}”",
            frontName = frontName.trim().ifBlank { "Front" },
            backName = backName.trim().ifBlank { "Back" },
            importTags = importTags && parsed.hasTags,
            subdecksAsLevels = subdecksAsLevels && parsed.subdecks.size > 1,
        )
        step = ImportStep.Importing(parsed, null)
        importJob = scope.launch {
            try {
                val result = AnkiImporter(graph.repository, graph.mediaStorage)
                    .import(parsed, mappings.values.toList(), options) { progress ->
                        step = ImportStep.Importing(parsed, progress)
                    }
                completed = result
            } catch (e: CancellationException) {
                step = ImportStep.Mapping(parsed)
                throw e
            } catch (e: Exception) {
                step = ImportStep.Failed(e.toUserMessage(), retryParsed = parsed)
            }
        }
    }

    fun stepBack() {
        when (val current = step) {
            is ImportStep.Pick -> navigator.pop()
            is ImportStep.Parsing -> {
                parseJob?.cancel()
                session?.delete()
                session = null
                step = ImportStep.Pick
            }
            is ImportStep.Mapping -> {
                session?.delete()
                session = null
                step = ImportStep.Pick
            }
            is ImportStep.Importing -> importJob?.cancel()
            is ImportStep.ImportingMemento -> parseJob?.cancel()
            is ImportStep.Failed -> step = current.retryParsed?.let { ImportStep.Mapping(it) } ?: ImportStep.Pick
        }
    }

    PlatformBackHandler(enabled = step !is ImportStep.Pick) { stepBack() }

    val mappingValidation = (step as? ImportStep.Mapping)?.let { validateMapping(it.parsed, mappings, deckName) }

    MementoScaffold(
        title = "Import deck",
        overline = ".ZIP · .APKG",
        onBack = { stepBack() },
        bottomBar = {
            val mapping = step as? ImportStep.Mapping
            if (mapping != null) {
                Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                    Column(
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val issue = mappingValidation?.error ?: mappingValidation?.warning
                        if (issue != null) {
                            Text(
                                issue,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (mappingValidation?.error != null) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        MementoButton(
                            text = "Start import",
                            onClick = { startImport(mapping.parsed) },
                            enabled = mappingValidation?.error == null,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        )
                    }
                }
            }
        },
    ) { padding ->
        when (val current = step) {
            is ImportStep.Pick -> EmptyState(
                emoji = "⇪",
                title = "Import a deck",
                message = "Choose a Memento deck .zip or an Anki .apkg. Memento decks import exactly as exported; Anki decks get a field-mapping review first.",
                modifier = Modifier.padding(padding),
                action = {
                    MementoButton("Choose file", onClick = pickApkg, leading = "+")
                },
            )

            is ImportStep.Parsing -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CircularProgressIndicator()
                    Text(
                        "READING DECK…",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            is ImportStep.Mapping -> MappingStep(
                parsed = current.parsed,
                mappings = mappings,
                deckName = deckName,
                onDeckName = { deckName = it },
                frontName = frontName,
                onFrontName = { frontName = it },
                backName = backName,
                onBackName = { backName = it },
                importTags = importTags,
                onImportTags = { importTags = it },
                subdecksAsLevels = subdecksAsLevels,
                onSubdecksAsLevels = { subdecksAsLevels = it },
                padding = padding,
            )

            is ImportStep.Importing -> ImportingStep(current.progress, Modifier.padding(padding)) {
                importJob?.cancel()
            }

            is ImportStep.ImportingMemento -> ImportingStep(current.progress, Modifier.padding(padding)) {
                parseJob?.cancel()
            }

            is ImportStep.Failed -> EmptyState(
                emoji = "⚠",
                title = "Import failed",
                message = current.message,
                modifier = Modifier.padding(padding),
                action = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (current.retryParsed != null) {
                            MementoButton("Back to mapping", onClick = { step = ImportStep.Mapping(current.retryParsed) })
                        }
                        MementoOutlineButton("Choose another file", onClick = {
                            session?.delete()
                            session = null
                            step = ImportStep.Pick
                        })
                    }
                },
            )
        }
    }

    completed?.let { result ->
        ImportCompleteDialog(result) {
            completed = null
            navigator.pop()
            navigator.push(Screen.DeckDetail(result.deckId))
        }
    }
}

private fun Exception.toUserMessage(): String = when (this) {
    is MementoZipException -> message ?: "Something went wrong while importing. The deck was not created."
    is ApkgException -> userMessage()
    else -> "Something went wrong while importing. The deck was not created."
}

private data class MappingValidation(val error: String?, val warning: String?)

private fun validateMapping(
    parsed: ParsedApkg,
    mappings: Map<Long, NoteTypeMapping>,
    deckName: String,
): MappingValidation {
    val included = parsed.noteTypes.filter { mappings[it.id]?.include == true }
    val error = when {
        deckName.isBlank() -> "Give the deck a name."
        included.isEmpty() -> "Include at least one note type."
        else -> included.firstNotNullOfOrNull { type ->
            val targets = mappings[type.id]?.targets.orEmpty()
            if (targets.none { it == FieldTarget.FRONT_TEXT }) {
                "Map a field in “${type.name}” to the front text."
            } else {
                null
            }
        }
    }
    val warning = if (error == null &&
        included.all { type -> mappings[type.id]?.targets.orEmpty().none { it == FieldTarget.BACK_TEXT } }
    ) {
        "No field is mapped to the back text — card backs will rely on notes, examples, or media."
    } else {
        null
    }
    return MappingValidation(error, warning)
}

@Composable
private fun MappingStep(
    parsed: ParsedApkg,
    mappings: MutableMap<Long, NoteTypeMapping>,
    deckName: String,
    onDeckName: (String) -> Unit,
    frontName: String,
    onFrontName: (String) -> Unit,
    backName: String,
    onBackName: (String) -> Unit,
    importTags: Boolean,
    onImportTags: (Boolean) -> Unit,
    subdecksAsLevels: Boolean,
    onSubdecksAsLevels: (Boolean) -> Unit,
    padding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).dismissKeyboardGestures(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "options") {
            SectionCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("Deck", trailing = "${parsed.noteCount} notes · ${parsed.mediaCount} media")
                    OutlinedTextField(
                        value = deckName,
                        onValueChange = onDeckName,
                        label = { Text("Deck name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = frontName,
                            onValueChange = onFrontName,
                            label = { Text("Front label") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = backName,
                            onValueChange = onBackName,
                            label = { Text("Back label") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (parsed.hasTags) {
                        ToggleRow("Import tags", importTags, onImportTags)
                    }
                    if (parsed.subdecks.size > 1) {
                        ToggleRow(
                            "Subdecks become levels (${parsed.subdecks.size})",
                            subdecksAsLevels,
                            onSubdecksAsLevels,
                        )
                    }
                }
            }
        }
        items(parsed.noteTypes, key = { it.id }) { type ->
            NoteTypeCard(
                type = type,
                mapping = mappings[type.id] ?: NoteTypeMapping(type.id, true, defaultTargets(type.fields)),
                showIncludeToggle = parsed.noteTypes.size > 1,
                frontName = frontName.ifBlank { "Front" },
                backName = backName.ifBlank { "Back" },
                onChange = { mappings[type.id] = it },
            )
        }
    }
}

@Composable
private fun NoteTypeCard(
    type: AnkiNoteType,
    mapping: NoteTypeMapping,
    showIncludeToggle: Boolean,
    frontName: String,
    backName: String,
    onChange: (NoteTypeMapping) -> Unit,
) {
    SectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        type.name.uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                InfoPill("${type.noteCount} ${if (type.noteCount == 1) "note" else "notes"}")
                if (showIncludeToggle) {
                    Switch(checked = mapping.include, onCheckedChange = { onChange(mapping.copy(include = it)) })
                }
            }
            if (mapping.include) {
                type.fields.forEach { field ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                field.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val sample = field.sample
                            if (sample != null) {
                                // rendered as it will look on card
                                RichText(
                                    markup = sample.text,
                                    rich = sample.isRich,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else {
                                Text(
                                    "(empty in every card)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        TargetSelector(
                            current = mapping.targets.getOrNull(field.ord) ?: FieldTarget.SKIP,
                            frontName = frontName,
                            backName = backName,
                            onSelect = { target ->
                                val updated = mapping.targets.toMutableList()
                                while (updated.size <= field.ord) updated.add(FieldTarget.SKIP)
                                updated[field.ord] = target
                                onChange(mapping.copy(targets = updated))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetSelector(
    current: FieldTarget,
    frontName: String,
    backName: String,
    onSelect: (FieldTarget) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val accent = when {
        current == FieldTarget.SKIP -> MaterialTheme.colorScheme.outlineVariant
        current.isFront -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.tertiary
    }
    Box {
        Surface(
            shape = ChipShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
            modifier = Modifier.clip(ChipShape).clickable { expanded = true },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    current.label(frontName, backName).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (current == FieldTarget.SKIP) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text("▾", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            FieldTarget.entries.forEach { target ->
                DropdownMenuItem(
                    text = {
                        Text(
                            target.label(frontName, backName).uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (target == current) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(target)
                    },
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ImportingStep(progress: ImportProgress?, modifier: Modifier = Modifier, onCancel: () -> Unit) {
    Box(modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        SectionCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionHeader("Importing")
                val (label, done, total) = when (progress) {
                    is ImportProgress.ConvertingNotes -> Triple("Reading cards", progress.done, progress.total)
                    is ImportProgress.SavingMedia -> Triple("Saving media", progress.done, progress.total)
                    is ImportProgress.WritingCards -> Triple("Writing cards", progress.done, progress.total)
                    null -> Triple("Preparing", 0, 0)
                }
                Text(
                    "$label…".uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (total > 0) {
                    LinearProgressIndicator(
                        progress = { done.toFloat() / total.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "$done / $total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                MementoOutlineButton("Cancel", onClick = onCancel, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ImportCompleteDialog(result: ImportResult, onOpenDeck: () -> Unit) {
    AlertDialog(
        onDismissRequest = onOpenDeck,
        title = { Text("IMPORT COMPLETE", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${result.cardsImported} cards imported · ${result.mediaSaved} media files",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (result.cardsSkippedEmptyFront > 0) {
                    Text(
                        "${result.cardsSkippedEmptyFront} notes skipped (empty front side)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (result.mediaMissing > 0) {
                    Text(
                        "${result.mediaMissing} media files were missing from the export",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenDeck) {
                Text("OPEN DECK", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
