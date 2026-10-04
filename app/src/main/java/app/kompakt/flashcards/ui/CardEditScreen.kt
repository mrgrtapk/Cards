package app.kompakt.flashcards.ui

import app.kompakt.flashcards.core.RichText
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kompakt.flashcards.core.Card
import app.kompakt.flashcards.core.deck
import app.kompakt.flashcards.core.deckPath
import app.kompakt.flashcards.core.deleteCard
import app.kompakt.flashcards.core.moveCard
import app.kompakt.flashcards.core.newId
import app.kompakt.flashcards.core.toggleStar
import app.kompakt.flashcards.core.upsertCard
import app.kompakt.flashcards.data.LibraryStore
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD

@Composable
fun CardEditScreen(store: LibraryStore, deckId: String?, cardId: String?, folderHint: String?, nav: Navigator) {
    CardEditor(
        store = store,
        deckId = deckId,
        cardId = cardId,
        folderHint = folderHint,
        onClose = nav::back,
        nav = nav,
    )
}

/**
 * Write a new card or edit one. The Deck row at the top shows where the card lives —
 * tap it to choose a deck for a new card, or to move an existing one.
 *
 * Used as its own screen and, from the Study screen, in place (with [nav] = null).
 */
@Composable
fun CardEditor(
    store: LibraryStore,
    deckId: String?,
    cardId: String?,
    folderHint: String?,
    onClose: () -> Unit,
    nav: Navigator?,
) {
    val data by store.data.collectAsState()
    val existing = cardId?.let { id -> data.cards.firstOrNull { it.id == id } }
    if (cardId != null && existing == null) {
        LaunchedEffect(Unit) { onClose() } // card was deleted
        return
    }

    var targetDeckId by rememberSaveable { mutableStateOf(deckId) }
    // An existing card may have been moved, so always show the deck it's in now.
    val deck = data.deck(existing?.deckId ?: targetDeckId ?: "")
    // TextFieldValue (not just the text) so the B and I buttons can wrap the selected words.
    var frontField by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(existing?.front ?: "")) }
    var backField by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(existing?.back ?: "")) }
    val front = frontField.text
    val back = backField.text
    var savedCount by rememberSaveable { mutableStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    // "Add card" before a deck was chosen: ask for the deck, then save.
    var saveAfterPick by remember { mutableStateOf(false) }
    val frontFocus = remember { FocusRequester() }

    if (picking) {
        DeckPicker(
            store = store,
            title = if (existing != null) "Move to…" else "Add card to…",
            startFolderId = deck?.folderId ?: folderHint,
            currentDeckId = deck?.id,
            onPick = { id ->
                if (existing != null) store.update { it.moveCard(existing.id, id) } else targetDeckId = id
                picking = false
            },
            onClose = {
                picking = false
                saveAfterPick = false
            },
        )
        return
    }

    LaunchedEffect(saveAfterPick, deck?.id) {
        if (saveAfterPick && deck != null) {
            saveAfterPick = false
            val f = front.trim()
            val b = back.trim()
            if (f.isNotEmpty() && b.isNotEmpty()) {
                store.update { it.upsertCard(Card(newId(), deck.id, f, b)) }
                frontField = TextFieldValue("")
                backField = TextFieldValue("")
                savedCount++
            }
        }
    }

    fun save() {
        val f = front.trim()
        val b = back.trim()
        if (f.isEmpty() || b.isEmpty()) return
        val target = deck
        if (target == null) {
            saveAfterPick = true
            picking = true
            return
        }
        val card = existing?.copy(front = f, back = b) ?: Card(newId(), target.id, f, b)
        store.update { it.upsertCard(card) }
        if (existing != null) {
            onClose()
        } else {
            // Stay here so several cards can be added in a row.
            frontField = TextFieldValue("")
            backField = TextFieldValue("")
            savedCount++
            runCatching { frontFocus.requestFocus() }
        }
    }

    ScreenScaffold(
        title = if (existing != null) "Edit card" else "New card",
        onBack = onClose,
        actions = {
            if (existing != null) {
                StarButton(existing.starred) { store.update { it.toggleStar(existing.id) } }
            }
        },
    ) {
        // Mudita's scrolling list: each part of the form is its own row, so the e-ink scroll
        // bar (with up/down arrows) shows when the form is taller than the screen.
        LazyColumnMMD(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = ScreenPadding, bottom = 24.dp),
            scrollStep = 1,
        ) {
            item(key = "deck") {
                Column {
                    FieldLabel("Deck")
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .border(1.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button, onClickLabel = "Choose deck") { picking = true }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextMMD(
                            text = deck?.let { data.deckPath(it).replace("/", " / ") } ?: "Choose a deck",
                            fontSize = 17.sp,
                            fontWeight = if (deck == null) FontWeight.Normal else FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        TextMMD(text = if (deck == null) "Choose" else "Change", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item(key = "front") {
                Column(Modifier.padding(top = 20.dp)) {
                    FieldLabel("Front")
                    TextFieldMMD(
                        value = frontField,
                        onValueChange = { frontField = it },
                        minLines = 2,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(frontFocus),
                    )
                }
            }
            item(key = "back") {
                Column(Modifier.padding(top = 20.dp)) {
                    FieldLabel("Back") { marker -> backField = backField.toggleMarker(marker) }
                    TextFieldMMD(
                        value = backField,
                        onValueChange = { backField = it },
                        minLines = 3,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item(key = "save") {
                Column(Modifier.padding(top = 28.dp)) {
                    PrimaryButton(
                        text = if (existing != null) "Save" else "Add card",
                        enabled = front.isNotBlank() && back.isNotBlank(),
                        onClick = ::save,
                    )
                    if (existing == null && savedCount > 0 && deck != null) {
                        Spacer(Modifier.height(12.dp))
                        TextMMD(text = "$savedCount added to ${deck.name}", fontSize = 14.sp)
                    }
                }
            }
            if (existing != null) {
                item(key = "delete") {
                    Column(Modifier.padding(top = 12.dp)) {
                        SecondaryButton("Delete card", { confirmDelete = true })
                    }
                }
            }
        }
    }

    if (confirmDelete && existing != null) {
        ConfirmDialog(
            title = "Delete this card?",
            message = RichText.plain(existing.front).take(80),
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                store.update { it.deleteCard(existing.id) }
                onClose()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** "Front" / "Back" with small B and I buttons that bold or italicize the selected words. */
@Composable
private fun FieldLabel(text: String, onFormat: ((String) -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextMMD(text = text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (onFormat != null) {
            FormatButton("B", "Bold", FontWeight.Black, FontStyle.Normal) { onFormat("**") }
            Spacer(Modifier.width(8.dp))
            FormatButton("I", "Italics", FontWeight.Medium, FontStyle.Italic) { onFormat("*") }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun FormatButton(label: String, description: String, weight: FontWeight, style: FontStyle, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .border(1.5.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(8.dp))
            .clickable(onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        TextMMD(
            text = AnnotatedString(label, SpanStyle(fontWeight = weight, fontStyle = style)),
            fontSize = 18.sp,
        )
    }
}

/**
 * Wraps the selected words in [marker] ("**" bold, "*" italics), or unwraps them if they're
 * already wrapped. With nothing selected, adds an empty pair with the cursor in the middle.
 */
private fun TextFieldValue.toggleMarker(marker: String): TextFieldValue {
    val s = selection.min
    val e = selection.max
    val t = text
    val m = marker.length
    if (s >= m && e + m <= t.length && t.substring(s - m, s) == marker && t.substring(e, e + m) == marker) {
        return TextFieldValue(t.substring(0, s - m) + t.substring(s, e) + t.substring(e + m), TextRange(s - m, e - m))
    }
    if (s == e) return TextFieldValue(t.substring(0, s) + marker + marker + t.substring(s), TextRange(s + m))
    return TextFieldValue(t.substring(0, s) + marker + t.substring(s, e) + marker + t.substring(e), TextRange(s + m, e + m))
}
