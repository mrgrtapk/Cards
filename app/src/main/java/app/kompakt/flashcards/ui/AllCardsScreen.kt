package app.kompakt.flashcards.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.kompakt.flashcards.R
import app.kompakt.flashcards.core.Card
import app.kompakt.flashcards.core.cardsOf
import app.kompakt.flashcards.core.deck
import app.kompakt.flashcards.core.deleteCard
import app.kompakt.flashcards.core.folderChain
import app.kompakt.flashcards.core.toggleStar
import app.kompakt.flashcards.data.LibraryStore
import com.mudita.mmd.components.lazy.LazyColumnMMD

private sealed interface CardsDialog {
    data class Actions(val card: Card) : CardsDialog
    data class Delete(val card: Card) : CardsDialog
}

/** Every card in one deck. Tap to edit, long-press for star / move / delete. */
@Composable
fun AllCardsScreen(store: LibraryStore, deckId: String, nav: Navigator) {
    val data by store.data.collectAsState()
    val deck = data.deck(deckId)
    if (deck == null) {
        LaunchedEffect(Unit) { nav.back() }
        return
    }
    val cards = remember(data) { data.cardsOf(deckId) }
    var dialog by remember { mutableStateOf<CardsDialog?>(null) }

    ScreenScaffold(
        title = deck.name,
        titleSuffix = "All cards",
        subtitle = data.folderChain(deck.folderId).joinToString(" / ") { it.name }.ifEmpty { null },
        onBack = nav::back,
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (cards.isEmpty()) {
                EmptyState(title = "No cards yet", message = "Use + New ▸ Card to add one, or send this deck from your computer.")
            } else {
                LazyColumnMMD(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(cards, key = { _, card -> card.id }) { index, card ->
                        CardRow(
                            card = card,
                            onClick = { nav.go(Route.EditCard(deckId, card.id)) },
                            onLongClick = { dialog = CardsDialog.Actions(card) },
                            onToggleStar = { store.update { it.toggleStar(card.id) } },
                        )
                        if (index < cards.lastIndex) ListDivider()
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        is CardsDialog.Actions -> ActionsDialog(
            title = d.card.front.lineSequence().first(),
            actions = listOf(
                (if (d.card.starred) "Unstar" else "Star") to {
                    store.update { it.toggleStar(d.card.id) }
                    dialog = null
                },
                "Move to another deck" to {
                    dialog = null
                    nav.go(Route.MoveCard(d.card.id))
                },
                "Delete" to { dialog = CardsDialog.Delete(d.card) },
            ),
            onDismiss = { dialog = null },
        )
        is CardsDialog.Delete -> ConfirmDialog(
            title = "Delete this card?",
            message = d.card.front.take(80),
            confirmLabel = "Delete",
            onConfirm = {
                store.update { it.deleteCard(d.card.id) }
                dialog = null
            },
            onDismiss = { dialog = null },
        )
    }
}
