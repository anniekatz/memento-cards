package com.annie.memento.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember

sealed interface Screen {
    data object DeckList : Screen

    data object ImportDeck : Screen

    data class DeckEditor(val deckId: Long?) : Screen

    data class DeckDetail(val deckId: Long) : Screen

    data class CardEditor(val deckId: Long, val cardId: Long?) : Screen

    data class BulkCardEditor(val deckId: Long, val cardIds: List<Long>) : Screen

    data class Review(val deckId: Long) : Screen
}

class Navigator(start: Screen) {
    private val stack = mutableStateListOf(start)

    val current: Screen get() = stack.last()

    val canGoBack: Boolean get() = stack.size > 1

    fun push(screen: Screen) {
        stack.add(screen)
    }

    fun pop(): Boolean {
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            return true
        }
        return false
    }

    fun popToRoot() {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
}

@Composable
fun rememberNavigator(start: Screen = Screen.DeckList): Navigator = remember { Navigator(start) }
