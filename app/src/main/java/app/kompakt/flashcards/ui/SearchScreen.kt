package app.kompakt.flashcards.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.text.style.TextAlign
import app.kompakt.flashcards.core.SearchScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kompakt.flashcards.R
import app.kompakt.flashcards.core.folderChain
import app.kompakt.flashcards.core.isFolderEmpty
import app.kompakt.flashcards.core.search
import app.kompakt.flashcards.core.toggleStar
import app.kompakt.flashcards.data.LibraryStore
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD

/** Find cards, decks and folders by any word in them. */
@Composable
fun SearchScreen(store: LibraryStore, nav: Navigator) {
    val data by store.data.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    // Which parts to look in — the chips under the search box.
    var fronts by rememberSaveable { mutableStateOf(true) }
    var backs by rememberSaveable { mutableStateOf(true) }
    var decksOn by rememberSaveable { mutableStateOf(true) }
    var foldersOn by rememberSaveable { mutableStateOf(true) }
    val scope = SearchScope(fronts = fronts, backs = backs, decks = decksOn, folders = foldersOn)
    val results = remember(data, query, scope) { data.search(query, scope) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    // Put the keyboard away (which also brings the bottom navigation back).
    fun dismissKeyboard() {
        keyboard?.hide()
        focusManager.clearFocus()
    }

    fun open(route: Route) {
        dismissKeyboard()
        nav.go(route)
    }

    ScreenScaffold(title = "Search", onBack = null) {
        TextFieldMMD(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { TextMMD(text = "Search cards, decks, folders", fontSize = 18.sp) },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp),
                )
            },
            trailingIcon = if (query.isNotEmpty()) {
                { IconAction(R.drawable.ic_close, "Clear search") { query = "" } }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { dismissKeyboard() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenPadding, vertical = 12.dp)
                .focusRequester(focus),
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = ScreenPadding, end = ScreenPadding, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ScopeChip("Front", fronts) { fronts = !fronts }
            ScopeChip("Back", backs) { backs = !backs }
            ScopeChip("Deck", decksOn) { decksOn = !decksOn }
            ScopeChip("Folder", foldersOn) { foldersOn = !foldersOn }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                // Tapping anywhere outside the search box closes the keyboard.
                .pointerInput(Unit) { detectTapGestures { dismissKeyboard() } },
        ) {
            when {
                query.isBlank() -> Unit
                results.isEmpty -> EmptyState(title = "No matches", message = "Nothing contains “${query.trim()}”.")
                else -> LazyColumnMMD(modifier = Modifier.fillMaxSize()) {
                    if (results.folders.isNotEmpty()) {
                        item(key = "hf") { SectionHeader("Folders", Modifier.padding(top = 0.dp)) }
                        itemsIndexed(results.folders, key = { _, f -> "f" + f.id }) { index, f ->
                            ListRow(
                                icon = if (data.isFolderEmpty(f.id)) R.drawable.ic_folder else R.drawable.ic_folder_filled,
                                title = f.name,
                                bold = true,
                                subtitle = data.folderChain(f.parentId).joinToString(" / ") { it.name }.ifEmpty { null },
                                onClick = { open(Route.Folder(f.id)) },
                            )
                            if (index < results.folders.lastIndex) ListDivider()
                        }
                    }
                    if (results.decks.isNotEmpty()) {
                        item(key = "hd") { SectionHeader("Decks") }
                        itemsIndexed(results.decks, key = { _, d -> "d" + d.id }) { index, d ->
                            ListRow(
                                icon = R.drawable.ic_deck,
                                title = d.name,
                                bold = true,
                                subtitle = data.folderChain(d.folderId).joinToString(" / ") { it.name }.ifEmpty { null },
                                onClick = { open(Route.Deck(d.id)) },
                            )
                            if (index < results.decks.lastIndex) ListDivider()
                        }
                    }
                    if (results.cards.isNotEmpty()) {
                        item(key = "hc") { SectionHeader("Cards · ${results.cards.size}") }
                        itemsIndexed(results.cards, key = { _, c -> "c" + c.id }) { index, card ->
                            CardRow(
                                card = card,
                                place = deckLabel(data, card.deckId),
                                onClick = { open(Route.EditCard(card.deckId, card.id)) },
                                onToggleStar = { store.update { it.toggleStar(card.id) } },
                            )
                            if (index < results.cards.lastIndex) ListDivider()
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/** Filled when that part is being searched. */
@Composable
private fun RowScope.ScopeChip(label: String, selected: Boolean, onToggle: () -> Unit) {
    SmallChip(label = label, selected = selected, modifier = Modifier.weight(1f), onClick = onToggle)
}
