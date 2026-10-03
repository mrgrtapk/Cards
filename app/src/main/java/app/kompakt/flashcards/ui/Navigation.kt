package app.kompakt.flashcards.ui

import app.kompakt.flashcards.BuildConfig
import com.mudita.mmd.components.text.TextMMD
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import app.kompakt.flashcards.core.PracticeFilter
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.kompakt.flashcards.core.addDeck
import app.kompakt.flashcards.core.addFolder
import app.kompakt.flashcards.core.nameTakenInFolder
import app.kompakt.flashcards.data.LibraryStore
import app.kompakt.flashcards.data.NavTab
import app.kompakt.flashcards.data.SettingsStore

/** Screens opened on top of a tab. The bottom navigation strip is hidden on all of them. */
sealed interface Route {
    data class Folder(val id: String) : Route
    data class Deck(val id: String) : Route

    /** Study [deckIds], or exactly [cardIds] when given (e.g. starred cards). */
    data class Study(
        val deckIds: Set<String>,
        val title: String,
        val practice: Boolean,
        val cardIds: List<String>? = null,
        /** For Study: only the due / new cards that this All / Due / New / Starred choice picks. */
        val filters: Set<PracticeFilter> = emptySet(),
    ) : Route

    /** Every card in a deck, on its own page (so answers aren't on the deck screen). */
    data class AllCards(val deckId: String) : Route

    /**
     * Edit [cardId], or write a new card when it's null. A new card goes into [deckId]
     * if given; otherwise the editor asks which deck, starting the picker in [folderHint].
     */
    data class EditCard(val deckId: String?, val cardId: String?, val folderHint: String? = null) : Route
    data class MoveCard(val cardId: String) : Route

    /** Move a deck or a folder (exactly one is set) into another folder. */
    /** Move one or more decks / folders (all from the same place) to another folder. */
    data class MoveItem(val deckIds: Set<String> = emptySet(), val folderIds: Set<String> = emptySet()) : Route
    data object Sync : Route
    data object Customize : Route
}

class Navigator(
    private val stack: MutableList<Route>,
    private val selectTab: (NavTab) -> Unit,
    private val isTabVisible: (NavTab) -> Boolean,
) {
    fun go(route: Route) {
        stack.add(route)
    }

    fun back() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    /** Straight back to Home, however deep you are. */
    fun home() {
        stack.clear()
        selectTab(NavTab.LIBRARY)
    }

    /** Opens Sync as a tab if it's in the strip, otherwise as a screen of its own. */
    fun openSync() {
        if (isTabVisible(NavTab.SYNC)) {
            stack.clear()
            selectTab(NavTab.SYNC)
        } else {
            go(Route.Sync)
        }
    }
}

/** How many never-seen cards one study session introduces: all of them. */
private const val DAY_MS = 24L * 60 * 60 * 1000

