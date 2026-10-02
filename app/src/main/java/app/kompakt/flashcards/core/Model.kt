package app.kompakt.flashcards.core

import java.util.UUID

/**
 * A folder. `parentId == null` means it sits at the top level of the library.
 * [position] is its place among its siblings when a folder is arranged by hand.
 */
data class Folder(val id: String, val parentId: String?, val name: String, val position: Int = 0)

/** A deck of cards. `folderId == null` means it sits at the top level of the library. */
data class Deck(val id: String, val folderId: String?, val name: String, val position: Int = 0)

data class Card(
    val id: String,
    val deckId: String,
    val front: String,
    val back: String,
    val review: ReviewState = ReviewState.NEW,
    val starred: Boolean = false,
)

/** Spaced-repetition memory state for one card (FSRS). Times are epoch milliseconds. */
data class ReviewState(
    val due: Long = 0L,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val reps: Int = 0,
    val lapses: Int = 0,
    val lastReview: Long = 0L,
) {
    val isNew: Boolean get() = reps == 0

    companion object {
        val NEW = ReviewState()
    }
}

/** The whole library. Immutable: every change produces a new instance. */
data class LibraryData(
    val folders: List<Folder> = emptyList(),
    val decks: List<Deck> = emptyList(),
    val cards: List<Card> = emptyList(),
)

data class DeckCounts(val total: Int = 0, val due: Int = 0, val new: Int = 0) {
    /** Cards a study session would show right now. */
    val studyable: Int get() = due + new

    operator fun plus(o: DeckCounts) = DeckCounts(total + o.total, due + o.due, new + o.new)
}

fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(16)

const val MINUTE_MS = 60_000L
const val DAY_MS = 86_400_000L
