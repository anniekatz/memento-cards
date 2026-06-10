@file:OptIn(ExperimentalLayoutApi::class)

package com.annie.memento.ui.review

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.annie.memento.di.LocalAppGraph
import com.annie.memento.model.Card
import com.annie.memento.model.CardSide
import com.annie.memento.model.DeckDetails
import com.annie.memento.ui.cardeditor.CardEditorScreen
import com.annie.memento.ui.components.AudioPlayButton
import com.annie.memento.ui.components.ColoredChip
import com.annie.memento.ui.components.MementoScaffold
import com.annie.memento.ui.components.MementoButton
import com.annie.memento.ui.components.MementoOutlineButton
import com.annie.memento.ui.components.MementoPanel
import com.annie.memento.ui.components.SectionHeader
import com.annie.memento.ui.components.StoredImageAutoHeight
import com.annie.memento.ui.components.cornerBrackets
import com.annie.memento.ui.components.MementoScanlines
import com.annie.memento.ui.navigation.Navigator
import com.annie.memento.ui.navigation.PlatformBackHandler
import com.annie.memento.ui.richtext.RichText
import com.annie.memento.ui.theme.CardPanelShape
import com.annie.memento.ui.theme.InsetShape
import com.annie.memento.ui.theme.toColor

@Composable
fun ReviewScreen(navigator: Navigator, deckId: Long) {
    val repo = LocalAppGraph.current.repository
    val details by repo.observeDeckDetails(deckId).collectAsState(initial = null)
    val cards by repo.observeCards(deckId).collectAsState(initial = emptyList())

    var started by remember { mutableStateOf(false) }
    var startWithA by remember { mutableStateOf(true) }
    var selectedLevels by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var selectedTags by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var shuffle by remember { mutableStateOf(false) }

    var sessionIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var index by remember { mutableStateOf(0) }
    val flipStates = remember { mutableStateMapOf<Int, Boolean>() }

    var editingCardId by remember { mutableStateOf<Long?>(null) }

    val current = details

    val filtered = remember(cards, selectedLevels, selectedTags) {
        cards.filter { card ->
            val levelOk = selectedLevels.isEmpty() || (card.levelId != null && card.levelId in selectedLevels)
            val tagOk = selectedTags.isEmpty() || card.tagIds.any { it in selectedTags }
            levelOk && tagOk
        }
    }
    val sessionCards = remember(sessionIds, cards) {
        val byId = cards.associateBy { it.id }
        sessionIds.mapNotNull { byId[it] }
    }

    LaunchedEffect(sessionCards.size) {
        if (index > sessionCards.lastIndex) index = sessionCards.lastIndex.coerceAtLeast(0)
    }

    PlatformBackHandler(enabled = editingCardId != null) { editingCardId = null }
    val editing = editingCardId
    if (editing != null) {
        CardEditorScreen(deckId = deckId, cardId = editing, onClose = { editingCardId = null })
        return
    }

    MementoScaffold(
        title = current?.deck?.name ?: "Review",
        overline = if (started) "REVIEW · ACTIVE" else "REVIEW · SETUP",
        onBack = { if (started) started = false else navigator.pop() },
    ) { padding ->
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@MementoScaffold
        }

        if (!started) {
            ReviewSetup(
                details = current,
                cardCount = filtered.size,
                startWithA = startWithA,
                onStartSideChange = { startWithA = it },
                selectedLevels = selectedLevels,
                onToggleLevel = { id ->
                    selectedLevels = if (id in selectedLevels) selectedLevels - id else selectedLevels + id
                },
                selectedTags = selectedTags,
                onToggleTag = { id ->
                    selectedTags = if (id in selectedTags) selectedTags - id else selectedTags + id
                },
                shuffle = shuffle,
                onShuffleChange = { shuffle = it },
                onStart = {
                    val ordered = if (shuffle) filtered.shuffled() else filtered
                    sessionIds = ordered.map { it.id }
                    index = 0
                    flipStates.clear()
                    started = true
                },
                modifier = Modifier.padding(padding),
            )
        } else {
            ReviewSession(
                details = current,
                cards = sessionCards,
                startWithA = startWithA,
                index = index,
                onIndexChange = { index = it },
                flipStates = flipStates,
                onRestart = { started = false },
                onEditCard = { sessionCards.getOrNull(index)?.let { editingCardId = it.id } },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

// setting up a review
@Composable
private fun ReviewSetup(
    details: DeckDetails,
    cardCount: Int,
    startWithA: Boolean,
    onStartSideChange: (Boolean) -> Unit,
    selectedLevels: Set<Long>,
    onToggleLevel: (Long) -> Unit,
    selectedTags: Set<Long>,
    onToggleTag: (Long) -> Unit,
    shuffle: Boolean,
    onShuffleChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val deck = details.deck
    Column(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MementoPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("Start side")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SideOption(deck.frontName, startWithA, { onStartSideChange(true) }, Modifier.weight(1f))
                        SideOption(deck.backName, !startWithA, { onStartSideChange(false) }, Modifier.weight(1f))
                    }
                }
            }

            if (deck.isHierarchical && details.levels.isNotEmpty()) {
                FilterSection(
                    title = "Filter by level",
                    subtitle = "Leave empty to include every level.",
                ) {
                    details.levels.forEach { level ->
                        ColoredChip(
                            label = level.displayName,
                            color = level.color?.toColor(),
                            selected = level.id in selectedLevels,
                            onClick = { onToggleLevel(level.id) },
                        )
                    }
                }
            }

            if (details.tags.isNotEmpty()) {
                FilterSection(
                    title = "Filter by tag",
                    subtitle = "Cards matching any selected tag are included.",
                ) {
                    details.tags.forEach { tag ->
                        ColoredChip(
                            label = tag.name,
                            color = tag.color?.toColor(),
                            selected = tag.id in selectedTags,
                            onClick = { onToggleTag(tag.id) },
                        )
                    }
                }
            }

            MementoPanel(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        SectionHeader("Shuffle")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Review the cards in a random order.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = shuffle, onCheckedChange = onShuffleChange)
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.surface) {
            Column {
                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                    MementoButton(
                        text = if (cardCount > 0) {
                            "Start · $cardCount ${if (cardCount == 1) "card" else "cards"}"
                        } else {
                            "No cards match filters"
                        },
                        onClick = onStart,
                        enabled = cardCount > 0,
                        leading = "▶",
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterSection(title: String, subtitle: String, chips: @Composable () -> Unit) {
    MementoPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chips()
            }
        }
    }
}

@Composable
private fun SideOption(name: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clip(InsetShape).clickable(onClick = onClick),
        shape = InsetShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = 15.dp), contentAlignment = Alignment.Center) {
            Text(
                name.uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

//session start

@Composable
private fun ReviewSession(
    details: DeckDetails,
    cards: List<Card>,
    startWithA: Boolean,
    index: Int,
    onIndexChange: (Int) -> Unit,
    flipStates: SnapshotStateMap<Int, Boolean>,
    onRestart: () -> Unit,
    onEditCard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (cards.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No cards to review.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    fun isFlipped(i: Int) = flipStates[i] == true
    fun goTo(target: Int) {
        flipStates[target] = false
        onIndexChange(target)
    }

    // stop any audio when you leave current card
    val audioPlayer = LocalAppGraph.current.audioPlayer
    DisposableEffect(index) {
        onDispose { audioPlayer.stop() }
    }

    val frontName = if (startWithA) details.deck.frontName else details.deck.backName
    val backName = if (startWithA) details.deck.backName else details.deck.frontName

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ProgressReadout(index = index, total = cards.size)

        AnimatedContent(
            targetState = index,
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(300)) { width -> direction * width } + fadeIn(tween(300)))
                    .togetherWith(
                        slideOutHorizontally(tween(300)) { width -> -direction * width } + fadeOut(tween(300)),
                    )
            },
            label = "cardNav",
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { cardIndex ->
            val card = cards[cardIndex.coerceIn(0, cards.lastIndex)]
            FlipCard(
                flipped = isFlipped(cardIndex),
                frontSide = if (startWithA) card.front else card.back,
                backSide = if (startWithA) card.back else card.front,
                frontName = frontName,
                backName = backName,
                onToggle = { flipStates[cardIndex] = !isFlipped(cardIndex) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MementoOutlineButton(
                text = "‹ Prev",
                onClick = { if (index > 0) goTo(index - 1) },
                enabled = index > 0,
                accent = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            MementoButton(
                text = "Flip",
                onClick = { flipStates[index] = !isFlipped(index) },
                modifier = Modifier.weight(1f),
            )
            MementoOutlineButton(
                text = "Next ›",
                onClick = { if (index < cards.lastIndex) goTo(index + 1) },
                enabled = index < cards.lastIndex,
                accent = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MementoOutlineButton(
                text = "↻ Restart",
                onClick = onRestart,
                accent = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            MementoOutlineButton(
                text = "✎ Edit card",
                onClick = onEditCard,
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ProgressReadout(index: Int, total: Int) {
    val fraction = (index + 1).toFloat() / total
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "CARD ${(index + 1).toString().padStart(2, '0')} / ${total.toString().padStart(2, '0')}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${(fraction * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Box(Modifier.fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
        }
    }
}

@Composable
private fun FlipCard(
    flipped: Boolean,
    frontSide: CardSide,
    backSide: CardSide,
    frontName: String,
    backName: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "cardFlip",
    )

    val flash = remember { Animatable(0f) }
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(flipped) {
        if (armed) {
            flash.snapTo(FLASH_ALPHA)
            flash.animateTo(0f, animationSpec = tween(durationMillis = 450))
        }
        armed = true
    }

    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(CardPanelShape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onToggle)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 14f * density
            },
    ) {
        if (rotation <= 90f) {
            CardFace(sideName = frontName, side = frontSide)
        } else {
            //rotate back, so content not mirrored
            Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
                CardFace(sideName = backName, side = backSide)
            }
        }
        if (flash.value > 0f) {
            Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = flash.value)))
        }
    }
}