const val NEW_CARDS_PER_SESSION = Int.MAX_VALUE

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlashcardsRoot(store: LibraryStore, settingsStore: SettingsStore) {
    val settings by settingsStore.settings.collectAsState()
    var tab by remember { mutableStateOf(NavTab.LIBRARY) }
    var creating by remember { mutableStateOf(false) }
    val stack = remember { mutableStateListOf<Route>() }
    val nav = remember {
        Navigator(
            stack = stack,
            selectTab = { tab = it },
            isTabVisible = { it in settingsStore.settings.value.visibleTabs },
        )
    }

    // First launch: add the example decks (only into an empty library).
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (!settingsStore.settings.value.seededExamples) {
            if (store.data.value.decks.isEmpty() && store.data.value.folders.isEmpty()) {
                val files = withContext(Dispatchers.IO) { runCatching { readExampleDecks(context) }.getOrDefault(emptyList()) }
                if (files.isNotEmpty()) store.importFiles(files)
            }
            settingsStore.update { it.copy(seededExamples = true) }
        }
    }

    // Every two weeks or every month (as chosen in Settings), a gentle nudge to export a backup.
    val backupImporter = rememberImporter(store, onExported = { settingsStore.markBackedUp() })
    var askBackup by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val s = settingsStore.settings.value
        val now = System.currentTimeMillis()
        if (s.backupPromptAt == 0L) {
            settingsStore.update { it.copy(backupPromptAt = now) } // start counting from the first launch
        } else if (s.backupReminder && store.data.value.cards.isNotEmpty() &&
            now - maxOf(s.lastBackupAt, s.backupPromptAt) > s.backupEveryDays * DAY_MS
        ) {
            askBackup = true
        }
    }

    // If the current tab gets hidden in Settings, fall back to Home.
    LaunchedEffect(settings.visibleTabs) {
        if (tab !in settings.visibleTabs) tab = NavTab.LIBRARY
    }

    BackHandler(enabled = stack.isNotEmpty() || tab != NavTab.LIBRARY) {
        if (stack.isNotEmpty()) nav.back() else tab = NavTab.LIBRARY
    }

    // While the on-screen keyboard is up (e.g. in Search), the bottom navigation steps aside.
    val keyboardOpen = WindowInsets.isImeVisible

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding(),
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val route = stack.lastOrNull()
            // Fresh state (scroll position, dialogs) for every screen.
            key(stack.size, route, tab) {
                when (route) {
                    null -> when (tab) {
                        NavTab.LIBRARY, NavTab.NEW -> LibraryScreen(store, settingsStore, null, nav)
                        NavTab.STARRED -> StarredScreen(store, settingsStore, nav)
                        NavTab.SEARCH -> SearchScreen(store, nav)
                        NavTab.SYNC -> ImportScreen(store, settingsStore, nav, showBack = false)
                        NavTab.SETTINGS -> SettingsScreen(store, settingsStore, nav)
                    }
                    is Route.Folder -> LibraryScreen(store, settingsStore, route.id, nav)
                    is Route.Deck -> DeckScreen(store, settingsStore, route.id, nav)
                    is Route.Study -> StudyScreen(store, settingsStore, route, nav)
                    is Route.AllCards -> AllCardsScreen(store, route.deckId, nav)
                    is Route.EditCard -> CardEditScreen(store, route.deckId, route.cardId, route.folderHint, nav)
                    is Route.MoveCard -> MoveCardScreen(store, route.cardId, nav)
                    is Route.MoveItem -> MoveItemScreen(store, route.deckIds, route.folderIds, nav)
                    Route.Customize -> CustomizeScreen(settingsStore, nav)
                    Route.Sync -> ImportScreen(store, settingsStore, nav, showBack = true)
                }
            }
        }

        // The bar is everywhere except while studying.
        val top = stack.lastOrNull()
        val showNav = !keyboardOpen && top !is Route.Study
        if (showNav) {
            BottomNav(
                tabs = settings.visibleTabs,
                current = tab,
                showLabels = settings.showNavLabels,
                onSelect = { selected ->
                    if (selected == NavTab.NEW) {
                        creating = true
                    } else {
                        // From any depth, a tab takes you to the top of that tab.
                        stack.clear()
                        tab = selected
                    }
                },
            )
        }
    }

    if (!settings.seenWelcome) {
        // Mention the example decks only if they're actually on Home.
        val hasExamples = store.data.value.folders.any { it.parentId == null && it.name == "Examples" }
        WelcomeDialog(mentionExamples = hasExamples) { settingsStore.update { it.copy(seenWelcome = true) } }
    }

    if (askBackup && settings.seenWelcome) {
        val later = {
            askBackup = false
            settingsStore.update { it.copy(backupPromptAt = System.currentTimeMillis()) }
        }
        DialogFrame(onDismiss = later) {
            TextMMD(text = "Time for a backup?", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            TextMMD(
                text = "It's been over ${if (settings.backupEveryDays <= 14) "two weeks" else "a month"} since you saved a copy of your cards. Export them as one .zip and keep it somewhere safe — on this device, a computer, or a cloud drive.",
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )
            Spacer(Modifier.height(18.dp))
            PrimaryButton("Export all cards", {
                later()
                backupImporter.exportAll()
            })
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Not now", later)
            Spacer(Modifier.height(6.dp))
            TextMMD(
                text = "You can turn this reminder off in Settings.",
                fontSize = 13.sp,
                color = MutedText,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (creating) {
        // "+ New" from inside a folder creates things in that folder; from a deck's card
        // list, a new card goes into that deck.
        val hereFolder = (stack.lastOrNull() as? Route.Folder)?.id
        val hereDeck = when (val top = stack.lastOrNull()) {
            is Route.AllCards -> top.deckId
            is Route.Deck -> top.id
            else -> null
        }
        NewItemFlow(
            store = store,
            parentId = hereFolder,
            onDismiss = { creating = false },
            onCreatedDeck = { deckId ->
                creating = false
                if (hereFolder == null) {
                    stack.clear()
                    tab = NavTab.LIBRARY
                }
                nav.go(Route.Deck(deckId))
            },
            onNewCard = {
                creating = false
                nav.go(Route.EditCard(deckId = hereDeck, cardId = null, folderHint = hereFolder))
            },
        )
    }
}

/** The "+" flow: choose folder or deck, then name it. */
@Composable
fun NewItemFlow(
    store: LibraryStore,
    parentId: String?,
    onDismiss: () -> Unit,
    onCreatedDeck: (String) -> Unit,
    onNewCard: () -> Unit,
) {
    var kind by remember { mutableStateOf<String?>(null) }
    val data by store.data.collectAsState()
    when (kind) {
        null -> ActionsDialog(
            title = "New",
            actions = listOf(
                "Card" to onNewCard,
                "Deck" to { kind = "deck" },
                "Folder" to { kind = "folder" },
            ),
            onDismiss = onDismiss,
        )
        "folder" -> TextInputDialog(
            title = "New folder",
            initial = "",
            confirmLabel = "Create",
            validate = { name -> if (data.nameTakenInFolder(parentId, name)) "That name is already used here" else null },
            onConfirm = { name ->
                store.update { it.addFolder(parentId, name) }
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        else -> TextInputDialog(
            title = "New deck",
            initial = "",
            confirmLabel = "Create",
            validate = { name -> if (data.nameTakenInFolder(parentId, name)) "That name is already used here" else null },
            onConfirm = { name ->
                val deck = store.updateAndGet { it.addDeck(parentId, name) }
                onCreatedDeck(deck.id)
            },
            onDismiss = onDismiss,
        )
    }
}
