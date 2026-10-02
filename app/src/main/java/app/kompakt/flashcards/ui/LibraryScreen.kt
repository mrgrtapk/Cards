package app.kompakt.flashcards.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import app.kompakt.flashcards.core.deck
import app.kompakt.flashcards.core.deckText
import app.kompakt.flashcards.core.exportFileName
import app.kompakt.flashcards.core.exportFolderZip
import app.kompakt.flashcards.core.exportItemsZip
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.kompakt.flashcards.BuildConfig
import app.kompakt.flashcards.R
import app.kompakt.flashcards.core.Deck
import app.kompakt.flashcards.core.DeckCounts
import app.kompakt.flashcards.core.Folder
import app.kompakt.flashcards.core.LibraryData
import app.kompakt.flashcards.core.childFolders
import app.kompakt.flashcards.core.counts
import app.kompakt.flashcards.core.countsByDeck
import app.kompakt.flashcards.core.practiceCards
import app.kompakt.flashcards.core.shift
import app.kompakt.flashcards.core.sortByName
import app.kompakt.flashcards.core.resetFolder
import app.kompakt.flashcards.core.orderedFolders
import app.kompakt.flashcards.core.orderedDecks
import app.kompakt.flashcards.core.decksIn
import app.kompakt.flashcards.core.decksUnder
import app.kompakt.flashcards.core.deleteDeck
import app.kompakt.flashcards.core.deleteFolder
import app.kompakt.flashcards.core.folder
import app.kompakt.flashcards.core.folderChain
import app.kompakt.flashcards.core.isFolderEmpty
import app.kompakt.flashcards.core.nameTakenInFolder
import app.kompakt.flashcards.core.plural
import app.kompakt.flashcards.core.renameDeck
import app.kompakt.flashcards.core.renameFolder
import app.kompakt.flashcards.data.AppSettings
import app.kompakt.flashcards.data.LibraryStore
import app.kompakt.flashcards.data.NavTab
import app.kompakt.flashcards.data.SettingsStore
import com.mudita.mmd.components.lazy.LazyColumnMMD

private sealed interface LibraryDialog {
    data class FolderActions(val id: String, val name: String) : LibraryDialog
    data class DeckActions(val id: String, val name: String) : LibraryDialog
    data class RenameFolder(val id: String, val name: String, val parentId: String?) : LibraryDialog
    data class RenameDeck(val id: String, val name: String, val folderId: String?) : LibraryDialog
    data class DeleteFolder(val id: String, val name: String) : LibraryDialog
    data class DeleteDeck(val id: String, val name: String) : LibraryDialog
    data object New : LibraryDialog
    data class ResetFolder(val id: String?, val name: String) : LibraryDialog
    data class DeleteSelected(val folderIds: Set<String>, val deckIds: Set<String>) : LibraryDialog
}