private const val FLASH_ALPHA = 0.20f

@Composable
private fun CardFace(sideName: String, side: CardSide) {
    Surface(
        modifier = Modifier.fillMaxSize().cornerBrackets(MaterialTheme.colorScheme.primary, topStart = false, bottomEnd = false),
        shape = CardPanelShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
    ) {
        Box(Modifier.fillMaxSize().MementoScanlines()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SectionHeader(sideName, accent = MaterialTheme.colorScheme.primary)
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                ) {
                    RichText(
                        markup = side.text,
                        rich = side.isRichText,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                    )
                    if (side.examples.isNotEmpty()) {
                        ExpandableTextSection(
                            title = "Examples",
                            text = side.examples.joinToString("\n") { "• $it" },
                            textColor = MaterialTheme.colorScheme.onSurface,
                            rich = side.isRichText,
                        )
                    }
                    side.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                        ExpandableTextSection(
                            title = "Notes",
                            text = notes,
                            textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            rich = side.isRichText,
                        )
                    }
                    if (side.audioPaths.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            side.audioPaths.forEach { audioPath -> AudioPlayButton(audioPath) }
                        }
                    }
                    side.imagePaths.forEach { imagePath ->
                        StoredImageAutoHeight(imagePath, Modifier.fillMaxWidth(0.95f), shape = InsetShape)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandableTextSection(
    title: String,
    text: String,
    textColor: Color,
    collapsedMaxLines: Int = 3,
    rich: Boolean = false,
) {
    var expanded by remember(text) { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Box(Modifier.fillMaxWidth()) {
            RichText(
                markup = text,
                rich = rich,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { layout -> if (!expanded) overflows = layout.hasVisualOverflow },
                modifier = Modifier.fillMaxWidth().padding(end = if (overflows) 24.dp else 0.dp),
            )
            if (overflows) {
                Text(
                    text = if (expanded) "▴" else "▾",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .clip(InsetShape)
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 4.dp),
                )
            }
        }
    }
}
