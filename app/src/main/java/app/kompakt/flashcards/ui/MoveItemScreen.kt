package app.kompakt.flashcards.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kompakt.flashcards.R
import app.kompakt.flashcards.core.addFolder
import app.kompakt.flashcards.core.canMoveFolderInto
import app.kompakt.flashcards.core.childFolders
import app.kompakt.flashcards.core.deck
import app.kompakt.flashcards.core.decksIn
import app.kompakt.flashcards.core.folder
import app.kompakt.flashcards.core.folderChain
import app.kompakt.flashcards.core.isFolderEmpty
import app.kompakt.flashcards.core.moveDeck
import app.kompakt.flashcards.core.moveFolder
import app.kompakt.flashcards.core.nameTakenInFolder
import app.kompakt.flashcards.data.LibraryStore
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD

/**
 * "Move deck" / "Move folder" / moving a selection: browse to a folder (or Home) and tap
 * "Move here". Everything being moved comes from the same place.
 */
@Composable
fun MoveItemScreen(store: LibraryStore, deckIds: Set<String>, folderIds: Set<String>, nav: Navigator) {
    val data by store.data.collectAsState()
    val decks = deckIds.mapNotNull { data.deck(it) }
    val folders = folderIds.mapNotNull { data.folder(it) }
    if (decks.isEmpty() && folders.isEmpty()) {
        LaunchedEffect(Unit) { nav.back() }
        return
    }
    val count = decks.size + folders.size
    val label = if (count == 1) "“${decks.firstOrNull()?.name ?: folders.first().name}”" else "$count items"
    val currentParent = decks.firstOrNull()?.folderId ?: folders.firstOrNull()?.parentId

    var browsingId by remember { mutableStateOf(currentParent) }
    var newFolder by remember { mutableStateOf(false) }
    val browsing = browsingId?.takeIf { data.folder(it) != null }
    val here = data.folder(browsing)

    // A folder can't go inside itself, so the ones being moved are left out of the list.
    val subfolders = data.childFolders(browsing).filter { it.id !in folderIds }
    val movingIds = deckIds + folderIds
    val clash = (decks.map { it.name } + folders.map { it.name }).firstOrNull { name ->
        data.childFolders(browsing).any { it.id !in movingIds && it.name.equals(name.trim(), ignoreCase = true) } ||
            data.decksIn(browsing).any { it.id !in movingIds && it.name.equals(name.trim(), ignoreCase = true) }
    }
    val problem = when {
        browsing == currentParent -> if (count == 1) "It's already here." else "They're already here."
        folders.any { !data.canMoveFolderInto(it.id, browsing) } -> "A folder can't go inside itself."
        clash != null -> "Something named “$clash” is already here."
        else -> null
    }

    fun up() {
        if (here == null) nav.back() else browsingId = here.parentId
    }
    BackHandler { up() }

    ScreenScaffold(
        title = "Move $label to…",
        subtitle = (listOf("Home") + data.folderChain(browsing).map { it.name }).joinToString(" / "),
        onBack = ::up,
        backIcon = if (here == null) R.drawable.ic_close else R.drawable.ic_back,
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (subfolders.isEmpty()) {
                EmptyState(
                    title = "No folders here",
                    message = "Move it here, or make a new folder for it.",
                )
            } else {
                LazyColumnMMD(modifier = Modifier.fillMaxSize(), scrollStep = 1) {
                    itemsIndexed(subfolders, key = { _, f -> f.id }) { index, f ->
                        ListRow(
                            icon = if (data.isFolderEmpty(f.id)) R.drawable.ic_folder else R.drawable.ic_folder_filled,
                            title = f.name,
                            bold = true,
                            subtitle = null,
                            onClick = { browsingId = f.id },
                        )
                        if (index < subfolders.lastIndex) ListDivider()
                    }
                }
            }
        }
        Column(Modifier.padding(ScreenPadding)) {
            if (problem != null) {
                TextMMD(
                    text = problem,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
            }
            PrimaryButton(
                text = "Move here",
                enabled = problem == null,
                onClick = {
                    store.update { lib ->
                        val withFolders = folderIds.fold(lib) { acc, id -> acc.moveFolder(id, browsing) }
                        deckIds.fold(withFolders) { acc, id -> acc.moveDeck(id, browsing) }
                    }
                    nav.back()
                },
            )
            Spacer(Modifier.height(10.dp))
            SecondaryButton("New folder here", { newFolder = true })
        }
    }

    if (newFolder) {
        TextInputDialog(
            title = "New folder",
            initial = "",
            confirmLabel = "Create",
            validate = { n -> if (data.nameTakenInFolder(browsing, n)) "That name is already used here" else null },
            onConfirm = { n ->
                newFolder = false
                store.update { it.addFolder(browsing, n) }
                // Open the new folder so it can be chosen.
                browsingId = store.data.value.folders.lastOrNull { it.parentId == browsing && it.name == n.trim() }?.id ?: browsing
            },
            onDismiss = { newFolder = false },
        )
    }
}
