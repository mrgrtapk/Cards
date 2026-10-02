package app.kompakt.flashcards.core

/**
 * Tiny dependency-free JSON reader/writer. Values are Map<String, Any?>, List<Any?>,
 * String, Long, Double, Boolean or null.
 */
object Json {

    fun stringify(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    private fun write(sb: StringBuilder, value: Any?) {
        when (value) {
            null -> sb.append("null")
            is String -> quote(sb, value)
            is Boolean -> sb.append(value)
            is Int, is Long -> sb.append(value)
            is Double -> if (value.isFinite()) sb.append(value) else sb.append("null")
            is Float -> write(sb, value.toDouble())
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) sb.append(',')
                    first = false
                    quote(sb, k.toString()); sb.append(':'); write(sb, v)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (v in value) {
                    if (!first) sb.append(',')
                    first = false
                    write(sb, v)
                }
                sb.append(']')
            }
            else -> quote(sb, value.toString())
        }
    }

    private fun quote(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ' || c == ' ' || c == ' ') {
                    sb.append("\\u").append(String.format("%04x", c.code))
                } else sb.append(c)
            }
        }
        sb.append('"')
    }

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipWs()
        val v = p.value()
        p.skipWs()
        if (p.i != text.length) throw IllegalArgumentException("Unexpected data at ${p.i}")
        return v
    }

    private class Parser(val s: String) {
        var i = 0

        fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun value(): Any? {
            skipWs()
            if (i >= s.length) throw IllegalArgumentException("Unexpected end of JSON")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw IllegalArgumentException("Bad JSON at $i")
            }
        }

        fun lit(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) throw IllegalArgumentException("Bad JSON at $i")
            i += word.length
            return v
        }

        fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++
            skipWs()
            if (s[i] == '}') { i++; return m }
            while (true) {
                skipWs()
                val k = str()
                skipWs()
                expect(':')
                m[k] = value()
                skipWs()
                if (s[i] == ',') { i++; continue }
                expect('}')
                return m
            }
        }

        fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            i++
            skipWs()
            if (s[i] == ']') { i++; return l }
            while (true) {
                l += value()
                skipWs()
                if (s[i] == ',') { i++; continue }
                expect(']')
                return l
            }
        }

        fun expect(c: Char) {
            if (i >= s.length || s[i] != c) throw IllegalArgumentException("Expected '$c' at $i")
            i++
        }

        fun str(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) throw IllegalArgumentException("Unterminated string")
                when (val c = s[i++]) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        when (val e = s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                            else -> throw IllegalArgumentException("Bad escape \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        fun num(): Any {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            val t = s.substring(start, i)
            return if (t.any { it in ".eE" }) t.toDouble() else t.toLong()
        }
    }
}

/** Reads and writes the library file stored on the phone. */
object LibraryCodec {
    private const val VERSION = 1

    fun encode(data: LibraryData): String = Json.stringify(
        mapOf(
            "version" to VERSION,
            "folders" to data.folders.map { mapOf("id" to it.id, "parent" to it.parentId, "name" to it.name, "pos" to it.position) },
            "decks" to data.decks.map { mapOf("id" to it.id, "folder" to it.folderId, "name" to it.name, "pos" to it.position) },
            "cards" to data.cards.map {
                val r = it.review
                mapOf(
                    "id" to it.id, "deck" to it.deckId, "front" to it.front, "back" to it.back,
                    "due" to r.due, "s" to r.stability, "d" to r.difficulty,
                    "reps" to r.reps, "lapses" to r.lapses, "last" to r.lastReview,
                    "star" to it.starred,
                )
            },
        ),
    )

    @Suppress("UNCHECKED_CAST")
    fun decode(text: String): LibraryData {
        val root = Json.parse(text) as Map<String, Any?>
        fun list(key: String) = (root[key] as? List<Map<String, Any?>>).orEmpty()
        fun Map<String, Any?>.str(k: String) = this[k] as? String
        fun Map<String, Any?>.long(k: String) = (this[k] as? Number)?.toLong() ?: 0L
        fun Map<String, Any?>.dbl(k: String) = (this[k] as? Number)?.toDouble() ?: 0.0
        return LibraryData(
            folders = list("folders").map {
                Folder(it.str("id")!!, it.str("parent"), it.str("name").orEmpty(), it.long("pos").toInt())
            },
            decks = list("decks").map {
                Deck(it.str("id")!!, it.str("folder"), it.str("name").orEmpty(), it.long("pos").toInt())
            },
            cards = list("cards").map {
                Card(
                    id = it.str("id")!!, deckId = it.str("deck")!!,
                    front = it.str("front").orEmpty(), back = it.str("back").orEmpty(),
                    review = ReviewState(
                        due = it.long("due"), stability = it.dbl("s"), difficulty = it.dbl("d"),
                        reps = it.long("reps").toInt(), lapses = it.long("lapses").toInt(),
                        lastReview = it.long("last"),
                    ),
                    starred = it["star"] == true,
                )
            },
        )
    }
}
