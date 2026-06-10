@file:OptIn(ExperimentalLayoutApi::class)

package com.annie.memento.ui.decklist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.annie.memento.di.LocalAppGraph
import com.annie.memento.model.DeckSummary
import com.annie.memento.ui.components.ConfirmDialog
import com.annie.memento.ui.components.EmptyState
import com.annie.memento.ui.components.GlyphIcon
import com.annie.memento.ui.components.InfoPill
import com.annie.memento.ui.components.MementoScaffold
import com.annie.memento.ui.components.MenuAction
import com.annie.memento.ui.components.MementoButton
import com.annie.memento.ui.components.MementoIconButton
import com.annie.memento.ui.components.MementoOutlineButton
import com.annie.memento.ui.components.OverflowMenu
import com.annie.memento.ui.components.SectionHeader
import com.annie.memento.ui.components.StoredImage
import com.annie.memento.ui.navigation.Navigator
import com.annie.memento.ui.navigation.Screen
import com.annie.memento.ui.theme.ButtonShape
import com.annie.memento.ui.theme.InsetShape
import com.annie.memento.ui.theme.PanelShape
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.launch

@Composable
fun DeckListScreen(navigator: Navigator) {
    val repo = LocalAppGraph.current.repository
    val scope = rememberCoroutineScope()
    val decks by repo.observeDeckSummaries().collectAsState(initial = emptyList())
    var deckPendingDelete by remember { mutableStateOf<DeckSummary?>(null) }

    MementoScaffold(
        title = "Decks",
        actions = {
            MementoIconButton("⇪", onClick = { navigator.push(Screen.ImportDeck) })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navigator.push(Screen.DeckEditor(null)) },
                shape = ButtonShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                GlyphIcon("+", fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text("New deck".uppercase(), style = MaterialTheme.typography.labelLarge)
            }
        },
    ) { padding ->
        if (decks.isEmpty()) {
            EmptyState(
                emoji = "∅",
                title = "No decks yet",
                message = "Create your first deck to start building flashcards.",
                modifier = Modifier.padding(padding),
                action = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MementoButton("Create a deck", onClick = { navigator.push(Screen.DeckEditor(null)) }, leading = "+")
                        MementoOutlineButton("Import from Anki", onClick = { navigator.push(Screen.ImportDeck) }, leading = "⇪")
                    }
                },
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    SectionHeader(
                        "Archive",
                        trailing = "${decks.size} ${if (decks.size == 1) "unit" else "units"}",
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
                itemsIndexed(decks, key = { _, summary -> summary.deck.id }) { index, summary ->
                    DeckCard(
                        index = index + 1,
                        summary = summary,
                        onOpen = { navigator.push(Screen.DeckDetail(summary.deck.id)) },
                        onEdit = { navigator.push(Screen.DeckEditor(summary.deck.id)) },
                        onDelete = { deckPendingDelete = summary },
                    )
                }
            }
        }
    }

    deckPendingDelete?.let { target ->
        ConfirmDialog(
            title = "Delete deck?",
            message = "“${target.deck.name}” and its ${target.cardCount} card(s) will be permanently deleted.",
            onConfirm = {
                scope.launch { repo.deleteDeck(target.deck.id) }
                deckPendingDelete = null
            },
            onDismiss = { deckPendingDelete = null },
        )
    }
}

@Composable
private fun DeckCard(
    index: Int,
    summary: DeckSummary,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val deck = summary.deck
    Surface(
        modifier = Modifier.fillMaxWidth().clip(PanelShape).clickable(onClick = onOpen),
        shape = PanelShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            // Full-height accent rail — the signature "active channel" marker.
            Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
            Column(Modifier.padding(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    deck.photoPath?.let { photoPath ->
                        Box(
                            Modifier.size(54.dp).clip(InsetShape)
                                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), InsetShape),
                        ) {
                            StoredImage(photoPath, Modifier.fillMaxSize(), ContentScale.Crop)
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "UNIT ${index.toString().padStart(2, '0')}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            deck.name,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${deck.frontName} ⇄ ${deck.backName}".uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!deck.description.isNullOrBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                deck.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    OverflowMenu(
                        listOf(
                            MenuAction("Edit deck", onClick = onEdit),
                            MenuAction("Delete deck", destructive = true, onClick = onDelete),
                        ),
                    )
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    InfoPill(
                        "${summary.cardCount} ${if (summary.cardCount == 1) "card" else "cards"}",
                        accent = MaterialTheme.colorScheme.primary,
                    )
                    if (deck.isHierarchical) {
                        InfoPill(
                            "${summary.levelCount} ${if (summary.levelCount == 1) "level" else "levels"}",
                            accent = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    if (summary.tagCount > 0) {
                        InfoPill(
                            "${summary.tagCount} ${if (summary.tagCount == 1) "tag" else "tags"}",
                            accent = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
        }
    }
}
