package app.kompakt.flashcards.core

/**
 * Reads Anki's "Notes in Plain Text" export (File › Export › Notes in Plain Text, .txt).
 *
 * Newer Anki versions start the file with header lines such as
 *
 *   #separator:tab
 *   #html:true
 *   #notetype column:2
 *   #deck column:3
 *   #tags column:5
 *
 * followed by one note per row. Older versions write plain tab-separated rows with no header.
 * Fields may be quoted ("…", with "" for a quote) when they contain the separator or a line break.
 *
 * What comes across:
 *  - The first field is the front and the second the back (Basic notes). "Basic (and reversed
 *    card)" also adds the reverse card, and "optional reversed" does when its third field is set.
 *  - Cloze notes ({{c1::answer::hint}}) become one card per cloze number: the front shows "[…]"
 *    (or "[hint]") in place of that blank, the back the full text plus the Back Extra field.
 *  - Anki's formatting becomes plain text: line breaks and list items are kept, other tags are
 *    dropped, and entities like &nbsp; are decoded. Images and sounds are left out (Cards is
 *    text-only), and the import report says how many were skipped.
 *  - Decks: with a deck column, "Bar::Evidence::Hearsay" becomes folders Bar › Evidence and the
 *    deck Hearsay. Without one, the deck is named after the file.
 *  - Tags and Anki's review history aren't imported; cards start as new.
 */
object AnkiText {

    data class Deck(val folders: List<String>, val name: String, val cards: List<Pair<String, String>>)

    data class Result(val decks: List<Deck>, val warnings: List<String>)

    private val HEADER = Regex("^#(separator|html|tags column|deck column|notetype column|guid column|columns|deck|notetype|if matches|tags):", RegexOption.IGNORE_CASE)
    private val Q_OR_A = Regex("^(q|question|front|a|answer|back)\\s*[:：](?![:：])", RegexOption.IGNORE_CASE)

    /** True for an Anki text export: Anki's header lines, or (older Anki) rows that are all tab-separated. */
    fun looksLikeAnki(fileName: String, text: String): Boolean {
        if (!fileName.endsWith(".txt", ignoreCase = true) && !fileName.endsWith(".tsv", ignoreCase = true)) return false
        val lines = text.removePrefix("﻿").lineSequence().map { it.trimEnd('\r') }.filter { it.isNotBlank() }.take(200).toList()
        if (lines.isEmpty()) return false
        if (lines.take(12).any { HEADER.containsMatchIn(it) }) return true
        // Older exports: no header, every row has a tab, and nothing in Cards' own formats.
        // (Cloze text contains "::", so only rule out lines that would be a whole "front :: back" card.)
        return lines.all { '\t' in it && !Q_OR_A.containsMatchIn(it.trim()) && !it.trimStart().startsWith("#") } &&
            lines.none { "::" in it && !CLOZE.containsMatchIn(it) }
    }

    fun parse(fileName: String, text: String): Result {
        val warnings = mutableListOf<String>()
        val clean = text.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')

        // Header lines come first; everything after them is data.
        var separator = '\t'
        var html: Boolean? = null
        var deckCol = 0; var notetypeCol = 0; var guidCol = 0; var tagsCol = 0
        var fixedNotetype: String? = null
        var fixedDeck: String? = null
        val body = StringBuilder()
        var inHeader = true
        var hasHeader = false
        for (line in clean.split('\n')) {
            if (inHeader && line.startsWith("#")) {
                hasHeader = true
                val key = line.substringBefore(':').removePrefix("#").trim().lowercase()
                val value = line.substringAfter(':', "").trim()
                when (key) {
                    "separator" -> separator = when (value.lowercase()) {
                        "tab" -> '\t'; "comma" -> ','; "semicolon" -> ';'; "space" -> ' '
                        "pipe" -> '|'; "colon" -> ':'
                        else -> value.firstOrNull() ?: '\t'
                    }
                    "html" -> html = value.equals("true", ignoreCase = true)
                    "deck column" -> deckCol = value.toIntOrNull() ?: 0
                    "notetype column" -> notetypeCol = value.toIntOrNull() ?: 0
                    "guid column" -> guidCol = value.toIntOrNull() ?: 0
                    "tags column" -> tagsCol = value.toIntOrNull() ?: 0
                    "notetype" -> fixedNotetype = value
                    "deck" -> fixedDeck = value
                }
                continue
            }
            inHeader = false
            body.append(line).append('\n')
        }

        val special = setOf(deckCol, notetypeCol, guidCol, tagsCol).filter { it > 0 }.toSet()
        val byDeck = linkedMapOf<String, MutableList<Pair<String, String>>>()
        var media = 0
        var row = 0

        // Newer exports quote fields that contain the separator, a quote or a line break; older
        // ones (no header) don't quote at all, so a stray quote mark must not be read as one.
        val rows = if (hasHeader) {
            DeckParser.readCsv(body.toString(), separator)
        } else {
            body.split('\n').map { it.split(separator) }
        }
        for (cells in rows) {
            row++
            if (cells.all { it.isBlank() }) continue
            val fields = cells.filterIndexed { i, _ -> (i + 1) !in special }
            val notetype = (if (notetypeCol > 0) cells.getOrNull(notetypeCol - 1) else fixedNotetype).orEmpty()
            val deck = (if (deckCol > 0) cells.getOrNull(deckCol - 1) else fixedDeck).orEmpty().trim()

            val raw = fields.map { it.trim() }
            media += raw.sumOf { MEDIA.findAll(it).count() }
            val f = raw.map { toText(it, html) }

            val cards = mutableListOf<Pair<String, String>>()
            val first = raw.getOrNull(0).orEmpty()
            if (notetype.contains("cloze", ignoreCase = true) || CLOZE.containsMatchIn(first)) {
                cards += clozeCards(first, f.getOrNull(1).orEmpty(), html)
            } else {
                val front = f.getOrNull(0).orEmpty()
                val back = f.getOrNull(1).orEmpty()
                if (front.isNotEmpty() && back.isNotEmpty()) {
                    cards += front to back
                    val reversed = notetype.contains("and reversed", ignoreCase = true) ||
                        (notetype.contains("optional reversed", ignoreCase = true) && f.getOrNull(2).orEmpty().isNotEmpty())
                    if (reversed) cards += back to front
                }
            }
            if (cards.isEmpty()) {
                warnings += "$fileName row $row skipped: no text on one side (image or sound only?)"
                continue
            }
            byDeck.getOrPut(deck) { mutableListOf() }.addAll(cards)
        }

        if (media > 0) warnings += "$fileName: $media image or sound reference(s) left out (Cards shows text only)"

        val fileDeck = fileName.substringAfterLast('/').substringBeforeLast('.').trim().ifEmpty { "Anki" }
        val decks = byDeck.map { (ankiDeck, cards) ->
            val parts = ankiDeck.split("::").map { it.trim().replace('/', '-') }.filter { it.isNotEmpty() }
            if (parts.isEmpty()) Deck(emptyList(), fileDeck, cards) else Deck(parts.dropLast(1), parts.last(), cards)
        }
        return Result(decks, warnings)
    }

