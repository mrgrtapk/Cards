package app.kompakt.flashcards.core

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A deck file sent from a computer (or read from a folder on the phone). */
data class IncomingFile(val path: String, val content: String)

data class ImportReport(
    val decksAdded: Int = 0,
    val decksUpdated: Int = 0,
    val decksRemoved: Int = 0,
    val cardsAdded: Int = 0,
    val cardsChanged: Int = 0,
    val cardsRemoved: Int = 0,
    val cardsTotal: Int = 0,
    val filesIgnored: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
) {
    val summary: String
        get() = buildList {
            val decks = decksAdded + decksUpdated
            add("$decks ${plural(decks, "deck")}, $cardsTotal ${plural(cardsTotal, "card")}")
            if (cardsAdded > 0) add("$cardsAdded new")
            if (cardsChanged > 0) add("$cardsChanged edited")
            if (cardsRemoved > 0) add("$cardsRemoved removed")
            if (decksRemoved > 0) add("${decksRemoved} ${plural(decksRemoved, "deck")} removed")
        }.joinToString(" · ")
}

fun plural(n: Int, word: String) = if (n == 1) word else word + "s"

private val nameComparator: Comparator<String> = String.CASE_INSENSITIVE_ORDER

fun LibraryData.childFolders(parentId: String?): List<Folder> =
    folders.filter { it.parentId == parentId }.sortedWith(compareBy(nameComparator) { it.name })

fun LibraryData.decksIn(folderId: String?): List<Deck> =
    decks.filter { it.folderId == folderId }.sortedWith(compareBy(nameComparator) { it.name })

fun LibraryData.folder(id: String?): Folder? = id?.let { fid -> folders.firstOrNull { it.id == fid } }

fun LibraryData.deck(id: String): Deck? = decks.firstOrNull { it.id == id }

fun LibraryData.cardsOf(deckId: String): List<Card> = cards.filter { it.deckId == deckId }

/** Folder ids from the top level down to [folderId] (inclusive). */
fun LibraryData.folderChain(folderId: String?): List<Folder> {
    val chain = mutableListOf<Folder>()
    var current = folder(folderId)
    while (current != null && chain.size < 64) {
        chain.add(0, current)
        current = folder(current.parentId)
    }
    return chain
}

fun LibraryData.deckPath(deck: Deck): String =
    (folderChain(deck.folderId).map { it.name } + deck.name).joinToString("/")

fun LibraryData.folderPath(folderId: String?): String = folderChain(folderId).joinToString("/") { it.name }

/** Every folder id inside [folderId], including itself (null = whole library). */
fun LibraryData.descendantFolderIds(folderId: String?): Set<String?> {
    val result = mutableSetOf<String?>(folderId)
    var frontier = listOf(folderId)
    while (frontier.isNotEmpty()) {
        val next = folders.filter { it.parentId in frontier && it.id !in result }.map { it.id }
        result.addAll(next)
        frontier = next
    }
    return result
}

fun LibraryData.decksUnder(folderId: String?): List<Deck> {
    val ids = descendantFolderIds(folderId)
    return decks.filter { it.folderId in ids }
}

fun Card.isDue(now: Long) = !review.isNew && review.due <= now

fun LibraryData.counts(deckIds: Set<String>, now: Long): DeckCounts {
    var total = 0; var due = 0; var new = 0
    for (c in cards) {
        if (c.deckId !in deckIds) continue
        total++
        if (c.review.isNew) new++ else if (c.review.due <= now) due++
    }
    return DeckCounts(total, due, new)
}

/** Counts for every deck in one pass over the cards. */
fun LibraryData.countsByDeck(now: Long): Map<String, DeckCounts> {
    val totals = HashMap<String, IntArray>()
    for (c in cards) {
        val a = totals.getOrPut(c.deckId) { IntArray(3) }
        a[0]++
        if (c.review.isNew) a[2]++ else if (c.review.due <= now) a[1]++
    }
    return decks.associate { d -> d.id to (totals[d.id]?.let { DeckCounts(it[0], it[1], it[2]) } ?: DeckCounts()) }
}

/** Earliest upcoming review in these decks, or null if nothing is scheduled. */
fun LibraryData.nextDue(deckIds: Set<String>): Long? =
    cards.filter { it.deckId in deckIds && !it.review.isNew }.minOfOrNull { it.review.due }

