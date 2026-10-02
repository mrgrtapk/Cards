package app.kompakt.flashcards.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import app.kompakt.flashcards.core.plural
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kompakt.flashcards.R
import app.kompakt.flashcards.core.Card
import app.kompakt.flashcards.core.Fsrs
import app.kompakt.flashcards.core.cardsOf
import app.kompakt.flashcards.core.counts
import app.kompakt.flashcards.core.deck
import app.kompakt.flashcards.core.deleteDeck
import app.kompakt.flashcards.core.folderChain
import app.kompakt.flashcards.core.nameTakenInFolder
import app.kompakt.flashcards.core.nextDue
import app.kompakt.flashcards.core.practiceCards
import app.kompakt.flashcards.core.renameDeck
import app.kompakt.flashcards.core.resetDeck
import app.kompakt.flashcards.data.LibraryStore
import app.kompakt.flashcards.data.SettingsStore
import com.mudita.mmd.components.text.TextMMD

private enum class DeckDialog { RENAME, RESET, DELETE }

/**
 * A deck at a glance: counts, Study, Practice, Shuffle, and a way into the full card list.
 * The cards themselves live on their own page so the answers aren't sitting here in view.
 */
@Composable
fun DeckScreen(store: LibraryStore, settingsStore: SettingsStore, deckId: String, nav: Navigator) {
    val data by store.data.collectAsState()
    val settings by settingsStore.settings.collectAsState()
    val deck = data.deck(deckId)
    if (deck == null) {
        LaunchedEffect(Unit) { nav.back() }
        return
    }
    val now = remember(data) { System.currentTimeMillis() }
    val cards = remember(data) { data.cardsOf(deckId) }
    val c = remember(data, now) { data.counts(setOf(deckId), now) }
    val starred = cards.count { it.starred }
    var dialog by remember { mutableStateOf<DeckDialog?>(null) }
    var showTips by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = deck.name,
        subtitle = data.folderChain(deck.folderId).joinToString(" / ") { it.name }.ifEmpty { null },
        onBack = nav::back,
        actions = {
            if (settings.showShuffleButton) {
                IconAction(
                    R.drawable.ic_shuffle,
                    if (settings.shuffle) "Shuffle is on" else "Shuffle is off",
                    selected = settings.shuffle,
                ) { settingsStore.update { it.copy(shuffle = !it.shuffle) } }
            }
            // Full-screen tips, just before ⋮ (can be hidden in Settings › Customize).
            if (settings.showTipsButton) {
                IconAction(R.drawable.ic_fullscreen, "Full-screen tips") { showTips = true }
            }
            OverflowMenu(
                listOf(
                    "Rename deck" to { dialog = DeckDialog.RENAME },
                    "Move deck" to { nav.go(Route.MoveItem(deckIds = setOf(deckId))) },
                    "Reset progress" to { dialog = DeckDialog.RESET },
                    "Delete deck" to { dialog = DeckDialog.DELETE },
                ),
            )
        },
    ) {
        // Centered and airy: the deck's size, Study and Practice, and the way into the cards.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val minHeight = maxHeight
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = minHeight)
                    .padding(horizontal = ScreenPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(30.dp))
                    TextMMD(text = c.total.toString(), fontSize = 52.sp, lineHeight = 56.sp, fontWeight = FontWeight.Bold)
                    TextMMD(text = plural(c.total, "card"), fontSize = 17.sp)
                    Spacer(Modifier.height(8.dp))
                    TextMMD(
                        text = buildList {
                            add("$starred starred")
                            if (settings.showDueCount) add("${c.due} due")
                            if (settings.showNewCount) add("${c.new} new")
                        }.joinToString(" · "),
                        fontSize = 14.sp,
                        color = MutedText,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(28.dp))

                    if (settings.menuStudy) {
                        // Study follows the All / Due / New / Starred choice too.
                        val n = studyCount(data.counts(setOf(deckId), settings.practiceFilters, now))
                        PrimaryButton(
                            text = if (n > 0) "Study · $n" else "Nothing to study",
                            enabled = n > 0,
                            icon = if (n > 0) R.drawable.ic_play_filled else null,
                            onClick = { nav.go(Route.Study(setOf(deckId), deck.name, practice = false, filters = settings.practiceFilters)) },
                        )
                        val next = if (n == 0 && c.total > 0) data.nextDue(setOf(deckId)) else null
                        if (next != null) {
                            Spacer(Modifier.height(6.dp))
                            TextMMD(
                                text = "Next review in ${Fsrs.formatInterval(next - now)}",
                                fontSize = 14.sp,
                                color = MutedText,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    if (settings.menuPractice) {
                        val filters = settings.practiceFilters
                        val practiceN = data.practiceCards(setOf(deckId), filters, now).size
                        val start = {
                            val ids = store.data.value.practiceCards(setOf(deckId), filters, System.currentTimeMillis()).map { it.id }
                            nav.go(Route.Study(setOf(deckId), deck.name, practice = true, cardIds = ids))
                        }
                        if (settings.menuStudy) Spacer(Modifier.height(10.dp))
                        val icon = R.drawable.ic_play_filled
                        if (settings.menuStudy) {
                            SecondaryButton("Practice · $practiceN", start, enabled = practiceN > 0, icon = icon)
                        } else {
                            PrimaryButton("Practice · $practiceN", start, enabled = practiceN > 0, icon = icon)
                        }
                    }
                    // Which cards Study and Practice use; the grey words name whichever is shown.
                    if (settings.menuStudy || settings.menuPractice) {
                        Spacer(Modifier.height(4.dp))
                        PracticeFilterPicker(
                            prefix = when {
                                settings.menuStudy && settings.menuPractice -> "Studying or practicing"
                                settings.menuStudy -> "Studying"
                                else -> "Practicing"
                            },
                            filters = settings.practiceFilters,
                            count = { f ->
                                if (settings.menuPractice) {
                                    data.practiceCards(setOf(deckId), f, now).size
                                } else {
                                    studyCount(data.counts(setOf(deckId), f, now))
                                }
                            },
                            onChange = { f -> settingsStore.update { it.copy(practiceFilters = f) } },
                        )
                    }

                }

                // The cards themselves stay a tap away, so the answers aren't in view here.
                Box(Modifier.padding(top = 24.dp, bottom = 16.dp)) {
                    if (cards.isEmpty()) {
                        TextMMD(text = "No cards yet", fontSize = 17.sp, color = MutedText)
                    } else {
                        SeeMoreLink("See all cards") { nav.go(Route.AllCards(deckId)) }
                    }
                }
            }
        }
    }

    if (showTips) StudyTipsDialog(practice = null) { showTips = false }

    // The first time a deck page opens: what Study and Practice each do.
    if (!settings.seenModesTips && (settings.menuStudy || settings.menuPractice)) {
        DialogFrame(onDismiss = { settingsStore.update { it.copy(seenModesTips = true) } }) {
            TextMMD(text = "Study or Practice?", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            TextMMD(text = "Study", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            TextMMD(
                text = "Shows the cards that are due, plus new ones. How you rate each card — Again, Good, or Easy — decides when you'll see it next, spacing reviews out so you remember them longer.",
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )
            Spacer(Modifier.height(12.dp))
            TextMMD(text = "Practice", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            TextMMD(
                text = "Goes through any cards, whenever you like, and never changes your study schedule — good for cramming or a quick review.",
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )
            Spacer(Modifier.height(12.dp))
            TextMMD(
                text = "Tap the line under the buttons to choose which cards: all of them, or any mix of due, new, and starred.",
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = MutedText,
            )
            Spacer(Modifier.height(18.dp))
            PrimaryButton("Okay", { settingsStore.update { it.copy(seenModesTips = true) } })
        }
    }

    when (dialog) {
        null -> Unit
        DeckDialog.RENAME -> TextInputDialog(
            title = "Rename deck",
            initial = deck.name,
            confirmLabel = "Save",
            validate = { name ->
                if (data.nameTakenInFolder(deck.folderId, name, exceptId = deck.id)) "That name is already used here" else null
            },
            onConfirm = { name -> store.update { it.renameDeck(deckId, name) }; dialog = null },
            onDismiss = { dialog = null },
        )
        DeckDialog.RESET -> ConfirmDialog(
            title = "Reset progress?",
            message = "All cards in “${deck.name}” will be treated as new again.",
            confirmLabel = "Reset",
            onConfirm = { store.update { it.resetDeck(deckId) }; dialog = null },
            onDismiss = { dialog = null },
        )
        DeckDialog.DELETE -> ConfirmDialog(
            title = "Delete “${deck.name}”?",
            message = "The deck and its study progress will be removed from this device.",
            confirmLabel = "Delete",
            onConfirm = {
                dialog = null
                store.update { it.deleteDeck(deckId) }
            },
            onDismiss = { dialog = null },
        )
    }
}

/**
 * A card in a list, laid out like the deck rows on Home: star on the left where the deck
 * icon would be, the front in bold, the back on one line beneath, and a chevron.
 */
@Composable
fun CardRow(
    card: Card,
    onClick: () -> Unit,
    onToggleStar: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    place: String? = null,
) {
    ListRow(
        title = card.front.replace('\n', ' '),
        subtitle = card.back.replace('\n', ' '),
        subtitleMaxLines = 1,
        detail = place,
        onClick = onClick,
        onLongClick = onLongClick,
        bold = true,
        titleMaxLines = 2,
        leading = { StarButton(card.starred, onToggleStar) },
    )
}
