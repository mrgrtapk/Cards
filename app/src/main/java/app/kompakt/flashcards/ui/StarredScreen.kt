package app.kompakt.flashcards.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.kompakt.flashcards.R
import app.kompakt.flashcards.core.LibraryData
import app.kompakt.flashcards.core.deck
import app.kompakt.flashcards.core.deckPath
import app.kompakt.flashcards.core.starredCards
import app.kompakt.flashcards.core.toggleStar
import app.kompakt.flashcards.data.LibraryStore
import app.kompakt.flashcards.data.SettingsStore
import com.mudita.mmd.components.lazy.LazyColumnMMD

/** Every starred card, from any deck. */
@Composable
fun StarredScreen(store: LibraryStore, settingsStore: SettingsStore, nav: Navigator) {
    val data by store.data.collectAsState()
    val settings by settingsStore.settings.collectAsState()
    val starred = remember(data) { data.starredCards() }

    fun practice() {
        nav.go(
            Route.Study(
                deckIds = emptySet(),
                title = "Starred",
                practice = true,
                cardIds = starred.map { it.id },
            ),
        )
    }

    ScreenScaffold(
        title = "Starred",
        onBack = null,
        actions = {
            if (starred.isNotEmpty()) IconAction(R.drawable.ic_play, "Practice starred cards") { practice() }
        },
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (starred.isEmpty()) {
                EmptyState(
                    title = "No starred cards",
                    message = "Tap the star on any card to keep it here for quick practice.",
                )
            } else {
                LazyColumnMMD(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(starred, key = { _, card -> card.id }) { index, card ->
                        CardRow(
                            card = card,
                            onClick = { nav.go(Route.EditCard(card.deckId, card.id)) },
                            onToggleStar = { store.update { it.toggleStar(card.id) } },
                        )
                        if (index < starred.lastIndex) ListDivider()
                    }
                }
            }
        }
    }
}

/** "in Languages / Spanish / Verbs" — where a card lives. */
fun deckLabel(data: LibraryData, deckId: String): String? =
    data.deck(deckId)?.let { "in " + data.deckPath(it).replace("/", " / ") }