/**
 * The cards for a study session: everything due (most overdue first), then up to
 * [newLimit] cards never seen before, in the order they appear in their decks.
 */
fun LibraryData.studyQueue(
    deckIds: Set<String>,
    now: Long,
    newLimit: Int,
    shuffle: Boolean = false,
    random: kotlin.random.Random = kotlin.random.Random.Default,
    filters: Set<PracticeFilter> = emptySet(),
): List<Card> {
    val inScope = cards.filter { it.deckId in deckIds && it.matches(filters, now) }
    val due = inScope.filter { it.isDue(now) }.sortedBy { it.review.due }
    val newCards = inScope.filter { it.review.isNew }
    if (!shuffle) return due + newCards.take(newLimit)
    // Shuffled: a random pick of new cards, mixed in with the due ones.
    return (due + newCards.shuffled(random).take(newLimit)).shuffled(random)
}

/** The Practice chips. None selected means "All". */
enum class PracticeFilter(val label: String) { DUE("Due"), NEW("New"), STARRED("Starred") }

/**
 * Cards to practice in [deckIds]: every card when [filters] is empty ("All"), otherwise
 * cards that are due, new, or starred — whichever of those are selected (combined).
 */
fun LibraryData.practiceCards(deckIds: Set<String>, filters: Set<PracticeFilter>, now: Long): List<Card> =
    cards.filter { c -> c.deckId in deckIds && c.matches(filters, now) }

/** Whether a card is picked by the All / Due / New / Starred choice (none = All). */
fun Card.matches(filters: Set<PracticeFilter>, now: Long): Boolean =
    filters.isEmpty() ||
        (PracticeFilter.DUE in filters && isDue(now)) ||
        (PracticeFilter.NEW in filters && review.isNew) ||
        (PracticeFilter.STARRED in filters && starred)

/** Like [counts], but only for the cards the All / Due / New / Starred choice picks. */
fun LibraryData.counts(deckIds: Set<String>, filters: Set<PracticeFilter>, now: Long): DeckCounts {
    var total = 0; var due = 0; var new = 0
    for (c in cards) {
        if (c.deckId !in deckIds || !c.matches(filters, now)) continue
        total++
        if (c.review.isNew) new++ else if (c.review.due <= now) due++
    }
    return DeckCounts(total, due, new)
}

fun LibraryData.isFolderEmpty(folderId: String): Boolean =
    folders.none { it.parentId == folderId } && decks.none { it.folderId == folderId }

fun LibraryData.starredCards(): List<Card> = cards.filter { it.starred }

fun LibraryData.toggleStar(cardId: String): LibraryData =
    copy(cards = cards.map { if (it.id == cardId) it.copy(starred = !it.starred) else it })

/** Moves a card (with its progress and star) to the end of another deck. */
fun LibraryData.moveCard(cardId: String, toDeckId: String): LibraryData {
    val card = cards.firstOrNull { it.id == cardId } ?: return this
    if (card.deckId == toDeckId || decks.none { it.id == toDeckId }) return this
    return copy(cards = cards.filter { it.id != cardId } + card.copy(deckId = toDeckId))
}

data class SearchResults(val folders: List<Folder>, val decks: List<Deck>, val cards: List<Card>) {
    val isEmpty: Boolean get() = folders.isEmpty() && decks.isEmpty() && cards.isEmpty()
}

/** Which parts of the library a search looks at. */
data class SearchScope(
    val fronts: Boolean = true,
    val backs: Boolean = true,
    val decks: Boolean = true,
    val folders: Boolean = true,
)

/** Case-insensitive search across folder names, deck names, and card fronts/backs. */
fun LibraryData.search(query: String, scope: SearchScope = SearchScope(), cardLimit: Int = 200): SearchResults {
    val q = query.trim()
    if (q.isEmpty()) return SearchResults(emptyList(), emptyList(), emptyList())
    fun hit(s: String) = s.contains(q, ignoreCase = true)
    return SearchResults(
        folders = if (!scope.folders) emptyList() else folders.filter { hit(it.name) }.sortedWith(compareBy(nameComparator) { it.name }),
        decks = if (!scope.decks) emptyList() else decks.filter { hit(it.name) }.sortedWith(compareBy(nameComparator) { it.name }),
        cards = cards.filter { (scope.fronts && hit(it.front)) || (scope.backs && hit(it.back)) }.take(cardLimit),
    )
}

