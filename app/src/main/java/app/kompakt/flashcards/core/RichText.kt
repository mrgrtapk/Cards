package app.kompakt.flashcards.core

/**
 * Bold and italics in card text, written the way many note apps do:
 *
 *   **bold**     *italics*     ***both***
 *
 * The markers stay in the stored text (so they survive export and re-import) and are hidden
 * when a card is shown. A lone asterisk ("5 * 3", "* item", "footnote*") is left as it is:
 * italics need text right after the opening "*" and right before the closing one.
 */
object RichText {

    data class Span(val start: Int, val end: Int, val bold: Boolean, val italic: Boolean)

    data class Styled(val text: String, val spans: List<Span>)

    private val BOLD = Regex("\\*\\*(?=\\S)(.+?)(?<=\\S)\\*\\*(?!\\*)", RegexOption.DOT_MATCHES_ALL)
    private val ITALIC = Regex("(?<![\\w*])\\*(?=[^\\s*])(.+?)(?<=[^\\s*])\\*(?![\\w*])", RegexOption.DOT_MATCHES_ALL)

    /** The text without markers, plus where bold and italic runs fall in it. */
    fun parse(source: String): Styled {
        if ('*' !in source) return Styled(source, emptyList())
        val out = StringBuilder()
        val spans = mutableListOf<Span>()

        fun italics(text: String, bold: Boolean) {
            var last = 0
            for (m in ITALIC.findAll(text)) {
                out.append(text, last, m.range.first)
                val start = out.length
                out.append(m.groupValues[1])
                spans += Span(start, out.length, bold = bold, italic = true)
                last = m.range.last + 1
            }
            out.append(text, last, text.length)
        }

        var last = 0
        for (m in BOLD.findAll(source)) {
            italics(source.substring(last, m.range.first), bold = false)
            val start = out.length
            italics(m.groupValues[1], bold = true)
            spans += Span(start, out.length, bold = true, italic = false)
            last = m.range.last + 1
        }
        italics(source.substring(last), bold = false)
        return Styled(out.toString(), spans)
    }

    /** The text with the markers removed (for lists, previews, and search results). */
    fun plain(source: String): String = parse(source).text
}
