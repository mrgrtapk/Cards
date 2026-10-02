package app.kompakt.flashcards.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import app.kompakt.flashcards.core.Deck
import app.kompakt.flashcards.core.Folder
import app.kompakt.flashcards.core.addDeck
import app.kompakt.flashcards.core.childFolders
import app.kompakt.flashcards.core.decksIn
import app.kompakt.flashcards.core.folder
import app.kompakt.flashcards.core.folderChain
import app.kompakt.flashcards.core.isFolderEmpty
import app.kompakt.flashcards.core.moveCard
import app.kompakt.flashcards.data.LibraryStore
import app.kompakt.flashcards.core.nameTakenInFolder
import com.mudita.mmd.components.lazy.LazyColumnMMD

/** Long-press → "Move to another deck". */
@Composable
fun MoveCardScreen(store: LibraryStore, cardId: String, nav: Navigator) {
    val data by store.data.collectAsState()
    val card = data.cards.firstOrNull { it.id == cardId }
    if (card == null) {
        LaunchedEffect(Unit) { nav.back() }
        return
    }
    DeckPicker(
        store = store,
        title = "Move to…",
        startFolderId = data.decks.firstOrNull { it.id == card.deckId }?.folderId,
        currentDeckId = card.deckId,
        onPick = { deckId ->
            store.update { it.moveCard(cardId, deckId) }
            nav.back()
        },
        onClose = nav::back,
    )
}

/**
 * Full-screen deck chooser. Tap folders to open them, tap a deck to choose it, or create a
 * new deck in the folder you're looking at. Back goes up a folder, then closes.
 */
@Composable
fun DeckPicker(
    store: LibraryStore,
    title: String,
    startFolderId: String?,
    currentDeckId: String?,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
) {
    val data by store.data.collectAsState()
    var folderId by remember { mutableStateOf(startFolderId) }
    var newDeck by remember { mutableStateOf(false) }

    // A folder we were browsing may have been deleted; fall back to the top level.
    val browsing = folderId?.takeIf { data.folder(it) != null }
    val here = data.folder(browsing)
    val rows: List<Any> = data.childFolders(browsing) + data.decksIn(browsing)

    fun up() {
        if (here == null) onClose() else folderId = here.parentId
    }
    BackHandler { up() }

    ScreenScaffold(
        title = title,
        subtitle = (listOf("Home") + data.folderChain(browsing).map { it.name }).joinToString(" / "),
        onBack = ::up,
        backIcon = if (here == null) R.drawable.ic_close else R.drawable.ic_back,
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (rows.isEmpty()) {
                EmptyState(title = "Nothing here", message = "Create a deck in this folder to put the card in it.")
            } else {
                LazyColumnMMD(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(rows, key = { _, item -> if (item is Folder) "f" + item.id else "d" + (item as Deck).id }) { index, item ->
                        if (item is Folder) {
                            ListRow(
                                icon = if (data.isFolderEmpty(item.id)) R.drawable.ic_folder else R.drawable.ic_folder_filled,
                                title = item.name,
                                bold = true,
                                subtitle = null,
                                onClick = { folderId = item.id },
                            )
                        } else {
                            val d = item as Deck
                            val isCurrent = d.id == currentDeckId
                            ListRow(
                                icon = R.drawable.ic_deck,
                                title = d.name,
                                bold = true,
                                subtitle = if (isCurrent) "Current deck" else null,
                                onClick = { if (!isCurrent) onPick(d.id) },
                                trailing = {},
                            )
                        }
                        if (index < rows.lastIndex) ListDivider()
                    }
                }
            }
        }
        Column(Modifier.padding(ScreenPadding)) {
            SecondaryButton("New deck here", { newDeck = true })
        }
    }

    if (newDeck) {
        TextInputDialog(
            title = "New deck",
            initial = "",
            confirmLabel = "Create",
            validate = { name -> if (data.nameTakenInFolder(browsing, name)) "That name is already used here" else null },
            onConfirm = { name ->
                newDeck = false
                val deck = store.updateAndGet { it.addDeck(browsing, name) }
                onPick(deck.id)
            },
            onDismiss = { newDeck = false },
        )
    }
}