// ---------------------------------------------------------------- editing

fun LibraryData.nameTakenInFolder(parentId: String?, name: String, exceptId: String? = null): Boolean {
    val n = name.trim()
    return folders.any { it.parentId == parentId && it.id != exceptId && it.name.equals(n, ignoreCase = true) } ||
        decks.any { it.folderId == parentId && it.id != exceptId && it.name.equals(n, ignoreCase = true) }
}

/** The next free position in a folder, so new items land at the end when arranged by hand. */
fun LibraryData.nextPosition(parentId: String?): Int =
    (folders.filter { it.parentId == parentId }.map { it.position } +
        decks.filter { it.folderId == parentId }.map { it.position }).maxOrNull()?.plus(1) ?: 0

fun LibraryData.addFolder(parentId: String?, name: String): LibraryData =
    copy(folders = folders + Folder(newId(), parentId, cleanName(name), nextPosition(parentId)))

fun LibraryData.addDeck(folderId: String?, name: String): Pair<LibraryData, Deck> {
    val deck = Deck(newId(), folderId, cleanName(name), nextPosition(folderId))
    return copy(decks = decks + deck) to deck
}

fun LibraryData.renameFolder(id: String, name: String): LibraryData =
    copy(folders = folders.map { if (it.id == id) it.copy(name = cleanName(name)) else it })

fun LibraryData.renameDeck(id: String, name: String): LibraryData =
    copy(decks = decks.map { if (it.id == id) it.copy(name = cleanName(name)) else it })

/** Moves a deck into another folder (null = top level), keeping all its cards. */
fun LibraryData.moveDeck(deckId: String, toFolderId: String?): LibraryData {
    if (toFolderId != null && folders.none { it.id == toFolderId }) return this
    val pos = nextPosition(toFolderId)
    return copy(decks = decks.map { if (it.id == deckId) it.copy(folderId = toFolderId, position = pos) else it })
}

/** Folders that [folderId] may be moved into: anywhere except itself or inside itself. */
fun LibraryData.canMoveFolderInto(folderId: String, toParentId: String?): Boolean =
    toParentId == null || (toParentId !in descendantFolderIds(folderId) && folders.any { it.id == toParentId })

/** Moves a folder (with everything inside it) under another folder (null = top level). */
fun LibraryData.moveFolder(folderId: String, toParentId: String?): LibraryData {
    if (!canMoveFolderInto(folderId, toParentId)) return this
    val pos = nextPosition(toParentId)
    return copy(folders = folders.map { if (it.id == folderId) it.copy(parentId = toParentId, position = pos) else it })
}

fun LibraryData.deleteFolder(id: String): LibraryData {
    val folderIds = descendantFolderIds(id)
    val deckIds = decks.filter { it.folderId in folderIds }.map { it.id }.toSet()
    return copy(
        folders = folders.filter { it.id !in folderIds },
        decks = decks.filter { it.id !in deckIds },
        cards = cards.filter { it.deckId !in deckIds },
    )
}

fun LibraryData.deleteDeck(id: String): LibraryData =
    copy(decks = decks.filter { it.id != id }, cards = cards.filter { it.deckId != id })

fun LibraryData.upsertCard(card: Card): LibraryData =
    if (cards.any { it.id == card.id }) copy(cards = cards.map { if (it.id == card.id) card else it })
    else copy(cards = cards + card)

fun LibraryData.deleteCard(id: String): LibraryData = copy(cards = cards.filter { it.id != id })

fun LibraryData.setReview(cardId: String, review: ReviewState): LibraryData =
    copy(cards = cards.map { if (it.id == cardId) it.copy(review = review) else it })

fun LibraryData.resetDeck(deckId: String): LibraryData =
    copy(cards = cards.map { if (it.deckId == deckId) it.copy(review = ReviewState.NEW) else it })

/** Resets study progress for every deck inside a folder, including sub-folders. */
fun LibraryData.resetFolder(folderId: String?): LibraryData {
    val deckIds = decksUnder(folderId).map { it.id }.toSet()
    return copy(cards = cards.map { if (it.deckId in deckIds) it.copy(review = ReviewState.NEW) else it })
}

// ---------------------------------------------------------------- arranging

