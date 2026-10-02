package app.kompakt.flashcards.core

/**
 * Turns a plain-text deck file written on a computer into cards.
 *
 * Supported in .txt / .md / .tsv files (mix freely, one card per entry):
 *
 *   front :: back                    one card per line ("- " bullets are fine)
 *   front<TAB>back                   pasted from Excel / Numbers
 *
 *   Q: question (may continue        multi-line card; leave a blank line
 *      on following lines)           between cards
 *   A: answer
 *
 *   # Heading   or   // note         ignored
 *
 * .csv files: first column = front, second column = back
 * (comma or semicolon separated, optional header row).
 */
object DeckParser {

    data class Result(val cards: List<Pair<String, String>>, val warnings: List<String>)

    val SUPPORTED_EXTENSIONS = setOf("txt", "md", "markdown", "text", "csv", "tsv")

    fun isSupported(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in SUPPORTED_EXTENSIONS &&
            !fileName.startsWith(".")

    fun parse(fileName: String, text: String): Result {
        val clean = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        return if (fileName.endsWith(".csv", ignoreCase = true)) {
            parseCsv(fileName, clean)
        } else {
            parseText(fileName, clean)
        }
    }

    // "Q:" / "A:" markers — but never "Q ::", which is a one-line card whose front is "Q".
    private val Q_PREFIX = Regex("^(q|question|front)\\s*[:：](?![:：])\\s?", RegexOption.IGNORE_CASE)
    private val A_PREFIX = Regex("^(a|answer|back)\\s*[:：](?![:：])\\s?", RegexOption.IGNORE_CASE)
    private val LIST_MARKER = Regex("^([-*+•]|\\d+[.)])\\s+")

    private fun parseText(fileName: String, text: String): Result {
        val cards = mutableListOf<Pair<String, String>>()
        val warnings = mutableListOf<String>()

        var front: StringBuilder? = null
        var back: StringBuilder? = null
        var field = 0 // 0 = not in a Q/A block, 1 = reading question, 2 = reading answer
        var blockStart = 0

        fun flush() {
            if (field != 0) {
                val f = front?.toString()?.trim().orEmpty()
                val b = back?.toString()?.trim().orEmpty()
                when {
                    f.isEmpty() -> warnings += "$fileName line $blockStart: card has no question"
                    b.isEmpty() -> warnings += "$fileName line $blockStart: \"${f.take(40)}\" has no answer (add an A: line)"
                    else -> cards += f to b
                }
            }
            front = null; back = null; field = 0
        }

        text.split('\n').forEachIndexed { index, raw ->
            val lineNo = index + 1
            val t = raw.trim()
            val qMatch = Q_PREFIX.find(t)
            val aMatch = A_PREFIX.find(t)
            when {
                t.isEmpty() -> flush()

                qMatch != null -> {
                    flush()
                    front = StringBuilder(t.substring(qMatch.range.last + 1).trim())
                    field = 1
                    blockStart = lineNo
                }

                aMatch != null && field == 1 -> {
                    back = StringBuilder(t.substring(aMatch.range.last + 1).trim())
                    field = 2
                }

                aMatch != null -> warnings += "$fileName line $lineNo: A: line without a Q: line above it"

                // A one-line card right after an answer (no blank line in between).
                field == 2 && t.contains("::") -> {
                    flush()
                    addInline(t, fileName, lineNo, cards, warnings)
                }

                field == 1 -> front!!.append('\n').append(t)
                field == 2 -> back!!.append('\n').append(t)

                t.startsWith("#") || t.startsWith("//") -> Unit

                else -> addInline(t, fileName, lineNo, cards, warnings)
            }
        }
        flush()
        return Result(cards, warnings)
    }

    private fun addInline(
        t: String,
        fileName: String,
        lineNo: Int,
        cards: MutableList<Pair<String, String>>,
        warnings: MutableList<String>,
    ) {
        val pair = when {
            t.contains("::") -> {
                val stripped = t.replaceFirst(LIST_MARKER, "")
                stripped.substringBefore("::").trim() to stripped.substringAfter("::").trim()
            }
            t.contains('\t') -> t.substringBefore('\t').trim() to t.substringAfter('\t').trim()
            else -> {
                warnings += "$fileName line $lineNo skipped: \"${t.take(40)}\" (no :: or Q:/A:)"
                return
            }
        }
        if (pair.first.isEmpty() || pair.second.isEmpty()) {
            warnings += "$fileName line $lineNo skipped: one side of the card is empty"
        } else {
            cards += pair
        }
    }

    private val FRONT_HEADERS = setOf("front", "question", "q", "term", "word", "prompt")
    private val BACK_HEADERS = setOf("back", "answer", "a", "definition", "meaning", "response")

    private fun parseCsv(fileName: String, text: String): Result {
        val firstLine = text.substringBefore('\n')
        val delimiter = if (firstLine.count { it == ';' } > firstLine.count { it == ',' }) ';' else ','
        val rows = readCsv(text, delimiter)
        val cards = mutableListOf<Pair<String, String>>()
        val warnings = mutableListOf<String>()
        rows.forEachIndexed { i, row ->
            val cells = row.map { it.trim() }
            if (cells.all { it.isEmpty() }) return@forEachIndexed
            if (i == 0 && cells.size >= 2 &&
                cells[0].lowercase() in FRONT_HEADERS && cells[1].lowercase() in BACK_HEADERS
            ) return@forEachIndexed
            if (cells.size < 2 || cells[0].isEmpty() || cells[1].isEmpty()) {
                warnings += "$fileName row ${i + 1} skipped: needs text in the first two columns"
            } else {
                cards += cells[0] to cells[1]
            }
        }
        return Result(cards, warnings)
    }

    /** RFC 4180-style reader: quoted fields may contain delimiters, newlines and "" escapes. */
    internal fun readCsv(text: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        cell.append('"'); i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    cell.append(c)
                }
            } else when (c) {
                '"' -> inQuotes = true
                delimiter -> { row += cell.toString(); cell.clear() }
                '\n' -> { row += cell.toString(); cell.clear(); rows += row; row = mutableListOf() }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString(); rows += row
        }
        return rows
    }

    /** Writes cards back out in the same format, so a deck can round-trip to your computer. */
    fun format(cards: List<Pair<String, String>>): String = buildString {
        for ((front, back) in cards) {
            val simple = !front.contains('\n') && !back.contains('\n') && !front.contains("::")
            if (simple) {
                append(front).append(" :: ").append(back).append('\n')
            } else {
                if (isNotEmpty() && !endsWith("\n\n")) append('\n')
                append("Q: ").append(noBlankLines(front)).append('\n')
                append("A: ").append(noBlankLines(back)).append("\n\n")
            }
        }
    }

    private fun noBlankLines(s: String) =
        s.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
}