    // ------------------------------------------------------------------ cloze

    private val CLOZE = Regex("\\{\\{c(\\d+)::(.*?)(?:::(.*?))?\\}\\}", RegexOption.DOT_MATCHES_ALL)

    private fun clozeCards(rawText: String, extra: String, html: Boolean?): List<Pair<String, String>> {
        val numbers = CLOZE.findAll(rawText).map { it.groupValues[1].toInt() }.distinct().sorted().toList()
        if (numbers.isEmpty()) return emptyList()
        val back = toText(CLOZE.replace(rawText) { it.groupValues[2] }, html)
            .let { if (extra.isNotEmpty()) "$it\n$extra" else it }
        return numbers.map { n ->
            val front = CLOZE.replace(rawText) { m ->
                if (m.groupValues[1].toInt() == n) {
                    val hint = m.groupValues[3]
                    if (hint.isNotEmpty()) "[$hint]" else "[…]"
                } else {
                    m.groupValues[2]
                }
            }
            toText(front, html) to back
        }.filter { it.first.isNotEmpty() }
    }

    // ------------------------------------------------------------------ HTML → text

    private val MEDIA = Regex("\\[sound:[^\\]]*]|<img\\b[^>]*>", RegexOption.IGNORE_CASE)

    /** Anki's field HTML as plain text. [html] false means the field is already plain text. */
    internal fun toText(field: String, html: Boolean?): String {
        var s = field.replace(MEDIA, "")
        val isHtml = html ?: Regex("<[a-zA-Z/][^>]*>|&[#a-zA-Z0-9]+;").containsMatchIn(s)
        if (isHtml) {
            s = s.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("<li\\b[^>]*>", RegexOption.IGNORE_CASE), "\n• ")
                .replace(Regex("<(div|p|tr|ul|ol|h[1-6])\\b[^>]*>", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("</(div|p|li|tr|ul|ol|h[1-6])>", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("<[^>]+>"), "")
            s = decodeEntities(s)
        }
        return s.replace(' ', ' ')
            .split('\n').map { it.trim().replace(Regex("[ \\t]{2,}"), " ") }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private val NAMED = mapOf(
        "nbsp" to " ", "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "ndash" to "–", "mdash" to "—", "hellip" to "…", "lsquo" to "‘", "rsquo" to "’",
        "ldquo" to "“", "rdquo" to "”", "bull" to "•", "middot" to "·", "times" to "×",
        "divide" to "÷", "deg" to "°", "plusmn" to "±", "frac12" to "½", "sect" to "§", "para" to "¶",
        "copy" to "©", "reg" to "®", "trade" to "™", "rarr" to "→", "larr" to "←", "harr" to "↔",
        "uarr" to "↑", "darr" to "↓", "le" to "≤", "ge" to "≥", "ne" to "≠", "eacute" to "é",
    )

    private fun decodeEntities(s: String): String =
        Regex("&(#x[0-9a-fA-F]+|#\\d+|[a-zA-Z][a-zA-Z0-9]*);").replace(s) { m ->
            val e = m.groupValues[1]
            when {
                e.startsWith("#x") || e.startsWith("#X") -> e.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
                e.startsWith("#") -> e.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) }
                else -> NAMED[e.lowercase()]
            } ?: m.value
        }
}