/** The library (top level, [folderId] = null) or the inside of one folder. */
@Composable
fun LibraryScreen(store: LibraryStore, settingsStore: SettingsStore, folderId: String?, nav: Navigator) {
    val data by store.data.collectAsState()
    val settings by settingsStore.settings.collectAsState()
    val folder = data.folder(folderId)
    if (folderId != null && folder == null) {
        LaunchedEffect(Unit) { nav.back() } // folder was deleted
        return
    }

    val now = remember(data) { System.currentTimeMillis() }
    val perDeck = remember(data, now) { data.countsByDeck(now) }
    val manual = settings.isManual(folderId)
    val subfolders = data.orderedFolders(folderId, manual)
    val decks = data.orderedDecks(folderId, manual)
    val total = sumCounts(data, folderId, perDeck)
    var dialog by remember { mutableStateOf<LibraryDialog?>(null) }
    // Arrange mode: up/down arrows on each row to put things in your own order.
    var arranging by remember { mutableStateOf(false) }
    // Select mode: check boxes on each row, then Move / Export / Delete them together.
    var selecting by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(setOf<String>()) } // "f" + folder id, "d" + deck id
    val allKeys = subfolders.map { "f" + it.id } + decks.map { "d" + it.id }
    val selected = picked.filter { it in allKeys }.toSet() // drop anything deleted meanwhile
    val selFolders = selected.filter { it.startsWith("f") }.map { it.drop(1) }.toSet()
    val selDecks = selected.filter { it.startsWith("d") }.map { it.drop(1) }.toSet()
    fun startSelecting(all: Boolean) {
        arranging = false
        picked = if (all) allKeys.toSet() else emptySet()
        selecting = true
    }
    fun stopSelecting() {
        selecting = false
        picked = emptySet()
    }
    fun toggle(key: String) {
        picked = if (key in selected) selected - key else selected + key
    }
    BackHandler(enabled = selecting) { stopSelecting() }

    // Long-press ▸ Export: the system save screen picks where the file goes.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportBytes by remember { mutableStateOf<(() -> ByteArray)?>(null) }
    fun saveTo(uri: android.net.Uri?) {
        val make = exportBytes ?: return
        exportBytes = null
        if (uri == null) return
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = make()
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("no stream")
                }.isSuccess
            }
            Toast.makeText(context, if (ok) "Exported" else "Couldn't export", Toast.LENGTH_SHORT).show()
        }
    }
    val zipExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { saveTo(it) }
    val textExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { saveTo(it) }

    // ⋮ › Sort: up/down arrows on every row, plus a Sort A–Z button. The first time, the
    // current A–Z order becomes the starting point for arranging by hand.
    fun startSorting() {
        if (!manual) {
            store.update { it.sortByName(folderId) }
            settingsStore.update { it.withManual(folderId, true) }
        }
        selecting = false
        arranging = true
    }
    val orderItems: List<Pair<String, () -> Unit>> = listOf("Sort" to { startSorting() })
    val selectItems: List<Pair<String, () -> Unit>> = listOf(
        "Select" to { startSelecting(all = false) },
        "Select all" to { startSelecting(all = true) },
    )

    ScreenScaffold(
        title = if (selecting) "${selected.size} selected" else folder?.name ?: "Cards",
        subtitle = if (selecting) {
            folder?.name ?: "Home"
        } else {
            folder?.let { f -> data.folderChain(f.parentId).joinToString(" / ") { it.name }.ifEmpty { null } }
        },
        onBack = when {
            selecting -> { { stopSelecting() } }
            folder != null -> { { nav.back() } }
            else -> null
        },
        backIcon = if (selecting) R.drawable.ic_close else R.drawable.ic_back,
        titleIcon = if (folder == null && !selecting) R.drawable.ic_cards_outline else null,
        actions = actions@{
            if (selecting) {
                if (selected.size < allKeys.size) {
                    TextAction("Select all") { picked = allKeys.toSet() }
                } else {
                    TextAction("Deselect all") { picked = emptySet() }
                }
                return@actions
            }
            val playShown = if (folder == null) settings.showPlayOnHome else settings.showPlayButton
            if (!arranging && total.total > 0 && playShown) {
                val deckIds = data.decksUnder(folderId).map { it.id }.toSet()
                val title = folder?.name ?: "All decks"
                PlayMenu(
                    settings = settings,
                    studyCount = studyCount(data.counts(deckIds, settings.practiceFilters, now)),
                    practiceCount = { f -> data.practiceCards(deckIds, f, now).size },
                    studyCountFor = { f -> studyCount(data.counts(deckIds, f, now)) },
                    onStudy = { nav.go(Route.Study(deckIds, title, practice = false, filters = settings.practiceFilters)) },
                    onPractice = { f ->
                        val ids = store.data.value.practiceCards(deckIds, f, System.currentTimeMillis()).map { it.id }
                        nav.go(Route.Study(deckIds, title, practice = true, cardIds = ids))
                    },
                    onFiltersChange = { f -> settingsStore.update { it.copy(practiceFilters = f) } },
                    onToggleShuffle = { settingsStore.update { it.copy(shuffle = !it.shuffle) } },
                )
            }
            if (folder != null) {
                // "+ New" in the bottom bar covers this unless it's been hidden there.
                if (NavTab.NEW !in settings.visibleTabs) {
                    IconAction(R.drawable.ic_add, "New card, deck, or folder") { dialog = LibraryDialog.New }
                }
                OverflowMenu(
                    listOf(
                        "Rename folder" to { dialog = LibraryDialog.RenameFolder(folder.id, folder.name, folder.parentId) },
                        "Move folder" to { nav.go(Route.MoveItem(folderIds = setOf(folder.id))) },
                    ) + (if (allKeys.isNotEmpty()) selectItems else emptyList()) + orderItems + listOf(
                        "Reset progress" to { dialog = LibraryDialog.ResetFolder(folder.id, folder.name) },
                        "Delete folder" to { dialog = LibraryDialog.DeleteFolder(folder.id, folder.name) },
                    ),
                )
            } else {
                // These live in the navigation strip unless the user has hidden them there.
                if (NavTab.NEW !in settings.visibleTabs) {
                    IconAction(R.drawable.ic_add, "New card, deck, or folder") { dialog = LibraryDialog.New }
                }
                if (NavTab.SYNC !in settings.visibleTabs) {
                    IconAction(NavTab.SYNC.icon(), NavTab.SYNC.title) { nav.openSync() }
                }
                if (subfolders.isNotEmpty() || decks.isNotEmpty()) OverflowMenu(selectItems + orderItems)
            }
        },
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (subfolders.isEmpty() && decks.isEmpty()) {
                if (folder == null) {
                    EmptyState(
                        title = "No cards yet",
                        message = if (BuildConfig.SYNC_ENABLED) {
                            "Write your cards on your computer and send them here over Wi‑Fi."
                        } else {
                            "Write your cards on a computer and import the files, or tap + to write one here."
                        },
                    ) {
                        PrimaryButton(if (BuildConfig.SYNC_ENABLED) "Open Sync" else "Import cards", { nav.openSync() })
                    }
                } else {
                    EmptyState(
                        title = "Empty folder",
                        message = "Send decks into this folder from your computer, or tap + to add a card, deck, or folder.",
                    )
                }
            } else {
                val rows: List<Any> = subfolders + decks
                LazyColumnMMD(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(rows, key = { _, item -> if (item is Folder) "f" + item.id else "d" + (item as Deck).id }) { index, item ->
                        if (item is Folder) {
                            val i = subfolders.indexOf(item)
                            ListRow(
                                icon = if (data.isFolderEmpty(item.id)) R.drawable.ic_folder else R.drawable.ic_folder_filled,
                                title = item.name,
                                bold = true,
                                subtitle = folderSubtitle(data, item.id, sumCounts(data, item.id, perDeck), settings),
                                onClick = {
                                    when {
                                        selecting -> toggle("f" + item.id)
                                        !arranging -> nav.go(Route.Folder(item.id))
                                    }
                                },
                                onLongClick = { if (!arranging && !selecting) dialog = LibraryDialog.FolderActions(item.id, item.name) },
                                leading = if (selecting) {
                                    { SelectBox("f" + item.id in selected) { toggle("f" + item.id) } }
                                } else {
                                    null
                                },
                                trailing = if (selecting) {
                                    {}
                                } else if (arranging) {
                                    { ArrangeArrows(item.name, i > 0, i < subfolders.lastIndex) { delta -> store.update { it.shift(folderId, item.id, delta) } } }
                                } else {
                                    { Chevron() }
                                },
                            )
                        } else {
                            val d = item as Deck
                            val i = decks.indexOf(d)
                            ListRow(
                                icon = R.drawable.ic_deck,
                                title = d.name,
                                bold = true,
                                subtitle = deckSubtitle(perDeck[d.id] ?: DeckCounts(), settings),
                                onClick = {
                                    when {
                                        selecting -> toggle("d" + d.id)
                                        !arranging -> nav.go(Route.Deck(d.id))
                                    }
                                },
                                onLongClick = { if (!arranging && !selecting) dialog = LibraryDialog.DeckActions(d.id, d.name) },
                                leading = if (selecting) {
                                    { SelectBox("d" + d.id in selected) { toggle("d" + d.id) } }
                                } else {
                                    null
                                },
                                trailing = if (selecting) {
                                    {}
                                } else if (arranging) {
                                    { ArrangeArrows(d.name, i > 0, i < decks.lastIndex) { delta -> store.update { it.shift(folderId, d.id, delta) } } }
                                } else {
                                    { Chevron() }
                                },
                            )
                        }
                        if (index < rows.lastIndex) ListDivider()
                    }
                }
            }
        }

        if (arranging) {
            Row(
                Modifier.padding(ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SecondaryButton("Sort A–Z", { store.update { it.sortByName(folderId) } }, compact = true, modifier = Modifier.weight(1f))
                PrimaryButton("Done", { arranging = false }, modifier = Modifier.weight(1f))
            }
        }
        if (selecting) {
            Row(
                Modifier.padding(ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val any = selected.isNotEmpty()
                SecondaryButton("Move", enabled = any, compact = true, modifier = Modifier.weight(1f), onClick = {
                    val route = Route.MoveItem(deckIds = selDecks, folderIds = selFolders)
                    stopSelecting()
                    nav.go(route)
                })
                SecondaryButton("Export", enabled = any, compact = true, modifier = Modifier.weight(1f), onClick = {
                    val f = selFolders
                    val d = selDecks
                    exportBytes = { store.data.value.exportItemsZip(f, d) }
                    zipExporter.launch(exportFileName(folder?.name ?: "Cards", "zip"))
                    stopSelecting()
                })
                SecondaryButton("Delete", enabled = any, compact = true, modifier = Modifier.weight(1f), onClick = {
                    dialog = LibraryDialog.DeleteSelected(selFolders, selDecks)
                })
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        LibraryDialog.New -> NewItemFlow(
            store = store,
            parentId = folderId,
            onDismiss = { dialog = null },
            onCreatedDeck = { deckId ->
                dialog = null
                nav.go(Route.Deck(deckId))
            },
            onNewCard = {
                dialog = null
                nav.go(Route.EditCard(deckId = null, cardId = null, folderHint = folderId))
            },
        )
        is LibraryDialog.FolderActions -> ActionsDialog(
            title = d.name,
            actions = listOf(
                "Rename" to { dialog = LibraryDialog.RenameFolder(d.id, d.name, folderId) },
                "Move" to {
                    dialog = null
                    nav.go(Route.MoveItem(folderIds = setOf(d.id)))
                },
                "Sort" to {
                    dialog = null
                    startSorting()
                },
                "Export" to {
                    dialog = null
                    val id = d.id
                    exportBytes = { store.data.value.exportFolderZip(id) }
                    zipExporter.launch(exportFileName(d.name, "zip"))
                },
                "Delete" to { dialog = LibraryDialog.DeleteFolder(d.id, d.name) },
            ),
            onDismiss = { dialog = null },
        )
        is LibraryDialog.DeckActions -> ActionsDialog(
            title = d.name,
            actions = listOf(
                "Rename" to { dialog = LibraryDialog.RenameDeck(d.id, d.name, folderId) },
                "Move" to {
                    dialog = null
                    nav.go(Route.MoveItem(deckIds = setOf(d.id)))
                },
                "Sort" to {
                    dialog = null
                    startSorting()
                },
                "Export" to {
                    dialog = null
                    val id = d.id
                    exportBytes = {
                        val lib = store.data.value
                        lib.deck(id)?.let { lib.deckText(it) }.orEmpty().toByteArray(Charsets.UTF_8)
                    }
                    textExporter.launch(exportFileName(d.name, "txt"))
                },
                "Delete" to { dialog = LibraryDialog.DeleteDeck(d.id, d.name) },
            ),
            onDismiss = { dialog = null },
        )
        is LibraryDialog.RenameFolder -> TextInputDialog(
            title = "Rename folder",
            initial = d.name,
            confirmLabel = "Save",
            validate = { name -> if (data.nameTakenInFolder(d.parentId, name, exceptId = d.id)) "That name is already used here" else null },
            onConfirm = { name -> store.update { it.renameFolder(d.id, name) }; dialog = null },
            onDismiss = { dialog = null },
        )
        is LibraryDialog.RenameDeck -> TextInputDialog(
            title = "Rename deck",
            initial = d.name,
            confirmLabel = "Save",
            validate = { name -> if (data.nameTakenInFolder(d.folderId, name, exceptId = d.id)) "That name is already used here" else null },
            onConfirm = { name -> store.update { it.renameDeck(d.id, name) }; dialog = null },
            onDismiss = { dialog = null },
        )
        is LibraryDialog.DeleteFolder -> ConfirmDialog(
            title = "Delete “${d.name}”?",
            message = "Everything inside it — folders, decks, and study progress — will be removed from this device.",
            confirmLabel = "Delete",
            onConfirm = { store.update { it.deleteFolder(d.id) }; dialog = null },
            onDismiss = { dialog = null },
        )
        is LibraryDialog.ResetFolder -> ConfirmDialog(
            title = "Reset progress?",
            message = "Every card in “${d.name}”, including its sub-folders, will be treated as new again. Stars are kept.",
            confirmLabel = "Reset",
            onConfirm = {
                store.update { it.resetFolder(d.id) }
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        is LibraryDialog.DeleteSelected -> {
            val n = d.folderIds.size + d.deckIds.size
            val what = listOfNotNull(
                d.folderIds.size.takeIf { it > 0 }?.let { "$it ${plural(it, "folder")}" },
                d.deckIds.size.takeIf { it > 0 }?.let { "$it ${plural(it, "deck")}" },
            ).joinToString(" and ")
            ConfirmDialog(
                title = "Delete $n ${plural(n, "item")}?",
                message = "$what — and everything inside them, with study progress — will be removed from this device.",
                confirmLabel = "Delete",
                onConfirm = {
                    store.update { lib ->
                        val noFolders = d.folderIds.fold(lib) { acc, id -> acc.deleteFolder(id) }
                        d.deckIds.fold(noFolders) { acc, id -> acc.deleteDeck(id) }
                    }
                    dialog = null
                    stopSelecting()
                },
                onDismiss = { dialog = null },
            )
        }
        is LibraryDialog.DeleteDeck -> ConfirmDialog(
            title = "Delete “${d.name}”?",
            message = "The deck and its study progress will be removed from this device.",
            confirmLabel = "Delete",
            onConfirm = { store.update { it.deleteDeck(d.id) }; dialog = null },
            onDismiss = { dialog = null },
        )
    }
}

private fun sumCounts(data: LibraryData, folderId: String?, perDeck: Map<String, DeckCounts>): DeckCounts =
    data.decksUnder(folderId).fold(DeckCounts()) { acc, d -> acc + (perDeck[d.id] ?: DeckCounts()) }

/** Cards one session would show: all due + new cards up to the per-session limit. */
fun studyCount(c: DeckCounts): Int = c.due + minOf(c.new, NEW_CARDS_PER_SESSION)

/** "12 cards · 3 due · 5 new" — each part can be hidden in Settings. Null when nothing's left. */
fun deckSubtitle(c: DeckCounts, settings: AppSettings): String? = buildList {
    if (settings.showCardCount) add("${c.total} ${plural(c.total, "card")}")
    if (settings.showDueCount && c.due > 0) add("${c.due} due")
    if (settings.showNewCount && c.new > 0) add("${c.new} new")
}.joinToString(" · ").ifEmpty { null }

/** "1 folder · 7 decks · 116 cards · 3 due" (cards and due include sub-folders). */
private fun folderSubtitle(data: LibraryData, folderId: String, c: DeckCounts, settings: AppSettings): String? {
    val folders = data.childFolders(folderId).size
    val decks = data.decksIn(folderId).size
    if (folders == 0 && decks == 0) return "Empty"
    return buildList {
        if (folders > 0) add("$folders ${plural(folders, "folder")}")
        if (settings.showDeckCount && decks > 0) add("$decks ${plural(decks, "deck")}")
        if (settings.showCardCount && c.total > 0) add("${c.total} ${plural(c.total, "card")}")
        if (settings.showDueCount && c.due > 0) add("${c.due} due")
    }.joinToString(" · ").ifEmpty { null }
}

/** Small up/down arrows used while arranging a folder by hand. */
@Composable
private fun ArrangeArrows(name: String, canUp: Boolean, canDown: Boolean, onMove: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (canUp) {
            IconAction(R.drawable.ic_up, "Move $name up", boxSize = 40.dp, iconSize = 22.dp) { onMove(-1) }
        } else {
            Spacer(Modifier.width(40.dp))
        }
        if (canDown) {
            IconAction(R.drawable.ic_down, "Move $name down", boxSize = 40.dp, iconSize = 22.dp) { onMove(1) }
        } else {
            Spacer(Modifier.width(40.dp))
        }
    }
}