/** Sub-folders of [parentId] in A–Z order, or in their hand-arranged order. */
fun LibraryData.orderedFolders(parentId: String?, manual: Boolean): List<Folder> =
    if (manual) {
        folders.filter { it.parentId == parentId }
            .sortedWith(compareBy<Folder> { it.position }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    } else {
        childFolders(parentId)
    }

/** Decks in [folderId] in A–Z order, or in their hand-arranged order. */
fun LibraryData.orderedDecks(folderId: String?, manual: Boolean): List<Deck> =
    if (manual) {
        decks.filter { it.folderId == folderId }
            .sortedWith(compareBy<Deck> { it.position }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    } else {
        decksIn(folderId)
    }

/**
 * Moves one folder or deck up (delta = -1) or down (+1) among its kind in [parentId].
 * Positions are renumbered 0, 1, 2… so the current on-screen order is kept exactly.
 */
fun LibraryData.shift(parentId: String?, itemId: String, delta: Int): LibraryData {
    val fs = orderedFolders(parentId, manual = true).toMutableList()
    val ds = orderedDecks(parentId, manual = true).toMutableList()
    val fi = fs.indexOfFirst { it.id == itemId }
    val di = ds.indexOfFirst { it.id == itemId }
    if (fi >= 0) {
        val t = fi + delta
        if (t in fs.indices) fs[fi] = fs[t].also { fs[t] = fs[fi] }
    } else if (di >= 0) {
        val t = di + delta
        if (t in ds.indices) ds[di] = ds[t].also { ds[t] = ds[di] }
    } else {
        return this
    }
    val fPos = fs.withIndex().associate { (i, f) -> f.id to i }
    val dPos = ds.withIndex().associate { (i, d) -> d.id to i }
    return copy(
        folders = folders.map { f -> fPos[f.id]?.let { f.copy(position = it) } ?: f },
        decks = decks.map { d -> dPos[d.id]?.let { d.copy(position = it) } ?: d },
    )
}

/**
 * Puts the folders and decks in [parentId] in A–Z order as their hand-arranged order, so they
 * can still be nudged up or down afterwards.
 */
fun LibraryData.sortByName(parentId: String?): LibraryData {
    val fPos = childFolders(parentId).withIndex().associate { (i, f) -> f.id to i }
    val dPos = decksIn(parentId).withIndex().associate { (i, d) -> d.id to i }
    return copy(
        folders = folders.map { f -> fPos[f.id]?.let { f.copy(position = it) } ?: f },
        decks = decks.map { d -> dPos[d.id]?.let { d.copy(position = it) } ?: d },
    )
}

private fun cleanName(name: String) = name.trim().replace('/', '-').ifEmpty { "Untitled" }

// ---------------------------------------------------------------- import from a computer

private fun normalizeKey(front: String) = front.trim().replace(Regex("\\s+"), " ").lowercase()

/** Splits "Law/Evidence/Hearsay.txt" into folder names and deck name; null if not a deck file. */
internal fun splitDeckPath(path: String): Pair<List<String>, String>? {
    val parts = path.replace('\\', '/').split('/').map { it.trim() }.filter { it.isNotEmpty() && it != "." }
    if (parts.isEmpty() || parts.any { it.startsWith(".") || it == ".." }) return null
    val file = parts.last()
    if (!DeckParser.isSupported(file)) return null
    val deckName = file.substringBeforeLast('.').trim().ifEmpty { return null }
    return parts.dropLast(1) to deckName
}

/**
 * Brings deck files into the library. Folders on the computer become folders here.
 *
 * A deck that already exists at the same path is updated in place: cards are matched by
 * their front text, so review progress is kept for cards you didn't change.
 *
 * @param appendOnly keep cards that aren't in the file (used by "quick add").
 * @param mirror also delete decks that weren't sent, so the phone matches the computer exactly.
 */
fun LibraryData.importFiles(
    files: List<IncomingFile>,
    mirror: Boolean = false,
    appendOnly: Boolean = false,
): Pair<LibraryData, ImportReport> {
    val folders = folders.toMutableList()
    val decks = decks.toMutableList()
    val cardsByDeck = cards.groupBy { it.deckId }.mapValues { it.value.toMutableList() }.toMutableMap()
    val warnings = mutableListOf<String>()
    val ignored = mutableListOf<String>()

    // Several files can map to one deck (Verbs.txt + Verbs.csv); combine them in order.
    val parsedByDeck = linkedMapOf<Pair<List<String>, String>, MutableList<Pair<String, String>>>()
    for (file in files) {
        val split = splitDeckPath(file.path)
        if (split == null) {
            ignored += file.path; continue
        }
        val fileName = file.path.substringAfterLast('/')
        // An Anki "Notes in Plain Text" export: may hold several decks (Anki's sub-decks become
        // folders, under whatever folder the file itself was in).
        if (AnkiText.looksLikeAnki(fileName, file.content)) {
            val anki = AnkiText.parse(fileName, file.content)
            warnings += anki.warnings
            for (deck in anki.decks) {
                val key = (split.first + deck.folders) to deck.name
                parsedByDeck.getOrPut(key) { mutableListOf() }.addAll(deck.cards)
            }
            continue
        }
        val result = DeckParser.parse(fileName, file.content)
        warnings += result.warnings
        parsedByDeck.getOrPut(split) { mutableListOf() }.addAll(result.cards)
    }

    fun positionIn(parentId: String?): Int =
        (folders.filter { it.parentId == parentId }.map { it.position } +
            decks.filter { it.folderId == parentId }.map { it.position }).maxOrNull()?.plus(1) ?: 0

    fun folderIdFor(names: List<String>): String? {
        var parent: String? = null
        for (name in names) {
            val existing = folders.firstOrNull { it.parentId == parent && it.name.equals(name, ignoreCase = true) }
            parent = existing?.id ?: Folder(newId(), parent, name, positionIn(parent)).also { folders += it }.id
        }
        return parent
    }

    var decksAdded = 0; var decksUpdated = 0; var cardsAdded = 0; var cardsChanged = 0
    var cardsRemoved = 0; var cardsTotal = 0
    val touchedDeckIds = mutableSetOf<String>()

    for ((key, parsed) in parsedByDeck) {
        val (folderNames, deckName) = key
        val folderId = folderIdFor(folderNames)
        var deck = decks.firstOrNull { it.folderId == folderId && it.name.equals(deckName, ignoreCase = true) }
        if (deck == null) {
            deck = Deck(newId(), folderId, deckName, positionIn(folderId)).also { decks += it }
            decksAdded++
        } else {
            decksUpdated++
        }
        touchedDeckIds += deck.id

        val existing = cardsByDeck[deck.id].orEmpty()
        val pool = existing.groupBy { normalizeKey(it.front) }.mapValues { ArrayDeque(it.value) }
        val updated = mutableMapOf<String, Card>() // existing card id -> its new text
        val result = mutableListOf<Card>()
        for ((front, back) in parsed) {
            val match = pool[normalizeKey(front)]?.removeFirstOrNull()
            if (match != null) {
                if (match.front != front || match.back != back) cardsChanged++
                val card = match.copy(front = front, back = back)
                updated[match.id] = card
                result += card
            } else {
                cardsAdded++
                result += Card(newId(), deck.id, front, back)
            }
        }
        val finalCards = if (appendOnly) {
            // Keep the deck's existing order; edited cards stay in place, new ones go at the end.
            existing.map { updated[it.id] ?: it } + result.filter { it.id !in updated }
        } else {
            cardsRemoved += existing.count { it.id !in updated }
            result
        }
        cardsTotal += finalCards.size
        cardsByDeck[deck.id] = finalCards.toMutableList()
    }

    var decksRemoved = 0
    if (mirror) {
        val gone = decks.filter { it.id !in touchedDeckIds }
        decksRemoved = gone.size
        gone.forEach { d -> cardsRemoved += cardsByDeck.remove(d.id)?.size ?: 0 }
        decks.removeAll(gone)
        // Remove folders that no longer contain any deck.
        var pruned = true
        while (pruned) {
            val empty = folders.filter { f -> decks.none { it.folderId == f.id } && folders.none { it.parentId == f.id } }
            pruned = empty.isNotEmpty()
            folders.removeAll(empty)
        }
    }

    val deckOrder = decks.map { it.id }
    val newCards = deckOrder.flatMap { cardsByDeck[it].orEmpty() }
    val report = ImportReport(
        decksAdded, decksUpdated, decksRemoved, cardsAdded, cardsChanged, cardsRemoved, cardsTotal,
        ignored, warnings,
    )
    return LibraryData(folders, decks, newCards) to report
}

// ---------------------------------------------------------------- export to a computer

fun LibraryData.deckText(deck: Deck): String =
    DeckParser.format(cardsOf(deck.id).map { it.front to it.back })

/** All decks as a .zip of text files in their folders — open it on a computer and edit. */
fun LibraryData.exportZip(rootName: String = "Cards"): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        for (deck in decks) {
            zip.putNextEntry(ZipEntry("$rootName/${safeZipPath(deckPath(deck))}.txt"))
            zip.write(deckText(deck).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        if (decks.isEmpty()) {
            zip.putNextEntry(ZipEntry("$rootName/"))
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

/**
 * One folder (and everything inside it) as a .zip of text files, starting with the folder's
 * own name — e.g. "Criminal Law/1. Act.txt". Unzip it and send the folder back to re-import.
 */
fun LibraryData.exportFolderZip(folderId: String): ByteArray {
    val top = folder(folderId) ?: return ByteArray(0)
    val parentPath = folderPath(top.parentId)
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        val inside = decksUnder(folderId)
        for (deck in inside) {
            val rel = deckPath(deck).removePrefix(parentPath).removePrefix("/")
            zip.putNextEntry(ZipEntry("${safeZipPath(rel)}.txt"))
            zip.write(deckText(deck).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        if (inside.isEmpty()) {
            zip.putNextEntry(ZipEntry("${safeZipPath(top.name)}/"))
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

/**
 * Several folders and decks from one place as a single .zip: each folder keeps its own name
 * at the top ("Criminal Law/Defenses.txt") and each deck is a text file beside them.
 */
fun LibraryData.exportItemsZip(folderIds: Collection<String>, deckIds: Collection<String>): ByteArray {
    val out = ByteArrayOutputStream()
    val used = mutableSetOf<String>()
    fun entry(zip: ZipOutputStream, name: String, text: String?) {
        if (!used.add(name)) return
        zip.putNextEntry(ZipEntry(name))
        if (text != null) zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
    ZipOutputStream(out).use { zip ->
        for (id in folderIds) {
            val top = folder(id) ?: continue
            val parentPath = folderPath(top.parentId)
            val inside = decksUnder(id)
            for (deck in inside) {
                val rel = deckPath(deck).removePrefix(parentPath).removePrefix("/")
                entry(zip, "${safeZipPath(rel)}.txt", deckText(deck))
            }
            if (inside.isEmpty()) entry(zip, "${safeZipPath(top.name)}/", null)
        }
        for (id in deckIds) {
            val deck = deck(id) ?: continue
            entry(zip, "${safeZipPath(deck.name)}.txt", deckText(deck))
        }
    }
    return out.toByteArray()
}

/** A safe file name for exporting one deck or folder. */
fun exportFileName(name: String, extension: String): String =
    name.replace(Regex("[\\\\/:*?\"<>|]"), "-").trim().ifEmpty { "Cards" } + "." + extension

private fun safeZipPath(path: String) =
    path.split('/').joinToString("/") { it.replace(Regex("[\\\\:*?\"<>|]"), "-") }

/**
 * The deck files inside a .zip, with their folder paths. A single top folder named "Cards"
 * (as in "Export all cards") is dropped, so its contents land on Home rather than in a
 * folder called Cards.
 */
fun unzipDeckFiles(bytes: ByteArray, maxFileBytes: Int = 20 * 1024 * 1024): List<IncomingFile> {
    val out = mutableListOf<IncomingFile>()
    java.util.zip.ZipInputStream(bytes.inputStream()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            val path = entry.name.replace('\\', '/').trimStart('/')
            val name = path.substringAfterLast('/')
            val hidden = path.split('/').any { it.startsWith(".") || it == "__MACOSX" }
            if (!entry.isDirectory && !hidden && DeckParser.isSupported(name)) {
                val data = zip.readBytes()
                if (data.size <= maxFileBytes) out += IncomingFile(path, data.toString(Charsets.UTF_8))
            }
        }
    }
    val tops = out.map { it.path.substringBefore('/', "") }.toSet()
    return if (tops.size == 1 && tops.first().equals("Cards", ignoreCase = true)) {
        out.map { it.copy(path = it.path.substringAfter('/')) }
    } else {
        out
    }
}
