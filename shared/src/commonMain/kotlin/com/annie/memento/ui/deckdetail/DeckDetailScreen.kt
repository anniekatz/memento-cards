@file:OptIn(ExperimentalLayoutApi::class)

package com.annie.memento.ui.deckdetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.annie.memento.data.mementoZip.MementoZipExporter
import com.annie.memento.di.LocalAppGraph
import com.annie.memento.model.Card
import com.annie.memento.model.CardSide
import com.annie.memento.model.DeckDetails
import com.annie.memento.platform.rememberFileSharer
import com.annie.memento.ui.components.ColoredChip
import com.annie.memento.ui.components.ConfirmDialog
import com.annie.memento.ui.components.GlyphIcon
import com.annie.memento.ui.components.InfoPill
import com.annie.memento.ui.components.MementoScaffold
import com.annie.memento.ui.components.MenuAction
import com.annie.memento.ui.components.MementoButton
import com.annie.memento.ui.components.MementoOutlineButton
import com.annie.memento.ui.components.OverflowMenu
import com.annie.memento.ui.components.SectionHeader
import com.annie.memento.ui.components.StoredImage
import com.annie.memento.ui.components.cornerBrackets
import com.annie.memento.ui.components.dismissKeyboardGestures
import com.annie.memento.ui.navigation.Navigator
import com.annie.memento.ui.navigation.PlatformBackHandler
import com.annie.memento.ui.navigation.Screen
import com.annie.memento.ui.richtext.richTextToPlain
import com.annie.memento.ui.richtext.richTextToSearchText
import com.annie.memento.ui.theme.ButtonShape
import com.annie.memento.ui.theme.NeutralChipColor
import com.annie.memento.ui.theme.InsetShape
import com.annie.memento.ui.theme.PanelShape
import com.annie.memento.ui.theme.TileShape
import com.annie.memento.ui.theme.toColor
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun DeckDetailScreen(navigator: Navigator, deckId: Long) {
    val graph = LocalAppGraph.current
    val repo = graph.repository
    val scope = rememberCoroutineScope()
    val details by repo.observeDeckDetails(deckId).collectAsState(initial = null)
    val cards by repo.observeCards(deckId).collectAsState(initial = emptyList())

    var deckPendingDelete by remember { mutableStateOf(false) }

    // share the deck as a memento zip
    val shareFile = rememberFileSharer()
    var exporting by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf<String?>(null) }

    fun exportDeck() {
        if (exporting) return
        exporting = true
        scope.launch {
            try {
                val zipPath = MementoZipExporter(repo, graph.mediaStorage).export(deckId, graph.cacheDirPath)
                shareFile(zipPath, "application/zip")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                exportError = "Couldn't export this deck. Check free storage and try again."
            } finally {
                exporting = false
            }
        }
    }

    // long press multiselect for bulk edit/delete
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var bulkPendingDelete by remember { mutableStateOf(false) }
    val selectionMode = selectedIds.isNotEmpty()

    // drop selections
    LaunchedEffect(cards) {
        if (selectedIds.isNotEmpty()) {
            val ids = cards.map { it.id }.toSet()
            if (!ids.containsAll(selectedIds)) selectedIds = selectedIds intersect ids
        }
    }

    PlatformBackHandler(enabled = selectionMode) { selectedIds = emptySet() }

    var searchQuery by remember { mutableStateOf("") }
    var filters by remember { mutableStateOf(CardFilters()) }
    var filtersExpanded by remember { mutableStateOf(false) }
    val query = searchQuery.trim()
    val searchIndex = remember(cards) { lazy { cards.associate { it.id to cardSearchText(it) } } }
    val visibleCards = remember(cards, query, filters) {
        val index = if (query.isEmpty()) null else searchIndex.value
        cards.withIndex().filter { (_, card) ->
            (index == null || index[card.id]?.contains(query, ignoreCase = true) == true) && filters.matches(card)
        }
    }

    // drop filters for deleted lvls/tags
    LaunchedEffect(details) {
        val d = details ?: return@LaunchedEffect
        val pruned = filters.pruned(d.levels.mapTo(mutableSetOf()) { it.id }, d.tags.mapTo(mutableSetOf()) { it.id })
        if (pruned != filters) filters = pruned
    }

    MementoScaffold(
        title = if (selectionMode) "${selectedIds.size} selected" else details?.deck?.name ?: "Deck",
        overline = if (selectionMode) "SELECT CARDS" else "DECK",
        onBack = { if (selectionMode) selectedIds = emptySet() else navigator.pop() },
        actions = {
            if (!selectionMode) {
                OverflowMenu(
                    listOf(
                        MenuAction("Edit deck") { navigator.push(Screen.DeckEditor(deckId)) },
                        MenuAction("Export deck") { exportDeck() },
                        MenuAction("Delete deck", destructive = true) { deckPendingDelete = true },
                    ),
                )
            }
        },
        bottomBar = {
            if (selectionMode) {
                Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MementoOutlineButton(
                            text = "Delete",
                            onClick = { bulkPendingDelete = true },
                            leading = "✕",
                            accent = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f).height(52.dp),
                        )
                        MementoButton(
                            text = if (selectedIds.size == 1) "Edit 1 card" else "Edit ${selectedIds.size} cards",
                            onClick = { navigator.push(Screen.BulkCardEditor(deckId, selectedIds.toList())) },
                            leading = "→",
                            modifier = Modifier.weight(1.4f).height(52.dp),
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (details != null && !selectionMode) {
                ExtendedFloatingActionButton(
                    onClick = { navigator.push(Screen.CardEditor(deckId, null)) },
                    shape = ButtonShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    GlyphIcon("+", fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("Add card".uppercase(), style = MaterialTheme.typography.labelLarge)
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

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().dismissKeyboardGestures(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                DeckHeader(
                    details = current,
                    cardCount = cards.size,
                    onReview = { navigator.push(Screen.Review(deckId)) },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(
                    "Cards",
                    trailing = when {
                        cards.isEmpty() -> "empty"
                        query.isNotEmpty() || filters.isActive -> "${visibleCards.size} of ${cards.size}"
                        else -> "${cards.size}"
                    },
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                )
            }
            if (cards.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search cards") },
                        singleLine = true,
                        trailingIcon = if (searchQuery.isEmpty()) {
                            null
                        } else {
                            {
                                Text(
                                    "✕",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.clip(InsetShape).clickable { searchQuery = "" }.padding(10.dp),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    FilterPanel(
                        details = current,
                        filters = filters,
                        expanded = filtersExpanded,
                        onToggleExpanded = { filtersExpanded = !filtersExpanded },
                        onFiltersChange = { filters = it },
                    )
                }
            }
            if (cards.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Surface(
                        shape = PanelShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("∅", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                            Text("No cards yet", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Tap “Add card” to create the first one.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            } else if (visibleCards.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Surface(
                        shape = PanelShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("∅", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                            Text("No matches", style = MaterialTheme.typography.titleSmall)
                            Text(
                                when {
                                    query.isNotEmpty() && filters.isActive -> "No cards contain “$query” and match the filters."
                                    query.isNotEmpty() -> "No cards contain “$query”."
                                    else -> "No cards match the filters."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            if (filters.isActive) {
                                TextButton(onClick = { filters = CardFilters() }) {
                                    Text(
                                        "CLEAR FILTERS",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // keep number throughout search
                items(visibleCards, key = { it.value.id }) { (index, card) ->
                    CardCell(
                        index = index + 1,
                        card = card,
                        details = current,
                        selectionMode = selectionMode,
                        selected = card.id in selectedIds,
                        onEdit = { navigator.push(Screen.CardEditor(deckId, card.id)) },
                        onToggleSelect = {
                            selectedIds = if (card.id in selectedIds) selectedIds - card.id else selectedIds + card.id
                        },
                    )
                }
            }
        }
    }

    if (deckPendingDelete) {
        ConfirmDialog(
            title = "Delete deck?",
            message = "“${details?.deck?.name ?: "This deck"}” and all of its cards will be permanently deleted.",
            onConfirm = {
                deckPendingDelete = false
                scope.launch {
                    repo.deleteDeck(deckId)
                    navigator.popToRoot()
                }
            },
            onDismiss = { deckPendingDelete = false },
        )
    }

    if (bulkPendingDelete) {
        val count = selectedIds.size
        ConfirmDialog(
            title = if (count == 1) "Delete 1 card?" else "Delete $count cards?",
            message = "Are you sure you want to delete ${if (count == 1) "this card" else "these $count cards"}? This can't be undone.",
            onConfirm = {
                bulkPendingDelete = false
                val ids = selectedIds.toList()
                selectedIds = emptySet()
                scope.launch { repo.bulkDeleteCards(ids) }
            },
            onDismiss = { bulkPendingDelete = false },
        )
    }

    if (exporting) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("EXPORTING…", style = MaterialTheme.typography.titleMedium) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Packing deck and media into a zip.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {},
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }

    exportError?.let { message ->
        AlertDialog(
            onDismissRequest = { exportError = null },
            title = { Text("EXPORT FAILED", style = MaterialTheme.typography.titleMedium) },
            text = { Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = { exportError = null }) {
                    Text("OK", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

@Composable
private fun DeckHeader(details: DeckDetails, cardCount: Int, onReview: () -> Unit) {
    val deck = details.deck
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = PanelShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoPill(deck.frontName, accent = MaterialTheme.colorScheme.primary)
                    Text("⇄", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    InfoPill(deck.backName, accent = MaterialTheme.colorScheme.secondary)
                }
                if (!deck.description.isNullOrBlank()) {
                    Text(
                        deck.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                deck.photoPath?.let { photoPath ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        StoredImage(
                            photoPath,
                            Modifier.fillMaxWidth(0.7f).aspectRatio(1f).clip(InsetShape)
                                .cornerBrackets(MaterialTheme.colorScheme.primary, inset = 6.dp),
                            ContentScale.Crop,
                        )
                    }
                }
                if (deck.isHierarchical && details.levels.isNotEmpty()) {
                    LabeledChips("Levels", details.levels.map { it.displayName to it.color?.toColor() })
                }
                if (details.tags.isNotEmpty()) {
                    LabeledChips("Tags", details.tags.map { it.name to it.color?.toColor() })
                }
            }
        }
        MementoButton(
            text = "Review deck",
            onClick = onReview,
            enabled = cardCount > 0,
            leading = "▶",
            modifier = Modifier.fillMaxWidth().height(54.dp),
        )
    }
}

@Composable
private fun LabeledChips(label: String, chips: List<Pair<String, Color?>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEach { (text, color) -> ColoredChip(text, color) }
        }
    }
}

@Composable
private fun FilterPanel(
    details: DeckDetails,
    filters: CardFilters,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onFiltersChange: (CardFilters) -> Unit,
) {
    Surface(
        shape = PanelShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpanded)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(if (expanded) "▾" else "▸", color = MaterialTheme.colorScheme.primary)
                Text("FILTERS", style = MaterialTheme.typography.labelLarge)
                if (filters.isActive) {
                    Text(
                        "· ${filters.activeCount}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (filters.isActive) {
                    Text(
                        "CLEAR",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clip(InsetShape)
                            .clickable { onFiltersChange(CardFilters()) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            if (expanded) {
                Column(
                    Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (details.deck.isHierarchical && details.levels.isNotEmpty()) {
                        FilterChipGroup("Levels") {
                            details.levels.forEach { level ->
                                ColoredChip(
                                    level.displayName,
                                    level.color?.toColor(),
                                    selected = level.id in filters.levelIds,
                                    onClick = { onFiltersChange(filters.copy(levelIds = filters.levelIds.toggled(level.id))) },
                                )
                            }
                            ColoredChip(
                                "No level",
                                null,
                                selected = null in filters.levelIds,
                                onClick = { onFiltersChange(filters.copy(levelIds = filters.levelIds.toggled(null))) },
                            )
                        }
                    }
                    if (details.tags.isNotEmpty()) {
                        FilterChipGroup("Tags") {
                            details.tags.forEach { tag ->
                                ColoredChip(
                                    tag.name,
                                    tag.color?.toColor(),
                                    selected = tag.id in filters.tagIds,
                                    onClick = { onFiltersChange(filters.copy(tagIds = filters.tagIds.toggled(tag.id))) },
                                )
                            }
                            ColoredChip(
                                "Untagged",
                                null,
                                selected = null in filters.tagIds,
                                onClick = { onFiltersChange(filters.copy(tagIds = filters.tagIds.toggled(null))) },
                            )
                        }
                    }
                    FilterChipGroup("Has") {
                        ContentFilter.entries.forEach { filter ->
                            ColoredChip(
                                filter.label,
                                null,
                                selected = filter in filters.content,
                                onClick = { onFiltersChange(filters.copy(content = filters.content.toggled(filter))) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipGroup(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

//preview cards
@Composable
private fun CardCell(
    index: Int,
    card: Card,
    details: DeckDetails,
    selectionMode: Boolean,
    selected: Boolean,
    onEdit: () -> Unit,
    onToggleSelect: () -> Unit,
) {
    val level = details.levelById(card.levelId)
    val tags = details.tagsByIds(card.tagIds)
    // level/tags just become color dots for view
    val dotColors = buildList<Color> {
        if (level != null) add(level.color?.toColor() ?: NeutralChipColor)
        tags.forEach { add(it.color?.toColor() ?: NeutralChipColor) }
    }

    val haptics = LocalHapticFeedback.current
    Surface(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(TileShape).combinedClickable(
            hapticFeedbackEnabled = false,
            onClick = { if (selectionMode) onToggleSelect() else onEdit() },
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggleSelect()
            },
        ),
        shape = TileShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f).compositeOver(MaterialTheme.colorScheme.surfaceContainer)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.fillMaxSize().padding(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "C-${index.toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (selectionMode) {
                    Box(
                        modifier = Modifier
                            .size(15.dp)
                            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .border(
                                BorderStroke(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Text("✓", color = MaterialTheme.colorScheme.onPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (card.front.isRichText) richTextToPlain(card.front.text) else card.front.text,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (card.back.isRichText) richTextToPlain(card.back.text) else card.back.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (dotColors.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    dotColors.forEach { dotColor ->
                        Box(Modifier.size(width = 8.dp, height = 4.dp).background(dotColor))
                    }
                }
            }
        }
    }
}

// can match: main text, examples, notes, furigana/alt text
private fun cardSearchText(card: Card): String = buildString {
    fun appendSide(side: CardSide) {
        (listOf(side.text) + side.examples + listOfNotNull(side.notes)).forEach { piece ->
            if (piece.isNotBlank()) appendLine(if (side.isRichText) richTextToSearchText(piece) else piece)
        }
    }
    appendSide(card.front)
    appendSide(card.back)
}
